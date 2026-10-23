/** Ngữ cảnh dùng chung cho các API giả lập: CSDL, người dùng hiện tại, nhật ký, thông báo, tiện ích nghiệp vụ hồ sơ */
import { HttpResponse } from 'msw';
import type {
  Application, ApplicationStatus, ApplicationSummary, AppDocument, CrossCheckWarning, ExtractedField, Recalculation,
  ScreeningRequest,
} from '../api/types';
import { loadDb, saveDb, type Account, type AppRecord, type Db, SYSTEM_DOC_TYPE } from './db';
import { simulate, toParamSet, toWeightSet, ageAt } from './engine';

export const db: Db = loadDb();
export const API = '/api';
export const persist = () => saveDb(db);
export const nextId = () => ++db.seq;
export const err = (status: number, message: string) => HttpResponse.json({ message }, { status });
export const nowIso = () => new Date().toISOString();
export const DEV_OTP = '123456';

export function currentUser(req: Request): Account | null {
  const h = req.headers.get('Authorization') || '';
  const id = db.tokens[h.replace(/^Bearer\s+/, '')];
  const a = db.accounts.find(x => x.id === id) || null;
  return a && a.status === 'ACTIVE' ? a : null;
}

export const activeVersion = () => db.versions.find(v => v.status === 'ACTIVE')!;
export const paramsOf = (vid: number) => toParamSet(db.params[vid]);
export const weightsOf = (vid: number) => toWeightSet(db.weights[vid]);
export const paramValue = (key: string) => Number(db.params[activeVersion().id].find(p => p.key === key && p.productCode === null)?.value ?? 0);
export const branchOf = (id: number) => db.branches.find(b => b.id === id);

export function log(actor: Account | null, action: string, detail: string, app?: AppRecord | null, actorType?: 'CUSTOMER' | 'STAFF' | 'SYSTEM') {
  db.logs.push({
    id: nextId(), customerId: app?.customerId ?? (actor?.role === 'CUSTOMER' ? actor.id : null), applicationCode: app?.code ?? null,
    actorType: actorType ?? (actor ? (actor.role === 'CUSTOMER' ? 'CUSTOMER' : 'STAFF') : 'SYSTEM'),
    actorName: actor?.name ?? 'Hệ thống', action, detail, createdAt: nowIso(),
  });
}

export function notify(target: { customerId?: number | null; branchId?: number | null }, title: string, body: string, link: string | null) {
  db.notifications.push({ id: nextId(), customerId: target.customerId ?? null, branchId: target.branchId ?? null, title, body, link, createdAt: nowIso(), read: false });
}

export const STATUS_LABEL: Record<ApplicationStatus, string> = {
  DRAFTING: 'Đang lập hồ sơ', WAITING_VERIFY: 'Chờ đối chiếu', NEED_MORE: 'Cần bổ sung', VERIFIED: 'Đã đối chiếu',
  APPROVED: 'Được phê duyệt', DISBURSED: 'Đã giải ngân', REJECTED: 'Bị từ chối', CANCELLED: 'Đã hủy', EXPIRED: 'Hết hạn',
};
export const OPEN_STATUSES: ApplicationStatus[] = ['DRAFTING', 'WAITING_VERIFY', 'NEED_MORE'];

export function setStatus(app: AppRecord, s: ApplicationStatus, actor: string, note: string | null = null) {
  app.status = s;
  app.updatedAt = nowIso();
  app.history.push({ status: s, at: app.updatedAt, actor, note });
}

export const screeningOf = (app: AppRecord) => db.screenings.find(s => s.id === app.screeningId) ?? null;

export function summary(a: AppRecord): ApplicationSummary {
  return {
    id: a.id, code: a.code, customerName: a.personal.fullName || 'Chưa khai', customerPhone: a.personal.phone,
    productCode: a.plan.productCode, productName: a.plan.productName, amount: a.plan.amount, termMonths: a.plan.termMonths,
    score: a.plan.score, branchName: branchOf(a.branchId)?.name ?? '', status: a.status, qrToken: a.qrToken,
    createdAt: a.createdAt, updatedAt: a.updatedAt, expiresAt: a.expiresAt,
  };
}

/** Hồ sơ trả về cho người xem, kèm các thao tác được phép */
export function view(a: AppRecord, viewer: Account): Application {
  const b = branchOf(a.branchId);
  const isOwner = viewer.role === 'CUSTOMER' && viewer.id === a.customerId;
  const isStaff = viewer.role === 'BRANCH_STAFF' && viewer.branchId === a.branchId;
  return {
    ...a, branchName: b?.name ?? '', branchAddress: b?.address ?? '',
    can: {
      edit: isOwner && (a.status === 'DRAFTING' || a.status === 'NEED_MORE' || a.status === 'EXPIRED'),
      submit: isOwner && a.status === 'DRAFTING',
      resubmit: isOwner && (a.status === 'NEED_MORE' || a.status === 'EXPIRED'),
      cancel: isOwner && OPEN_STATUSES.concat(['EXPIRED']).includes(a.status),
      verify: isStaff && a.status === 'WAITING_VERIFY',
      decide: isStaff && a.status === 'VERIFIED',
      disburse: isStaff && a.status === 'APPROVED',
    },
  };
}

