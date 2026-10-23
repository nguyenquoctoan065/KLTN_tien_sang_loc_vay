// Kiểu dữ liệu trao đổi với backend (khớp docs/openapi.yaml và mô hình quan hệ ở Chương 3)

export type Role = 'CUSTOMER' | 'BRANCH_STAFF' | 'ADMIN';

export interface AuthUser {
  id: number;
  name: string;
  role: Role;
  /** số điện thoại (khách hàng) hoặc email (nội bộ) */
  username: string;
  email?: string | null;
  branchName?: string;
}

export interface LoginRequest {
  kind: 'CUSTOMER' | 'STAFF';
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  user: AuthUser;
}

export interface RegisterRequest {
  phone: string;
  email?: string;
  password: string;
  otp: string;
}

export type ProductCode = 'TD' | 'TC';

/** GÓI VAY (loan_product) */
export interface LoanProduct {
  id: number;
  code: ProductCode;
  name: string;
  productType: 'CONSUMER' | 'OVERDRAFT';
  repaymentMethod: string;
  description: string;
  status: 'ACTIVE' | 'INACTIVE';
}

/** SẢN PHẨM BÁN CHÉO (cross_sell_product) */
export interface CrossSellProduct {
  id: number;
  name: string;
  productType: string;
  estCost: number;
  scoreBonus: number;
  rateDiscount: number;
  status: 'ACTIVE' | 'INACTIVE';
}

/** Nghĩa vụ nợ khác (other_debt) */
export interface OtherDebt {
  lender: string;
  monthlyPayment: number;
}

export type CreditHistory = 'NEVER_BORROWED' | 'ON_TIME' | 'LATE_OVER_90' | 'UNSURE';

/** Ghi chú chung trên trang kết quả sàng lọc */
export interface ScreeningNote {
  type: 'INFO' | 'WARNING';
  text: string;
}

/** MB01 – Phiếu thông tin sàng lọc (không có họ tên, CCCD, SĐT) + đồng ý MB02 phần (a) */
export interface ScreeningRequest {
  consentA: { agreed: boolean; docVersion: string };
  nationality: string;
  dob: string; // yyyy-MM-dd
  gender: 'MALE' | 'FEMALE';
  maritalStatus: 'SINGLE' | 'MARRIED' | 'OTHER';
  dependents: number;
  avgIncome: number;
  salaryBank: string;
  isAbbankStaff: boolean;
  creditHistory: CreditHistory;
  otherDebts: OtherDebt[];
  purpose: string;
  desiredAmount: number;
  desiredTerm: number;
}

/** Một dòng của MB03 – kết quả gợi ý cho từng gói (screening_result) */
export interface ScreeningResultItem {
  productCode: ProductCode;
  productName: string;
  eligible: boolean;
  /** lý do không đạt điều kiện loại trừ */
  reasons: string[];
  /** hướng cải thiện tương ứng */
  hints: string[];
  dti: number | null; // % DTI sau vay ước tính với số tiền mong muốn
  maxSafeAmount: number | null;
  refRate: number;
  maxTerm: number;
  score: number | null;
  rankNo: number | null;
}

/** MB03 – Bảng kết quả gợi ý gói vay */
export interface ScreeningResponse {
  id: string;
  createdAt: string;
  versionNo: string;
  input: ScreeningRequest;
  age: number;
  livingCost: number;
  disposableIncome: number;
  currentDti: number; // %
  results: ScreeningResultItem[];
  notes: ScreeningNote[];
  savedToCustomerId: number | null;
}

export interface SimulationRequest {
  screeningId: string;
  productCode: ProductCode;
  amount: number;
  termMonths: number;
  crossSellIds: number[];
}

export interface ScheduleRow {
  period: number;
  principal: number;
  interest: number;
  payment: number;
  balance: number;
}

export type SuggestionType = 'REDUCE_AMOUNT' | 'EXTEND_TERM' | 'SWITCH_PRODUCT' | 'CROSS_SELL';

export interface Suggestion {
  type: SuggestionType;
  title: string;
  detail: string;
  productCode: ProductCode;
  amount: number;
  termMonths: number;
  crossSellIds: number[];
  score: number;
  extraCost: number;
}

