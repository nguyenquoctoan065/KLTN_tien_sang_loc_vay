import { api } from './client';
import type {
  AccuracyResponse, ActivityLog, Application, AppNotification, Branch, CrossSellProduct as CS, DocumentType, JobInfo, LoanProduct as LP,
  MyData, PersonalInfo, ProductDocRule, ReportResponse, StaffAccount, StatsResponse, WithdrawConsentResult, ProductCode,
} from './types';
import type {
  ApplicationSummary, AuthUser, CrossSellProduct, LoanPlan, LoanProduct, LoginRequest, LoginResponse, ParamVersion,
  ParamVersionDetail, PolicyParam, RegisterRequest, ScoringWeight, ScreeningRequest, ScreeningResponse, SimulationRequest,
  SimulationResponse, TestRunResult,
} from './types';

export const authApi = {
  login: (b: LoginRequest) => api.post<LoginResponse>('/auth/login', b).then(r => r.data),
  sendOtp: (phone: string) => api.post<{ sent: boolean; devOtp?: string }>('/auth/otp', { phone }).then(r => r.data),
  register: (b: RegisterRequest) => api.post<LoginResponse>('/auth/register', b).then(r => r.data),
  me: () => api.get<AuthUser>('/auth/me').then(r => r.data),
  logout: () => api.post('/auth/logout').then(r => r.data),
  /** OTP cho khách hàng đang đăng nhập (xác nhận giấy đề nghị, rút đồng ý) */
  myOtp: () => api.post<{ sent: boolean; devOtp?: string }>('/auth/otp', {}).then(r => r.data),
  changePassword: (b: { oldPassword: string; newPassword: string }) => api.post('/auth/change-password', b).then(r => r.data),
};

export const catalogApi = {
  products: () => api.get<LoanProduct[]>('/products').then(r => r.data),
  crossSells: () => api.get<CrossSellProduct[]>('/cross-sell-products').then(r => r.data),
  branches: () => api.get<Branch[]>('/branches').then(r => r.data),
};

export const appApi = {
  mine: () => api.get<ApplicationSummary[]>('/me/applications').then(r => r.data),
  create: (b: { planId: number; branchId: number; consentB: { agreed: boolean; docVersion: string } }) =>
    api.post<Application>('/applications', b).then(r => r.data),
  get: (id: number) => api.get<Application>(`/applications/${id}`).then(r => r.data),
  personal: (id: number, b: PersonalInfo) => api.put<Application>(`/applications/${id}/personal`, b).then(r => r.data),
  upload: (id: number, docId: number, file: File, issuedDate?: string) => {
    const fd = new FormData();
    fd.append('file', file);
    if (issuedDate) fd.append('issuedDate', issuedDate);
    return api.post<Application>(`/applications/${id}/documents/${docId}`, fd).then(r => r.data);
  },
  confirmFields: (id: number, docId: number, fields: { id: number; confirmedValue: string }[]) =>
    api.put<Application>(`/applications/${id}/documents/${docId}/fields`, { fields }).then(r => r.data),
  updatePlan: (id: number, b: { amount: number; termMonths: number; crossSellIds: number[] }) =>
    api.put<Application>(`/applications/${id}/plan`, b).then(r => r.data),
  createForm: (id: number) => api.post<Application>(`/applications/${id}/request-form`).then(r => r.data),
  updateForm: (id: number, values: Record<string, string>) => api.put<Application>(`/applications/${id}/request-form`, { values }).then(r => r.data),
  submit: (id: number, otp: string) => api.post<Application>(`/applications/${id}/submit`, { otp }).then(r => r.data),
  cancel: (id: number, reason?: string) => api.post<Application>(`/applications/${id}/cancel`, { reason }).then(r => r.data),
};

export const noticeApi = {
  list: () => api.get<AppNotification[]>('/notifications').then(r => r.data),
  read: (id: number) => api.post(`/notifications/${id}/read`).then(r => r.data),
  readAll: () => api.post('/notifications/read-all').then(r => r.data),
};

export const myDataApi = {
  get: () => api.get<MyData>('/me/data').then(r => r.data),
  withdraw: (scope: 'A' | 'B', otp: string) => api.post<WithdrawConsentResult>('/me/consents/withdraw', { scope, otp }).then(r => r.data),
};

export const screeningApi = {
  create: (b: ScreeningRequest) => api.post<ScreeningResponse>('/screenings', b).then(r => r.data),
  get: (id: number) => api.get<ScreeningResponse>(`/screenings/${id}`).then(r => r.data),
  save: (id: number) => api.post<ScreeningResponse>(`/screenings/${id}/save`).then(r => r.data),
  mine: () => api.get<ScreeningResponse[]>('/me/screenings').then(r => r.data),
};

export const planApi = {
  simulate: (b: SimulationRequest) => api.post<SimulationResponse>('/simulations', b).then(r => r.data),
  create: (b: SimulationRequest) => api.post<LoanPlan>('/plans', b).then(r => r.data),
  get: (id: number) => api.get<LoanPlan>(`/plans/${id}`).then(r => r.data),
  confirm: (id: number) => api.post<LoanPlan>(`/plans/${id}/confirm`).then(r => r.data),
  mine: () => api.get<LoanPlan[]>('/me/plans').then(r => r.data),
};