export function canSee(a: AppRecord, u: Account) {
  return (u.role === 'CUSTOMER' && u.id === a.customerId) || (u.role === 'BRANCH_STAFF' && u.branchId === a.branchId) || u.role === 'ADMIN';
}

// ---------------------------- máy đọc giấy tờ (OCR giả lập) ----------------------------

const fmtMoney = (v: number) => Math.round(v).toLocaleString('vi-VN');
const parseMoney = (s: string | null | undefined) => Number(String(s ?? '').replace(/[^\d]/g, '')) || 0;

/** Sinh giá trị máy đọc dựa trên thông tin khai báo, có sai lệch nhỏ để minh họa bước xác nhận và đối chiếu */
export function ocrFields(typeId: number, app: AppRecord, inp: ScreeningRequest | null, seed: number): ExtractedField[] {
  const f = (name: string, label: string, v: string, conf: number): ExtractedField =>
    ({ id: nextId(), name, label, ocrValue: v, confidence: conf, confirmedValue: null });
  const p = app.personal;
  const jitter = ((seed % 13) - 6) / 100; // -6%..+6%
  const income = inp?.avgIncome ?? 15_000_000;
  switch (typeId) {
    case 2:
      return [
        f('full_name', 'Họ và tên', (p.fullName || '').toUpperCase(), 97),
        f('national_id', 'Số CCCD', p.nationalId || '', 95),
        f('dob', 'Ngày sinh', (p.dob || inp?.dob || '').split('-').reverse().join('/'), 93),
        f('gender', 'Giới tính', (p.gender || inp?.gender) === 'FEMALE' ? 'Nữ' : 'Nam', 98),
        f('address', 'Nơi thường trú', p.address || '', 81),
      ];
    case 3:
      return [f('address', 'Địa chỉ cư trú', p.address || '', 84)];
    case 4:
      return [
        f('employer', 'Đơn vị công tác', 'Công ty CP Công nghệ Sao Mai', 88),
        f('salary', 'Mức lương theo hợp đồng', fmtMoney(Math.round(income * 1.05 / 1e5) * 1e5), 86),
      ];
    case 5:
      return [
        f('salary_bank', 'Ngân hàng trả lương', inp?.salaryBank ?? '', 92),
        f('avg_income', 'Lương chuyển khoản bình quân 03 tháng', fmtMoney(Math.round(income * (1 + jitter) / 1e4) * 1e4), 90),
      ];
    case 6:
      return [
        f('beneficiary', 'Bên thụ hưởng', 'Công ty TNHH Thương mại Hòa Bình', 87),
        f('invoice_amount', 'Giá trị hóa đơn, hợp đồng', fmtMoney(Math.round(app.plan.amount * 1.08 / 1e6) * 1e6), 89),
      ];
    default:
      return [];
  }
}

const fieldVal = (docs: AppDocument[], typeId: number, name: string) => {
  const d = docs.find(x => x.documentTypeId === typeId && x.status === 'CONFIRMED');
  const fl = d?.fields.find(x => x.name === name);
  return fl ? (fl.confirmedValue ?? fl.ocrValue) : null;
};

