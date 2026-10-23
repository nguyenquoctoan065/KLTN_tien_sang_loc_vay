/** Dữ liệu cho API giả lập – cùng cấu trúc với database/*.sql. Lưu vào localStorage để giữ khi tải lại trang. */
import type {
  ActivityLog, Application, ApplicationStatus, AppDocument, AppNotification, Branch, ConsentRecord, CrossSellProduct,
  DocumentType, JobInfo, LoanPlan, LoanProduct, ParamVersion, PolicyParam, ProductCode, ProductDocRule, ScoringWeight,
  ScreeningResponse, TestRunResult,
} from '../api/types';
import { APP_STATUS } from '../utils/status';

export interface Account {
  id: number;
  kind: 'CUSTOMER' | 'STAFF';
  username: string;
  email?: string | null;
  password: string;
  name: string;
  role: 'CUSTOMER' | 'BRANCH_STAFF' | 'ADMIN';
  branchId: number | null;
  status: 'ACTIVE' | 'LOCKED';
  createdAt: string;
}

/** Hồ sơ lưu trong CSDL giả lập (không có trường `can` – được tính theo người xem) */
export type AppRecord = Omit<Application, 'can' | 'branchName' | 'branchAddress'> & {
  /** điểm hệ thống tại thời điểm nộp – dùng cho 8.2 */
  predictedScore: number;
  screeningId: string | null;
};

export interface NotificationRecord extends AppNotification {
  customerId: number | null;
  branchId: number | null;
}

export interface LogRecord extends ActivityLog { customerId: number | null; }
export interface ConsentRow extends ConsentRecord { customerId: number | null; }

export interface Db {
  version: number;
  seq: number;
  accounts: Account[];
  branches: Branch[];
  products: LoanProduct[];
  crossSells: CrossSellProduct[];
  docTypes: DocumentType[];
  productDocs: ProductDocRule[];
  versions: ParamVersion[];
  params: Record<number, PolicyParam[]>;
  weights: Record<number, ScoringWeight[]>;
  testResults: Record<number, TestRunResult>;
  screenings: ScreeningResponse[];
  plans: (LoanPlan & { customerId: number })[];
  applications: AppRecord[];
  notifications: NotificationRecord[];
  logs: LogRecord[];
  consents: ConsentRow[];
  jobs: JobInfo[];
  /** mốc thời gian lượt sàng lọc, phương án của dữ liệu lịch sử (phục vụ phễu chuyển đổi ở 8.1) */
  past: { screenings: string[]; plans: string[] };
  tokens: Record<string, number>;
}

export const DB_VERSION = 9;

export const PARAM_LABELS: Record<string, string> = {
  DTI_MAX: 'Tỷ lệ DTI tối đa',
  LIVING_COST_BASE: 'Chi phí sinh hoạt định mức của người vay',
  LIVING_COST_PER_DEPENDENT: 'Chi phí sinh hoạt định mức mỗi người phụ thuộc',
  RETIRE_AGE_MALE: 'Tuổi nghỉ hưu – nam',
  RETIRE_AGE_FEMALE: 'Tuổi nghỉ hưu – nữ',
  ROOM_FACTOR: 'Hệ số room tín dụng / tỷ trọng dư nợ tín chấp',
  MAX_RATE_DISCOUNT: 'Mức giảm lãi suất tối đa từ bán chéo',
  DOC_VALIDITY_DAYS: 'Thời hạn hiệu lực giấy tờ',
  RETENTION_MONTHS: 'Thời hạn lưu trữ hồ sơ đã đóng',
  MIN_AGE: 'Tuổi tối thiểu',
  INCOME_MULTIPLIER: 'Hệ số hạn mức theo thu nhập',
  MAX_AMOUNT: 'Trần hạn mức',
  MIN_AMOUNT: 'Số tiền vay tối thiểu',
  MAX_TERM: 'Kỳ hạn tối đa',
  MIN_TERM: 'Kỳ hạn tối thiểu',
  REF_RATE: 'Lãi suất tham chiếu',
};

