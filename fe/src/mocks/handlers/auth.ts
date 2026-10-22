import { http, HttpResponse, delay } from 'msw';
import type { LoginRequest, RegisterRequest } from '../../api/types';
import { API, db, err, nextId, persist, currentUser, nowIso, log, DEV_OTP } from '../ctx';

const userOut = (a: (typeof db.accounts)[number]) => ({
  id: a.id, name: a.name, role: a.role, username: a.username,
  branchName: a.branchId ? db.branches.find(b => b.id === a.branchId)?.name : undefined,
});

export const authHandlers = [
  http.post(`${API}/auth/login`, async ({ request }) => {
    await delay(250);
    const b = (await request.json()) as LoginRequest;
    const acc = db.accounts.find(a => a.kind === b.kind && a.username === b.username.trim() && a.password === b.password);
    if (!acc) return err(401, 'Sai tài khoản hoặc mật khẩu.');
    if (acc.status !== 'ACTIVE') return err(403, 'Tài khoản đang bị khóa, liên hệ quản trị viên.');
    const token = `mock-${acc.id}-${Date.now()}`;
    db.tokens[token] = acc.id;
    log(acc, 'Đăng nhập', acc.kind === 'CUSTOMER' ? 'Khách hàng đăng nhập' : 'Nhân viên đăng nhập');
    persist();
    return HttpResponse.json({ token, user: userOut(acc) });
  }),
  http.post(`${API}/auth/logout`, ({ request }) => {
    const h = (request.headers.get('Authorization') || '').replace(/^Bearer\s+/, '');
    const u = currentUser(request);
    if (u) log(u, 'Đăng xuất', '');
    delete db.tokens[h]; persist();
    return HttpResponse.json({ ok: true });
  }),
  http.post(`${API}/auth/otp`, async ({ request }) => {
    await delay(250);
    const b = (await request.json()) as { phone?: string };
    const u = currentUser(request);
    const phone = b.phone || (u?.role === 'CUSTOMER' ? u.username : '');
    if (!/^0\d{9}$/.test(phone)) return err(400, 'Số điện thoại không hợp lệ.');
    return HttpResponse.json({ sent: true, devOtp: DEV_OTP });
  }),
  http.post(`${API}/auth/register`, async ({ request }) => {
    await delay(250);
    const b = (await request.json()) as RegisterRequest;
    if (b.otp !== DEV_OTP) return err(400, 'Mã OTP không đúng.');
    if (db.accounts.some(a => a.kind === 'CUSTOMER' && a.username === b.phone)) return err(409, 'Số điện thoại đã được đăng ký.');
    const acc = { id: nextId(), kind: 'CUSTOMER' as const, username: b.phone, password: b.password, name: `Khách hàng ${b.phone.slice(-4)}`,
      role: 'CUSTOMER' as const, branchId: null, status: 'ACTIVE' as const, createdAt: nowIso() };
    db.accounts.push(acc);
    const token = `mock-${acc.id}-${Date.now()}`;
    db.tokens[token] = acc.id;
    log(acc, 'Đăng ký tài khoản', '');
    persist();
    return HttpResponse.json({ token, user: userOut(acc) });
  }),
  http.get(`${API}/auth/me`, ({ request }) => {
    const u = currentUser(request);
    return u ? HttpResponse.json(userOut(u)) : err(401, 'Chưa đăng nhập.');
  }),
  // 1.4 Đổi mật khẩu
  http.post(`${API}/auth/change-password`, async ({ request }) => {
    const u = currentUser(request);
    if (!u) return err(401, 'Chưa đăng nhập.');
    const b = (await request.json()) as { oldPassword: string; newPassword: string };
    if (b.oldPassword !== u.password) return err(400, 'Mật khẩu hiện tại không đúng.');
    if (!b.newPassword || b.newPassword.length < 6) return err(400, 'Mật khẩu mới tối thiểu 6 ký tự.');
    if (b.newPassword === b.oldPassword) return err(400, 'Mật khẩu mới phải khác mật khẩu hiện tại.');
    u.password = b.newPassword;
    log(u, 'Đổi mật khẩu', '');
    persist();
    return HttpResponse.json({ ok: true });
  }),
];
