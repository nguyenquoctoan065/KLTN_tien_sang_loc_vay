-- B7 Quản trị (QT07, 1.5–1.7). Không sửa V1–V8.

-- Tác vụ định kỳ (docs/quy-tac-tinh-toan.md mục 13): schema JobInfo của GET /admin/jobs, POST /admin/jobs/{code}/run.
-- Lịch chạy (schedule) không lưu ở đây mà sinh từ cron app.jobs.* trong application.yml.
CREATE TABLE scheduled_job (
    code varchar(40) NOT NULL,
    name varchar(150) NOT NULL,
    last_run_at timestamptz,           -- JobInfo.lastRunAt
    last_result varchar(500),          -- JobInfo.lastResult
    CONSTRAINT pk_scheduled_job PRIMARY KEY (code)
);

INSERT INTO scheduled_job (code, name) VALUES
 ('EXPIRE_DOCUMENTS', 'Kiểm tra giấy tờ quá thời hạn hiệu lực'),
 ('PURGE_RETENTION', 'Xóa dữ liệu hết thời hạn lưu trữ'),
 ('REMIND_DRAFTS', 'Nhắc khách hàng hoàn tất hồ sơ đang lập');

-- GET /admin/logs: 500 dòng mới nhất.
CREATE INDEX idx_activity_log_created_at ON activity_log (created_at DESC);
-- Ba tác vụ định kỳ lọc hồ sơ theo trạng thái và thời điểm cập nhật.
CREATE INDEX idx_loan_application_status_updated ON loan_application (status, updated_at);