/** MB04 – Kế hoạch trả nợ dự kiến (kèm kiểm tra theo hạn mức và DTI) */
export interface SimulationResponse {
  productCode: ProductCode;
  productName: string;
  amount: number;
  termMonths: number;
  rate: number; // %/năm sau ưu đãi bán chéo
  monthlyObligation: number; // nghĩa vụ hằng tháng quy đổi để tính DTI
  firstPayment: number;
  totalInterest: number;
  totalPayment: number;
  dtiAfter: number; // %
  dtiMax: number; // %
  disposableAfter: number;
  maxSafeAmount: number;
  maxTermAllowed: number;
  score: number;
  ok: boolean;
  issues: string[];
  extraCost: number;
  schedule: ScheduleRow[];
  suggestions: Suggestion[];
}

/** MB07 – Phương án vay đề xuất (loan_plan) */
export interface LoanPlan {
  id: string;
  screeningId: string;
  productCode: ProductCode;
  productName: string;
  amount: number;
  termMonths: number;
  refRate: number;
  monthlyPayment: number;
  totalInterest: number;
  dtiAfter: number;
  score: number;
  status: 'DRAFT' | 'CONFIRMED';
  crossSells: { id: number; name: string; extraCost: number; benefit: string }[];
  createdAt: string;
  /** Danh mục giấy tờ cần chuẩn bị (bản xem trước MB05) */
  checklist: { name: string; group: string; required: boolean }[];
  /** hồ sơ đã lập từ phương án (nếu có) */
  applicationId?: string | null;
}

export type ApplicationStatus =
  | 'DRAFTING'
  | 'WAITING_VERIFY'
  | 'NEED_MORE'
  | 'VERIFIED'
  | 'APPROVED'
  | 'DISBURSED'
  | 'REJECTED'
  | 'CANCELLED'
  | 'EXPIRED';

export interface ApplicationSummary {
  id: string;
  code: string;
  customerName: string;
  customerPhone: string;
  productCode: ProductCode;
  productName: string;
  amount: number;
  termMonths: number;
  score: number;
  branchName: string;
  status: ApplicationStatus;
  qrToken: string | null;
  createdAt: string;
  updatedAt: string;
  expiresAt: string | null;
}

export interface ParamVersion {
  id: number;
  versionNo: string;
  legalBasis: string;
  status: 'DRAFT' | 'ACTIVE' | 'EXPIRED';
  createdBy: string;
  createdAt: string;
  activatedAt: string | null;
}

/** Một dòng MB11 – Bảng tham số chính sách (policy_param) */
export interface PolicyParam {
  id: number;
  productCode: ProductCode | null;
  key: string;
  label: string;
  value: string;
  unit: string;
}

/** Một dòng MB12 – Ma trận trọng số (scoring_weight) */
export interface ScoringWeight {
  id: number;
  productCode: ProductCode;
  criterion: string;
  label: string;
  weight: number;
}

export interface ParamVersionDetail extends ParamVersion {
  params: PolicyParam[];
  weights: ScoringWeight[];
  testResult: TestRunResult | null;
}

export interface TestRunRow {
  profile: string;
  productCode: ProductCode;
  activeEligible: boolean;
  draftEligible: boolean;
  activeMaxSafe: number | null;
  draftMaxSafe: number | null;
  activeScore: number | null;
  draftScore: number | null;
}

/** Kết quả chạy thử phiên bản nháp trên bộ hồ sơ mẫu (7.4) */
export interface TestRunResult {
  runAt: string;
  rows: TestRunRow[];
  changed: number;
}

export interface ApiError {
  message: string;
  fields?: Record<string, string>;
}

// ======================= Giai đoạn 2: hồ sơ vay, quản trị, báo cáo =======================

export interface Branch {
  id: number;
  code: string;
  name: string;
  address: string;
  phone: string;
  status: 'ACTIVE' | 'INACTIVE';
  openApplications?: number;
}

/** Kết quả ngừng hoạt động chi nhánh: số hồ sơ và số nhân viên đã chuyển sang chi nhánh nhận */
export interface BranchDeactivateResult extends Branch {
  transferred: number;
  transferredStaff: number;
}