export const CRITERIA_LABELS: Record<string, string> = {
  DTI: 'Tỷ lệ DTI sau vay',
  AMOUNT_RATIO: 'Số tiền mong muốn so với hạn mức an toàn',
  SALARY_BANK: 'Ngân hàng nhận lương',
  STAFF: 'Cán bộ nhân viên ABBANK',
  TERM_TO_RETIRE: 'Thời gian còn lại đến tuổi nghỉ hưu',
};

/** Giấy đề nghị vay vốn do hệ thống lập ở bước 4.2, không tải lên */
export const SYSTEM_DOC_TYPE = 1;

function seedParams(): PolicyParam[] {
  const rows: [ProductCode | null, string, string, string][] = [
    [null, 'DTI_MAX', '70', '%'], [null, 'LIVING_COST_BASE', '4000000', 'VNĐ/tháng'],
    [null, 'LIVING_COST_PER_DEPENDENT', '2000000', 'VNĐ/tháng'], [null, 'RETIRE_AGE_MALE', '62', 'tuổi'],
    [null, 'RETIRE_AGE_FEMALE', '60', 'tuổi'], [null, 'ROOM_FACTOR', '100', '%'], [null, 'MAX_RATE_DISCOUNT', '2', '%/năm'],
    [null, 'DOC_VALIDITY_DAYS', '30', 'ngày'], [null, 'RETENTION_MONTHS', '12', 'tháng'],
    ['TD', 'MIN_AGE', '22', 'tuổi'], ['TD', 'INCOME_MULTIPLIER', '15', 'lần thu nhập'], ['TD', 'MAX_AMOUNT', '500000000', 'VNĐ'],
    ['TD', 'MIN_AMOUNT', '10000000', 'VNĐ'], ['TD', 'MAX_TERM', '60', 'tháng'], ['TD', 'MIN_TERM', '6', 'tháng'], ['TD', 'REF_RATE', '12.5', '%/năm'],
    ['TC', 'MIN_AGE', '19', 'tuổi'], ['TC', 'MAX_AMOUNT', '200000000', 'VNĐ'], ['TC', 'MIN_AMOUNT', '10000000', 'VNĐ'],
    ['TC', 'MAX_TERM', '12', 'tháng'], ['TC', 'MIN_TERM', '12', 'tháng'], ['TC', 'REF_RATE', '18', '%/năm'],
  ];
  return rows.map(([c, k, v, u], i) => ({ id: i + 1, productCode: c, key: k, label: PARAM_LABELS[k], value: v, unit: u }));
}

function seedWeights(): ScoringWeight[] {
  const w: [ProductCode, string, number][] = [
    ['TD', 'DTI', 35], ['TD', 'AMOUNT_RATIO', 25], ['TD', 'SALARY_BANK', 15], ['TD', 'STAFF', 10], ['TD', 'TERM_TO_RETIRE', 15],
    ['TC', 'DTI', 30], ['TC', 'AMOUNT_RATIO', 20], ['TC', 'SALARY_BANK', 25], ['TC', 'STAFF', 20], ['TC', 'TERM_TO_RETIRE', 5],
  ];
  return w.map(([p, c, x], i) => ({ id: i + 1, productCode: p, criterion: c, label: CRITERIA_LABELS[c], weight: x }));
}

