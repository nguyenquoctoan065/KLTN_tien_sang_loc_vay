-- B5 – Chi nhánh (QT04–QT05). Bảng verification, verification_detail, approval_result đã có từ V1, cột verify_result/verify_reason
-- của application_document có từ V6; ở đây chỉ thêm chỉ mục cho các truy vấn của nhân viên.

-- Application.verifications, Application.approval (nạp theo hồ sơ mỗi lần trả Application)
CREATE INDEX ix_verification_application_id ON verification (application_id);
CREATE INDEX ix_approval_result_application_id ON approval_result (application_id);

-- GET /staff/applications, GET /staff/summary (lọc theo chi nhánh và trạng thái)
CREATE INDEX ix_loan_application_branch_status ON loan_application (branch_id, status);

-- GET /staff/applications/by-qr/{token}
CREATE INDEX ix_loan_application_qr_token ON loan_application (qr_token);
