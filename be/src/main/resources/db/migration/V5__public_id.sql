-- V5__public_id.sql: mã công khai (UUID) cho dữ liệu khách hàng nhìn thấy trên URL.
-- API chỉ trả và nhận public_id; khóa chính bigint giữ nguyên cho khóa ngoại và không bao giờ trả ra ngoài.
-- gen_random_uuid() có sẵn từ PostgreSQL 13 nên các dòng đã có cũng được gán mã.

ALTER TABLE screening ADD COLUMN public_id uuid NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE screening ADD CONSTRAINT uq_screening_public_id UNIQUE (public_id);
COMMENT ON COLUMN screening.public_id IS 'Mã công khai: ScreeningResponse.id, SimulationRequest.screeningId, LoanPlan.screeningId, /screenings/{id}';

ALTER TABLE loan_plan ADD COLUMN public_id uuid NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE loan_plan ADD CONSTRAINT uq_loan_plan_public_id UNIQUE (public_id);
COMMENT ON COLUMN loan_plan.public_id IS 'Mã công khai: LoanPlan.id, CreateApplicationRequest.planId, /plans/{id}';

ALTER TABLE loan_application ADD COLUMN public_id uuid NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE loan_application ADD CONSTRAINT uq_loan_application_public_id UNIQUE (public_id);
COMMENT ON COLUMN loan_application.public_id IS 'Mã công khai: Application.id, ApplicationSummary.id, LoanPlan.applicationId, đường dẫn thông báo, /applications/{id}, /staff/applications/{id}';
