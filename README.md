# Hệ thống gợi ý và tiền sàng lọc hồ sơ vay tín chấp – mã nguồn

Đồ án tốt nghiệp: _Nghiên cứu và phát triển hệ thống gợi ý và tiền sàng lọc hồ sơ vay tiêu dùng tín chấp đối với cá nhân_.

```
.
├── CLAUDE.md              Hướng dẫn cho Claude Code (đọc tự động)
├── fe/                    Frontend React + TypeScript + Ant Design (đã chạy được với API giả lập)
├── database/              Script PostgreSQL: V1__init.sql (24 bảng theo Chương 3), V2__seed.sql (dữ liệu mẫu)
├── be/                    Backend Spring Boot (đang xây dựng – be/CLAUDE.md)
├── docs/openapi.yaml      Hợp đồng API giữa frontend và backend
├── docs/fe-sua-doi.md     Việc cần sửa ở frontend (góp ý 22/10)
├── docs/be-huong-dan.md   Kế hoạch xây backend theo giai đoạn B0–B9
├── tests/e2e/             Kiểm thử Playwright toàn luồng
├── docs/quy-tac-tinh-toan.md  Công thức sàng lọc, hạn mức, điểm, mô phỏng, đối chiếu giấy tờ, trạng thái hồ sơ,
│                          tác vụ định kỳ, độ chính xác mô hình – để cài đặt ở backend
└── docker-compose.yml     PostgreSQL 16 cho môi trường phát triển
```

## Chạy frontend

Yêu cầu Node.js 20 trở lên.

```bash
cd fe
npm install
npm run dev          # http://localhost:5173
```

Mặc định frontend dùng **API giả lập** chạy ngay trong trình duyệt (MSW, xem `fe/src/mocks`), nên không cần backend.
Dữ liệu giả lập lưu trong localStorage của trình duyệt (có sẵn ~90 hồ sơ lịch sử 6 tháng ở 3 chi nhánh để xem báo cáo). OTP giả lập: `123456`.
Frontend đã đủ 29 chức năng – xem `fe/README.md`.

Tài khoản thử (mật khẩu `123456`):

| Vai trò                         | Đăng nhập tại     | Tài khoản                                                          |
| ------------------------------- | ----------------- | ------------------------------------------------------------------ |
| Khách hàng                      | /dang-nhap        | 0901234567                                                         |
| Nhân viên chi nhánh (BP01)      | /noi-bo/dang-nhap | nhanvien@tiensangloc.local (Hà Nội), nv.hcm@tiensangloc.local, nv.danang@tiensangloc.local |
| Quản trị viên chính sách (BP02) | /noi-bo/dang-nhap | quantri@tiensangloc.local                                                  |

## Nối với backend thật

1. Chạy PostgreSQL: `docker compose up -d` (tự tạo bảng và dữ liệu mẫu).
2. Backend Spring Boot chạy ở cổng 8080, cài đặt các API theo `docs/openapi.yaml` và công thức theo `docs/quy-tac-tinh-toan.md`.
3. Trong `fe/.env.development` đặt `VITE_USE_MOCK=false`. Vite sẽ chuyển `/api` tới `http://localhost:8080`.

Gợi ý cho backend: Spring Boot 3, Java 21, Spring Web, Spring Data JPA, Spring Security (JWT), Flyway
(chép `database/*.sql` vào `src/main/resources/db/migration`), springdoc-openapi, BCrypt cho mật khẩu.

Lưu trữ tệp giấy tờ: frontend gửi `multipart/form-data` (`file`, `issuedDate`) tới `POST /applications/{id}/documents/{docId}`;
backend lưu tệp (thư mục cục bộ hoặc MinIO), trả `previewUrl` để xem lại. Trích xuất MB06 có thể bắt đầu bằng dữ liệu giả
như bản mock, sau thay bằng dịch vụ OCR (Tesseract, FPT.AI, VNPT eKYC…).
