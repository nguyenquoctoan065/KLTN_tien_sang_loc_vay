# Frontend – quy ước

Đọc thêm `../CLAUDE.md` (quy tắc chung) và `fe/README.md` (cấu trúc thư mục, phạm vi 29 chức năng).

- Ant Design 5 với `App.useApp()` cho `message`/`modal`; dữ liệu server qua `@tanstack/react-query` (khóa query
  đặt theo tài nguyên: `['application', id]`, `['admin-catalog']`…); gọi API chỉ qua `src/api/endpoints.ts`.
- Kiểu dữ liệu chỉ khai báo trong `src/api/types.ts` và phải khớp `docs/openapi.yaml`.
- Lỗi hiển thị bằng `errorMessage(e)` từ `src/api/client.ts` (đọc `message` tiếng Việt của backend).
- Định dạng tiền/ngày dùng `src/utils/format.ts`; nhãn trạng thái dùng `src/utils/status.ts` (APP_STATUS).
- Mock: `src/mocks/db.ts` (dữ liệu, `DB_VERSION` – tăng số này khi đổi cấu trúc dữ liệu mẫu để localStorage tự làm mới),
  `ctx.ts` (hàm dùng chung), `engine.ts` (tính toán), `handlers/*` (API).
- Chữ hiển thị tiếng Việt, dễ hiểu với người chưa từng vay; không hiện mã biểu mẫu MBxx; không hiện chữ “PoC”,
  “đồ án”, “thử nghiệm” ở giao diện khách hàng. Gợi ý tài khoản thử chỉ hiện khi `import.meta.env.VITE_USE_MOCK === 'true'`.
- `localStorage`/`sessionStorage` luôn bọc try/catch.
- Trước khi báo xong: `npm run build` và `npx eslint src` (0 lỗi), chạy `python ../tests/e2e/full_flow.py` nếu đụng luồng chính.