/** NHÂN VIÊN (staff) */
export interface StaffAccount {
  id: number;
  fullName: string;
  email: string;
  role: 'BRANCH_STAFF' | 'ADMIN';
  branchId: number | null;
  branchName: string | null;
  status: 'ACTIVE' | 'LOCKED';
  createdAt: string;
}

/** LOẠI GIẤY TỜ (document_type) */
export interface DocumentType {
  id: number;
  name: string;
  group: string;
  allowedFormats: string;
  validityDays: number | null;
  status: 'ACTIVE' | 'INACTIVE';
}

export interface ProductDocRule {
  productCode: ProductCode;
  documentTypeId: number;
  required: boolean;
}

/** TRƯỜNG TRÍCH XUẤT (extracted_field) – một dòng của MB06 */
export interface ExtractedField {
  id: number;
  name: string;
  label: string;
  ocrValue: string;
  confidence: number; // %
  confirmedValue: string | null;
}

export type DocStatus = 'MISSING' | 'UPLOADED' | 'CONFIRMED' | 'REJECTED_QUALITY';

/** TÀI LIỆU (application_document) – một dòng của MB05 */
export interface AppDocument {
  id: number;
  documentTypeId: number;
  typeName: string;
  group: string;
  required: boolean;
  allowedFormats: string;
  status: DocStatus;
  fileName: string | null;
  fileFormat: string | null;
  fileSize: number | null; // KB
  versionNo: number;
  issuedDate: string | null;
  qualityStatus: 'OK' | 'BLURRY' | 'WRONG_FORMAT' | 'TOO_LARGE' | 'WRONG_TYPE' | 'CORRUPTED' | null;
  qualityMessage: string | null;
  uploadedAt: string | null;
  previewUrl: string | null;
  /** đường dẫn mở tệp gốc cỡ đầy đủ (ảnh gốc hoặc PDF), ký có hạn 10 phút; null nếu chưa tải hoặc tệp không đạt */
  fileUrl: string | null;
  fields: ExtractedField[];
  /** kết quả đối chiếu gần nhất tại chi nhánh */
  verifyResult: 'MATCH' | 'MISMATCH' | 'MISSING' | null;
  verifyReason: string | null;
}

/** Sai lệch giữa giá trị kiểm chứng (MB06) và giá trị khai báo (MB01) */
export interface CrossCheckWarning {
  field: string;
  declared: string;
  verified: string;
  message: string;
  severity: 'INFO' | 'WARNING' | 'BLOCKING';
}

export interface PersonalInfo {
  fullName: string;
  nationalId: string;
  dob: string;
  gender: 'MALE' | 'FEMALE';
  address: string;
  phone: string;
  email: string;
}

/** GIẤY ĐỀ NGHỊ VAY VỐN (loan_request_form) – MB15 */
export interface LoanRequestForm {
  status: 'DRAFT' | 'CONFIRMED';
  content: { label: string; key: string; value: string; editable: boolean }[];
  createdAt: string;
  otpConfirmedAt: string | null;
}

/** PHIẾU ĐỐI CHIẾU (verification) – MB09 */
export interface Verification {
  id: number;
  staffName: string;
  overallResult: 'MATCH' | 'MISMATCH' | 'PENDING';
  note: string;
  verifiedAt: string;
  details: { documentId: number; typeName: string; result: 'MATCH' | 'MISMATCH' | 'MISSING'; reason: string }[];
}

/** KẾT QUẢ XÉT DUYỆT (approval_result) – MB16 */
export interface ApprovalResult {
  id: number;
  resultType: 'APPROVED' | 'REJECTED' | 'MORE_INFO';
  approvedAmount: number | null;
  approvedTerm: number | null;
  approvedRate: number | null;
  reason: string | null;
  recordedAt: string;
  staffName: string;
  disbursedAt: string | null;
  disbursedAmount: number | null;
  beneficiary: string | null;
}

export interface StatusEvent {
  status: ApplicationStatus;
  at: string;
  actor: string;
  note: string | null;
}

