-- V4__sua_dinh_dang_sao_ke.sql: sao kê tài khoản nhận lương cho phép thêm ảnh JPG, PNG (theo fe/src/mocks/db.ts, docTypesSeed).
-- Phục vụ DocumentType.allowedFormats (kiểm tra tệp tải lên ở B4, /admin/document-types ở B7). Không sửa V2 vì V2 đã chạy.
UPDATE document_type SET allowed_formats = 'PDF,JPG,PNG' WHERE name = 'Sao kê tài khoản nhận lương 03 tháng';