/** Bước (29)–(30): đối chiếu MB06 với MB01, giữa các giấy tờ; tính lại DTI theo giá trị kiểm chứng */
export function crossCheck(app: AppRecord): { warnings: CrossCheckWarning[]; recalc: Recalculation | null } {
  const inp = screeningOf(app)?.input ?? null;
  const w: CrossCheckWarning[] = [];
  const p = app.personal;
  const name = fieldVal(app.documents, 2, 'full_name');
  if (name && p.fullName && name.trim().toUpperCase() !== p.fullName.trim().toUpperCase())
    w.push({ field: 'Họ và tên', declared: p.fullName, verified: name, message: 'Họ tên trên CCCD khác thông tin cá nhân đã khai.', severity: 'WARNING' });
  const nid = fieldVal(app.documents, 2, 'national_id');
  if (nid && p.nationalId && nid.replace(/\s/g, '') !== p.nationalId)
    w.push({ field: 'Số CCCD', declared: p.nationalId, verified: nid, message: 'Số CCCD trên giấy tờ khác số đã khai.', severity: 'BLOCKING' });
  if (nid && !/^\d{12}$/.test(nid.replace(/\s/g, '')))
    w.push({ field: 'Số CCCD', declared: p.nationalId, verified: nid, message: 'Số CCCD phải gồm 12 chữ số.', severity: 'BLOCKING' });
  const dob = fieldVal(app.documents, 2, 'dob');
  if (dob && inp) {
    const iso = dob.split('/').reverse().join('-');
    if (iso !== inp.dob) w.push({ field: 'Ngày sinh', declared: inp.dob.split('-').reverse().join('/'), verified: dob, message: 'Ngày sinh trên CCCD khác ngày sinh đã khai khi sàng lọc; điều kiện tuổi được kiểm tra lại.', severity: 'WARNING' });
  }
  const bank = fieldVal(app.documents, 5, 'salary_bank');
  if (bank && inp && bank !== inp.salaryBank)
    w.push({ field: 'Ngân hàng nhận lương', declared: inp.salaryBank, verified: bank, message: 'Ngân hàng trên sao kê khác ngân hàng đã khai.', severity: 'INFO' });
  const inv = fieldVal(app.documents, 6, 'invoice_amount');
  if (inv && parseMoney(inv) > 0 && parseMoney(inv) < app.plan.amount)
    w.push({ field: 'Giá trị hóa đơn', declared: fmtMoney(app.plan.amount), verified: inv, message: 'Số tiền vay vượt giá trị hóa đơn, hợp đồng mua bán.', severity: 'BLOCKING' });
  let recalc: Recalculation | null = null;
  const incStr = fieldVal(app.documents, 5, 'avg_income');
  if (incStr && inp) {
    const verified = parseMoney(incStr);
    if (verified > 0) {
      const diff = (verified - inp.avgIncome) / inp.avgIncome;
      if (Math.abs(diff) >= 0.05)
        w.push({ field: 'Thu nhập bình quân', declared: fmtMoney(inp.avgIncome), verified: incStr,
          message: `Thu nhập trên sao kê ${diff < 0 ? 'thấp' : 'cao'} hơn khai báo ${Math.abs(Math.round(diff * 100))}%; DTI và điểm được tính lại theo sao kê.`, severity: 'WARNING' });
      const v = activeVersion();
      const sim = simulate({ ...inp, avgIncome: verified }, paramsOf(v.id), weightsOf(v.id),
        { screeningId: '', productCode: app.plan.productCode, amount: app.plan.amount, termMonths: app.plan.termMonths, crossSellIds: app.plan.crossSells.map(c => c.id) },
        db.crossSells);
      recalc = { verifiedIncome: verified, dtiAfter: sim.dtiAfter, maxSafeAmount: sim.maxSafeAmount, score: sim.score, ok: sim.ok, issues: sim.issues };
      if (!sim.ok) w.push({ field: 'Phương án vay', declared: fmtMoney(app.plan.amount), verified: fmtMoney(sim.maxSafeAmount),
        message: 'Theo thu nhập kiểm chứng, phương án không còn đạt ngưỡng an toàn; cần điều chỉnh số tiền hoặc kỳ hạn.', severity: 'BLOCKING' });
    }
  }
  if (inp && p.dob && p.dob !== inp.dob) {
    const age = ageAt(p.dob);
    if (age < (app.plan.productCode === 'TD' ? 22 : 19))
      w.push({ field: 'Tuổi', declared: String(ageAt(inp.dob)), verified: String(age), message: 'Theo ngày sinh thực tế, chưa đủ tuổi tối thiểu của gói vay.', severity: 'BLOCKING' });
  }
  return { warnings: w, recalc };
}

export function refreshChecks(app: AppRecord) {
  const r = crossCheck(app);
  app.warnings = r.warnings;
  app.recalc = r.recalc;
}

export function missingForSubmit(app: AppRecord): string[] {
  const m: string[] = [];
  const p = app.personal;
  if (!p.fullName || !p.nationalId || !p.address || !p.dob) m.push('Chưa khai đủ thông tin cá nhân (họ tên, số CCCD, ngày sinh, địa chỉ).');
  for (const d of app.documents) {
    if (d.documentTypeId === SYSTEM_DOC_TYPE) continue;
    if (d.required && d.status !== 'CONFIRMED') m.push(`Chưa ${d.status === 'UPLOADED' ? 'xác nhận thông tin' : 'tải lên'}: ${d.typeName}.`);
  }
  if (app.warnings.some(w => w.severity === 'BLOCKING')) m.push('Còn sai lệch cần xử lý (xem mục Cảnh báo đối chiếu).');
  return m;
}

export function makeQr() {
  return 'QR' + Math.random().toString(36).slice(2, 10).toUpperCase();
}

export async function sha256(text: string) {
  const buf = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text));
  return Array.from(new Uint8Array(buf)).map(b => b.toString(16).padStart(2, '0')).join('');
}

/** Ảnh thu nhỏ cho bản xem trước (chỉ ảnh); backend thật trả đường dẫn tải có thời hạn */
export async function thumbnail(file: File): Promise<string | null> {
  if (!file.type.startsWith('image/')) return null;
  try {
    const bmp = await createImageBitmap(file);
    const scale = Math.min(1, 360 / Math.max(bmp.width, bmp.height));
    const c = document.createElement('canvas');
    c.width = Math.round(bmp.width * scale); c.height = Math.round(bmp.height * scale);
    c.getContext('2d')!.drawImage(bmp, 0, 0, c.width, c.height);
    return c.toDataURL('image/jpeg', 0.7);
  } catch {
    return null;
  }
}

export { fmtMoney, parseMoney };