/** Kết quả tính lại theo giá trị kiểm chứng (bước 30) */
export interface Recalculation {
  verifiedIncome: number;
  dtiAfter: number;
  maxSafeAmount: number;
  score: number;
  ok: boolean;
  issues: string[];
}

/** HỒ SƠ VAY (loan_application) – chi tiết */
export interface Application {
  id: string;
  code: string;
  customerId: number;
  status: ApplicationStatus;
  branchId: number;
  branchName: string;
  branchAddress: string;
  plan: LoanPlan;
  purpose: string;
  personal: PersonalInfo;
  consentBAt: string;
  versionNo: string;
  contentHash: string | null;
  lockedAt: string | null;
  qrToken: string | null;
  expiresAt: string | null;
  createdAt: string;
  updatedAt: string;
  documents: AppDocument[];
  warnings: CrossCheckWarning[];
  recalc: Recalculation | null;
  requestForm: LoanRequestForm | null;
  verifications: Verification[];
  approval: ApprovalResult | null;
  history: StatusEvent[];
  /** các thao tác được phép với người đang xem */
  can: { edit: boolean; submit: boolean; cancel: boolean; resubmit: boolean; verify: boolean; decide: boolean; disburse: boolean };
}

export interface AppNotification {
  id: number;
  title: string;
  body: string;
  link: string | null;
  createdAt: string;
  read: boolean;
}

/** NHẬT KÝ (activity_log) */
export interface ActivityLog {
  id: number;
  applicationCode: string | null;
  actorType: 'CUSTOMER' | 'STAFF' | 'SYSTEM';
  actorName: string;
  action: string;
  detail: string;
  createdAt: string;
}

export interface ConsentRecord {
  id: number;
  scope: 'A' | 'B';
  docVersion: string;
  agreedAt: string;
  withdrawnAt: string | null;
  applicationCode: string | null;
}

/** 6.1 Dữ liệu cá nhân của khách hàng */
export interface MyData {
  account: { phone: string; name: string; createdAt: string };
  personal: PersonalInfo | null;
  consents: ConsentRecord[];
  screenings: number;
  plans: number;
  applications: { code: string; status: ApplicationStatus; documents: number }[];
  logs: ActivityLog[];
}

export interface WithdrawConsentResult {
  scope: 'A' | 'B';
  deletedScreenings: number;
  deletedDocuments: number;
  cancelledApplications: string[];
  keptApplications: string[];
  message: string;
}

/** Tác vụ định kỳ (1.7) */
export interface JobInfo {
  code: string;
  name: string;
  schedule: string;
  lastRunAt: string | null;
  lastResult: string | null;
}

/** 8.1 Thống kê hồ sơ */
export interface StatsResponse {
  from: string;
  to: string;
  totals: { screenings: number; plans: number; applications: number; submitted: number; verified: number; approved: number; disbursed: number; rejected: number; cancelled: number; expired: number };
  byStatus: { status: ApplicationStatus; count: number }[];
  byProduct: { productCode: ProductCode; count: number; amount: number }[];
  byBranch: { branchName: string; count: number; approved: number; rejected: number }[];
  byMonth: { month: string; created: number; disbursed: number; rejected: number }[];
  avgProcessingDays: number;
  disbursedAmount: number;
}

/** 8.2 Đánh giá độ chính xác mô hình */
export interface AccuracyResponse {
  threshold: number;
  sample: number;
  matrix: { tp: number; fp: number; tn: number; fn: number };
  /** null = chưa đủ dữ liệu (xem docs/quy-tac-tinh-toan.md mục 15) */
  accuracy: number | null;
  precision: number | null;
  recall: number | null;
  byBand: { band: string; total: number; approved: number; rate: number }[];
  byProduct: { productCode: ProductCode; sample: number; accuracy: number }[];
}

/** 8.3 MB14 – báo cáo thống kê và đề xuất hiệu chỉnh */
export interface ReportResponse {
  period: string;
  generatedAt: string;
  stats: StatsResponse;
  accuracy: AccuracyResponse;
  proposals: { id: string; title: string; detail: string; paramKey: string | null; productCode: ProductCode | null; currentValue: string | null; proposedValue: string | null }[];
}
