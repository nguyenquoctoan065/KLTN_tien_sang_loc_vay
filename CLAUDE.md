# Hệ thống gợi ý và tiền sàng lọc hồ sơ vay tín chấp – hướng dẫn cho Claude Code

Đồ án tốt nghiệp (ABBANK): khách hàng tự sàng lọc, mô phỏng phương án vay, lập hồ sơ trực tuyến; nhân viên chi nhánh
đối chiếu bản gốc, ghi nhận kết quả; quản trị viên chỉnh tham số, xem báo cáo. Toàn bộ giao diện và thông báo lỗi bằng tiếng Việt.

## Cấu trúc

```
fe/          React 18 + TypeScript + Vite + Ant Design 5. Đủ 29 chức năng, đang chạy trên API giả lập (MSW)
be/          Spring Boot 3 + Java 21 (đang xây dựng – xem be/CLAUDE.md)
database/    V1__init.sql (24 bảng), V2__seed.sql (dữ liệu mẫu) – PostgreSQL 16
docs/
  openapi.yaml           HỢP ĐỒNG API – nguồn sự thật duy nhất giữa fe và be
  quy-tac-tinh-toan.md   công thức sàng lọc, hạn mức, điểm, đối chiếu giấy tờ, trạng thái, tác vụ, báo cáo
  fe-sua-doi.md          việc cần sửa ở frontend (đợt góp ý 22/10/2026)
  be-huong-dan.md        kế hoạch và quy ước xây dựng backend theo từng giai đoạn
tests/e2e/   full_flow.py – kiểm thử Playwright toàn luồng (khách → nhân viên → quản trị)
docker-compose.yml       PostgreSQL 16 cho phát triển
```

## Quy tắc chung

1. **Hợp đồng trước, code sau.** Mọi thay đổi request/response phải sửa cùng lúc: `docs/openapi.yaml`,
   `fe/src/api/types.ts`, `fe/src/api/endpoints.ts`, mock `fe/src/mocks/handlers/*`, và backend (nếu đã có endpoint đó).
   Sau khi sửa openapi chạy `npx -y @redocly/cli@1.25.11 lint docs/openapi.yaml` – phải 0 lỗi.
2. **Mock là bản đặc tả chạy được.** Hành vi của backend (mã lỗi, thông báo, chuyển trạng thái, ai được làm gì) phải giống
   `fe/src/mocks/handlers/*.ts`, `fe/src/mocks/ctx.ts` và bộ máy tính `fe/src/mocks/engine.ts`. Khi mock và tài liệu
   lệch nhau, hỏi người dùng trước khi chọn.
3. **Công thức nghiệp vụ** nằm trong `docs/quy-tac-tinh-toan.md`. Sửa công thức thì sửa tài liệu này, engine.ts và Java cùng lúc.
4. **Cơ sở dữ liệu**: không sửa V1/V2 sau khi backend đã chạy Flyway trên máy người dùng; thêm file `V3__…`, `V4__…`.
   (Trước khi backend chạy lần đầu thì được sửa thẳng V1/V2.)
5. Không đưa mã biểu mẫu nội bộ (MB01…MB16) ra chữ hiển thị trên giao diện; chỉ dùng trong comment và tài liệu.
6. Tiền là số nguyên VND (`long` ở Java, `number` ở TS). Tỷ lệ phần trăm làm tròn 1 chữ số thập phân như engine.ts.

Dịch vụ ngoài của backend: Cloudinary (lưu ảnh/tệp giấy tờ), Gemini API (đọc thông tin giấy tờ). Khóa qua biến môi trường
`CLOUDINARY_URL`, `GEMINI_API_KEY`, không bao giờ commit.

## Lệnh thường dùng

```bash
docker compose up -d                       # PostgreSQL (db loan_prescreening, user loan / loan123)
cd fe && npm install && npm run dev        # http://localhost:5173 ; build: npm run build ; lint: npx eslint src
cd be && ./mvnw spring-boot:run            # http://localhost:8080/api  (khi đã có backend)
python tests/e2e/full_flow.py              # cần fe đang chạy; BASE_URL=http://localhost:5173
```

Frontend gọi backend thật khi `fe/.env.development` có `VITE_USE_MOCK=false` (Vite proxy `/api` → `localhost:8080`).

## Tài khoản thử (mật khẩu 123456, OTP môi trường dev: 123456)

| Vai trò | Đăng nhập | Tài khoản |
|---|---|---|
| Khách hàng | /dang-nhap | 0901234567 |
| Nhân viên CN Hà Nội / HCM / Đà Nẵng | /noi-bo/dang-nhap | nhanvien@poc.local / nv.hcm@poc.local / nv.danang@poc.local |
| Quản trị viên | /noi-bo/dang-nhap | quantri@poc.local |

## Cách làm việc

- Làm theo từng mục trong `docs/fe-sua-doi.md` hoặc từng giai đoạn trong `docs/be-huong-dan.md`; xong mục nào đánh dấu `[x]` mục đó.
- Mỗi mục xong phải: build/lint/test pass, và kiểm tra lại đúng “Tiêu chí hoàn thành” của mục.
- Không tự thêm thư viện lớn ngoài danh sách trong tài liệu khi chưa hỏi.