/** Bộ sinh số ngẫu nhiên có hạt giống để dữ liệu mẫu ổn định giữa các lần tải */
function mulberry32(a: number) {
  return () => {
    a |= 0; a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const HO = ['Nguyễn', 'Trần', 'Lê', 'Phạm', 'Hoàng', 'Vũ', 'Võ', 'Đặng', 'Bùi', 'Đỗ', 'Hồ', 'Ngô', 'Dương', 'Lý'];
const DEM_NAM = ['Văn', 'Minh', 'Quang', 'Đức', 'Hữu', 'Thành', 'Anh', 'Gia'];
const DEM_NU = ['Thị', 'Thu', 'Ngọc', 'Thanh', 'Minh', 'Phương', 'Hải'];
const TEN_NAM = ['An', 'Bình', 'Cường', 'Dũng', 'Hùng', 'Huy', 'Khoa', 'Long', 'Nam', 'Phong', 'Quân', 'Sơn', 'Tuấn', 'Việt'];
const TEN_NU = ['Anh', 'Chi', 'Giang', 'Hà', 'Hạnh', 'Hương', 'Lan', 'Linh', 'Mai', 'Ngân', 'Trang', 'Vy', 'Yến', 'Thảo'];
const STREETS = ['Láng Hạ', 'Trần Duy Hưng', 'Nguyễn Trãi', 'Lê Lợi', 'Hai Bà Trưng', 'Điện Biên Phủ', 'Nguyễn Văn Linh', 'Bạch Đằng'];
const PURPOSES_TD = ['Mua sắm đồ dùng, thiết bị', 'Mua xe máy, ô tô', 'Sửa chữa, nâng cấp nhà ở', 'Học tập', 'Chữa bệnh', 'Cưới hỏi'];

const iso = (d: Date) => d.toISOString();
const addDays = (d: Date, n: number) => new Date(d.getTime() + n * 864e5);

export function docTypesSeed(): DocumentType[] {
  return [
    { id: 1, name: 'Giấy đề nghị vay vốn (mẫu ngân hàng)', group: 'Khác', allowedFormats: 'PDF', validityDays: null, status: 'ACTIVE' },
    { id: 2, name: 'Căn cước công dân', group: 'Pháp lý', allowedFormats: 'JPG,PNG,PDF', validityDays: null, status: 'ACTIVE' },
    { id: 3, name: 'Xác nhận cư trú', group: 'Pháp lý', allowedFormats: 'JPG,PNG,PDF', validityDays: 180, status: 'ACTIVE' },
    { id: 4, name: 'Hợp đồng lao động hoặc xác nhận nhân sự', group: 'Thu nhập', allowedFormats: 'JPG,PNG,PDF', validityDays: 30, status: 'ACTIVE' },
    { id: 5, name: 'Sao kê tài khoản nhận lương 03 tháng', group: 'Thu nhập', allowedFormats: 'PDF,JPG,PNG', validityDays: 30, status: 'ACTIVE' },
    { id: 6, name: 'Hóa đơn, hợp đồng mua bán hoặc hợp đồng 3 bên', group: 'Mục đích', allowedFormats: 'JPG,PNG,PDF', validityDays: 30, status: 'ACTIVE' },
  ];
}

export function productDocsSeed(): ProductDocRule[] {
  return [
    { productCode: 'TD', documentTypeId: 1, required: true }, { productCode: 'TD', documentTypeId: 2, required: true },
    { productCode: 'TD', documentTypeId: 3, required: true }, { productCode: 'TD', documentTypeId: 4, required: true },
    { productCode: 'TD', documentTypeId: 5, required: true }, { productCode: 'TD', documentTypeId: 6, required: true },
    { productCode: 'TC', documentTypeId: 1, required: true }, { productCode: 'TC', documentTypeId: 2, required: true },
    { productCode: 'TC', documentTypeId: 5, required: true }, { productCode: 'TC', documentTypeId: 4, required: false },
  ];
}

/** Sinh khoảng 90 hồ sơ lịch sử trong 6 tháng (phục vụ 5.1, 8.1–8.3) */
function historical(rnd: () => number, accounts: Account[], branches: Branch[], docTypes: DocumentType[], rules: ProductDocRule[], nextId: () => number) {
  const apps: AppRecord[] = [];
  const now = new Date();
  const pick = <T,>(a: T[]) => a[Math.floor(rnd() * a.length)];
  const N = 90;
  for (let i = 0; i < N; i++) {
    const male = rnd() < 0.5;
    const name = `${pick(HO)} ${pick(male ? DEM_NAM : DEM_NU)} ${pick(male ? TEN_NAM : TEN_NU)}`;
    const phone = `09${String(10000000 + Math.floor(rnd() * 89999999)).slice(0, 8)}`;
    const cid = nextId();
    accounts.push({ id: cid, kind: 'CUSTOMER', username: phone, password: '123456', name, role: 'CUSTOMER', branchId: null, status: 'ACTIVE', createdAt: '' });
    const daysAgo = Math.floor(Math.pow(rnd(), 0.8) * 180) + 1;
    const created = addDays(now, -daysAgo);
    accounts[accounts.length - 1].createdAt = iso(addDays(created, -1));
    const code: ProductCode = rnd() < 0.68 ? 'TD' : 'TC';
    const income = Math.round((9 + rnd() * 36) * 1e6 / 5e5) * 5e5;
    const amount = code === 'TD' ? Math.round((30 + rnd() * 320) / 5) * 5e6 : Math.round((10 + rnd() * 120) / 5) * 5e6;
    const term = code === 'TD' ? pick([12, 24, 36, 48, 60]) : 12;
    const rate = code === 'TD' ? 12.5 : 18;
    const score = Math.round((35 + rnd() * 60) * 10) / 10;
    const branch = branches[Math.floor(rnd() * branches.length)];
    // trạng thái: hồ sơ cũ đã đóng, hồ sơ gần đây còn mở
    let status: ApplicationStatus;
    const pApprove = 1 / (1 + Math.exp(-(score - 58) / 7));
    if (daysAgo > 25) {
      const r = rnd();
      status = r < 0.08 ? 'CANCELLED' : r < 0.13 ? 'EXPIRED' : rnd() < pApprove ? 'DISBURSED' : 'REJECTED';
    } else {
      status = pick<ApplicationStatus>(['DRAFTING', 'WAITING_VERIFY', 'WAITING_VERIFY', 'NEED_MORE', 'VERIFIED', 'VERIFIED', 'APPROVED']);
    }
    const monthly = code === 'TD' ? Math.round(amount / term + amount * rate / 1200) : Math.round(amount * rate / 1200);
    const plan: LoanPlan = {
      id: crypto.randomUUID(), screeningId: '', productCode: code, productName: code === 'TD' ? 'Vay tiêu dùng không tài sản bảo đảm' : 'Vay thấu chi không tài sản bảo đảm',
      amount, termMonths: term, refRate: rate, monthlyPayment: monthly, totalInterest: Math.round(code === 'TD' ? amount * rate / 1200 * (term + 1) / 2 : amount * rate / 100),
      dtiAfter: Math.round(monthly / income * 1000) / 10, score, status: 'CONFIRMED', crossSells: [], createdAt: iso(created), checklist: [],
    };
    const dob = new Date(1965 + Math.floor(rnd() * 37), Math.floor(rnd() * 12), 1 + Math.floor(rnd() * 27));
    const personal = {
      fullName: name, nationalId: `0${Math.floor(1e10 + rnd() * 8.9e10)}`, dob: dob.toISOString().slice(0, 10), gender: (male ? 'MALE' : 'FEMALE') as 'MALE' | 'FEMALE',
      address: `${1 + Math.floor(rnd() * 300)} ${pick(STREETS)}, ${branch.address}`, phone, email: '',
    };
    const purpose = code === 'TD' ? pick(PURPOSES_TD) : 'Chi tiêu cá nhân linh hoạt';
    const submitted = status !== 'DRAFTING';
    const docs: AppDocument[] = rules.filter(r => r.productCode === code).map(r => {
      const t = docTypes.find(d => d.id === r.documentTypeId)!;
      const has = submitted || rnd() < 0.5;
      return {
        id: nextId(), documentTypeId: t.id, typeName: t.name, group: t.group, required: r.required, allowedFormats: t.allowedFormats,
        status: has ? 'CONFIRMED' : 'MISSING', fileName: has ? (t.id === SYSTEM_DOC_TYPE ? 'giay-de-nghi-vay-von.pdf' : `tai-lieu-${t.id}.pdf`) : null,
        fileFormat: has ? 'PDF' : null, fileSize: has ? 300 + Math.floor(rnd() * 900) : null, versionNo: 1,
        issuedDate: has ? iso(addDays(created, -3)).slice(0, 10) : null, qualityStatus: has ? 'OK' : null, qualityMessage: null,
        uploadedAt: has ? iso(addDays(created, 1)) : null, previewUrl: null, fileUrl: null,
        fields: [], verifyResult: null, verifyReason: null,
      };
    });
    const actorStaff = accounts.find(a => a.role === 'BRANCH_STAFF' && a.branchId === branch.id);
    const staffName = actorStaff?.name ?? 'Nhân viên chi nhánh';
    const history: AppRecord['history'] = [{ status: 'DRAFTING', at: iso(created), actor: name, note: null }];
    let t = addDays(created, 1 + rnd() * 2);
    const ev = (s: ApplicationStatus, actor: string, note: string | null = null, gap = 1 + rnd() * 3) => {
      history.push({ status: s, at: iso(t), actor, note }); t = addDays(t, gap);
    };
    let approval: AppRecord['approval'] = null;
    const verifications: AppRecord['verifications'] = [];
    if (submitted && status !== 'CANCELLED') ev('WAITING_VERIFY', name);
    if (status === 'CANCELLED') ev('CANCELLED', name, 'Khách hàng hủy hồ sơ');
    if (status === 'EXPIRED') { ev('EXPIRED', 'Hệ thống', 'Giấy tờ quá thời hạn hiệu lực'); }
    if (status === 'NEED_MORE') {
      verifications.push({ id: nextId(), staffName, overallResult: 'MISMATCH', note: 'Sao kê lương không có dấu xác nhận', verifiedAt: iso(t),
        details: docs.filter(d => d.documentTypeId !== SYSTEM_DOC_TYPE).map(d => ({ documentId: d.id, typeName: d.typeName,
          result: d.documentTypeId === 5 ? 'MISMATCH' : 'MATCH', reason: d.documentTypeId === 5 ? 'Sao kê lương không có dấu xác nhận của ngân hàng' : '' })) });
      const d5 = docs.find(d => d.documentTypeId === 5); if (d5) { d5.verifyResult = 'MISMATCH'; d5.verifyReason = 'Sao kê lương không có dấu xác nhận của ngân hàng'; }
      ev('NEED_MORE', staffName, 'Sao kê lương không có dấu xác nhận');
    }
    if (['VERIFIED', 'APPROVED', 'DISBURSED', 'REJECTED'].includes(status)) {
      verifications.push({ id: nextId(), staffName, overallResult: 'MATCH', note: 'Giấy tờ khớp bản gốc', verifiedAt: iso(t),
        details: docs.filter(d => d.documentTypeId !== SYSTEM_DOC_TYPE).map(d => ({ documentId: d.id, typeName: d.typeName, result: 'MATCH', reason: '' })) });
      docs.forEach(d => { if (d.documentTypeId !== SYSTEM_DOC_TYPE) d.verifyResult = 'MATCH'; });
      ev('VERIFIED', staffName, null, 1 + rnd() * 2);
    }
    if (['APPROVED', 'DISBURSED'].includes(status)) {
      approval = { id: nextId(), resultType: 'APPROVED', approvedAmount: amount, approvedTerm: term, approvedRate: rate, reason: null,
        recordedAt: iso(t), staffName, disbursedAt: null, disbursedAmount: null, beneficiary: null };
      ev('APPROVED', staffName, null, 1 + rnd() * 3);
    }
    if (status === 'DISBURSED' && approval) {
      approval.disbursedAt = iso(t).slice(0, 10); approval.disbursedAmount = amount;
      approval.beneficiary = code === 'TD' ? 'Đơn vị cung cấp hàng hóa, dịch vụ' : 'Tài khoản thanh toán của khách hàng';
      ev('DISBURSED', staffName);
    }
    if (status === 'REJECTED') {
      approval = { id: nextId(), resultType: 'REJECTED', approvedAmount: null, approvedTerm: null, approvedRate: null,
        reason: pick(['Lịch sử tín dụng (CIC) có nợ cần chú ý', 'Thu nhập thực tế không đủ đáp ứng nghĩa vụ trả nợ', 'Vượt room tín dụng của chi nhánh trong kỳ']),
        recordedAt: iso(t), staffName, disbursedAt: null, disbursedAmount: null, beneficiary: null };
      ev('REJECTED', staffName, approval.reason);
    }
    const last = history[history.length - 1];
    const year = String(created.getFullYear()).slice(2);
    const mon = String(created.getMonth() + 1).padStart(2, '0');
    apps.push({
      id: crypto.randomUUID(), code: `HS${year}${mon}-${String(i + 1).padStart(4, '0')}`, customerId: cid, status, branchId: branch.id, plan, purpose, personal,
      consentBAt: iso(created), versionNo: '2026.10', contentHash: submitted ? Array.from({ length: 64 }, () => '0123456789abcdef'[Math.floor(rnd() * 16)]).join('') : null,
      lockedAt: submitted ? history[1]?.at ?? null : null, qrToken: submitted ? `QR${Math.floor(rnd() * 1e10).toString(36).toUpperCase()}` : null,
      expiresAt: submitted ? iso(addDays(created, 31)) : null, createdAt: iso(created), updatedAt: last.at, documents: docs, warnings: [], recalc: null,
      requestForm: submitted ? { status: 'CONFIRMED', content: [], createdAt: iso(created), otpConfirmedAt: history[1]?.at ?? null } : null,
      verifications, approval, history, predictedScore: score, screeningId: null,
    });
  }
  return apps;
}

export function seed(): Db {
  let seq = 1000;
  const nextId = () => ++seq;
  const rnd = mulberry32(20261021);
  const now = new Date();
  const branches: Branch[] = [
    { id: 1, code: 'HN01', name: 'Chi nhánh Hà Nội', address: 'Hà Nội', phone: '02400000001', status: 'ACTIVE' },
    { id: 2, code: 'HCM01', name: 'Chi nhánh Hồ Chí Minh', address: 'TP. Hồ Chí Minh', phone: '02800000001', status: 'ACTIVE' },
    { id: 3, code: 'DN01', name: 'Chi nhánh Đà Nẵng', address: 'Đà Nẵng', phone: '02360000001', status: 'ACTIVE' },
  ];
  const created = iso(addDays(now, -200));
  const accounts: Account[] = [
    { id: 1, kind: 'CUSTOMER', username: '0901234567', email: 'khachhang@tiensangloc.local', password: '123456', name: 'Khách hàng mẫu', role: 'CUSTOMER', branchId: null, status: 'ACTIVE', createdAt: created },
    { id: 101, kind: 'STAFF', username: 'nhanvien@tiensangloc.local', password: '123456', name: 'Nguyễn Thu Hà', role: 'BRANCH_STAFF', branchId: 1, status: 'ACTIVE', createdAt: created },
    { id: 103, kind: 'STAFF', username: 'nv.hcm@tiensangloc.local', password: '123456', name: 'Lê Hoàng Phúc', role: 'BRANCH_STAFF', branchId: 2, status: 'ACTIVE', createdAt: created },
    { id: 104, kind: 'STAFF', username: 'nv.danang@tiensangloc.local', password: '123456', name: 'Phan Thị Mỹ', role: 'BRANCH_STAFF', branchId: 3, status: 'ACTIVE', createdAt: created },
    { id: 102, kind: 'STAFF', username: 'quantri@tiensangloc.local', password: '123456', name: 'Trần Minh Đức', role: 'ADMIN', branchId: null, status: 'ACTIVE', createdAt: created },
  ];
  const docTypes = docTypesSeed();
  const rules = productDocsSeed();
  const applications = historical(rnd, accounts, branches, docTypes, rules, nextId);
  const consents: ConsentRow[] = applications.map(a => ({ id: nextId(), customerId: a.customerId, scope: 'B', docVersion: 'DK-1.0',
    agreedAt: a.consentBAt, withdrawnAt: null, applicationCode: a.code }));
  const past = { screenings: [] as string[], plans: [] as string[] };
  for (const a of applications) {
    const t0 = Date.parse(a.createdAt);
    const k = 2 + Math.floor(rnd() * 3);
    for (let j = 0; j < k; j++) past.screenings.push(new Date(t0 - rnd() * 5 * 864e5).toISOString());
    past.plans.push(new Date(t0 - rnd() * 864e5).toISOString());
    if (rnd() < 0.5) past.plans.push(new Date(t0 - rnd() * 3 * 864e5).toISOString());
  }
  for (let j = 0; j < 120; j++) past.screenings.push(new Date(now.getTime() - rnd() * 180 * 864e5).toISOString());
  const logs: LogRecord[] = [];
  for (const a of applications) {
    for (const h of a.history) {
      logs.push({ id: nextId(), customerId: a.customerId, applicationCode: a.code, actorType: h.actor === 'Hệ thống' ? 'SYSTEM' : h.actor === a.personal.fullName ? 'CUSTOMER' : 'STAFF',
        actorName: h.actor, action: 'Đổi trạng thái', detail: `→ ${APP_STATUS[h.status].label}${h.note ? ` (${h.note})` : ''}`, createdAt: h.at });
    }
  }
  return {
    version: DB_VERSION,
    seq,
    accounts,
    branches,
    products: [
      { id: 1, code: 'TD', name: 'Vay tiêu dùng không tài sản bảo đảm', productType: 'CONSUMER', repaymentMethod: 'Gốc trả đều hằng tháng, lãi trên dư nợ giảm dần',
        description: 'Phục vụ nhu cầu tiêu dùng hợp pháp; giải ngân vào tài khoản bên thụ hưởng theo hóa đơn, hợp đồng.', status: 'ACTIVE' },
      { id: 2, code: 'TC', name: 'Vay thấu chi không tài sản bảo đảm', productType: 'OVERDRAFT', repaymentMethod: 'Lãi trả hằng tháng, gốc trả cuối kỳ hoặc trả dần',
        description: 'Cấp hạn mức vào tài khoản thanh toán, chi tiêu linh hoạt; không áp dụng cho kinh doanh.', status: 'ACTIVE' },
    ],
    crossSells: [
      { id: 1, name: 'Bảo hiểm nhân thọ', productType: 'INSURANCE', estCost: 8_000_000, scoreBonus: 5, rateDiscount: 1.0, status: 'ACTIVE' },
      { id: 2, name: 'Thẻ tín dụng', productType: 'CARD', estCost: 0, scoreBonus: 3, rateDiscount: 0.5, status: 'ACTIVE' },
      { id: 3, name: 'Chứng chỉ tiền gửi', productType: 'DEPOSIT', estCost: 0, scoreBonus: 4, rateDiscount: 0.7, status: 'ACTIVE' },
      { id: 4, name: 'Tài khoản số đẹp', productType: 'ACCOUNT', estCost: 1_000_000, scoreBonus: 2, rateDiscount: 0.3, status: 'ACTIVE' },
    ],
    docTypes,
    productDocs: rules,
    versions: [
      { id: 1, versionNo: '2026.10', legalBasis: 'Biên bản 01/2026/BBXN-ABBANK-ĐATN ngày 10/10/2026', status: 'ACTIVE',
        createdBy: 'Trần Minh Đức', createdAt: iso(addDays(now, -11)), activatedAt: iso(addDays(now, -10)) },
    ],
    params: { 1: seedParams() },
    weights: { 1: seedWeights() },
    testResults: {},
    screenings: [],
    plans: [],
    applications,
    notifications: [],
    logs,
    consents,
    jobs: [
      { code: 'EXPIRE_DOCUMENTS', name: 'Kiểm tra giấy tờ quá thời hạn hiệu lực', schedule: 'Hằng ngày lúc 01:00', lastRunAt: null, lastResult: null },
      { code: 'PURGE_RETENTION', name: 'Xóa dữ liệu hết thời hạn lưu trữ', schedule: 'Ngày 1 hằng tháng lúc 02:00', lastRunAt: null, lastResult: null },
      { code: 'REMIND_DRAFTS', name: 'Nhắc khách hàng hoàn tất hồ sơ đang lập', schedule: 'Hằng ngày lúc 09:00', lastRunAt: null, lastResult: null },
    ],
    past,
    tokens: {},
  };
}

const KEY = 'tsl-mock-db';

export function loadDb(): Db {
  try {
    const raw = localStorage.getItem(KEY);
    if (raw) {
      const d = JSON.parse(raw) as Db;
      if (d.version === DB_VERSION) return d;
    }
  } catch { /* bỏ qua */ }
  const d = seed();
  saveDb(d);
  return d;
}

export function saveDb(d: Db) {
  try { localStorage.setItem(KEY, JSON.stringify(d)); } catch { /* bỏ qua */ }
}

export function resetDb() {
  try { localStorage.removeItem(KEY); } catch { /* bỏ qua */ }
}
