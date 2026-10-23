# Backend – quy ước

Spring Boot 3.3 · Java 21 · Maven · PostgreSQL 16 · Flyway. Package gốc `vn.kltn.tiensanglocvay`, chia theo chức năng.

- Kế hoạch, quyết định kỹ thuật và danh sách giai đoạn: `../docs/be-huong-dan.md` – đọc trước khi làm, đánh dấu `[x]` khi xong.
- Hợp đồng API: `../docs/openapi.yaml`. DTO là `record`, tên trường giống hệt schema. Không thêm/bớt trường khi chưa sửa openapi + fe.
- Hành vi và thông báo lỗi phải giống mock `../fe/src/mocks/handlers/*.ts`, `../fe/src/mocks/ctx.ts`.
- Công thức: `../fe/src/mocks/engine.ts` + `../docs/quy-tac-tinh-toan.md`; kiểm thử đối chứng bằng fixtures sinh từ engine.ts.
- Lỗi: `{ "message": "...", "fields": {...} }` qua `ApiException` + `GlobalExceptionHandler`.
- Migration: không sửa file Flyway đã chạy; thêm `V<n>__mo_ta.sql`.
- Đổi trạng thái hồ sơ chỉ qua `ApplicationStateMachine`; ghi nhật ký và thông báo ở những chỗ mock làm.
- Ảnh/tệp giấy tờ lưu **Cloudinary** (type `authenticated`, xem qua URL ký có hạn); đọc giấy tờ bằng **Gemini API** (JSON theo schema).
  Chi tiết: mục 5.3, 5.4 của hướng dẫn. Khóa chỉ qua biến môi trường `CLOUDINARY_URL`, `GEMINI_API_KEY` – không commit
  (thêm `.env` vào `be/.gitignore`). Test dùng `app.storage.provider=local`, `app.ocr.provider=fake`, không gọi dịch vụ ngoài.
- Chỉ gửi ảnh giấy tờ mẫu/giả lên Gemini gói miễn phí (điều khoản cấm gửi dữ liệu cá nhân lên gói miễn phí).
- Thư viện: được tự thêm dependency theo quy tắc ở `../docs/be-huong-dan.md` mục 2 (ưu tiên starter Spring Boot; ghi version nếu Spring
  không quản lý; mỗi dependency mới có 1 dòng lý do ở mục 9 và comment trong `pom.xml`). Hỏi trước khi đổi framework/ORM,
  thêm dịch vụ trả phí hoặc gọi ra ngoài internet, hoặc dùng thư viện chỉ có một người duy trì.
- Trước khi báo xong: `./mvnw verify` (Testcontainers cần Docker đang chạy).

```bash
docker compose up -d            # ở thư mục gốc; PostgreSQL ở cổng 5434
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
./mvnw verify
```
