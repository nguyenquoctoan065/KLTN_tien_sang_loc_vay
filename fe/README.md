# Frontend – React + TypeScript + Ant Design

```bash
npm install
npm run dev       # môi trường phát triển, http://localhost:5173
npm run build     # build ra thư mục dist/
npm run preview   # chạy thử bản build
```

## Cấu trúc

```
src/
├── api/              client axios, kiểu dữ liệu (types.ts khớp docs/openapi.yaml), hàm gọi API (endpoints.ts)
├── app/              router, theme
├── auth/             đăng nhập, phân quyền theo vai trò (RequireRole)
├── components/       dùng chung: OTP, trạng thái, dòng thời gian, chuông thông báo, đổi mật khẩu, biểu đồ trả nợ
├── components/app/   khối của hồ sơ vay: bước, thông tin cá nhân, thẻ giấy tờ + MB06, cảnh báo đối chiếu,
│                     điều chỉnh phương án, giấy đề nghị MB15, phiếu tóm tắt MB08 (QR)
├── layouts/          bố cục khách hàng và khu vực nội bộ (menu theo vai trò)
├── mocks/            API giả lập (MSW) – db.ts dữ liệu mẫu, ctx.ts hàm dùng chung, engine.ts bộ máy tính toán,
│                     handlers/ theo nhóm: auth, catalog, screening, applications, me, staff, admin, reports
├── pages/customer/   trang chủ, đăng nhập/đăng ký, sàng lọc, mô phỏng, phương án, lập hồ sơ, chi tiết hồ sơ, trang của tôi
├── pages/internal/   tra cứu và xử lý hồ sơ (nhân viên), tham số, danh mục, nhân viên, chi nhánh, tác vụ, nhật ký, báo cáo
└── utils/            định dạng tiền, ngày; nhãn trạng thái hồ sơ
```

## Biến môi trường

| Biến | Ý nghĩa |
|---|---|
| `VITE_USE_MOCK` | `true`: dùng API giả lập; `false`: gọi backend |
| `VITE_API_BASE_URL` | Mặc định `/api` (dev server proxy tới cổng 8080) |

Dữ liệu giả lập lưu trong localStorage (khóa `tsl-mock-db`). Muốn về dữ liệu ban đầu: xóa khóa này rồi tải lại trang.
OTP giả lập luôn là `123456`.

## Phạm vi – đủ 29 chức năng

| Nhóm | Chức năng | Màn hình |
|---|---|---|
| 1. Tài khoản | 1.1 đăng ký (OTP), 1.2 đăng nhập, 1.3 đăng xuất, 1.4 đổi mật khẩu, 1.5 thông báo, 1.6 quản lý tài khoản nhân viên | /dang-ky, /dang-nhap, chuông thông báo, /noi-bo/nhan-vien |
| 2. Sàng lọc (QT01) | 2.1 đồng ý MB02(a), 2.2 khai báo MB01, 2.3 kết quả gợi ý MB03, 2.4 lưu kết quả | /sang-loc, /sang-loc/:id |
| 3. Phương án (QT02) | 3.1 mô phỏng MB04, 3.2 đề xuất điều chỉnh, 3.3 lưu/xác nhận MB07 | /mo-phong, /phuong-an/:id |
| 4. Hồ sơ (QT03) | 4.1 lập hồ sơ: đồng ý MB02(b), chọn chi nhánh, MB05, tải giấy tờ (kiểm tra chất lượng), trích xuất MB06, đối chiếu tự động; 4.2 giấy đề nghị MB15, nộp bằng OTP, phiếu MB08 có QR | /ho-so/tao, /ho-so/:id |
| 5. Đối chiếu, kết quả (QT04–05) | 5.1 tra cứu theo QR/bộ lọc, 5.2 đối chiếu bản gốc MB09, 5.3 ghi nhận kết quả MB16 và giải ngân, 5.4 khách theo dõi trạng thái | /noi-bo/ho-so, /noi-bo/doi-chieu, /noi-bo/ket-qua |
| 6. Quyền dữ liệu (QT06) | 6.1 xem/xuất dữ liệu, nhật ký truy cập, rút đồng ý (OTP); 6.2 hủy hồ sơ | /cua-toi?tab=du-lieu |
| 7. Quản trị (QT07–09) | 7.1–7.5 phiên bản tham số (nháp, sửa MB11/MB12, chạy thử, kích hoạt, lịch sử), 7.3 danh mục gói vay/giấy tờ/bán chéo, 7.6 tác vụ định kỳ, 7.7 chi nhánh, nhật ký hệ thống, thống kê, độ chính xác mô hình, báo cáo MB14 | /noi-bo/tham-so, /noi-bo/danh-muc, /noi-bo/tac-vu, /noi-bo/chi-nhanh, /noi-bo/nhat-ky, /noi-bo/bao-cao |

## Tài khoản thử (mật khẩu `123456`)

| Vai trò | Tài khoản |
|---|---|
| Khách hàng | 0901234567 |
| Nhân viên Chi nhánh Hà Nội | nhanvien@tiensangloc.local |
| Nhân viên Chi nhánh Hồ Chí Minh | nv.hcm@tiensangloc.local |
| Nhân viên Chi nhánh Đà Nẵng | nv.danang@tiensangloc.local |
| Quản trị viên chính sách | quantri@tiensangloc.local |
