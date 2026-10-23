# Hệ thống gợi ý và tiền sàng lọc hồ sơ vay tín chấp – mã nguồn

Đồ án tốt nghiệp: _Nghiên cứu và phát triển hệ thống gợi ý và tiền sàng lọc hồ sơ vay tiêu dùng tín chấp đối với cá nhân_.
Khách hàng tự sàng lọc, mô phỏng phương án vay, lập hồ sơ trực tuyến; nhân viên chi nhánh đối chiếu bản gốc, ghi nhận kết quả;
quản trị viên chỉnh tham số, xem báo cáo.

## Cấu trúc thư mục

```
.
├── fe/                    Frontend React 18 + TypeScript + Vite + Ant Design 5 (29 chức năng; kèm API giả lập MSW)
├── be/                    Backend Spring Boot 3.3 + Java 21 + PostgreSQL + Flyway (be/CLAUDE.md)
│   └── src/main/resources/db/migration   Flyway V1 (24 bảng), V2 (dữ liệu mẫu), V3…; db/dev: dữ liệu lịch sử cho báo cáo
├── database/              Bản gốc V1__init.sql, V2__seed.sql (tham khảo; backend dùng bản trong be/)
├── docs/
│   ├── openapi.yaml           Hợp đồng API giữa frontend và backend
│   ├── quy-tac-tinh-toan.md   Công thức sàng lọc, hạn mức, điểm, đối chiếu giấy tờ, trạng thái, tác vụ, báo cáo
│   ├── be-huong-dan.md        Kế hoạch và ghi chú xây backend (B0–B9)
│   └── fe-sua-doi.md          Việc đã sửa ở frontend (góp ý 22/10)
├── tests/
│   ├── e2e/full_flow.py       Kiểm thử Playwright toàn luồng (khách → nhân viên → quản trị); files/: giấy tờ mẫu
│   └── contract/compare_endpoints.py   So path + method backend (springdoc) với docs/openapi.yaml
├── docker-compose.yml     PostgreSQL 16 cho phát triển (cổng 5434)
└── CLAUDE.md              Hướng dẫn cho Claude Code
```

## Yêu cầu cài đặt

| Phần mềm | Phiên bản | Dùng cho |
| --- | --- | --- |
| Node.js | 20 trở lên | frontend |
| JDK | 21 | backend (Maven có sẵn qua `be\mvnw.cmd`, không cần cài) |
| Docker Desktop | bản mới | PostgreSQL 16; Testcontainers khi chạy `mvnw verify` |
| Python 3 + Playwright | 3.10 trở lên | kiểm thử e2e: `pip install playwright pyyaml` ; `python -m playwright install chromium` |

Các lệnh dưới đây dùng **Windows PowerShell**, chạy từ thư mục gốc dự án (PowerShell 5.1 không có `&&`, dùng `;`).

## Chạy toàn hệ thống (3 cửa sổ PowerShell)

### 1. Cơ sở dữ liệu

```powershell
docker compose up -d          # PostgreSQL 16 ở localhost:5434, db tien_sang_loc_vay, user tiensangloc / tiensangloc123
```

Bảng và dữ liệu mẫu do Flyway của backend tạo ở lần chạy đầu. Muốn về dữ liệu ban đầu: `docker compose down -v; docker compose up -d`
rồi chạy lại backend.

### 2. Backend (http://localhost:8080/api, Swagger: http://localhost:8080/api/swagger-ui.html)

Tạo `be\.env` (đã nằm trong `.gitignore`, **không commit**) nếu dùng Cloudinary và Gemini thật:

```properties
CLOUDINARY_URL=cloudinary://<api_key>:<api_secret>@<cloud_name>
GEMINI_API_KEY=<khóa lấy ở Google AI Studio>
```

```powershell
cd be
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev,local"   # không cần mạng/khóa: lưu tệp cục bộ, đọc giấy tờ giả lập
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"         # Cloudinary + Gemini thật (cần be\.env)
```

| Profile | Lưu tệp giấy tờ | Đọc giấy tờ (OCR) | Ghi chú |
| --- | --- | --- | --- |
| `dev` | Cloudinary (thư mục `tien-sang-loc-vay/dev`) | Gemini | OTP cố định `123456`; nạp thêm ~90 hồ sơ lịch sử (`db/dev`) cho báo cáo; đọc khóa từ `be\.env` |
| `dev,local` | `be\data\uploads` | giả lập (điền sẵn như API giả lập) | như `dev` nhưng không gọi dịch vụ ngoài – kết quả ổn định, dùng cho e2e |
| `test` | `local` | giả lập | chỉ dùng khi chạy test (`mvnw verify`), DB trong Testcontainers |

Chỉ tải lên Gemini ảnh giấy tờ mẫu/giả (ví dụ `tests\e2e\files`), không dùng giấy tờ thật – gói miễn phí không cho gửi dữ liệu cá nhân.