export const staffApi = {
  applications: (q: { status?: string; keyword?: string }) =>
    api.get<ApplicationSummary[]>('/staff/applications', { params: q }).then(r => r.data),
  byQr: (token: string) => api.get<{ id: number }>(`/staff/applications/by-qr/${encodeURIComponent(token)}`).then(r => r.data),
  summary: () => api.get<{ waitingVerify: number; needMore: number; verified: number; approved: number }>('/staff/summary').then(r => r.data),
  verify: (id: number, b: { details: { documentId: number; result: 'MATCH' | 'MISMATCH' | 'MISSING'; reason: string }[]; note: string }) =>
    api.post<Application>(`/staff/applications/${id}/verification`, b).then(r => r.data),
  decide: (id: number, b: { resultType: 'APPROVED' | 'REJECTED' | 'MORE_INFO'; approvedAmount?: number; approvedTerm?: number; approvedRate?: number; reason?: string }) =>
    api.post<Application>(`/staff/applications/${id}/decision`, b).then(r => r.data),
  disburse: (id: number, b: { disbursedAt: string; disbursedAmount: number; beneficiary: string }) =>
    api.post<Application>(`/staff/applications/${id}/disbursement`, b).then(r => r.data),
};

export const adminApi = {
  versions: () => api.get<ParamVersion[]>('/admin/param-versions').then(r => r.data),
  version: (id: number) => api.get<ParamVersionDetail>(`/admin/param-versions/${id}`).then(r => r.data),
  clone: (id: number, b: { versionNo: string; legalBasis: string; changes?: { key: string; productCode: ProductCode | null; value: string }[] }) =>
    api.post<{ id: number }>(`/admin/param-versions/${id}/clone`, b).then(r => r.data),
  update: (id: number, b: { params: PolicyParam[]; weights: ScoringWeight[]; legalBasis?: string }) =>
    api.put(`/admin/param-versions/${id}`, b).then(r => r.data),
  remove: (id: number) => api.delete(`/admin/param-versions/${id}`).then(r => r.data),
  test: (id: number) => api.post<TestRunResult>(`/admin/param-versions/${id}/test`).then(r => r.data),
  activate: (id: number) => api.post<ParamVersion>(`/admin/param-versions/${id}/activate`).then(r => r.data),
  staff: () => api.get<StaffAccount[]>('/admin/staff').then(r => r.data),
  createStaff: (b: { fullName: string; email: string; role: 'BRANCH_STAFF' | 'ADMIN'; branchId: number | null }) =>
    api.post<StaffAccount & { tempPassword: string }>('/admin/staff', b).then(r => r.data),
  updateStaff: (id: number, b: { fullName: string; role: 'BRANCH_STAFF' | 'ADMIN'; branchId: number | null }) =>
    api.put<StaffAccount>(`/admin/staff/${id}`, b).then(r => r.data),
  staffAction: (id: number, action: 'lock' | 'unlock' | 'reset-password') =>
    api.post<StaffAccount & { tempPassword?: string }>(`/admin/staff/${id}/${action}`).then(r => r.data),
  branches: () => api.get<Branch[]>('/admin/branches').then(r => r.data),
  createBranch: (b: Omit<Branch, 'id' | 'status'>) => api.post<Branch>('/admin/branches', b).then(r => r.data),
  updateBranch: (id: number, b: Omit<Branch, 'id' | 'status'>) => api.put<Branch>(`/admin/branches/${id}`, b).then(r => r.data),
  deactivateBranch: (id: number, transferToBranchId?: number) =>
    api.post<Branch & { transferred: number }>(`/admin/branches/${id}/deactivate`, { transferToBranchId }).then(r => r.data),
  activateBranch: (id: number) => api.post<Branch>(`/admin/branches/${id}/activate`).then(r => r.data),
  jobs: () => api.get<JobInfo[]>('/admin/jobs').then(r => r.data),
  runJob: (code: string) => api.post<JobInfo>(`/admin/jobs/${code}/run`).then(r => r.data),
  catalog: () => api.get<{ products: LP[]; documentTypes: DocumentType[]; productDocuments: ProductDocRule[]; crossSells: CS[] }>('/admin/catalog').then(r => r.data),
  updateProduct: (id: number, b: Partial<LP>) => api.put<LP>(`/admin/products/${id}`, b).then(r => r.data),
  createDocType: (b: Omit<DocumentType, 'id'>) => api.post<DocumentType>('/admin/document-types', b).then(r => r.data),
  updateDocType: (id: number, b: Omit<DocumentType, 'id'>) => api.put<DocumentType>(`/admin/document-types/${id}`, b).then(r => r.data),
  updateProductDocs: (b: ProductDocRule[]) => api.put<ProductDocRule[]>('/admin/product-documents', b).then(r => r.data),
  createCrossSell: (b: Omit<CS, 'id'>) => api.post<CS>('/admin/cross-sell-products', b).then(r => r.data),
  updateCrossSell: (id: number, b: Omit<CS, 'id'>) => api.put<CS>(`/admin/cross-sell-products/${id}`, b).then(r => r.data),
  logs: (q: { keyword?: string; actorType?: string }) => api.get<ActivityLog[]>('/admin/logs', { params: q }).then(r => r.data),
  stats: (q: { from?: string; to?: string; branchId?: number }) => api.get<StatsResponse>('/admin/stats', { params: q }).then(r => r.data),
  accuracy: (threshold: number) => api.get<AccuracyResponse>('/admin/model-accuracy', { params: { threshold } }).then(r => r.data),
  report: (month: string) => api.get<ReportResponse>('/admin/reports/mb14', { params: { month } }).then(r => r.data),
};
