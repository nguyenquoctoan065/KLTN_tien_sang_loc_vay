-- B8 – Báo cáo (QT09): chỉ mục cho các truy vấn tổng hợp của /admin/stats, /admin/model-accuracy, /admin/reports/mb14.

-- StatsResponse: hồ sơ, lượt sàng lọc, phương án lọc theo ngày tạo
CREATE INDEX ix_loan_application_created_at ON loan_application (created_at);
CREATE INDEX ix_screening_created_at ON screening (created_at);
CREATE INDEX ix_loan_plan_created_at ON loan_plan (created_at);

-- StatsResponse.totals/byMonth: các mốc đã từng đạt trong lịch sử trạng thái
CREATE INDEX ix_status_history_application_status ON application_status_history (application_id, to_status, created_at);