Gemini nhận ra giấy tờ mẫu (có chữ “MẪU”, “không có giá trị”). Mặc định (`app.ocr.allow-specimen=false`) giấy tờ mẫu bị từ chối
(“Tệp là giấy tờ mẫu, không có giá trị…”); profile `dev` đặt `true` nên vẫn nhận để chạy thử, và ghi nhật ký “Chấp nhận giấy tờ mẫu
(môi trường thử nghiệm)”. Không bật ở môi trường thật.

### 3. Frontend (http://localhost:5173)

```powershell
cd fe
npm install
npm run dev                                   # gọi backend thật: Vite chuyển /api tới localhost:8080
```

Mặc định `fe\.env.development` đặt `VITE_USE_MOCK=false`. Chạy **không cần backend** với API giả lập trong trình duyệt (MSW,
dữ liệu lưu localStorage, có sẵn hồ sơ lịch sử):

```powershell
cd fe
$env:VITE_USE_MOCK="true"; npm run dev        # chỉ có hiệu lực trong cửa sổ PowerShell này
```

(hoặc tạo `fe\.env.development.local` chứa `VITE_USE_MOCK=true`). `npm run build` mặc định dùng API giả lập (`fe\.env.production`).

## Tài khoản thử

Mật khẩu `123456`; OTP ở profile `dev` và API giả lập: `123456`.

| Vai trò | Đăng nhập tại | Tài khoản |
| --- | --- | --- |
| Khách hàng | /dang-nhap | `0901234567` hoặc `khachhang@tiensangloc.local` |
| Nhân viên Chi nhánh Hà Nội | /noi-bo/dang-nhap | `nhanvien@tiensangloc.local` |
| Nhân viên Chi nhánh Hồ Chí Minh | /noi-bo/dang-nhap | `nv.hcm@tiensangloc.local` |
| Nhân viên Chi nhánh Đà Nẵng | /noi-bo/dang-nhap | `nv.danang@tiensangloc.local` |
| Quản trị viên chính sách | /noi-bo/dang-nhap | `quantri@tiensangloc.local` |

## Kiểm thử

```powershell
cd be; .\mvnw.cmd verify                      # build + test backend (Docker phải đang chạy cho Testcontainers)
cd fe; npm run build; npx eslint src          # build + lint frontend
npx -y @redocly/cli@1.25.11 lint docs/openapi.yaml   # kiểm tra hợp đồng API
```

Hai test gọi dịch vụ thật chỉ chạy khi có biến môi trường tương ứng (không đọc `be\.env`): `CloudinaryLiveTest` cần
`CLOUDINARY_URL`, `GeminiOcrLiveTest` cần `GEMINI_API_KEY`; thiếu thì được bỏ qua.

### Kiểm thử toàn luồng (Playwright)

`tests\e2e\full_flow.py` đi hết luồng: sàng lọc → phương án → lập hồ sơ, tải giấy tờ → nộp (OTP) → nhân viên đối chiếu, yêu cầu
bổ sung → khách nộp lại → duyệt, giải ngân → khách theo dõi → các màn hình quản trị. In `ERRORS [...]` (lỗi console/trang),
thoát mã 1 nếu có lỗi; ảnh chụp ở `tests\e2e\shots` (không commit).

```powershell
# Backend thật: DB mới + backend dev,local + frontend mặc định (VITE_USE_MOCK=false)
docker compose down -v; docker compose up -d
# (cửa sổ khác) cd be; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev,local"
# (cửa sổ khác) cd fe; npm run dev
python tests\e2e\full_flow.py --mode real

# API giả lập: frontend chạy với $env:VITE_USE_MOCK="true"
python tests\e2e\full_flow.py --mode mock
```

| Tham số | Biến môi trường | Mặc định | Ý nghĩa |
| --- | --- | --- | --- |
| `--mode mock\|real` | `E2E_MODE` | `mock` | `real` kiểm tra trước backend qua `/api/actuator/health`; báo lỗi nếu frontend đang chạy khác chế độ đã chọn |
| `--base-url` | `BASE_URL` | `http://localhost:5173` | địa chỉ frontend |
| `--headed` | `E2E_HEADED=1` | tắt | mở cửa sổ trình duyệt để xem |
| `--ocr-timeout` | `E2E_OCR_TIMEOUT` | mock 5, real 60 | số giây chờ tải lên và đọc giấy tờ |

Chế độ `real` cần DB mới mỗi lần chạy (luồng tạo hồ sơ mới và ngừng hoạt động Chi nhánh Đà Nẵng). Chạy được với cả profile
`dev` (Cloudinary + Gemini thật) – khi đó kết quả đọc giấy tờ phụ thuộc Gemini.

### So endpoint backend với hợp đồng API

```powershell
python tests\contract\compare_endpoints.py    # backend đang chạy; so /api/v3/api-docs với docs/openapi.yaml
```

Báo endpoint thiếu/thừa theo path + method, thoát mã 1 nếu khác. `/files/local` và `/files/cloud` chỉ có một cái tùy kho lưu
trữ đang dùng nên thiếu cái còn lại không tính lỗi.
