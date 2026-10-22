-- V2__seed.sql: dữ liệu mẫu (cùng nội dung với API giả lập ở frontend). Mật khẩu mọi tài khoản: 123456 (BCrypt).
-- Giá trị tham số là số liệu mẫu để chạy thử; số liệu thực do ngân hàng cấu hình (biên bản 01/2026/BBXN-ABBANK-ĐATN, mục V.2).

INSERT INTO branch (id, code, name, address, phone, status) VALUES
 (1, 'HN01', 'Chi nhánh Hà Nội', 'Hà Nội', '02400000001', 'ACTIVE'),
 (2, 'HCM01', 'Chi nhánh Hồ Chí Minh', 'TP. Hồ Chí Minh', '02800000001', 'ACTIVE'),
 (3, 'DN01', 'Chi nhánh Đà Nẵng', 'Đà Nẵng', '02360000001', 'ACTIVE');

INSERT INTO staff (id, branch_id, full_name, email, password_hash, role, status) VALUES
 (101, 1, 'Nguyễn Thu Hà', 'nhanvien@poc.local', '$2b$10$HlSDoPkE1d9WhpUrxM53j.SHPexrEC0l5dx9oBlmE7Bvw9F.PkPjS', 'BRANCH_STAFF', 'ACTIVE'),
 (103, 2, 'Lê Hoàng Phúc', 'nv.hcm@poc.local', '$2b$10$HlSDoPkE1d9WhpUrxM53j.SHPexrEC0l5dx9oBlmE7Bvw9F.PkPjS', 'BRANCH_STAFF', 'ACTIVE'),
 (104, 3, 'Phan Thị Mỹ', 'nv.danang@poc.local', '$2b$10$HlSDoPkE1d9WhpUrxM53j.SHPexrEC0l5dx9oBlmE7Bvw9F.PkPjS', 'BRANCH_STAFF', 'ACTIVE'),
 (102, NULL, 'Trần Minh Đức', 'quantri@poc.local', '$2b$10$HlSDoPkE1d9WhpUrxM53j.SHPexrEC0l5dx9oBlmE7Bvw9F.PkPjS', 'ADMIN', 'ACTIVE');

INSERT INTO customer (id, phone, password_hash, full_name, status) VALUES
 (1, '0901234567', '$2b$10$HlSDoPkE1d9WhpUrxM53j.SHPexrEC0l5dx9oBlmE7Bvw9F.PkPjS', 'Khách hàng mẫu', 'ACTIVE');

INSERT INTO loan_product (id, code, name, product_type, repayment_method, description, status) VALUES
 (1, 'TD', 'Vay tiêu dùng không tài sản bảo đảm', 'CONSUMER', 'Gốc trả đều hằng tháng, lãi trên dư nợ giảm dần',
  'Phục vụ nhu cầu tiêu dùng hợp pháp; giải ngân vào tài khoản bên thụ hưởng theo hóa đơn, hợp đồng.', 'ACTIVE'),
 (2, 'TC', 'Vay thấu chi không tài sản bảo đảm', 'OVERDRAFT', 'Lãi trả hằng tháng, gốc trả cuối kỳ hoặc trả dần',
  'Cấp hạn mức vào tài khoản thanh toán, chi tiêu linh hoạt; không áp dụng cho kinh doanh.', 'ACTIVE');

INSERT INTO document_type (id, name, doc_group, allowed_formats, validity_days, status) VALUES
 (1, 'Giấy đề nghị vay vốn (mẫu ngân hàng)', 'Khác', 'PDF', NULL, 'ACTIVE'),
 (2, 'Căn cước công dân', 'Pháp lý', 'JPG,PNG,PDF', NULL, 'ACTIVE'),
 (3, 'Xác nhận cư trú', 'Pháp lý', 'JPG,PNG,PDF', 180, 'ACTIVE'),
 (4, 'Hợp đồng lao động hoặc xác nhận nhân sự', 'Thu nhập', 'JPG,PNG,PDF', 30, 'ACTIVE'),
 (5, 'Sao kê tài khoản nhận lương 03 tháng', 'Thu nhập', 'PDF', 30, 'ACTIVE'),
 (6, 'Hóa đơn, hợp đồng mua bán hoặc hợp đồng 3 bên', 'Mục đích', 'JPG,PNG,PDF', 30, 'ACTIVE');

INSERT INTO product_document_type (product_id, document_type_id, required) VALUES
 (1, 1, true), (1, 2, true), (1, 3, true), (1, 4, true), (1, 5, true), (1, 6, true),
 (2, 1, true), (2, 2, true), (2, 5, true), (2, 4, false);

INSERT INTO cross_sell_product (id, name, product_type, est_cost, score_bonus, rate_discount, status) VALUES
 (1, 'Bảo hiểm nhân thọ', 'INSURANCE', 8000000, 5, 1.0, 'ACTIVE'),
 (2, 'Thẻ tín dụng', 'CARD', 0, 3, 0.5, 'ACTIVE'),
 (3, 'Chứng chỉ tiền gửi', 'DEPOSIT', 0, 4, 0.7, 'ACTIVE'),
 (4, 'Tài khoản số đẹp', 'ACCOUNT', 1000000, 2, 0.3, 'ACTIVE');

INSERT INTO param_version (id, version_no, legal_basis, status, created_by, activated_by, created_at, activated_at) VALUES
 (1, '2026.10', 'Biên bản 01/2026/BBXN-ABBANK-ĐATN ngày 10/10/2026', 'ACTIVE', 102, 102, now(), now());

INSERT INTO policy_param (version_id, product_id, param_key, param_value, unit) VALUES
 (1, NULL, 'DTI_MAX', '70', '%'),
 (1, NULL, 'LIVING_COST_BASE', '4000000', 'VNĐ/tháng'),
 (1, NULL, 'LIVING_COST_PER_DEPENDENT', '2000000', 'VNĐ/tháng'),
 (1, NULL, 'RETIRE_AGE_MALE', '62', 'tuổi'),
 (1, NULL, 'RETIRE_AGE_FEMALE', '60', 'tuổi'),
 (1, NULL, 'ROOM_FACTOR', '100', '%'),
 (1, NULL, 'MAX_RATE_DISCOUNT', '2', '%/năm'),
 (1, NULL, 'DOC_VALIDITY_DAYS', '30', 'ngày'),
 (1, NULL, 'RETENTION_MONTHS', '12', 'tháng'),
 (1, 1, 'MIN_AGE', '22', 'tuổi'),
 (1, 1, 'INCOME_MULTIPLIER', '15', 'lần thu nhập'),
 (1, 1, 'MAX_AMOUNT', '500000000', 'VNĐ'),
 (1, 1, 'MIN_AMOUNT', '10000000', 'VNĐ'),
 (1, 1, 'MAX_TERM', '60', 'tháng'),
 (1, 1, 'MIN_TERM', '6', 'tháng'),
 (1, 1, 'REF_RATE', '12.5', '%/năm'),
 (1, 2, 'MIN_AGE', '19', 'tuổi'),
 (1, 2, 'MAX_AMOUNT', '200000000', 'VNĐ'),
 (1, 2, 'MIN_AMOUNT', '10000000', 'VNĐ'),
 (1, 2, 'MAX_TERM', '12', 'tháng'),
 (1, 2, 'MIN_TERM', '12', 'tháng'),
 (1, 2, 'REF_RATE', '18', '%/năm');

INSERT INTO scoring_weight (version_id, product_id, criterion, weight, adjust_factor) VALUES
 (1, 1, 'DTI', 35, 1.00),
 (1, 1, 'AMOUNT_RATIO', 25, 1.00),
 (1, 1, 'SALARY_BANK', 15, 1.00),
 (1, 1, 'STAFF', 10, 1.00),
 (1, 1, 'TERM_TO_RETIRE', 15, 1.00),
 (1, 2, 'DTI', 30, 1.00),
 (1, 2, 'AMOUNT_RATIO', 20, 1.00),
 (1, 2, 'SALARY_BANK', 25, 1.00),
 (1, 2, 'STAFF', 20, 1.00),
 (1, 2, 'TERM_TO_RETIRE', 5, 1.00);

-- Đặt lại bộ đếm khóa tự tăng sau khi chèn id cố định
SELECT setval(pg_get_serial_sequence('branch', 'id'), (SELECT max(id) FROM branch));
SELECT setval(pg_get_serial_sequence('staff', 'id'), (SELECT max(id) FROM staff));
SELECT setval(pg_get_serial_sequence('customer', 'id'), (SELECT max(id) FROM customer));
SELECT setval(pg_get_serial_sequence('loan_product', 'id'), (SELECT max(id) FROM loan_product));
SELECT setval(pg_get_serial_sequence('document_type', 'id'), (SELECT max(id) FROM document_type));
SELECT setval(pg_get_serial_sequence('cross_sell_product', 'id'), (SELECT max(id) FROM cross_sell_product));
SELECT setval(pg_get_serial_sequence('param_version', 'id'), (SELECT max(id) FROM param_version));
