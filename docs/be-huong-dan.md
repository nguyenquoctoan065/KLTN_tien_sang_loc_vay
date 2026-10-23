# Hướng dẫn xây dựng backend (be/)

Tài liệu cho Claude Code dựng backend Java bên cạnh `fe/`. Làm theo giai đoạn **B0 → B9**, mỗi giai đoạn một phiên làm việc.
Đầu mỗi phiên: đọc `CLAUDE.md`, `be/CLAUDE.md` và phần giai đoạn đang làm. Cuối phiên: đánh dấu `[x]`, ghi ghi chú ở cuối file.

## 1. Nguồn đặc tả (thứ tự ưu tiên)

1. `docs/openapi.yaml` – đường dẫn, phương thức, request/response, vai trò (`x-roles`), mã lỗi.
2. `fe/src/mocks/handlers/*.ts` + `fe/src/mocks/ctx.ts` – hành vi chi tiết: kiểm tra quyền, thông báo lỗi tiếng Việt,
   chuyển trạng thái, ghi nhật ký, gửi thông báo. **Backend phải trả đúng thông báo `message` như mock.**
3. `fe/src/mocks/engine.ts` + `docs/quy-tac-tinh-toan.md` – công thức sàng lọc, hạn mức, điểm, mô phỏng, đề xuất.
4. `database/V1__init.sql`, `V2__seed.sql` – lược đồ dữ liệu.

Nếu hai nguồn lệch nhau: hỏi người dùng, không tự chọn.

## 2. Công nghệ (cố định)

| Thành phần | Lựa chọn |
|---|---|
| Ngôn ngữ, build | Java 21, Maven (có `mvnw`), Spring Boot 3.3.x |
| Web | spring-boot-starter-web, validation; context path `/api`; cổng 8080 |
| Dữ liệu | Spring Data JPA (Hibernate 6), PostgreSQL 16, **Flyway** (`src/main/resources/db/migration`) |
| Bảo mật | spring-boot-starter-security, BCrypt; token phiên ngẫu nhiên lưu bảng (xem 5.1) |
| Tài liệu API | springdoc-openapi-starter-webmvc-ui (Swagger tại `/api/swagger-ui.html`) |
| Tiện ích | Lombok, MapStruct (ánh xạ entity ↔ DTO) |
| Lưu ảnh, tệp giấy tờ | Cloudinary – `com.cloudinary:cloudinary-http5:2.5.0` |
| Đọc thông tin giấy tờ | Gemini API – `com.google.genai:google-genai:1.78.0` (Google Gen AI Java SDK) |
| Kiểm thử | JUnit 5, Spring Boot Test, MockMvc, **Testcontainers PostgreSQL**, AssertJ |

Bảng trên là bộ thư viện nền. Ngoài bảng, **được tự thêm dependency** nếu giúp code gọn hoặc an toàn hơn, với điều kiện:
- ưu tiên starter chính thức của Spring Boot (version do `spring-boot-starter-parent` quản lý) và thư viện phổ biến, còn bảo trì, giấy phép mở (Apache/MIT);
- ghi version cụ thể nếu Spring không quản lý; không dùng bản SNAPSHOT/beta;
- mỗi dependency mới ghi 1 dòng lý do vào mục 9 của tài liệu này và comment trong `pom.xml`.

**Vẫn phải hỏi trước** nếu: đổi framework/ORM; thêm dịch vụ trả phí hoặc gọi ra ngoài internet; hoặc thư viện chỉ có một người duy trì.
Frontend giữ nguyên quy tắc cũ: hỏi trước khi thêm thư viện.

## 3. Cấu trúc thư mục

```
be/
├── pom.xml, mvnw, .mvn/
├── CLAUDE.md
└── src/
    ├── main/java/vn/kltn/tiensanglocvay/
    │   ├── TienSangLocVayApplication.java
    │   ├── common/        ApiException, GlobalExceptionHandler, Clock/IdGen, Money/Percent helpers, PageDto
    │   ├── security/      SecurityConfig, TokenAuthFilter, CurrentUser (record: kind, id, role, branchId), @RequireRole
    │   ├── auth/          AuthController, AuthService, OtpService, SessionRepository…
    │   ├── catalog/       products, cross-sells, branches, document types, product-document rules
    │   ├── policy/        param versions, policy_param, scoring_weight, ActivePolicy (cache phiên bản hiệu lực)
    │   ├── engine/        ScreeningEngine, SimulationEngine, Schedule – Java thuần, không phụ thuộc Spring/JPA
    │   ├── screening/     /screenings, /simulations
    │   ├── plan/          /plans
    │   ├── application/   /applications/** : hồ sơ, thông tin cá nhân, giấy đề nghị, nộp, hủy, CrossCheckService
    │   ├── document/      upload, StorageService (CloudinaryStorageService, LocalStorageService cho test),
    │   │                  QualityChecker, OcrService (GeminiOcrService, FakeOcrService)
    │   ├── staff/         /staff/** : tra cứu, đối chiếu, kết quả, giải ngân
    │   ├── privacy/       /me/data, /me/consents/withdraw
    │   ├── notification/  /notifications, NotificationService
    │   ├── audit/         ActivityLogService, /admin/logs
    │   ├── admin/         /admin/staff, /admin/branches, /admin/catalog…, /admin/jobs, ScheduledJobs
    │   └── report/        /admin/stats, /admin/model-accuracy, /admin/reports/mb14
    ├── main/resources/
    │   ├── application.yml, application-dev.yml (Cloudinary + Gemini, import be/.env), application-local.yml (kho cục bộ + OCR giả)
    │   └── db/migration/  V1__init.sql, V2__seed.sql (chép từ ../database), V3__app_support.sql (B1), V4__sua_dinh_dang_sao_ke.sql (B2), V5__public_id.sql (mã công khai UUID), V6__… (B4), V7__… (mỗi giai đoạn cần thêm bảng/cột một file mới); db/dev/V1000__dev_history.sql
    └── test/java/vn/kltn/tiensanglocvay/…  (+ test/resources/engine-fixtures/*.json)
```

Mỗi module: `XxxController` (chỉ nhận/trả DTO, kiểm tra vai trò) → `XxxService` (nghiệp vụ, `@Transactional`)
→ `XxxRepository` (JPA). DTO là `record` Java, tên và trường **giống hệt** schema trong openapi.yaml.

## 4. Quy ước

- **JSON**: camelCase như openapi. Ngày `LocalDate` → `"yyyy-MM-dd"`, thời điểm `OffsetDateTime` ISO-8601 (`Asia/Ho_Chi_Minh`).
  `spring.jackson.default-property-inclusion: always` (FE cần trường `null`).
- **Tiền** `long` VND; **tỷ lệ %** `double`, làm tròn giống engine.ts (`Math.round(x*10)/10`). Không dùng `float`.
- **Lỗi**: luôn trả `{ "message": "<tiếng Việt>", "fields": { "<tên trường>": "<lỗi>" } }` (`fields` chỉ khi lỗi validate).
  `ApiException(HttpStatus, message)`; `GlobalExceptionHandler` xử lý cả `MethodArgumentNotValidException`,
  `AccessDeniedException` (403 “Không có quyền.”), 401 “Chưa đăng nhập.” – lấy đúng chữ trong mock.
- **Quyền**: vai trò `CUSTOMER`, `BRANCH_STAFF`, `ADMIN`. Nhân viên chỉ thao tác hồ sơ thuộc `branchId` của mình (mock: xem chi tiết hồ sơ khác chi nhánh → 404 “Không tìm thấy hồ sơ.”,
  tra cứu QR hồ sơ khác chi nhánh → 403 “Hồ sơ thuộc chi nhánh khác.”). Quản trị không thao tác nghiệp vụ hồ sơ.
- **Nhật ký**: mọi hành động mock gọi `log(...)` thì backend gọi `ActivityLogService.log(actor, action, detail, application)`
  cùng chữ `action`. Mọi chỗ mock gọi `notify(...)` thì gọi `NotificationService`.
- **Trạng thái hồ sơ**: chỉ đổi qua một hàm `ApplicationStateMachine.transition(app, to, actor, note)` – kiểm tra chuyển hợp lệ
  theo `docs/quy-tac-tinh-toan.md` mục 12, ghi lịch sử, cập nhật `updated_at`.
- Trường tính toán `can {edit, submit, cancel, resubmit, verify, decide, disburse}` của `Application`: chép logic hàm `view()` trong `ctx.ts`.
- Nhật ký phải ghi bằng nhãn tiếng Việt (ví dụ trạng thái “Được phê duyệt”, không ghi `APPROVED`): dùng cùng bảng nhãn với `APP_STATUS` trong `fe/src/utils/status.ts` cho `action` và `detail`.
- Không log mật khẩu, OTP, số CCCD đầy đủ.

## 5. Quyết định kỹ thuật

### 5.1 Đăng nhập và phiên
- Token: 32 byte ngẫu nhiên (Base64URL) trả cho FE; DB lưu **SHA-256** của token trong bảng `auth_session`
  (account_kind, account_id, created_at, expires_at = +8 giờ, revoked_at). Đăng xuất, khóa tài khoản, đổi/reset mật khẩu → thu hồi phiên.
  (Dùng bảng thay JWT để thu hồi được ngay – chức năng 1.3, 1.6.)
- `TokenAuthFilter` đọc `Authorization: Bearer …`, nạp `CurrentUser`.
- Khách đăng nhập bằng số điện thoại hoặc email (xem `docs/fe-sua-doi.md` F1). Tài khoản `LOCKED` → thông báo như mock.

### 5.2 OTP
- Bảng `otp_code` (target: số điện thoại hoặc id khách, purpose: REGISTER/SUBMIT/WITHDRAW…, code_hash, expires_at +5 phút, attempts ≤ 5, used_at).
- Profile `dev`: `app.otp.fixed-code=123456`, không gửi SMS, ghi log “OTP dev”. Interface `SmsSender` để sau gắn nhà cung cấp thật.

### 5.3 Lưu tệp giấy tờ – Cloudinary
- `interface StorageService { StoredFile upload(byte[] data, String fileName, String folder); String signedUrl(StoredFile f, Duration ttl); void delete(StoredFile f); }`
  - `CloudinaryStorageService` (mặc định, `app.storage.provider=cloudinary`).
  - `LocalStorageService` (ghi `./data/uploads`, dùng cho test và khi chưa có khóa).
- **Cấu hình**: một biến `CLOUDINARY_URL=cloudinary://<api_key>:<api_secret>@<cloud_name>` (lấy ở Cloudinary Console).
  Khởi tạo `new Cloudinary(System.getenv("CLOUDINARY_URL"))`. Không ghi khóa vào code hay `application.yml` đã commit.
- **Tải lên** (giấy tờ có CCCD là dữ liệu cá nhân, không để công khai):
  `uploader().upload(bytes, ObjectUtils.asMap("type", "authenticated", "resource_type", "auto",
  "folder", "tien-sang-loc-vay/<môi trường>/app-<applicationId>", "public_id", "doc-<documentId>-v<version>", "overwrite", true))`.
  - Lưu vào DB: `public_id`, `resource_type`, `format`, `version`, `bytes`. Cột `storage_path` giữ `public_id`;
    các cột còn lại thêm bằng migration của giai đoạn B4 (V6 trở đi) (`storage_resource_type`, `storage_version`).
  - Không lưu `secure_url` công khai.
- **Xem lại tệp**: `previewUrl` trong DTO là URL ký có hạn 10 phút, sinh mới mỗi lần trả hồ sơ; FE dùng thẳng trong thẻ `<img>`.
  - **Đã chốt (23/10/2026) – backend làm trung gian**: gói Cloudinary miễn phí không có URL giao tệp tự hết hạn (token theo thời gian
    chỉ từ gói Advanced; `expires_at` chỉ dùng được với `private_download_url`, trả tệp gốc). Vì vậy `previewUrl` là
    `/api/files/cloud?path&rt&v&fmt&exp&sig` (HMAC-SHA256 trên mọi tham số + hạn, khóa `app.storage.url-secret`, cùng `UrlSigner` với
    `/files/local`). `CloudFileController` kiểm tra chữ ký rồi tải ảnh bằng URL ký của Cloudinary
    `cloudinary.url().type("authenticated").resourceType(rt).signed(true).version(v)…` và trả về; URL Cloudinary không lộ ra trình duyệt.
  - Ảnh xem trước dùng biến đổi `w_480,c_limit,q_auto,f_auto`.
  - PDF: trang đầu dùng `pg_1` (đuôi `.jpg`) để làm ảnh xem trước. Tệp gốc cỡ đầy đủ (nút “Mở tệp”) qua `fileUrl` = `/api/files/cloud?…&kind=original` (`kind` nằm trong chữ ký; B5).
  - `LocalStorageService` có `GET /api/files/local?path&exp&sig` (chỉ ảnh, xem ghi chú B4 phần A).
- **PDF**: tài khoản Cloudinary miễn phí mặc định chặn phát tệp PDF. Bật *Settings → Security → Allow delivery of PDF and ZIP files*.
- **Xóa**: tác vụ `PURGE_RETENTION`, rút đồng ý phạm vi B và tải lại phiên bản mới (tùy chọn giữ bản cũ) đều gọi
  `uploader().destroy(publicId, {type: authenticated, resource_type: rt, invalidate: true})`.
- **Thứ tự xử lý khi upload**:
  1. Kiểm tra chất lượng theo `docs/quy-tac-tinh-toan.md` mục 9, ngay trên bytes nhận được và trước khi gửi lên Cloudinary.
     Đặt `spring.servlet.multipart.max-file-size: 15MB` để backend tự trả lỗi “lớn hơn 10 MB” bằng tiếng Việt.
  2. Đạt thì upload lên Cloudinary.
  3. Gọi OCR (5.4) với chính bytes đó, không tải lại từ Cloudinary.
  4. Lưu DB trong một transaction.
  - Nếu bước 3 lỗi thì vẫn lưu giấy tờ ở trạng thái `UPLOADED` với `fields` rỗng và `qualityMessage`
    “Chưa đọc được thông tin tự động, vui lòng nhập tay”. FE vẫn cho khách nhập giá trị xác nhận.
- Kiểm tra độ nét (`BLURRY`): giữ quy tắc kích thước của mock. Có thể bổ sung bằng kết quả Gemini (`isReadable=false` ở 5.4).

### 5.4 Đọc thông tin giấy tờ – Gemini API
- `interface OcrService { OcrResult extract(DocumentType type, byte[] data, String mimeType); }`
  - `OcrResult` gồm `isReadable`, `documentMatchesType`, `issue` và danh sách `fields`.
  - `GeminiOcrService` (mặc định, `app.ocr.provider=gemini`).
  - `FakeOcrService` (`app.ocr.provider=fake`, cho test và khi hết hạn mức): chép đúng hàm `ocrFields` trong `fe/src/mocks/ctx.ts`.
- **Khóa**: `GEMINI_API_KEY` (tạo ở Google AI Studio), biến môi trường hoặc `be/.env` (profile dev import). Một bean dùng chung
  `GeminiConfig`: `Client.builder().apiKey(…).httpOptions(timeout, retry SDK tắt)`.
- **Model**: `app.ocr.model`, mặc định `gemini-3.5-flash-lite` (rẻ và nhanh). Nếu đọc sai nhiều thì đổi sang `gemini-3.8-flash`.
  Tên model đổi theo thời gian, nên chỉ để trong cấu hình, không viết cứng trong code.
- **Gọi API**: một lần `client.models.generateContent(model, content, config)`.
  - `content` gồm `Part.fromBytes(data, mimeType)` (ảnh JPG/PNG hoặc PDF) và `Part.fromText(prompt)`.
  - `config` có `responseMimeType("application/json")`, `responseSchema(...)` và `temperature(0)`.
  - `responseSchema` sinh theo loại giấy tờ, dùng các `field_key` ở mục 9 của quy tắc (danh mục trong `document/DocumentFields`):
    CCCD `full_name, national_id, dob, gender, address`; cư trú `address`; HĐLĐ `employer, salary`; sao kê `salary_bank, avg_income`;
    hóa đơn `beneficiary, invoice_amount`. **Đã chốt (23/10/2026)**: HĐLĐ dùng `salary`, cư trú dùng `address` như mock (trước ghi
    `contract_salary`, `residence_address`).
    ```json
    { "isReadable": bool, "documentMatchesType": bool, "isSpecimen": bool, "issue": string|null,
      "fields": [ { "key": "national_id", "value": string|null, "confidence": number /* 0–100 */ } ] }
    ```
  - Prompt tiếng Việt, liệt kê từng `field_key` và định dạng mong muốn:
    - ngày `dd/MM/yyyy`;
    - số tiền chỉ gồm chữ số, không dấu chấm;
    - tên in hoa giữ dấu;
    - không đoán khi không đọc được (`value: null`).
    - Riêng sao kê, yêu cầu tính “lương chuyển khoản bình quân 03 tháng” từ các giao dịch có nội dung lương.
- **Ánh xạ kết quả**:
  - `isReadable=false` → `REJECTED_QUALITY` / `BLURRY`, kèm `issue` làm lời nhắc.
  - `documentMatchesType=false` → `REJECTED_QUALITY` / `WRONG_TYPE`, lời nhắc “Tệp không phải <tên giấy tờ>”.
    Bổ sung `WRONG_TYPE` vào enum `qualityStatus` trong openapi và FE.
  - `isSpecimen=true` (ảnh rõ nhưng là giấy tờ mẫu/specimen, có dấu “MẪU”, “không có giá trị”) → `REJECTED_QUALITY` / `SPECIMEN`, lời nhắc
    “Tệp là giấy tờ mẫu, không có giá trị, vui lòng tải giấy tờ thật.” Prompt yêu cầu Gemini báo `isSpecimen=true` (không dùng `isReadable=false`)
    và vẫn đọc các trường. `isSpecimen` thiếu trong JSON coi là false. Thứ tự xét: `isReadable`, `documentMatchesType`, rồi `isSpecimen`
    (`OcrPolicy.judge`). Cấu hình `app.ocr.allow-specimen` (mặc định `false`; `application-dev.yml` đặt `true`): khi `true` giấy tờ mẫu vẫn được
    nhận như thường và ghi nhật ký “Chấp nhận giấy tờ mẫu (môi trường thử nghiệm)”. Không bật ở môi trường thật. Migration `V11` thêm `SPECIMEN`
    vào CHECK `quality_status`.
  - Còn lại → `fields`, lưu `ocr_value` và `confidence`. Chuẩn hóa số tiền về dạng `18.800.000` như mock để `CrossCheckService` dùng chung.
- **Độ bền**:
  - Timeout 30 giây.
  - Thử lại 2 lần khi gặp 429/503 (chờ lũy tiến 1 s rồi 3 s).
  - Ghi log thời gian, số token và mã lỗi; không ghi nội dung ảnh hay giá trị trích xuất.
  - JSON không đúng schema → coi như lỗi OCR (xem 5.3, bước 3).
- **Dữ liệu cá nhân**: theo điều khoản Gemini API, ở gói **miễn phí** Google được dùng dữ liệu gửi lên để cải thiện sản phẩm và
  người duyệt có thể đọc, và điều khoản yêu cầu không gửi thông tin cá nhân lên gói miễn phí. Vì vậy:
  - khi phát triển và demo chỉ dùng ảnh giấy tờ mẫu hoặc giả (`tests/e2e/files`), không dùng CCCD thật;
  - muốn chạy với dữ liệu thật thì bật thanh toán (gói trả phí) cho project của API key;
  - ghi rõ điều này trong báo cáo đồ án.
- **Kiểm thử**:
  - Test đơn vị dùng `FakeOcrService` hoặc mock `Client`.
  - Có một test tích hợp `@EnabledIfEnvironmentVariable(named="GEMINI_API_KEY", matches=".+")` chạy với 5 tệp trong `tests/e2e/files`,
    chỉ kiểm tra cấu trúc kết quả (`GeminiOcrLiveTest`; tương tự `CloudinaryLiveTest` với `CLOUDINARY_URL`).

### 5.5 Bộ máy tính (engine)
- Chép `fe/src/mocks/engine.ts` sang `engine/` (Java thuần, có `record` input/output). Giữ nguyên thứ tự kiểm tra, câu chữ lý do và hướng cải thiện.
- **Kiểm thử đối chứng**: viết `be/tools/gen-engine-fixtures.ts` chạy bằng `npx tsx`, gọi engine.ts với ~30 bộ đầu vào
  (đủ trường hợp: tuổi biên, DTI biên, nợ khác, CBNV ABBANK, bán chéo, từng `creditHistory`, kỳ hạn vượt tuổi nghỉ hưu…)
  và ghi JSON vào `src/test/resources/engine-fixtures/`. `EngineFixtureTest` đọc các file này, so kết quả Java = TS (chênh tiền 0, tỷ lệ ≤ 0,05).
  Mỗi lần sửa engine.ts phải sinh lại fixtures.

### 5.6 Tác vụ định kỳ
- `@EnableScheduling`; ba job theo `docs/quy-tac-tinh-toan.md` mục 13, cron cấu hình trong `application.yml`.
- Mỗi job là một bean `ScheduledJob { code(); run(): String }`; `/admin/jobs/{code}/run` gọi cùng bean. Bảng `scheduled_job` lưu
  `last_run_at`, `last_result`.

### 5.7 Cơ sở dữ liệu bổ sung (V3, V6, V7…; V4 đã dùng ở B2, V5 cho mã công khai UUID)
Mỗi giai đoạn tự so DTO trong openapi.yaml với lược đồ hiện có và thêm phần thiếu vào **một file migration mới theo thứ tự**
(`V6__…`, `V7__…`); không sửa file đã chạy. `V3__app_support.sql` (B1) đã có `auth_session`, `otp_code` và unique index `lower(email)`. Còn cần:
- `notification` (người nhận: customer/staff/branch, title, body, link, read_at),
  `application_status_history` (application_id, from_status, to_status, actor, note, created_at), `scheduled_job`.
- `loan_application`: `cancel_reason`, các cột còn thiếu so với `Application` DTO (ví dụ thông tin cá nhân khai cho hồ sơ nếu không lưu ở `customer`).
- `application_document`: `status`, `quality_status`, `quality_message`, `issued_date`, `version_no` nếu V1 chưa có.
- Cột `credit_history` của `screening` (nếu chưa sửa thẳng V1 theo F2).
Ghi rõ trong comment SQL mỗi cột phục vụ DTO nào.

### 5.8 Dữ liệu lịch sử cho báo cáo
Màn hình thống kê/độ chính xác/MB14 cần dữ liệu nhiều tháng. Viết `db/dev/V1000__dev_history.sql` (số 1000 để luôn lớn hơn các migration chính; chỉ chạy profile dev qua
`spring.flyway.locations` thêm `classpath:db/dev`) sinh ~90 hồ sơ 6 tháng ở 3 chi nhánh, bám theo hàm `historical()` trong `fe/src/mocks/db.ts`.

## 6. Giai đoạn

Mỗi giai đoạn hoàn thành khi: `./mvnw verify` pass (có test MockMvc + Testcontainers cho từng endpoint của giai đoạn),
các trang FE tương ứng chạy được với `VITE_USE_MOCK=false`, và thông báo lỗi giống mock.

### B0. Khung dự án
- [x] Tạo Spring Boot (Maven wrapper, Java 21), các dependency mục 2, `application.yml` (datasource tới docker-compose, `server.servlet.context-path=/api`).
- [x] Flyway: chép `database/V1__init.sql`, `V2__seed.sql`; `spring.jpa.hibernate.ddl-auto=validate`.
  Sửa docker-compose: bỏ mount `./database` vào initdb (Flyway sẽ tạo bảng) – hỏi người dùng trước khi sửa.
- [x] `GlobalExceptionHandler`, `ApiException`, `CurrentUser`, SecurityConfig (mọi `/api/**` cần đăng nhập trừ các endpoint `security: []` trong openapi).
- [x] `GET /api/actuator/health` (spring-boot-starter-actuator). Test khởi động với Testcontainers.

### B1. Tài khoản (1.1–1.4)
- [x] V3 phần `auth_session`, `otp_code`. `/auth/login`, `/auth/logout`, `/auth/otp`, `/auth/register`, `/auth/me`, `/auth/change-password`.
- [x] Test: đăng nhập đúng/sai/khóa, OTP sai, đăng ký trùng số/email, đổi mật khẩu thu hồi phiên cũ.

### B2. Danh mục và tham số đọc
- [x] Entity: loan_product, cross_sell_product, branch, document_type, product_document_type, param_version, policy_param, scoring_weight.
- [x] `/products`, `/products/{code}/checklist`, `/cross-sell-products`, `/branches`. `ActivePolicy` lấy phiên bản đang hiệu lực.

### B3. Sàng lọc, mô phỏng, phương án (QT01–QT02)
- [x] `engine/` + fixtures (5.5). `/screenings`, `/screenings/{id}`, `/screenings/{id}/save`, `/me/screenings` (consent A).
- [x] `/simulations`, `/plans`, `/plans/{id}`, `/plans/{id}/confirm`, `/me/plans`.
- [x] Bật FE với backend thật: chạy luồng sàng lọc → phương án.

### B4. Hồ sơ vay (QT03, 4.1–4.2)
- [x] Migration mới (`V6__…`) cho phần còn lại của 5.7. `/applications` (tạo, consent B, sinh danh mục giấy tờ theo gói), `/me/applications`, `/applications/{id}`.
- [x] `/personal`, upload `/documents/{docId}` (QualityChecker → StorageService → OcrService), `/fields`, `CrossCheckService` (mục 10),
  `/plan`, `/request-form`, `/submit` (OTP, khóa nội dung mục 11, QR, hạn), `/cancel`.
- [x] `NotificationService` + `/notifications`, `/notifications/{id}/read`, `/notifications/read-all`; lịch sử trạng thái.
- [x] (B4b) `CloudinaryStorageService` + URL ký có hạn (5.3), `GeminiOcrService` (5.4); test chạy với `storage.provider=local`, `ocr.provider=fake`.

### B5. Chi nhánh (QT04–QT05)
- [x] `/staff/applications` (lọc trạng thái nhiều giá trị, từ khóa, chi nhánh của nhân viên), `/staff/applications/by-qr/{token}`, `/staff/summary`.
- [x] `/verification` (MATCH → VERIFIED, có MISMATCH/MISSING → NEED_MORE, bắt buộc lý do), `/decision`, `/disbursement`.

### B6. Quyền dữ liệu (QT06)
- [x] `/me/data` (dữ liệu, các lần đồng ý, nhật ký truy cập), `/me/consents/withdraw` (mục 14, OTP).

### B7. Quản trị (QT07, 1.6, 7.x)
- [x] Phiên bản tham số: list/get/clone (kèm `changes`)/update/delete nháp/test (bộ hồ sơ mẫu trong `handlers/admin.ts`)/activate.
- [x] `/admin/staff` + lock/unlock/reset-password; `/admin/branches` + deactivate (chuyển hồ sơ đang mở và nhân viên sang `transferToBranchId`)/activate.
- [x] `/admin/catalog`, `/admin/products/{id}`, `/admin/document-types`, `/admin/product-documents`, `/admin/cross-sell-products`.
- [x] `/admin/jobs`, `/admin/jobs/{code}/run` + `@Scheduled`; `/admin/logs` (lọc, phân trang).

### B8. Báo cáo (QT09)
- [x] `/admin/stats` (phễu, theo tháng/trạng thái/chi nhánh/gói), `/admin/model-accuracy` (mục 15), `/admin/reports/mb14` – viết bằng truy vấn SQL
  (JPQL/native) thay vì nạp hết vào bộ nhớ; so kết quả với mock trên cùng dữ liệu `V1000__dev_history.sql`.
- [x] `db/dev/V1000__dev_history.sql` (5.8).

### B9. Ghép nối và kiểm thử toàn luồng
- [x] `fe/.env.development`: `VITE_USE_MOCK=false`. Chạy `python tests/e2e/full_flow.py` → `ERRORS []`.
- [x] So từng endpoint với openapi: tải `/api/v3/api-docs` của springdoc, viết script so danh sách path+method với `docs/openapi.yaml` (không thiếu, không thừa).
- [x] Cập nhật `README.md`: cách chạy backend, biến môi trường, tài khoản.

## 7. Cấu hình mẫu (`application.yml`)

```yaml
server: { port: 8080, servlet: { context-path: /api } }
spring:
  datasource: { url: jdbc:postgresql://localhost:5434/tien_sang_loc_vay, username: tiensangloc, password: tiensangloc123 }
  jpa: { hibernate: { ddl-auto: validate }, open-in-view: false, properties: { hibernate.jdbc.time_zone: Asia/Ho_Chi_Minh } }
  flyway: { locations: classpath:db/migration }
  servlet: { multipart: { max-file-size: 15MB, max-request-size: 16MB } }
  jackson: { time-zone: Asia/Ho_Chi_Minh, default-property-inclusion: always }
app:
  session-hours: 8
  otp: { fixed-code: ""}          # dev: 123456
  storage:
    provider: cloudinary          # cloudinary | local
    folder: tien-sang-loc-vay/prod  # dev: tien-sang-loc-vay/dev, test: tien-sang-loc-vay/test
    signed-url-minutes: 10
    url-secret: ""                # khóa HMAC của previewUrl; trống = sinh ngẫu nhiên mỗi lần khởi động
    local-dir: ./data/uploads
  ocr:
    provider: gemini              # gemini | fake
    model: gemini-3.5-flash-lite
    timeout-seconds: 30
# Bí mật chỉ đặt qua biến môi trường hoặc be/.env (đã có trong .gitignore; profile dev import bằng spring.config.import):
#   CLOUDINARY_URL=cloudinary://<api_key>:<api_secret>@<cloud_name>
#   GEMINI_API_KEY=<khóa từ Google AI Studio>
  jobs:
    expire-documents: "0 0 1 * * *"
    purge-retention: "0 0 2 1 * *"
    remind-drafts: "0 0 9 * * *"
```
`application-dev.yml`: `spring.config.import: optional:file:.env[.properties]`, `app.otp.fixed-code: "123456"`, `app.storage.folder: tien-sang-loc-vay/dev`.
`application-local.yml` (chạy kèm dev khi không có mạng/khóa: `-Dspring-boot.run.profiles=dev,local`): `app.storage.provider: local`, `app.ocr.provider: fake`.
Profile `test`: `app.storage.provider: local`, `app.ocr.provider: fake`, `app.storage.folder: tien-sang-loc-vay/test`, `spring.flyway.locations` chỉ `classpath:db/migration` (không chạy db/dev; riêng `DevHistoryMigrationTest` bật db/dev trong container của nó).
`application-dev.yml` (từ B8): `spring.flyway.locations: classpath:db/migration,classpath:db/dev`, `spring.flyway.out-of-order: true`.

## 8. Gợi ý khi dùng Claude Code

- Mở **thư mục gốc dự án** (chứa `fe/`, `be/`, `docs/`) để Claude Code đọc được cả mock lẫn tài liệu.
- Mỗi phiên một giai đoạn: “Đọc docs/be-huong-dan.md, làm giai đoạn B3”. Dùng chế độ lập kế hoạch (plan mode) trước khi viết code cho B4, B7.
- Yêu cầu Claude chạy `./mvnw verify` và đối chiếu với mock trước khi báo xong.

## 9. Ghi chú tiến độ
(Claude Code ghi lại tại đây: quyết định đã chốt với người dùng, việc còn tồn đọng.)

### B0 – xong 23/10/2026
- Khung `be/`: Spring Boot **3.3.13**, Java 21, Maven wrapper (Maven 3.9.9). start.spring.io hiện chỉ còn Boot ≥ 4.0 nên `pom.xml` viết tay; wrapper chép từ skeleton Boot 4.0.8 rồi đổi sang Maven 3.9.9.
  Dependency đúng mục 2 (springdoc 2.6.0, MapStruct 1.6.3 + lombok-mapstruct-binding 0.2.0, cloudinary-http5 2.5.0, google-genai 1.78.0, Testcontainers qua `spring-boot-testcontainers`). Flyway 10 cần thêm `flyway-database-postgresql`.
- Đã có: `ApiException`, `ErrorBody` (record `{message, fields}`, `fields` chỉ khi có), `GlobalExceptionHandler`, `CurrentUser`/`Role`/`AccountKind`, `SecurityConfig` (stateless, CSRF tắt, BCrypt bean; 401 “Chưa đăng nhập.”, 403 “Không có quyền.” dạng JSON).
  Chưa có `TokenAuthFilter` (làm ở B1 cùng bảng `auth_session`), nên hiện mọi endpoint cần đăng nhập đều trả 401.
- Endpoint không cần đăng nhập (rút từ `security: []` / `[{}, bearer]` trong openapi): POST `/auth/login`, `/auth/register`, `/auth/otp`, `/screenings`, `/simulations`; GET `/products`, `/products/*/checklist`, `/cross-sell-products`, `/branches`, `/screenings/*`.
  Thêm cho tiện phát triển: `/actuator/health`, Swagger UI và `/v3/api-docs`. Khi làm B1 cần để `TokenAuthFilter` vẫn nạp `CurrentUser` (nếu có token) trên các endpoint `[{}, bearer]`.
- `UserDetailsServiceAutoConfiguration` bị loại để Spring Security không tạo tài khoản `user` với mật khẩu ngẫu nhiên.
- Thông báo lỗi chung chưa có trong mock nên tự đặt: “Dữ liệu không hợp lệ.” (400), “Không tìm thấy.” (404 đường dẫn lạ), “Phương thức không được hỗ trợ.” (405), “Lỗi hệ thống, vui lòng thử lại sau.” (500), “Tệp lớn hơn 10 MB.” (vượt `max-file-size`). Đối chiếu lại với mock khi làm B1/B4.
- Actuator chỉ mở `health` (không chi tiết). `application-dev.yml` chỉ đặt OTP cố định `123456`; `src/test/resources/application-test.yml` đặt storage `local`, OCR `fake`.
  Chưa thêm `classpath:db/dev` vào `spring.flyway.locations` của profile test vì thư mục này chỉ có ở B8.
- **docker-compose.yml** (đã hỏi và được đồng ý): bỏ mount `./database` vào initdb để Flyway tạo bảng; chạy `docker compose down -v` rồi `up -d` để bỏ dữ liệu initdb cũ.
- **Cổng PostgreSQL: 5434** (đã hỏi và được đồng ý): 5432 bị dịch vụ Windows `postgresql-x64-18` chiếm (app kết nối nhầm vào đó và báo sai mật khẩu), 5433 dùng cho dự án khác (container `emagazine-dev-db`).
  Đã đặt `docker-compose.yml` (`5434:5432`), `application.yml` (`jdbc:postgresql://localhost:5434/...`), mẫu cấu hình ở mục 7, `CLAUDE.md`, `be/CLAUDE.md`. Máy không có xung đột có thể đổi lại.
- Kiểm tra: `.\mvnw.cmd verify` → 12 test pass (6 `GlobalExceptionHandlerTest`, 6 `TienSangLocVayApplicationTests` với Testcontainers: health UP, Flyway V1+V2 đủ 24 bảng, 401 tiếng Việt, endpoint công khai không bị chặn). `.\mvnw.cmd spring-boot:run` với DB Docker mới: Flyway áp V1, V2; `GET /api/actuator/health` → `{"status":"UP"}`; `GET /api/auth/me` → 401 “Chưa đăng nhập.”; `/api/v3/api-docs` → 200.
- Còn tồn đọng: `@RequireRole` chưa làm (cần khi có controller, B1).

### B1 – xong 23/10/2026
- **Đã có**: `V3__app_support.sql` (`auth_session`, `otp_code`, unique index `lower(email)` trên `customer`), `auth/` (`AuthController`, `AuthService`, `SessionService`, `OtpService`, `SmsSender` + `LoggingSmsSender`, entity `Customer`/`Staff`/`AuthSession`/`OtpCode`, DTO record), `security/TokenAuthFilter`, `audit/ActivityLogService` (bản tối thiểu: ghi `activity_log`), `common/` thêm `Clock`, `Hashing`, `OkResult`.
- **Phiên**: token 32 byte Base64URL (43 ký tự), DB chỉ lưu SHA-256; hết hạn sau `app.session-hours` (8 giờ). `TokenAuthFilter` bỏ qua token sai/hết hạn/tài khoản không còn `ACTIVE` (không báo lỗi), nên các endpoint `[{}, bearer]` vẫn chạy; endpoint cần đăng nhập trả 401 “Chưa đăng nhập.”. `/auth/logout` được mở công khai vì openapi quy định luôn trả 200 (mock cũng vậy); chỉ ghi nhật ký “Đăng xuất” khi token còn hiệu lực.
- **Thông báo lỗi** chép từng chữ từ `handlers/auth.ts` và kiểm tra trong `AuthApiTest`: “Sai tài khoản hoặc mật khẩu.” (401), “Tài khoản đang bị khóa, liên hệ quản trị viên.” (403, chỉ sau khi đúng mật khẩu như mock), “Số điện thoại không hợp lệ.”, “Mã OTP không đúng.”, “Số điện thoại đã được đăng ký.”, “Email đã được sử dụng.”, “Mật khẩu hiện tại không đúng.”, “Mật khẩu mới tối thiểu 6 ký tự.”, “Mật khẩu mới phải khác mật khẩu hiện tại.”. Thứ tự kiểm tra giống mock (đăng ký: OTP → trùng số → trùng email; đổi mật khẩu: mật khẩu cũ → độ dài → trùng mật khẩu cũ).
- **Khác mock có chủ đích** (mock không có hoặc không thể có):
  - OTP phải được gửi trước (`/auth/otp`) mới đăng ký được; mock chỉ so với `123456`. Mã hết hạn sau 5 phút, sai quá 5 lần thì mã vô hiệu, gửi lại mã mới thì mã cũ vô hiệu. Hai thông báo **tự đặt** (mock không có): “Mã OTP đã hết hạn hoặc nhập sai quá nhiều lần, vui lòng gửi lại mã mới.” (400). Chưa gửi mã hoặc mã sai vẫn là “Mã OTP không đúng.”. Số lần sai được lưu ngay cả khi yêu cầu lỗi (`OtpService.check` chạy `REQUIRES_NEW`); mã chỉ bị đánh dấu đã dùng khi thao tác gọi thành công (`consume`), nên trùng số/email không làm cháy mã.
  - Đăng ký kiểm tra thêm `phone` đúng `^0\d{9}$`, `password` ≥ 6 ký tự (trả 400 “Dữ liệu không hợp lệ.” kèm `fields`) và email đúng dạng (sau khi trim); mock không kiểm tra vì FE đã kiểm tra trước.
  - Đổi mật khẩu thu hồi **mọi phiên khác** của tài khoản nhưng giữ phiên đang dùng (FE không bị đăng xuất, giống mock); với nhân viên và khách như nhau.
  - Đăng nhập: email khách không phân biệt hoa thường (như mock); email nhân viên so khớp đúng từng chữ (như mock, vì mock so `username ===`).
- **OTP cho các giai đoạn sau**: `purpose` = `REGISTER` (chưa đăng nhập) hoặc `CONFIRM` (khách đang đăng nhập gọi `/auth/otp` với body `{}`). B4 (`/submit`) và B6 (`/me/consents/withdraw`) kiểm tra bằng `otpService.check(sốĐiệnThoạiKhách, OtpService.PURPOSE_CONFIRM, otp)` rồi `consume(id)` trong giao dịch của thao tác. Dev: `app.otp.fixed-code=123456` (`application-dev.yml` và `application-test.yml`) → `devOtp` có trong `OtpSent`; không có mã cố định thì sinh ngẫu nhiên 6 số và `devOtp = null`. `LoggingSmsSender` chỉ ghi log “chưa cấu hình nhà cung cấp SMS” (không in mã OTP); muốn gửi SMS thật thì thêm bean `SmsSender` và bỏ `@Component` của `LoggingSmsSender`.
- **Cần quyết định/nhắc cho giai đoạn sau**:
  - **Đã chốt (23/10/2026)**: migration chính tiếp tục `V4__…`, `V5__…` theo giai đoạn (không sửa V3 đã chạy); dữ liệu lịch sử dev là `db/dev/V1000__dev_history.sql`; đã sửa mục 5.7, 5.8, B4, B8 cho khớp. Tên `V3__app_support.sql` giữ nguyên dù chỉ có phần tài khoản.
  - **Đã chốt (23/10/2026)**: `docs/openapi.yaml` mô tả bearer là chuỗi ngẫu nhiên (không phải JWT), server lưu SHA-256, hết hạn 8 giờ, thu hồi khi đăng xuất/khóa/đổi mật khẩu; đã thêm thông báo OTP hết hạn mới “Mã OTP đã hết hạn hoặc nhập sai quá nhiều lần, vui lòng gửi lại mã mới.” vào mô tả lỗi chung, `/auth/register`; redocly lint 0 lỗi. Các endpoint `/submit` (B4) và `/me/consents/withdraw` (B6) cũng có thể trả thông báo này khi kiểm tra OTP.
  - `ActivityLogService` mới có `log(actor, action, detail, applicationId)`; `activity_log` không có cột tên người thực hiện hay `customerId` như mock, nên B7 (`/admin/logs`) lấy tên bằng cách nối `actor_type` + `actor_id` với `customer`/`staff`.
  - `Customer` mới ánh xạ cột tài khoản (phone, email, password_hash, full_name, status, created_at); thêm cột hồ sơ cá nhân khi cần ở B4. Tên chi nhánh của `AuthUser.branchName` đang lấy bằng truy vấn native trong `StaffRepository`; B2 có entity `Branch` thì đổi sang entity.
  - `@RequireRole` vẫn chưa làm (B1 chưa có endpoint phân theo vai trò ngoài đăng nhập); làm cùng controller đầu tiên cần phân quyền (B3/B4). `TokenAuthFilter` đã đặt `ROLE_<CUSTOMER|BRANCH_STAFF|ADMIN>` và principal là `CurrentUser` (`@AuthenticationPrincipal CurrentUser`).
- **Kiểm tra**: `.\mvnw.cmd verify` → 40 test pass (6 `GlobalExceptionHandlerTest`, 6 `TienSangLocVayApplicationTests` – đã sửa: Flyway V1–V3, 26 bảng, `/auth/login` rỗng → 400; 28 `AuthApiTest` MockMvc + Testcontainers: đăng nhập khách bằng SĐT/email, 3 nhân viên + quản trị, sai mật khẩu/không tồn tại/sai loại tài khoản, thiếu trường, tài khoản khóa, phiên hết hạn, đăng xuất, OTP không hợp lệ/hết hạn/sai 5 lần/dùng một lần, đăng ký thành công/trùng số/trùng email/dữ liệu sai, đổi mật khẩu thu hồi phiên khác). Chạy `spring-boot:run -Dspring-boot.run.profiles=dev` trên PostgreSQL Docker (Flyway áp V3), FE chạy `VITE_USE_MOCK=false` (đặt qua biến môi trường, **chưa đổi** `fe/.env.development`, B9 mới đổi): bằng Playwright đăng nhập khách (SĐT, email) → `/cua-toi`, nhân viên 3 chi nhánh → `/noi-bo/ho-so`, quản trị → `/noi-bo/bao-cao`, sai mật khẩu hiện đúng “Sai tài khoản hoặc mật khẩu.”, đăng ký mới với OTP dev `123456` → `/cua-toi`; không có lỗi trang. Các trang sau đăng nhập vẫn gọi endpoint chưa làm (B2 trở đi) nên chưa hiển thị dữ liệu. Chưa chạy `tests/e2e/full_flow.py` (cần đủ endpoint, thuộc B9).

### B2 – xong 23/10/2026
- **Đã có**: package `catalog/` (entity `Branch`, `LoanProduct`, `CrossSellProduct`, `DocumentType`, `ProductDocumentType` với `@IdClass`; repository; `CatalogService`, `CatalogController`; DTO `LoanProductDto`, `CrossSellProductDto`, `BranchDto`, `ChecklistItem`) và `policy/` (entity `ParamVersion`, `PolicyParam`, `ScoringWeight`; `ActivePolicy`, `PolicySnapshot`). Không cần migration mới (V1/V2 đủ).
- **Endpoint** (đều công khai, chỉ trả bản ghi `ACTIVE` như mock, sắp theo `id`): `GET /products`, `/products/{code}/checklist`, `/cross-sell-products`, `/branches`. JSON khớp openapi và `handlers/catalog.ts`: tiền là số nguyên (`estCost`), `scoreBonus`/`rateDiscount` là số thập phân (`5.0`, JS đọc ra `5`). `BranchDto` không có `openApplications` (chỉ thêm ở `GET /admin/branches`, B7). Mã gói lạ ở `/checklist` trả `[]` (mock cũng vậy). Tên giấy tờ `id = 1` được nối thêm “ – hệ thống tự lập” (hằng `DocumentType.SYSTEM_DOC_TYPE`).
- **Thứ tự checklist**: bảng `product_document_type` không có cột thứ tự, mock thì giữ thứ tự chèn. Backend sắp **bắt buộc trước, rồi theo `document_type.id`**: khớp mock với dữ liệu mẫu (TC: giấy đề nghị, CCCD, sao kê, rồi hợp đồng lao động tùy chọn). Nếu B7 cho thêm quy tắc, giấy tờ mới bắt buộc sẽ nằm theo `id`, không nằm cuối như mock.
- **`ActivePolicy`**: `current()` nạp phiên bản `ACTIVE` một lần và giữ bản chụp bất biến (`PolicySnapshot`: `common(key)`, `product(maGoi, key)`, `weight(maGoi, tieuChi)` trả `BigDecimal`, khóa tham số theo mục 1, tiêu chí theo mục 6 của quy-tac-tinh-toan). Thiếu tham số → `IllegalStateException` nêu tên tham số (lỗi cấu hình, thành 500); không có phiên bản ACTIVE cũng vậy. **B7 phải gọi `ActivePolicy.evict()` sau khi giao dịch kích hoạt đã commit**; sửa tham số bằng SQL tay cũng cần khởi động lại backend. `param_version.test_result` (jsonb) chưa ánh xạ, thêm ở B7.
- **Dọn từ B1**: `AuthService` lấy tên chi nhánh qua entity `Branch`; đã xóa truy vấn native `StaffRepository.findBranchName`.
- **Đã sửa test B0**: `endpointCongKhaiKhongBiChan` trước kỳ vọng `/products`… trả 404 (chưa có controller), nay kỳ vọng 200; `/screenings/*` và `POST /screenings`, `/simulations` vẫn 404 đến B3.
- **Đã chốt (23/10/2026)**: `allowedFormats` của “Sao kê tài khoản nhận lương 03 tháng” theo mock là `PDF,JPG,PNG` (V2 ghi `PDF`). Thêm `V4__sua_dinh_dang_sao_ke.sql` cập nhật `document_type`, không sửa V2; migration của B4 là `V5__…` (sau dời thành `V6__…`, xem ghi chú “Mã công khai UUID”). Giá trị dùng khi kiểm tra tệp tải lên (B4) và ở `/admin/document-types` (B7).
- **Nhắc B7**: sau khi giao dịch kích hoạt phiên bản tham số commit, phải gọi `ActivePolicy.evict()`, nếu không backend vẫn dùng bản đã nạp.
- **Kiểm tra**: `.\mvnw.cmd verify` → 53 test pass (13 test mới `CatalogApiTest`: 4 endpoint đối chiếu từng trường với dữ liệu mẫu/mock, ẩn bản ghi `INACTIVE` của gói/bán chéo/chi nhánh, checklist TD/TC/mã lạ, `ActivePolicy` nạp đúng phiên bản 2026.10, tổng trọng số mỗi gói = 100, thiếu tham số báo rõ, `evict()` nạp lại). Chạy `spring-boot:run -Dspring-boot.run.profiles=dev` trên PostgreSQL Docker và FE `VITE_USE_MOCK=false` (biến môi trường, **chưa đổi** `fe/.env.development`): bằng Playwright trang chủ `/` gọi `GET /api/products` (không qua service worker MSW) và hiện hai thẻ gói vay, không lỗi trang. Để chắc chắn dữ liệu từ DB, tạm đổi tên gói TD trong DB thành “Vay tiêu dùng (kiểm tra B2)”, trang chủ hiện đúng tên đó, rồi đã trả lại tên cũ. Mô tả điều kiện trong thẻ (tuổi, hạn mức, kỳ hạn) do FE tự soạn, chưa đọc từ tham số.

### B3 – xong 23/10/2026
- **Đã có**:
  - `engine/` (Java thuần): `Engine` chép từng hàm của engine.ts, nhận `LocalDate today` tường minh; record đầu ra trùng tên trường với openapi và dùng thẳng làm DTO (`ScreeningResultItem`, `ScreeningNote`, `ScheduleRow`, `Suggestion`, `SimulationResponse`); `JsFormat` định dạng số như JS (`toLocaleString('vi-VN')`, `String(x)`).
  - `policy/EngineParams` đọc `PolicySnapshot` thành `ParamSet`/`WeightSet` với giá trị mặc định như `toParamSet()`. `ActivePolicy` thêm `version(id)` (cache theo id, `evict()` xóa cả hai) và giữ thứ tự tham số/trọng số theo id.
  - `screening/` (`ScreeningController`, `ScreeningService`, `SimulationService`, entity `Screening`, `OtherDebt`, `ScreeningResult`, `Consent`) và `plan/` (`PlanController`, `PlanService`, entity `LoanPlan`, `PlanCrossSell`).
  - **Không có migration mới** (V1 đủ cột); B4 vẫn là `V5__…` (sau dời thành `V6__…`, xem ghi chú “Mã công khai UUID”).
- **Fixtures**: `npx -y tsx@4.19.2 be/tools/gen-engine-fixtures.ts` (ở thư mục gốc) gọi thẳng `fe/src/mocks/engine.ts`, sinh 32 file `be/src/test/resources/engine-fixtures/NN-ten.json`. Mỗi file có tham số, trọng số, bán chéo, input, kết quả `screen()` và 4–11 lần `simulate()`; có đủ 4 `creditHistory`, tuổi biên, nghỉ hưu, 29/02, mọi lý do loại trừ, cả 4 loại đề xuất và 3 bộ tham số/trọng số khác.
  - Script cố định `Date` ở 23/10/2026 10:00 và đặt `TZ=Asia/Ho_Chi_Minh` (engine.ts đọc `new Date('yyyy-mm-dd')` theo UTC rồi lấy ngày theo giờ máy, nên phải sinh ở múi giờ ≥ UTC; script tự kiểm tra). Script cũng kiểm tra Node có ICU tiếng Việt.
  - `EngineFixtureTest` so cây JSON: chuỗi, mảng và tiền phải bằng tuyệt đối; `dti`, `currentDti`, `dtiAfter`, `dtiMax`, `score`, `rate`, `refRate` lệch ≤ 0,05. Hiện khớp tuyệt đối. Đã thử cố ý làm sai một câu chữ và cách làm tròn: test báo 24/32 ca sai.
  - **Mỗi lần sửa engine.ts: sửa `Engine.java`, chạy lại script, commit fixtures.**
  - Chỗ JS khó chép: `new Date(y, m, d)` cộng tràn ngày (sinh 29/02, nghỉ hưu năm không nhuận → 01/03, ca `13-sinh-ngay-29-02` có `maxTermAllowed` 41 thay vì 40); các phép chia giữ dạng `double` và đúng thứ tự như TS.
- **`GET /screenings/{id}` tính lại, không lưu JSON**: chạy `screen()` với input đã lưu, tham số của `screening.version_id` và ngày `created_at` (giờ VN); kết quả giống hệt lúc POST (có test so nguyên JSON). Đúng vì phiên bản ACTIVE/EXPIRED không sửa được (mock chỉ sửa DRAFT); B7 phải giữ quy tắc này. `screening_result`, `screening.dti`, `disposable_income` vẫn được ghi cho báo cáo B8: `reason` nối các lý do bằng dấu cách, cắt 255 ký tự; `dti` lớn hơn 999.99 (cột `decimal(5,2)`) ghi 999.99, API vẫn trả số thật.
- **`/simulations` và `POST /plans` dùng phiên bản đang áp dụng** (như mock), còn `GET /screenings/{id}` dùng phiên bản lúc sàng lọc.
- **Quyền**: `POST /screenings/*/save`, `POST /plans`, `GET /plans/*`, `POST /plans/*/confirm` mở ở SecurityConfig để service trả đúng chữ của mock: 401 “Cần đăng nhập tài khoản khách hàng.”, 401 “Cần đăng nhập tài khoản khách hàng để lưu phương án.”, 404 “Không tìm thấy phương án.” (cả khi không có token). `/me/screenings`, `/me/plans` cần đăng nhập (401 “Chưa đăng nhập.”); tài khoản nội bộ nhận `[]`. `@RequireRole` vẫn chưa cần (B5 làm cùng các endpoint nhân viên).
- **Thông báo lỗi chép từ mock**: “Cần đồng ý xử lý dữ liệu trước khi sàng lọc.”, “Thông tin thu nhập hoặc số tiền vay không hợp lệ.”, “Không tìm thấy lượt sàng lọc.”, “Hồ sơ không đạt điều kiện của gói vay này.” (422), “Phương án chưa đạt: ” + các vấn đề nối bằng dấu cách (422). Nhật ký: “Lưu kết quả sàng lọc” / “Lượt #id”, “Lưu phương án vay” / “Phương án #id”, “Xác nhận phương án vay” / “Phương án #id”.
- **Khác mock có chủ đích** (đã duyệt trong kế hoạch):
  - Lượt sàng lọc đã thuộc khách khác: `/save` và `POST /plans` trả 404 “Không tìm thấy lượt sàng lọc.” (mock cho gắn lại). `loan_plan` không có `customer_id`, chủ phương án là `screening.customer_id` nên phải cố định.
  - `POST /plans` kiểm tra điều kiện loại trừ trước như `/simulations` (mock không kiểm tra; thu nhập 0 sẽ chia cho 0).
  - `POST /plans` gắn cả đồng ý phần A vào khách hàng (mock chỉ gắn lượt sàng lọc); cần cho B6 (rút đồng ý theo khách).
  - Thứ tự kiểm tra: đồng ý A → thu nhập/số tiền (đúng chữ mock) → các trường còn lại trả 400 “Dữ liệu không hợp lệ.” kèm `fields` (thiếu trường, vượt độ dài cột, `desiredTerm` ≥ 1, `dependents` ≥ 0, tiền ≤ 15 chữ số). `POST /plans` kiểm tra đăng nhập trước dữ liệu như mock.
- **Rủi ro còn để nguyên**: `GET /screenings/{id}` công khai theo id tuần tự (đúng openapi và mock), ai đoán được id thì xem được thông tin khai báo không định danh (tuổi, thu nhập, nợ). Nếu muốn chặn thì cần đổi openapi (ví dụ id ngẫu nhiên hoặc chỉ chủ sở hữu xem lượt đã lưu). **Đã xử lý** bằng mã công khai UUID (xem ghi chú “Mã công khai UUID”).
- **Kiểm tra**: `.\mvnw.cmd verify` → 111 test pass (mới: 33 `EngineFixtureTest`, 17 `ScreeningApiTest`, 7 `PlanApiTest`; sửa `TienSangLocVayApplicationTests` vì `/screenings`, `/simulations` nay có controller).
  - Chạy backend dev + FE `VITE_USE_MOCK=false` (biến môi trường, **chưa đổi** `fe/.env.development`), dùng Playwright theo bước QT01–QT02 của `tests/e2e/full_flow.py`: đăng nhập → `/sang-loc` → kết quả → mô phỏng (“Phương án nằm trong ngưỡng an toàn”) → “Chọn phương án này” → “Xác nhận phương án” (trang `/phuong-an/1` hiện “Đã xác nhận”, danh mục giấy tờ). Lượt ẩn danh → “Lưu kết quả” → đăng nhập → tự lưu (“Đã lưu vào tài khoản”). Mọi lời gọi `/api/screenings*`, `/simulations`, `/plans*` thành công; không có lỗi trang. Lỗi console chỉ là 404 của `/notifications`, `/me/applications` (B4).
  - Đối chiếu DB: `screening` + `consent` A gắn khách, `screening_result` khớp fixture cơ bản (TD 300.000.000 đ, điểm 86.5, hạng 1), `loan_plan` CONFIRMED, `activity_log` đủ 3 hành động.
- **Lưu ý DB dev**: mật khẩu tài khoản thử `0901234567` trong DB Docker đã bị đổi lúc 08:39 ngày 23/10/2026 (nhật ký “Đổi mật khẩu”, từ lần thử trước), nên đăng nhập bằng `123456` không được. Tôi không đặt lại; luồng trên dùng khách mới `0977091445` / `123456` đăng ký qua OTP dev. Muốn trả về như seed thì `docker compose down -v` rồi chạy lại backend (Flyway tạo lại dữ liệu mẫu), hoặc đổi mật khẩu lại qua giao diện.

### Mã công khai UUID – xong 23/10/2026
- **Phạm vi**: lượt sàng lọc, phương án vay, hồ sơ vay dùng UUID (`public_id`) ở mọi chỗ API/FE: `id` trong response, tham số đường dẫn, `screeningId` / `planId` / `applicationId`, by-qr (`PublicIdResult`), đường dẫn trong thông báo. Id số không trả ra ngoài. Mã hồ sơ hiển thị (HS2610-0509) giữ nguyên. Các bảng khác (danh mục, chi nhánh, nhân viên, tham số, thông báo, nhật ký, giấy tờ trong hồ sơ `docId`, khách hàng `customerId`/`savedToCustomerId`) giữ id số.
- **Hợp đồng**: `docs/openapi.yaml` thêm `parameters/PublicId` (string uuid) dùng cho 16 thao tác `/screenings/{id}…`, `/plans/{id}…`, `/applications/{id}…`, `/staff/applications/{id}/…` (`/notifications/{id}/read` và admin giữ `Id`); thêm `PublicIdResult`; đổi kiểu các trường trên; ghi quy ước ở `info.description`. `GET /screenings/{id}` đổi `security` thành `[{}, bearer]` để nhận ra chủ. Redocly: 0 lỗi, 19 cảnh báo (bằng số cảnh báo trước khi sửa).
- **DB**: `V5__public_id.sql` thêm `public_id uuid NOT NULL DEFAULT gen_random_uuid()` + `UNIQUE` cho `screening`, `loan_plan`, `loan_application` (dòng cũ tự có mã). Khóa chính bigint giữ nguyên cho khóa ngoại. **Migration của B4 dời thành `V6__…`** (đã sửa mục 3, 5.3, 5.7, B4).
- **Backend**:
  - Entity `Screening`, `LoanPlan` có `publicId` (sinh `UUID.randomUUID()` trong constructor, `updatable = false`), repository `findByPublicId`. B4 làm entity `LoanApplication` thì theo cùng mẫu.
  - Controller nhận `{id}` là `String`; `common/PublicIds.parse(raw, thôngBáo404)` chỉ nhận UUID dạng chuẩn 36 ký tự. Id số, chuỗi lạ, UUID rút gọn hay không gạch nối đều thành 404 với thông báo của tài nguyên (không phải 400).
  - `screeningId` không phải UUID trong JSON là lỗi kiểu dữ liệu → 400 “Dữ liệu không hợp lệ.”. `POST /plans` và `POST /screenings/*/save` được `security/CustomerOnlyFilter` chặn trước khi đọc body (chưa đăng nhập hoặc không phải khách hàng → 401 đúng chữ của mock, kể cả body sai định dạng), nên thứ tự lỗi khớp mock. Các đường dẫn còn lại (`GET /plans/*`, `POST /plans/*/confirm`) không đọc body nên vẫn do `PlanService` trả 404 “Không tìm thấy phương án.”. `GET /screenings/{id}` 404 mà chưa đăng nhập: trang kết quả sàng lọc ở FE hiện thêm “Nếu bạn đã lưu kết quả này vào tài khoản, hãy đăng nhập để xem.” kèm nút Đăng nhập (`?next=` về đúng trang).
  - `PublicIds.shortCode` (8 ký tự đầu) dùng cho nhật ký.
- **Quyền xem lượt sàng lọc** (đã chốt): chưa thuộc ai → ai có UUID xem/mô phỏng được; đã lưu vào tài khoản → chỉ chủ tài khoản.
  - `GET /screenings/{id}`: không tồn tại, sai định dạng, hoặc đã lưu mà người xem không phải chủ (kể cả ẩn danh, nhân viên, quản trị) đều 404 **“Không tìm thấy kết quả sàng lọc.”** (một câu để không lộ lượt có tồn tại hay không).
  - `/save`, `/simulations` (mới áp dụng quy tắc này), `POST /plans`: 404 “Không tìm thấy lượt sàng lọc.”.
  - Phương án giữ kiểm tra chủ như cũ (404 “Không tìm thấy phương án.”).
  - Hệ quả: tạo lượt ẩn danh rồi lưu vào tài khoản thì chính trình duyệt ẩn danh đó không xem lại được nữa nếu không đăng nhập (FE đã tự đưa về trang đăng nhập khi bấm “Lưu kết quả”).
- **Nhật ký**: “Lượt #<8 ký tự đầu>”, “Phương án #<8 ký tự đầu>” (mock và backend). FE hiện cùng mã ngắn (`utils/format.ts` `shortId`) ở tiêu đề kết quả sàng lọc, phương án và bảng ở trang “Của tôi”.
- **Frontend**:
  - `types.ts`, `endpoints.ts` đổi kiểu sang `string`. Các trang bỏ `Number(id)`: `ScreeningResult`, `Simulation`, `PlanDetail`, `ApplicationDetail`, `CreateApplication` (`?phuongAn=`), `ScreeningWizard` (`?tu=`), `StaffApplicationDetail`.
  - Mock sinh mã bằng `crypto.randomUUID()` cho 3 loại (cả dữ liệu lịch sử), `DB_VERSION` 7 → 8. `tests/e2e/full_flow.py` không cần sửa (chỉ dùng URL dạng chuỗi).
  - Mock `screening.ts` thêm luôn 3 điểm backend đã làm khác ở B3 để mock và backend lại khớp: lượt đã lưu của khách khác → 404, `POST /plans` kiểm tra điều kiện loại trừ trước (422), lưu phương án gắn đồng ý phần A vào khách. `/me/screenings`, `/me/plans` của tài khoản nội bộ trả `[]` như backend.
- **Kiểm tra**:
  - `.\mvnw.cmd verify` → 115 test pass (thêm: id số / UUID ngẫu nhiên / chuỗi lạ / UUID rút gọn → 404 ở `/screenings`, `/save`, `/plans`, `/confirm`; lượt đã lưu chỉ chủ xem và mô phỏng được; luồng ẩn danh → lưu → người khác 404; response chỉ có UUID; `applicationId` là `public_id` của hồ sơ chưa hủy; Flyway V1–V5, 3 bảng có `public_id`).
  - `npm run build`, `npx eslint src` (0 lỗi, 4 cảnh báo fast-refresh có sẵn), redocly lint 0 lỗi.
  - `python tests\e2e\full_flow.py` trên mock → `ERRORS []` (URL `/ho-so/<uuid>`).
  - Backend thật (dev, Flyway áp V5) + FE `VITE_USE_MOCK=false`: sàng lọc → mô phỏng → lưu/xác nhận phương án (`/phuong-an/<uuid>`) và ẩn danh → “Lưu kết quả” → đăng nhập → tự lưu đều chạy; nhật ký ghi “Phương án #206d167f”, “Lượt #171ebf53”. Lỗi console chỉ là 404 của `/notifications`, `/me/applications` (B4).
  - DB Docker dev lúc chạy được tạo lại từ đầu (Flyway áp cả V1–V5), nên tài khoản `0901234567` / `123456` dùng lại được; khách thử `0977091445` ghi ở B3 không còn.

### B4 phần A – xong 23/10/2026 (chưa nối Cloudinary/Gemini; còn B4b)
- **Đã có**:
  - `V6__ho_so_vay.sql`: `loan_application` thêm thông tin cá nhân theo hồ sơ (`full_name, national_id, dob, gender, address, email`; `phone` lấy từ `customer`), `cancel_reason`, `warnings`/`recalc` (jsonb, kết quả đối chiếu lần gần nhất như mock lưu `app.warnings/recalc`); `application_document` bỏ NOT NULL các cột tệp (dòng MISSING tạo sẵn), thêm `status`, `required`, `quality_message`, `storage_resource_type`, `storage_version`, `verify_result`, `verify_reason`, CHECK `quality_status` (gồm `WRONG_TYPE` cho B4b); `extracted_field.label`; bảng `application_status_history`, `notification`. `scheduled_job` để B7.
  - `application/`: entity + repository, `Dtos` (record trùng schema), `ApplicationStateMachine` (bảng chuyển mục 12 + EXPIRED→NEED_MORE/CANCELLED; chuyển sai là `IllegalStateException`), `CrossCheckService` (chép `crossCheck`/`refreshChecks`/`missingForSubmit`), `RequestFormBuilder` (chép `buildForm`), `ApplicationSupport` (quyền `own`/`canSee`, nạp `AppBundle`, dựng DTO + `can`), `ApplicationService`, `DocumentUploadService`, `ApplicationController`.
  - `document/`: `StorageService` + `StoredFile`, `LocalStorageService` (`app.storage.provider=local`), `LocalFileController`, `QualityChecker`, `OcrService` + `OcrResult` + `OcrContext`, `FakeOcrService` (`app.ocr.provider=fake`).
  - `notification/`: `NotificationService.toCustomer/toBranch`, `/notifications` (khách: của mình; nhân viên: của chi nhánh, `customer_id` rỗng; quản trị `[]`; 50 mới nhất), `/read` (của người khác bỏ qua), `/read-all`.
  - `application-dev.yml` đặt `storage.provider: local`, `ocr.provider: fake` (tệp ở `be/data/uploads`, đã có trong `.gitignore`). **B4b**: bỏ hai khối này khi có `CLOUDINARY_URL`, `GEMINI_API_KEY`. Chạy không có profile dev/test thì thiếu bean `StorageService`/`OcrService` (chưa có Cloudinary/Gemini) nên không khởi động được – B4b thêm hai bean đó.
- **Thiết kế cho B4b**:
  - `StorageService` đúng chữ ký 5.3. `OcrService.extract(type, data, mimeType, ctx)` có thêm `OcrContext` (thông tin đã khai) – chỉ `FakeOcrService` dùng để sinh giá trị như mock; `GeminiOcrService` bỏ qua. `OcrResult(readable, documentMatchesType, issue, fields[key,label,value,confidence])`; ánh xạ BLURRY / WRONG_TYPE (“Tệp không phải <tên giấy tờ>.”) đã viết sẵn trong `DocumentUploadService`. Khi bật Gemini cần thêm `WRONG_TYPE` vào enum `qualityStatus` của openapi + FE.
  - Upload chạy 3 bước: giao dịch chỉ đọc kiểm tra quyền/khóa/giấy tờ/tệp → **ngoài giao dịch** Quality → Storage → OCR (không giữ kết nối DB khi gọi dịch vụ ngoài) → giao dịch ghi (kiểm tra khóa lại). Lỗi DB thì xóa tệp vừa lưu; thành công thì xóa tệp phiên bản cũ sau commit. Tệp không đạt chất lượng **không** lưu kho (theo 5.3) nên không có `previewUrl` (mock vẫn hiện ảnh thu nhỏ của tệp bị từ chối). OCR lỗi → `UPLOADED`, `qualityMessage` “Chưa đọc được thông tin tự động, vui lòng nhập tay”, `fields` rỗng (theo 5.3).
  - **Khóa trường HĐLĐ**: **đã chốt** `salary` theo mock (B4b sửa quy-tac mục 9; cùng lúc sửa cư trú `residence_address` → `address`).
- **`previewUrl` cục bộ**: `GET /api/files/local?path&exp&sig` (đã thêm vào openapi, `security: []`, chỉ có khi `provider=local`). Thẻ `<img>` của FE không gửi được Bearer nên dùng URL ký HMAC-SHA256 (`app.storage.local-secret`, để trống thì sinh ngẫu nhiên mỗi lần khởi động) hạn `app.storage.signed-url-minutes`; quyền kiểm tra lúc cấp URL (chỉ chủ hồ sơ, nhân viên chi nhánh tiếp nhận, quản trị mới nhận). Chữ ký sai/hết hạn/đường dẫn ra ngoài thư mục/tệp không còn → 404 “Không tìm thấy.”. Chỉ ảnh có `previewUrl`, PDF `null` như mock.
- **Khác mock có chủ đích**:
  - Nhật ký “Tạo hồ sơ vay” ghi tên chi nhánh (mock ghi “Chi nhánh ” + tên, thành “Chi nhánh Chi nhánh Hà Nội”); **đã sửa mock** cho khớp.
  - Đối chiếu tuổi đọc `MIN_AGE` của gói ở phiên bản đang áp dụng (mock viết cứng 22/19, cùng giá trị dữ liệu mẫu).
  - `/submit` kiểm tra OTP qua `OtpService` (FE gọi `/auth/otp` trước trong `OtpModal`); mã chỉ bị dùng khi nộp thành công.
  - `PUT /personal` kiểm tra thêm độ dài (họ tên, email ≤ 100, địa chỉ ≤ 255), `dob` yyyy-MM-dd, `gender` MALE/FEMALE → 400 “Dữ liệu không hợp lệ.” kèm `fields`; trường không gửi giữ nguyên (như mock gộp đối tượng). Xác nhận CCCD chỉ điền sẵn số CCCD khi đủ 12 chữ số (cột 12 ký tự; số sai vẫn bị cảnh báo BLOCKING).
  - `PUT /plan` sửa thẳng dòng `loan_plan` + `plan_cross_sell` của hồ sơ (một phương án chỉ có một hồ sơ chưa hủy); trang phương án cũng thấy số mới.
  - `issuedDate` sai định dạng → 400 (mock lưu nguyên chuỗi).
  - `typeName`, `group`, `allowedFormats` đọc từ `document_type` lúc trả về (mock chép lúc tạo hồ sơ); `required` chụp lúc tạo như mock.
- **Để B5**: `verifications: []`, `approval: null`; B5 ghi `application_document.verify_result/verify_reason` (bản sao kết quả đối chiếu gần nhất, upload tự xóa) để nộp lại chặn “Cần tải lại: …”. Đổi trạng thái chỉ qua `ApplicationStateMachine.transition`.
- **Kiểm tra**:
  - `.\mvnw.cmd verify` → 131 test pass. Mới: 11 `ApplicationApiTest` (lỗi tạo hồ sơ đúng thứ tự mock, danh mục 2,3,4,5,6,1, lịch sử, consent B, quyền xem chủ/nhân viên cùng-khác chi nhánh/quản trị, personal, upload WRONG_FORMAT/TOO_LARGE/BLURRY/OK + giá trị OCR giả lập + previewUrl tải được + chữ ký sai/hết hạn/`..` 404 + xóa tệp cũ, cảnh báo CCCD BLOCKING chặn giấy đề nghị, thu nhập sao kê lệch 5% + recalc, `PUT /plan` 422/400/200, giấy đề nghị 19 ô, OTP sai/chưa gửi, nộp: QR `QR[A-Z0-9]{8}`, băm 64 hex, hạn +30 ngày, thông báo khách + chi nhánh, nộp lại NEED_MORE giữ QR, EXPIRED → NEED_MORE → WAITING_VERIFY và chặn giấy tờ quá 30 ngày, hủy + 409), 1 `NotificationApiTest`, 2 `DocumentUnitTest`, 2 `ApplicationUnitTest`; `TienSangLocVayApplicationTests` sửa Flyway V1–V6, 28 bảng. Redocly lint 0 lỗi (19 cảnh báo như trước).
  - Backend dev + FE `VITE_USE_MOCK=false` (biến môi trường, **chưa đổi** `fe/.env.development`), Playwright theo bước 53–105 của `tests/e2e/full_flow.py`: đăng nhập 0901234567 → sàng lọc → phương án → tạo hồ sơ CN Hà Nội → thông tin cá nhân → `mo.jpg` hiện “Ảnh có độ phân giải thấp hoặc bị mờ…” → cccd, cutru, hdld, saoke.pdf, hoadon (4 ảnh xem trước tải được qua `/api/files/local`) → xác nhận → lập giấy đề nghị → OTP 123456 → “Chờ đối chiếu tại Chi nhánh Hà Nội” → tab Phiếu tóm tắt có mã QR, mã băm, hạn 22/11/2026. Không lỗi API/console. DB: lịch sử 2 dòng, 2 thông báo, nhật ký đủ hành động, tệp trong `be/data/uploads`.
  - DB Docker dev còn hồ sơ HS2610-0501 (DRAFTING) của khách `0901234567` từ lần chạy thử đầu bị dừng giữa chừng (lỗi in tiếng Việt của script thử, không phải lỗi backend); hủy trên giao diện hoặc `docker compose down -v` nếu muốn sạch.

### B4 phần B – xong 23/10/2026 (Cloudinary + Gemini)
- **Đã chốt với người dùng**:
  - Khóa trường HĐLĐ là `salary` (theo mock). Cùng lúc sửa giấy xác nhận cư trú `residence_address` → `address` (mock và `FakeOcrService` đều dùng `address`, cùng nguyên tắc “mock là đặc tả”). Đã sửa `quy-tac-tinh-toan.md` mục 9 và 5.4.
  - `previewUrl` với Cloudinary đi qua backend (`/api/files/cloud`, xem 5.3) vì gói miễn phí không có URL giao tệp tự hết hạn.
- **Thư viện**: không thêm thư viện mới. Dùng `com.cloudinary:cloudinary-http5:2.5.0` và `com.google.genai:google-genai:1.78.0` (đã có từ B0); proxy tải ảnh dùng `java.net.http.HttpClient` của JDK. **Model**: `gemini-3.5-flash-lite` (`app.ocr.model`, đổi sang `gemini-3.8-flash` nếu đọc sai nhiều). **Không cần V7**: V6 đã có `storage_resource_type`, `storage_version` và CHECK `quality_status` gồm `WRONG_TYPE`.
- **Đã có** (`document/`):
  - `DocumentFields`: khóa, nhãn, kiểu giá trị theo loại giấy tờ (2–6). Dùng chung cho `FakeOcrService` (nhãn), schema/prompt Gemini và dòng trống khi máy đọc lỗi.
  - `UrlSigner` (tách từ `LocalStorageService`): HMAC-SHA256 + hạn, khóa `app.storage.url-secret` (tên cũ `local-secret` vẫn đọc được).
  - `CloudinaryConfig`, `CloudinaryStorageService` (upload `authenticated` + `resource_type auto`, thư mục `app.storage.folder/app-<id>`, public_id `doc-<docId>-v<n>`, `destroy` với `invalidate`), `CloudFileController` (`GET /files/cloud`: chữ ký sai/hết hạn/tệp không còn → 404 “Không tìm thấy.”, Cloudinary lỗi → 502 “Không tải được tệp từ kho lưu trữ, vui lòng thử lại sau.”). Ảnh xem trước `c_limit,f_auto,q_auto,w_480`; PDF thêm `pg_1`, đuôi `.jpg`. Với Cloudinary, PDF cũng có `previewUrl` (resource_type `image`); kho cục bộ vẫn chỉ ảnh.
  - `GeminiConfig` (timeout `app.ocr.timeout-seconds`, tắt retry của SDK), `GeminiOcrService`: một lần `generateContent` (tệp + prompt tiếng Việt), `responseMimeType application/json`, `responseSchema` theo loại (`key` là enum các khóa của loại), `temperature 0`. Thử lại 429/503 sau 1 s rồi 3 s; lỗi khác ném ra ngay. Log loại giấy tờ, model, thời gian, số token, mã lỗi; không log tệp hay giá trị.
  - Chuẩn hóa kết quả: đủ trường theo thứ tự danh mục (thiếu → rỗng, độ tin cậy 0; khóa lạ bỏ); tiền → `18.800.000`; ngày → dd/MM/yyyy; giới tính Nam/Nữ; tên in hoa; số CCCD bỏ khoảng trắng; độ tin cậy kẹp 0–100 (thang 0–1 nhân 100). JSON hỏng hoặc thiếu `isReadable`/`documentMatchesType`/`fields` → lỗi OCR.
- **Máy đọc lỗi** → `UPLOADED` / `OK`, “Chưa đọc được thông tin tự động, vui lòng nhập tay”, `fields` là **các dòng trống** của loại giấy tờ (không phải mảng rỗng) vì FE chỉ hiện ô nhập và nút xác nhận khi có trường. FE `DocumentCard` hiện lời nhắc này (Alert vàng + thông báo) và ẩn thẻ % khi máy đọc không có giá trị.
- **Hợp đồng**: `qualityStatus` thêm `WRONG_TYPE` (openapi, `types.ts`; mock không sinh giá trị này), mô tả upload thêm nhánh máy đọc, `previewUrl` mô tả mới, thêm `GET /files/cloud`. Redocly 0 lỗi (19 cảnh báo như trước).
- **Profile**: `dev` = Cloudinary + Gemini, nạp `be/.env` bằng `spring.config.import: optional:file:.env[.properties]` (đường dẫn tính từ thư mục chạy `be/`; biến môi trường thật được ưu tiên hơn). Thiếu khóa → không khởi động, thông báo chỉ tên biến. `local` (`-Dspring-boot.run.profiles=dev,local`) = kho `be/data/uploads` + OCR giả, chạy không cần mạng. `test` giữ local/fake, thư mục `tien-sang-loc-vay/test`. Không profile thì thư mục `tien-sang-loc-vay/prod`.
- **Kiểm tra**:
  - `.\mvnw.cmd verify` → 144 test, 0 lỗi, 2 bỏ qua (`GeminiOcrLiveTest`, `CloudinaryLiveTest` chỉ chạy khi shell có `GEMINI_API_KEY` / `CLOUDINARY_URL`; test không đọc `be/.env`). Mới: 8 `GeminiOcrServiceTest` (schema/prompt, chuẩn hóa, JSON hỏng, thử lại 429→503→OK, bỏ cuộc sau 2 lần, 400 không thử lại, loại không có trường không gọi API, dòng trống), 2 `CloudinaryStorageServiceTest` (URL Cloudinary ký + biến đổi, URL proxy ký/sửa tham số/hết hạn), `ApplicationApiTest.upload_ocrResultMapping` (máy đọc lỗi → dòng trống → nhập tay → CONFIRMED; WRONG_TYPE; BLURRY kèm lời nhắc của máy đọc).
  - Chạy thật: `$env:GEMINI_API_KEY=…; $env:CLOUDINARY_URL=…; .\mvnw.cmd test "-Dtest=GeminiOcrLiveTest,CloudinaryLiveTest"`. Chưa chạy trong phiên này (khóa chỉ có trong `be/.env`, không đọc file đó).
  - `npm run build`, `npx eslint src` (0 lỗi, 4 cảnh báo có sẵn).
  - `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"` khởi động được (khóa nạp từ `be/.env`), health UP. Người dùng tự thử tải 5 tệp mẫu trên FE với `VITE_USE_MOCK=false`.
- **Còn tồn đọng**: ~~nút “Mở tệp” tải PDF gốc~~ (xong ở B5, `AppDocument.fileUrl`); `PURGE_RETENTION` (B7) và rút đồng ý phạm vi B (B6) phải gọi `StorageService.delete`.

### B5 – xong 23/10/2026
- **Đã có**:
  - `security/RequireRole` + `RequireRoleInterceptor` (HandlerInterceptor, đăng ký ở `WebConfig`): đặt trên controller hoặc phương thức; kiểm tra **trước khi đọc body**, nên vai trò sai luôn 403 “Không có quyền.” kể cả body hỏng (thứ tự như mock). Chưa đăng nhập vẫn 401 “Chưa đăng nhập.” của SecurityConfig. Các giai đoạn sau (B6 khách, B7 quản trị) dùng lại `@RequireRole(Role.X)`.
  - `staff/` (`StaffController` gắn `@RequireRole(Role.BRANCH_STAFF)`, `StaffService`, `StaffDtos`); entity `Verification`, `VerificationDetail`, `ApprovalResult` + repository đặt trong `application/` vì `ApplicationSupport.view` dùng (điền `verifications`, `approval` – B4 còn để `[]`/`null`).
  - `V7__b5_chi_nhanh.sql`: chỉ thêm chỉ mục (`verification.application_id`, `approval_result.application_id`, `loan_application(branch_id, status)`, `loan_application.qr_token`); bảng/cột đã có từ V1, V6.
- **Hành vi** (chép `handlers/staff.ts`, thông báo lỗi đúng từng chữ, có test):
  - `GET /staff/applications`: JPQL nối `customer` (số điện thoại), chỉ chi nhánh của nhân viên, trừ DRAFTING; `status` nhiều giá trị phân cách dấu phẩy (giá trị lạ bị bỏ, toàn giá trị lạ → `[]`); `keyword` trim + chữ thường, chứa trong mã/họ tên/số điện thoại (thoát `% _ \`), mã QR khớp đúng không phân biệt hoa thường; sắp `updatedAt` giảm dần.
  - `by-qr`: 404 “Không tìm thấy hồ sơ theo mã QR.”, khác chi nhánh 403 “Hồ sơ thuộc chi nhánh khác.”. `summary`: đếm theo trạng thái bằng `group by`.
  - `/verification`, `/decision`, `/disbursement`: hồ sơ khác chi nhánh hoặc mã sai → 404 “Không tìm thấy hồ sơ của chi nhánh.”; khóa dòng hồ sơ `PESSIMISTIC_WRITE` đến hết giao dịch (hai nhân viên không ghi cùng lúc). Ghi chú lịch sử, thông báo khách và nhật ký (“Đối chiếu giấy tờ”, “Cập nhật kết quả xét duyệt” + nhãn trạng thái, “Cập nhật giải ngân”) giống mock; tiền định dạng `JsFormat.vnd`, lãi suất như `String(x)` của JS.
  - Đối chiếu ghi `verification` + `verification_detail` và chép `verify_result/verify_reason` vào giấy tờ, nên nộp lại bị chặn “Cần tải lại: …” đến khi khách tải lại (upload tự xóa kết quả cũ, đã có từ B4).
  - Mỗi lần ghi kết quả thêm một dòng `approval_result`; `Application.approval` là dòng mới nhất (tương đương mock ghi đè `app.approval`). Giải ngân cập nhật dòng mới nhất.
- **Khác mock có chủ đích**:
  - Chưa đăng nhập → 401 “Chưa đăng nhập.” (mock trả 403 “Không có quyền.” vì `!u`), cho thống nhất với mọi endpoint khác.
  - Kiểm tra chặt hơn → 400 “Dữ liệu không hợp lệ.” kèm `fields` (chạy sau các kiểm tra có chữ của mock): đối chiếu – `documentId` không thuộc danh sách giấy tờ đã tải/trùng, `result` ngoài MATCH/MISMATCH/MISSING, lý do/ghi chú > 255; xét duyệt – `resultType` sai/thiếu, lãi suất > 999,99 hoặc quá 2 chữ số thập phân, số tiền > 15 chữ số, kỳ hạn > 1200; giải ngân – ngày sai dạng yyyy-MM-dd, bên thụ hưởng > 150. Số tiền/kỳ hạn/lãi suất âm coi như thiếu (mock coi số âm là có).
  - Lý do/bên thụ hưởng chỉ có khoảng trắng coi như thiếu. Ghi chú lịch sử > 255 và nội dung thông báo > 500 ký tự bị cắt (kèm “…”).
  - Như B3/B4: body sai định dạng JSON trả 400 trước 404/409 (Spring đọc body trước khi vào service); vai trò vẫn được kiểm tra trước body.
- **`AppDocument.fileUrl`** (đã thêm vào openapi, `types.ts`, mock): đường dẫn ký HMAC hạn `app.storage.signed-url-minutes` (10 phút) trả **tệp gốc cỡ đầy đủ** (ảnh gốc hoặc PDF), chỉ có với tệp đã lưu kho (đạt chất lượng). `StorageService.fileUrl`: kho cục bộ dùng chung `/files/local` (vốn phát tệp gốc); Cloudinary là `/files/cloud?…&kind=original`, `kind` nằm trong chữ ký (chữ ký ảnh xem trước không mở được tệp gốc và ngược lại), `CloudFileController` tải tệp gốc bằng URL ký của Cloudinary không biến đổi, `Content-Disposition: inline; filename="doc-<id>-v<n>.<đuôi>"`. PDF trên Cloudinary vẫn cần bật *Allow delivery of PDF and ZIP files* (5.3). Chưa thử `kind=original` với Cloudinary thật (chạy thử dùng kho cục bộ); `CloudinaryStorageServiceTest` kiểm tra URL và chữ ký.
  - Mock: blob URL của tệp vừa tải giữ trong bộ nhớ (`rememberFile` trong `ctx.ts`, không lưu localStorage vì quá lớn), `view()` điền `fileUrl` theo mã giấy tờ + phiên bản; tải lại trang hoặc tệp không đạt → `null`. **Không tăng `DB_VERSION`** (khác kế hoạch): `view()` luôn điền `fileUrl` nên dữ liệu mock cũ vẫn dùng được, không cần xóa.
  - FE: nút “Mở tệp” (tab mới) ở `DocumentCard` của khách và cột “Tệp” ở bảng giấy tờ của nhân viên.
- **FE – In giấy đề nghị ở tab “Phiếu tóm tắt”**: `RequestFormPrintButton` thêm `sheet` (mặc định `true`); trang chi tiết hồ sơ của khách vẽ `<RequestFormPrint>` một lần ở cấp trang và hai nút (tab “Hồ sơ và giấy tờ”, tab “Phiếu tóm tắt”) dùng `sheet={false}`, nên bản in không bị lặp khi cả hai tab đã mở.
- **Kiểm tra**:
  - `.\mvnw.cmd verify` → 150 test, 0 lỗi, 2 bỏ qua (live test Cloudinary/Gemini). Mới: 5 `StaffApiTest` (khách/quản trị 403 cả khi body hỏng, chưa đăng nhập 401; danh sách: chỉ chi nhánh, không DRAFTING, nhiều trạng thái, trạng thái lạ, từ khóa mã/tên/SĐT/QR, `%`, thứ tự; by-qr chữ thường/404/HCM 403; summary; luồng đầy đủ không khớp → NEED_MORE → chặn nộp lại → tải lại → nộp lại → khớp → các lỗi xét duyệt → duyệt → các lỗi giải ngân → giải ngân, kiểm thông báo, nhật ký, 7 dòng lịch sử; MORE_INFO rồi REJECTED; `fileUrl` ảnh + PDF đúng Content-Type và nội dung), 1 test mới trong `CloudinaryStorageServiceTest`; `TienSangLocVayApplicationTests` sửa Flyway V1–V7.
  - `npm run build`, `npx eslint src` (0 lỗi, 4 cảnh báo có sẵn); redocly lint 0 lỗi (19 cảnh báo như trước).
  - Backend `dev,local` + FE `VITE_USE_MOCK=false`, Playwright: khách 0901234567 lập hồ sơ HS2610-0505 tại CN Hà Nội, “Mở tệp” sao kê → 200 `application/pdf` → nộp; tab Phiếu tóm tắt có nút “In giấy đề nghị”, chỉ một bản in; NV HCM nhập mã QR → “Hồ sơ thuộc chi nhánh khác.”; NV Hà Nội nhập mã (chữ thường) → “Mở tệp” CCCD → 200 `image/jpeg`; sao kê “Không khớp” → khách thấy “Chi nhánh ghi nhận: …”, tải lại, nộp lại OTP → NV khớp toàn bộ → phê duyệt → giải ngân (“Đã giải ngân”, “Phiếu đối chiếu (2)”). Lỗi API duy nhất là 403 by-qr của NV HCM (đúng ý). `python tests\e2e\full_flow.py` trên mock → `ERRORS []`.
  - Lúc thử, cổng 8080 đang có một backend cũ của dự án (profile `dev`, mã trước B5) nên tôi chạy bản mới ở cổng 8081 với một cấu hình Vite tạm (đã xóa); không tắt tiến trình cũ. Tiến trình đó vẫn chạy mã cũ, cần khởi động lại để có B5. DB Docker dev đã áp V7 và có thêm hồ sơ thử HS2610-0505 (đã giải ngân).

### B6 – xong 23/10/2026
- **Đã có** (`privacy/`: `PrivacyController`, `PrivacyService`, `PrivacyDtos`; `audit/ActivityLogDto` dùng chung với `/admin/logs` ở B7). Không cần migration (bảng `consent`, `activity_log` đủ cột; không có V8).
  - Quyền: `CustomerOnlyFilter` mở rộng cho `GET /me/data` và `POST /me/consents/withdraw` → chưa đăng nhập hoặc không phải khách hàng đều 401 “Cần đăng nhập tài khoản khách hàng.” (đúng chữ mock), kiểm tra trước khi đọc body. Không dùng `@RequireRole` vì mock trả 401 chứ không phải 403.
  - `GET /me/data`: `account` (tên như `AuthUser.name`: họ tên hoặc “Khách hàng ”+4 số cuối), `personal` của hồ sơ mới nhất có họ tên, `consents` mới nhất trước kèm `applicationCode` (hồ sơ có `consent_id` = lần đồng ý B đó), đếm lượt sàng lọc đã lưu/phương án, `applications` theo thứ tự tạo với số giấy tờ đã tải (có tên tệp, trừ giấy đề nghị hệ thống), `logs` 100 dòng mới nhất: thao tác của chính khách **và mọi thao tác trên hồ sơ của khách** (kể cả nhân viên xem hồ sơ; tên nhân viên lấy từ bảng `staff`). Lượt xem được ghi “Xem dữ liệu cá nhân” **sau** khi dựng kết quả nên chỉ hiện từ lần xem sau (như mock).
  - `POST /me/consents/withdraw` (một giao dịch, khóa dòng các hồ sơ của khách `PESSIMISTIC_WRITE` để không chạy song song với nhân viên đang đối chiếu):
    - Phần A: xóa lượt sàng lọc đã lưu của khách mà **không có phương án nào đã lập hồ sơ** (kể cả hồ sơ đã hủy, vì hồ sơ còn tham chiếu phương án nên không xóa được; mock cũng giữ), cùng `plan_cross_sell`, `loan_plan`, `other_debt`, `screening_result`; đánh dấu `withdrawn_at` mọi đồng ý phần A chưa rút.
    - Phần B: hồ sơ DRAFTING/WAITING_VERIFY/NEED_MORE/EXPIRED chuyển CANCELLED qua `ApplicationStateMachine` (ghi chú “Khách hàng rút lại sự đồng ý”, thông báo chi nhánh “Hồ sơ đã hủy do khách hàng rút đồng ý”); với các hồ sơ đó và cả hồ sơ CANCELLED từ trước: giấy tờ về MISSING, xóa tệp trong kho (Cloudinary/cục bộ), `extracted_field`, giấy đề nghị, CCCD/địa chỉ/email, cảnh báo, `recalc`. Hồ sơ VERIFIED trở đi vào `keptApplications`. Đánh dấu `withdrawn_at` đồng ý phần B.
    - Thứ tự kiểm tra: `scope` ∉ {A, B} → 400 “Dữ liệu không hợp lệ.” kèm `fields.scope` (trước OTP, để yêu cầu sai không làm tăng số lần nhập sai); rồi OTP qua `OtpService` (“Mã OTP không đúng.” / “Mã OTP đã hết hạn hoặc nhập sai quá nhiều lần, vui lòng gửi lại mã mới.”), `consume` khi thành công. Nhật ký “Rút lại sự đồng ý” / “Phần (a|b)”.
    - Thông báo kết quả (`message`) chép từng chữ từ mock.
- **Đã chốt với hợp đồng/mock** (openapi và mock cùng nói, quy-tac mục 14 thiếu nên đã sửa mục 14): phần B xử lý cả EXPIRED và CANCELLED; phần A giữ lượt gắn bất kỳ hồ sơ nào.
- **Khác mock có chủ đích** (đã sửa mock nếu có thể):
  - Xóa luôn **giấy đề nghị** (`loan_request_form`) của các hồ sơ bị xóa dữ liệu vì nội dung có CCCD, địa chỉ; mock đã thêm `a.requestForm = null`.
  - Xóa tệp trong kho chạy **trong giao dịch**: không xóa được (Cloudinary lỗi) → hoàn tác toàn bộ, 502 “Không xóa được tệp trên kho lưu trữ, vui lòng thử lại sau.” (thông báo tự đặt; đã thêm `502` vào openapi), OTP chưa bị dùng nên khách thử lại ngay. Chọn cách này thay vì xóa sau commit (như thay phiên bản tệp ở B4) vì tệp CCCD còn trên kho sau khi khách đã rút đồng ý là lỗi quyền riêng tư không ai biết. Đổi lại giữ kết nối DB vài giây khi gọi Cloudinary; nếu một phần tệp đã xóa rồi mới lỗi thì lần thử lại vẫn đúng vì xóa tệp không còn là thao tác bỏ qua được.
  - Hồ sơ vừa hủy ghi `cancel_reason` “Khách hàng rút lại sự đồng ý” (mock không có; không hiện ra API).
- **Hợp đồng**: `/applications/{id}/submit` và `/me/consents/withdraw` thêm thông báo OTP hết hạn (“Mã OTP đã hết hạn hoặc nhập sai quá nhiều lần, vui lòng gửi lại mã mới.”) vào mô tả lỗi; `/me/consents/withdraw` mô tả lại phần A/B, thứ tự kiểm tra và thêm phản hồi 502. Redocly 0 lỗi (19 cảnh báo như trước).
- **Kiểm tra**:
  - `.\mvnw.cmd verify` → 155 test, 0 lỗi, 2 bỏ qua (live test Cloudinary/Gemini). Mới 5 `PrivacyApiTest`: quyền 401 (không token/nhân viên/quản trị, kể cả body hỏng); `myData` (khách mới → nhật ký chỉ có “Đăng ký tài khoản”, lượt xem nằm ở lần sau, hồ sơ + giấy tờ + đồng ý A/B kèm mã hồ sơ, nhân viên xem hồ sơ hiện trong nhật ký, khách khác không thấy); lỗi rút đồng ý (scope sai, chưa gửi/sai OTP, OTP dùng một lần); phần A (xóa đúng lượt không gắn hồ sơ cùng dữ liệu con, giữ lượt gắn hồ sơ, đồng ý A đã rút, B còn hiệu lực, rút lần hai không còn gì); phần B (4 hồ sơ: đang nộp, nháp, đã đối chiếu, đã hủy còn tệp → hủy 2, giữ 1, xóa 7 tài liệu, tệp biến mất khỏi kho trừ hồ sơ được giữ, `extracted_field`/giấy đề nghị/CCCD/địa chỉ/email đã xóa, thông báo chi nhánh, lỗi xóa tệp → 502 và hoàn tác, rút lần hai idempotent).
  - `npm run build`, `npx eslint src` (0 lỗi, 4 cảnh báo có sẵn).
  - Backend `dev` (Cloudinary thật, `--app.ocr.provider=fake` để không gửi gì lên Gemini; cổng 8081 vì 8080 đang có backend cũ chưa khởi động lại) + FE `VITE_USE_MOCK=false` (cấu hình Vite tạm, đã xóa), Playwright, khách mới 0992741105: lập hồ sơ HS2610-0509, 5 tệp lên Cloudinary, nộp; “Mở tệp” CCCD → 200 `image/jpeg`, sao kê → 200 `application/pdf`, ảnh xem trước 200; trang “Dữ liệu cá nhân” hiện đủ thông tin; “Tải dữ liệu (JSON)” ra tệp 7 khóa (`account, applications, consents, logs, personal, plans, screenings`) khớp dữ liệu; rút đồng ý B qua OTP → “Đã xóa 5 tài liệu…”, hồ sơ “Đã hủy”, CCCD/địa chỉ trống, đồng ý B “Đã rút”, nhật ký có “Rút lại sự đồng ý – Phần (b)”. **Cả ba đường dẫn ký lấy trước khi rút (tệp gốc ảnh, tệp gốc PDF, ảnh xem trước) trả 404 sau khi rút**, nên tệp đã biến mất trên Cloudinary; lưu ý ngay sau khi rút CDN còn trả bản lưu tạm (200) khoảng vài giây, lần kiểm sau ~10 giây mới 404. Chạy thật `kind=original` với Cloudinary (PDF + ảnh) lần đầu ở đây: đạt. Không có lỗi API/console.
- **Phát hiện ngoài phạm vi B6** (chưa sửa):
  - ~~Cloudinary từ chối PDF hỏng → upload trả 500~~: đã xử lý (xem mục “Tệp hỏng khi tải giấy tờ”).
  - `tests/e2e/files/saoke.pdf` đã bị xóa trong commit B5 nên `tests/e2e/full_flow.py` không chạy được nếu thiếu tệp này; bản cũ (80 KB) cũng là PDF hỏng với Cloudinary, cần PDF hợp lệ nếu chạy e2e với backend thật.
  - DB Docker dev còn hồ sơ thử của các lần trước (HS2610-0502…0505 của 0901234567 và HS2610-0509 đã hủy); ba hồ sơ thử bị bỏ dở của lần chạy lỗi (0506–0508) đã dọn bằng chính chức năng rút đồng ý B. Tiến trình backend cũ ở cổng 8080 vẫn chạy mã trước B5/B6, cần khởi động lại.

### Tệp hỏng khi tải giấy tờ – xong 23/10/2026
- **Vấn đề** (phát hiện lúc thử B6): Cloudinary từ chối PDF hỏng bằng `RuntimeException("Invalid PDF file")`, upload trả 500.
- **Đã làm**:
  - `document/CorruptedFileException`: kho ném khi từ chối tệp hỏng / không mở được. `CloudinaryStorageService.upload` dịch lỗi có chữ “Invalid … file”, “Empty file”, “corrupt…” (regex, không phân biệt hoa thường); lỗi khác của Cloudinary (khóa sai, mạng, hết hạn mức) giữ nguyên là lỗi hệ thống (500). `LocalStorageService` từ chối tệp rỗng.
  - `DocumentUploadService` bắt `CorruptedFileException` ở bước lưu kho: giấy tờ `REJECTED_QUALITY` / `CORRUPTED`, `qualityMessage` “Tệp bị hỏng hoặc không mở được, vui lòng xuất/chụp lại và tải lên lần nữa.”, không gửi máy đọc, không có `previewUrl`/`fileUrl`, vẫn trả 200 và tăng `versionNo`. Như BLURRY: bản thay thế bị từ chối thì tệp phiên bản cũ bị xóa (giấy tờ không còn tệp nào trong kho).
  - `V8__chat_luong_tep_hong.sql`: thêm `CORRUPTED` vào CHECK `ck_application_document_quality`.
  - Hợp đồng: `qualityStatus` thêm `CORRUPTED` (openapi, `types.ts`), mô tả upload thêm nhánh này; `quy-tac-tinh-toan.md` mục 9. `DocumentCard` không cần sửa (đã hiện `qualityMessage` ở `Alert` lỗi cho mọi `REJECTED_QUALITY`).
  - Mock: `isCorrupted` trong `handlers/applications.ts` giả lập kho từ chối (tệp rỗng hoặc đầu tệp sai chữ ký `%PDF-` / `FF D8 FF` / `89 50 4E 47`), chạy sau 3 bước kiểm tra cũ (như backend: tệp ảnh nhỏ vẫn là BLURRY trước).
  - **Không** kiểm tra chữ ký tệp ở backend: nhiều test dùng byte giả (toàn số 0) làm “ảnh”/“PDF”; phát hiện hỏng chỉ dựa vào việc kho từ chối. Kho cục bộ vì thế chỉ bắt tệp rỗng.
- **Kiểm tra**:
  - `.\mvnw.cmd verify` → 157 test, 0 lỗi, 2 bỏ qua. Mới: `ApplicationApiTest.upload_corruptedFileIsRejectedQuality` (PDF cắt cụt bị kho từ chối → CORRUPTED, tệp cũ bị xóa, `quality_status` trong DB; `IllegalStateException` của kho vẫn 500; tệp rỗng thật qua kho cục bộ → CORRUPTED; tải lại tệp tốt → UPLOADED/OK), `CloudinaryStorageServiceTest.upload_translatesCloudinaryFileRejectionToCorruptedFile` (mock `Uploader`: 4 thông báo hỏng → `CorruptedFileException`, 4 lỗi khác giữ nguyên). `TienSangLocVayApplicationTests` sửa Flyway V1–V8. `npm run build`, `npx eslint src` (0 lỗi, 4 cảnh báo có sẵn), redocly 0 lỗi.
  - `tests/e2e/files/saoke.pdf` đã được thay bằng PDF hợp lệ (48 KB, chưa commit); `python tests\e2e\full_flow.py` trên mock → `ERRORS []`. Playwright trên mock: tải “PDF” hỏng lên thẻ Sao kê → `Alert` đỏ đúng lời nhắc, tải lại `saoke.pdf` → đạt.
  - Cloudinary thật (backend `dev`, `--app.ocr.provider=fake`, cổng 8081, khách thử rồi rút đồng ý B để xóa tệp): `saoke.pdf` mới → 200, UPLOADED/OK, `fileUrl` trả đúng 48 310 byte `application/pdf`, `previewUrl` trả `image/png` (trang đầu); bản `saoke.pdf` hỏng cũ (80 016 byte, lấy từ commit B4) → 200, REJECTED_QUALITY / CORRUPTED, đúng lời nhắc (trước đây 500). Do lỗi chạy lệnh của tôi, `saoke.pdf` hợp lệ được tải lên Cloudinary 3 lần thay vì 1 (đều đã xóa bằng rút đồng ý B).

### B7 – xong 23/10/2026
- **Đã có**:
  - `V9__quan_tri.sql`: bảng `scheduled_job` (code, name, last_run_at, last_result; 3 dòng seed tên như mock), chỉ mục `activity_log(created_at desc)`, `loan_application(status, updated_at)`. `param_version.test_result` (jsonb, có từ V1) ánh xạ vào entity dạng chuỗi JSON.
  - `admin/`: `ParamVersionController/Service`, `AdminStaffController/Service`, `AdminBranchController/Service`, `AdminCatalogController/Service`, `JobController`, `AdminDtos`, `FieldErrors` (400 “Dữ liệu không hợp lệ.” kèm `fields`), `PolicyLabels` (chép `PARAM_LABELS`, `CRITERIA_LABELS`), `SampleProfiles` (chép `SAMPLE_PROFILES`); `admin/job/`: `ScheduledJob`, `ExpireDocumentsJob`, `PurgeRetentionJob`, `RemindDraftsJob`, `JobService`, `JobScheduler` (`@EnableScheduling`, cron `app.jobs.*`, giờ VN), entity `JobRecord`; `audit/AdminLogController` (`GET /admin/logs`). Mọi controller quản trị gắn `@RequireRole(Role.ADMIN)`.
  - `application/ApplicationDataEraser`: phần xóa dữ liệu định danh của một hồ sơ (giấy tờ → MISSING, `extracted_field`, giấy đề nghị, CCCD, địa chỉ, cảnh báo) tách từ `PrivacyService.withdrawB`, dùng chung cho rút đồng ý B và `PURGE_RETENTION`; hành vi B6 giữ nguyên (`PrivacyApiTest` vẫn đạt). `ApplicationStateMachine.note(app, actor, note)`: dòng lịch sử giữ nguyên trạng thái (chuyển chi nhánh). `ActivePolicy.load(id)`: đọc phiên bản không qua bộ nhớ đệm (bản nháp khi chạy thử).
  - Entity thêm constructor/setter cho thao tác ghi: `Staff` (thêm `@GeneratedValue`, `created_at`), `Branch`, `LoanProduct`, `DocumentType`, `CrossSellProduct`, `ProductDocumentType`, `ParamVersion`, `PolicyParam`, `ScoringWeight`, `LoanApplication.setBranchId`. Không thêm thư viện.
- **Phiên bản tham số**: mọi thao tác ghi khóa dòng phiên bản (`PESSIMISTIC_WRITE`) và chỉ chạy trên DRAFT, nên bản ACTIVE/EXPIRED không đổi (giữ giả định của `GET /screenings/{id}` ở B3; có test so nguyên JSON trước/sau kích hoạt). Kích hoạt: bản ACTIVE cũ → EXPIRED, `flush` (index `uq_param_version_active`), bản nháp → ACTIVE, `ActivePolicy.evict()` trong `afterCommit`. Chạy thử dùng `Engine.exclusion/maxSafeAmount/scoreOf` với tham số bản ACTIVE và bản nháp, ngày hôm nay giờ VN, lưu `{runAt, changed, rows}`. Tạo nháp song song vi phạm `uq_param_version_draft` / `version_no` → 409 đúng chữ mock.
- **Đã chốt với người dùng (23/10/2026)**:
  - Ngừng chi nhánh **chuyển cả nhân viên** BRANCH_STAFF sang chi nhánh nhận; còn hồ sơ đang xử lý hoặc còn nhân viên mà thiếu chi nhánh nhận hợp lệ → 409 (“Chi nhánh còn N hồ sơ đang xử lý; chọn chi nhánh nhận chuyển.” như mock, hoặc tự đặt “Chi nhánh còn N nhân viên; chọn chi nhánh nhận chuyển.”). Response thêm `transferredStaff` (openapi, `types.ts` `BranchDeactivateResult`, mock, `Branches.tsx` luôn hiện ô chọn chi nhánh nhận và báo số hồ sơ + nhân viên đã chuyển). Nhật ký “Ngừng hoạt động chi nhánh” thêm “, chuyển m nhân viên”.
  - Mật khẩu tạm (tạo nhân viên, đặt lại) **ngẫu nhiên 10 ký tự** (`SecureRandom`, bỏ ký tự dễ nhầm), chỉ lưu BCrypt; mock giữ `123456`. Khóa **và** đặt lại mật khẩu thu hồi mọi phiên (mock nay cũng xóa token khi đặt lại).
  - Lịch tác vụ theo cron `application.yml`; `schedule` sinh từ cron (“Hằng ngày lúc 01:00”, “Ngày 1 hằng tháng lúc 02:00”, “Hằng ngày lúc 09:00”; cron khác → “Theo lịch …”; `-` → “Không chạy tự động”). Đã sửa chữ trong mock `db.ts`, `DB_VERSION` 8 → 9. Profile test đặt cron `-` (tắt).
- **Tác vụ định kỳ**: chạy tay và theo lịch đều qua `JobService.run` (một `ReentrantLock` mỗi tác vụ; đang chạy → 409 “Tác vụ đang chạy, vui lòng thử lại sau.” – tự đặt, đã thêm vào openapi). Nhật ký “Chạy tác vụ định kỳ” “tên: kết quả”, tác nhân là quản trị viên (chạy tay) hoặc hệ thống (theo lịch).
  - EXPIRE_DOCUMENTS: khóa dòng hồ sơ, chuyển qua `ApplicationStateMachine` (tác nhân “Hệ thống”), thông báo khách, nhật ký SYSTEM “Chuyển hết hạn”.
  - PURGE_RETENTION: `RETENTION_MONTHS` của bản ACTIVE (thiếu/0 → 12), mỗi tháng 30 ngày như mock. **Mỗi hồ sơ một giao dịch**, tệp xóa trong giao dịch qua `StorageService.delete` (Cloudinary/kho cục bộ); xóa lỗi → hồ sơ đó hoàn tác, chạy tiếp hồ sơ khác, kết quả thêm “ k hồ sơ chưa xóa được tệp, sẽ thử lại lần sau.”. Xóa thêm **giấy đề nghị** (có CCCD, địa chỉ) như B6 – đã sửa quy-tac mục 13 và mock (`a.requestForm = null`).
  - REMIND_DRAFTS: như mock (mỗi lần chạy nhắc lại).
- **Nhật ký** (`GET /admin/logs`): giữ hợp đồng mảng ≤ 500 dòng mới nhất (FE đã phân trang 20 dòng/trang phía trình duyệt, không đổi openapi). Truy vấn native nối `customer`/`staff`/`loan_application` để có tên (“Khách hàng ”+4 số cuối khi chưa có họ tên) và mã hồ sơ; từ khóa trim + chữ thường, thoát `% _ \`, tìm trên chuỗi “tên thao tác chi tiết mã” như mock.
- **Khác mock có chủ đích** (chặt hơn, 400 “Dữ liệu không hợp lệ.” kèm `fields`, chạy sau các kiểm tra có chữ của mock): PUT phiên bản phải gửi đúng bộ khóa tham số/tiêu chí của bản nháp, giá trị là số, trọng số 0–100 tối đa 2 chữ số thập phân; clone kiểm tra `versionNo` ≤ 20, `legalBasis` ≤ 255, `changes` là số; nhân viên: họ tên ≤ 100, vai trò hợp lệ, chi nhánh phải tồn tại và đang hoạt động; chi nhánh/danh mục: trường bắt buộc, độ dài theo cột, `status` ACTIVE/INACTIVE, `validityDays` ≥ 1, tiền ≥ 0 nguyên, `scoreBonus`/`rateDiscount` 0–999,99; giấy tờ theo gói: loại giấy tờ phải tồn tại, không trùng; `actorType` lạ ở nhật ký → 400 (mock trả rỗng). `allowedFormats` chuẩn hóa chữ hoa, phân cách dấu phẩy (“jpg, png” → “JPG,PNG”). Như các giai đoạn trước: body JSON hỏng → 400 trước 404/409; vai trò vẫn kiểm tra trước body.
- **Thứ tự quy tắc giấy tờ**: `PUT /admin/product-documents` và `GET /admin/catalog` trả theo gói, bắt buộc trước, rồi theo mã loại (như B2), không theo thứ tự gửi lên như mock.
- **Kiểm tra**:
  - `.\mvnw.cmd verify` → 170 test, 0 lỗi, 2 bỏ qua (live Cloudinary/Gemini). Mới 13 test: `ParamVersionApiTest` (quyền; luồng clone + changes → các lỗi PUT → chạy thử 12 dòng khớp bộ máy Java → sửa lại xóa kết quả chạy thử → kích hoạt, `ActivePolicy` đổi sau commit, lượt sàng lọc cũ không đổi, lượt mới dùng bản mới, sửa/xóa/chạy thử/kích hoạt bản ACTIVE và EXPIRED → 409, xóa nháp), `AdminStaffBranchApiTest` (tạo/sửa/khóa → phiên cũ 401, đăng nhập báo bị khóa → mở khóa → đặt lại mật khẩu thu hồi phiên; chi nhánh CRUD, ngừng chi nhánh chỉ có nhân viên, ngừng Đà Nẵng chuyển hồ sơ + nhân viên sang HCM kèm lịch sử, thông báo khách và chi nhánh nhận, mở lại), `AdminCatalogLogApiTest` (danh mục đủ nhánh lỗi, INACTIVE ẩn khỏi danh mục công khai, giấy tờ theo gói áp dụng cho checklist; nhật ký lọc loại, từ khóa có `%`/`_`, tên người thực hiện, giới hạn 500 và thứ tự), `JobApiTest` (mô tả cron, quyền/mã lạ, hết hạn, nhắc, xóa lưu trữ – tệp thật biến mất khỏi kho cục bộ, lỗi xóa tệp hoàn tác đúng hồ sơ đó và lần sau xóa nốt). `TienSangLocVayApplicationTests`: Flyway V1–V9, 29 bảng. (Gộp thành 4 lớp test thay vì 6 như kế hoạch để bớt số container.)
  - `npm run build`, `npx tsc -b`, `npx eslint src` (0 lỗi, 4 cảnh báo có sẵn); redocly lint 0 lỗi (19 cảnh báo như trước).
  - Backend `dev,local` (cổng 8080, Flyway áp V9) + FE `VITE_USE_MOCK=false` (biến môi trường, **chưa đổi** `fe/.env.development`), Playwright với `quantri@tiensangloc.local`: tạo bản nháp 2026.11 → sửa DTI tối đa 72 (thử trọng số TD tổng 105% → “Tổng trọng số của gói TD phải bằng 100% (hiện 105%).”) → lưu → chạy thử → kích hoạt (2026.11 “Đang áp dụng”, 2026.10 “Hết hiệu lực”); khóa `nv.danang` → đăng nhập báo “Tài khoản đang bị khóa, liên hệ quản trị viên.” → mở khóa → đăng nhập lại được; ngừng CN Đà Nẵng chuyển sang HCM → “Đã ngừng hoạt động và chuyển 1 hồ sơ và 1 nhân viên”; chạy 3 tác vụ (0 hồ sơ hết hạn / xóa 0 tài liệu / nhắc 0 khách – DB dev không có hồ sơ đủ điều kiện); trang nhật ký có đủ hành động, lọc “chi nhánh” đúng. Lỗi API chỉ có 400 trọng số, 403 tài khoản khóa (cố ý) và 404 `/admin/stats` (B8).
  - **DB Docker dev sau khi thử**: phiên bản **2026.11 (DTI tối đa 72%) đang áp dụng**, 2026.10 hết hiệu lực; muốn quay về 70% thì tạo bản nháp từ 2026.11, sửa DTI rồi kích hoạt (hoặc `docker compose down -v`). CN Đà Nẵng đã mở lại, `nv.danang` đã trả về Đà Nẵng; hồ sơ thử HS2610-0514 (khách 0914448672, DRAFTING) tạo để thử chuyển chi nhánh vẫn nằm ở CN Hồ Chí Minh.
- **Khóa dòng chi nhánh** (sửa sau B7, cùng ngày):
  - `POST /applications` (`ApplicationService.create`) đọc chi nhánh bằng `BranchRepository.findByIdForUpdate` (`SELECT … FOR UPDATE`, `PESSIMISTIC_WRITE`) rồi **mới** kiểm tra `ACTIVE` (400 “Chi nhánh không hợp lệ.” như cũ). `AdminBranchService.deactivate` khóa cùng dòng, nên hồ sơ mới hoặc chờ rồi bị từ chối, hoặc đã được ghi trước khi `deactivate` đếm và chuyển hồ sơ đang xử lý.
  - `deactivate` khóa cả chi nhánh bị ngừng lẫn chi nhánh nhận **theo thứ tự id tăng dần** (hai lần ngừng chéo A→B, B→A không khóa chết) và kiểm tra lại `ACTIVE` của chi nhánh nhận sau khi khóa. `activate` đã khóa từ trước.
  - Test `BranchLockConcurrencyTest` (3 test): giữ khóa dòng bằng một giao dịch đang đổi INACTIVE, tạo hồ sơ phải chờ rồi bị từ chối (bỏ khóa ở `create` thì test này fail); 8 yêu cầu tạo hồ sơ chạy cùng lúc với ngừng chi nhánh → không còn hồ sơ nào ở chi nhánh đã ngừng, hồ sơ đã tạo đều nằm ở chi nhánh nhận; hai lần ngừng chéo nhau đều 200, không 500.
- **Giới hạn đã biết** (không sửa): khóa chống chạy chồng của tác vụ định kỳ (`JobService`, `ReentrantLock` mỗi tác vụ) chỉ có tác dụng trong một tiến trình backend. Chạy nhiều bản backend cùng lúc thì mỗi bản có thể chạy tác vụ theo lịch của nó; khi đó cần khóa ở DB (ví dụ `pg_advisory_lock` hoặc ShedLock).
- **Giao diện (sửa sau B7)**: trang “Tài khoản nhân viên” hiện mật khẩu tạm (khi tạo và khi đặt lại) **một lần** trong Modal có ô chỉ đọc và nút “Sao chép” (Modal không đóng bằng nền/Esc, phải bấm “Đã lưu lại, đóng”). `RequireRole` (`fe/src/auth/AuthContext.tsx`): đã đăng nhập nhưng sai vai trò thì hiện lỗi 403 “Bạn không có quyền truy cập trang này.” tại chỗ, không chuyển sang trang khác; chưa đăng nhập vẫn chuyển tới trang đăng nhập kèm `?next=`.

### B8 – xong 23/10/2026
- **Đã có**:
  - `report/`: `ReportController` (`@RequireRole(Role.ADMIN)`), `ReportService`, `ReportDtos` (record đúng tên trường `StatsResponse`, `AccuracyResponse`, `ReportProposal`, `ReportResponse`). Truy vấn bằng `NamedParameterJdbcTemplate` (SQL native, `count/sum … FILTER`, `GROUP BY`), không nạp hồ sơ vào bộ nhớ: CTE hồ sơ trong khoảng ngày ⨝ phương án ⨝ gói, gộp `application_status_history` một lần theo hồ sơ (`bool_or` các mốc, `min(created_at)` của lần nộp/giải ngân/từ chối đầu tiên, `max(created_at)`), kết quả xét duyệt mới nhất qua `LEFT JOIN LATERAL` (như `ApplicationSupport.approval`). Điểm dự đoán = `loan_plan.score` (đã cập nhật khi đổi phương án trong hồ sơ, giống `predictedScore` của mock). Danh sách tháng `byMonth` sinh ở Java.
  - `V10__bao_cao.sql`: chỉ mục `loan_application(created_at)`, `screening(created_at)`, `loan_plan(created_at)`, `application_status_history(application_id, to_status, created_at)`.
  - `db/dev/V1000__dev_history.sql` (một khối `DO` PL/pgSQL, `setseed` cố định, ngày lùi từ `now()`): 90 hồ sơ ở 3 chi nhánh trong ~6 tháng theo `historical()` của mock (TD 68%, số tiền/kỳ hạn/lãi, điểm 35–95, hồ sơ > 25 ngày đã đóng với xác suất duyệt logistic theo điểm, 3 lý do từ chối), đủ bảng liên quan: khách hàng `0968800001…090` (mật khẩu 123456), đồng ý A/B, 2–4 lượt sàng lọc mỗi khách + 120 lượt ẩn danh, phương án CONFIRMED (+ nửa số khách có phương án DRAFT), giấy tờ theo danh mục gói (không có tệp thật, `storage_path` rỗng → `previewUrl` null), giấy đề nghị, lịch sử trạng thái, phiếu đối chiếu + chi tiết, kết quả xét duyệt, nhật ký “Đổi trạng thái”. **Khác mock**: 7 hồ sơ đầu luôn là hồ sơ gần đây mang lần lượt các trạng thái đang mở để luôn đủ 9 trạng thái; mốc lịch sử không vượt quá hiện tại.
  - Profile dev: thêm `classpath:db/dev` và `spring.flyway.out-of-order: true` (để V11, V12… sau này vẫn áp được trên DB dev đã có V1000). Profile test không chạy db/dev (có test kiểm tra `flyway_schema_history` không có 1000).
  - Mock: tách phần tính sang `fe/src/mocks/reportCalc.ts` (hàm thuần `statsOf`, `accuracyOf`, `mb14Of`), `handlers/reports.ts` chỉ gọi lại. Sửa kèm: (a) luật “từ chối vì thu nhập” tính **lũy kế đến cuối kỳ** (trước đây đếm mọi hồ sơ, kể cả sau tháng báo cáo); (b) lỗi danh sách tháng `byMonth` lùi 1 tháng ở GMT+7 (do `new Date(from+'T00:00:00').toISOString()`), nay sinh bằng số học chuỗi `YYYY-MM`. Không đổi cấu trúc dữ liệu, không tăng `DB_VERSION`.
- **Đã chốt với người dùng (23/10/2026)**: giữ **4 đề xuất như mock** (thêm “Siết ngưỡng DTI tối đa” khi độ chính xác dương < 75%, không dưới 65%) – đã bổ sung vào `quy-tac-tinh-toan.md` mục 15; luật thu nhập lũy kế đến cuối kỳ như trên.
- **Khác mock có chủ đích**: ngày/tháng theo lịch giờ Việt Nam (mock cắt chuỗi ISO UTC); `from`/`to` sai định dạng, `branchId` không phải số, khoảng > 10 năm, `month` sai dạng `YYYY-MM` → 400 “Dữ liệu không hợp lệ.” kèm `fields` (đã thêm `'400'` vào openapi cho `/admin/stats`, `/admin/reports/mb14`). Giữ như mock: `branchId=0` = toàn hệ thống, `threshold` thiếu/không phải số/0 → 55, chi nhánh không tồn tại → số liệu rỗng, lượt sàng lọc/phương án khi lọc chi nhánh là ước lượng theo tỷ trọng hồ sơ.
- **Hành vi cần biết**: tháng có cả “nới” và “siết” DTI cùng lúc nay thành một đề xuất “Xem lại ma trận trọng số” (xem mục “sửa logic đề xuất MB14” bên dưới, thay thế hành vi ban đầu của giai đoạn này).
- **Kiểm tra**:
  - `be/tools/gen-report-fixtures.ts` (`npx -y tsx@4.19.2 be/tools/gen-report-fixtures.ts`) dựng bộ 25 hồ sơ (đủ trạng thái, điểm biên 45/55/85, lý do “Thu nhập”, kết quả rơi sang tháng sau, mốc 09:00–18:00 giờ VN) và chạy `reportCalc.ts` cho 14 ca → `src/test/resources/report-fixtures/report-cases.json`. Sửa `reportCalc.ts` phải sinh lại.
  - `ReportApiTest` (5 test): nạp bộ dữ liệu vào các bảng thật, 14 ca trả JSON **trùng khớp** mock (số so theo giá trị, bỏ `generatedAt`, cả trường thừa/thiếu; MB14 tháng 9 và 10 đủ 4 đề xuất, tháng 7–8 “Giữ nguyên”); 401/403 khách và nhân viên; 400 tham số sai; mặc định `from/to/month` theo giờ VN, `branchId=0`, chi nhánh lạ, `threshold=0`; profile test không có V1000. `DevHistoryMigrationTest` (3 test, container riêng bật db/dev): 90 hồ sơ, đủ 9 trạng thái, 3 chi nhánh, 2 gói, ≥ 6 tháng, trạng thái hiện tại khớp dòng lịch sử cuối; 3 endpoint có số liệu; nhân viên CN Hà Nội mở được mọi hồ sơ lịch sử của chi nhánh. `TienSangLocVayApplicationTests` cập nhật danh sách Flyway V1–V10.
  - `.\mvnw.cmd verify` → 181 test, 0 lỗi, 2 bỏ qua (live Cloudinary/Gemini). `npm run build`, `npx eslint src` (0 lỗi, 4 cảnh báo có sẵn); redocly lint 0 lỗi (19 cảnh báo như trước).
  - Backend `dev,local` (DB Docker dev lúc chạy đang trống nên Flyway áp V1 → V10 và V1000) + FE `VITE_USE_MOCK=false` (biến môi trường, chưa đổi `fe/.env.development`), Playwright với `quantri@tiensangloc.local`: Thống kê 26/04–23/10/2026: 90 hồ sơ, 386 lượt sàng lọc, 126 phương án, thời gian xử lý TB 5,7 ngày, đủ 7 tháng; lọc CN Hồ Chí Minh (30 hồ sơ), đổi khoảng 01/08–30/09; Độ chính xác: ngưỡng 55 → 66 hồ sơ, 81,8 / 82,5 / 86,8%, kéo ngưỡng 60 gọi lại API; Báo cáo hiệu chỉnh tháng 09/2026 (“Giữ nguyên”), xuất CSV `bao-cao-hieu-chinh-2026-09.csv`, nút In gọi `window.print`; tháng 05/2026 có “Nới DTI 70 → 72” và “Tăng chi phí sinh hoạt 4.000.000 → 4.500.000”, bấm “Tạo bản nháp tham số” → sang trang phiên bản nháp `2026.10.23-dti-up` có `DTI_MAX = 72`, sau đó đã xóa bản nháp thử (DB dev chỉ còn 2026.10 đang áp dụng). Không có lỗi API.

### B8 – sửa logic đề xuất MB14 (23/10/2026)
- **Thay đổi** (mock `reportCalc.ts`, backend `ReportService`, `quy-tac-tinh-toan.md` mục 15, openapi, `types.ts`, `Reports.tsx` sửa cùng lúc):
  - `AccuracyResponse.accuracy/precision/recall` nay **nullable** (openapi `nullable: true`, TS `number | null`, Java `Double`): `null` = chưa đủ dữ liệu; FE (`Indicator` trong `Reports.tsx`, xuất CSV) hiện “Chưa đủ dữ liệu” thay vì 0%. Ngưỡng `MIN_SAMPLE` = 10 (cùng giá trị ở `reportCalc.ts` và `ReportService`): độ nhạy cần ≥ 10 hồ sơ có kết quả, độ chính xác dương cần ≥ 10 hồ sơ được đánh giá đạt (TP+FP), độ chính xác chỉ `null` khi chưa có hồ sơ nào. Tỷ lệ theo nhóm điểm và theo gói vẫn 0 khi nhóm rỗng như trước (không đổi hợp đồng).
  - MB14: tháng chưa có hồ sơ nào có kết quả (lũy kế đến cuối kỳ) → chỉ “Chưa đủ dữ liệu để đề xuất hiệu chỉnh” (`no-data`); độ nhạy và độ chính xác dương cùng thấp (< 75%, cùng có giá trị) → bỏ cả hai đề xuất DTI, chỉ thêm “Xem lại ma trận trọng số” (`weights-review`); mỗi đề xuất DTI chỉ xét khi chỉ số của nó đủ dữ liệu.
  - **Chưa được chỉ định, đã tự chọn** (đổi lại nếu không đúng ý): (1) khi có `weights-review` vẫn xét `living-up` (căn cứ khác, theo lý do từ chối) nhưng bỏ `tc-weight` vì trùng ý; (2) ~~tháng ít dữ liệu vẫn “Giữ nguyên tham số”~~ → **đã chốt lại cùng ngày**: tháng có hồ sơ có kết quả nhưng dưới 10 và không đề xuất nào hiện “Giữ nguyên tham số (dữ liệu còn ít: n hồ sơ có kết quả, cần thêm để đánh giá chính xác)” (`id` `keep-low-data`). (1) đã được xác nhận.
  - Hệ quả với DB dev: tháng 04/2026 (chưa có kết quả nào) không còn đề xuất “Siết ngưỡng DTI” sai, chỉ còn “Chưa đủ dữ liệu…”.
- **Fixtures**: `gen-report-fixtures.ts` dựng lại bộ 72 hồ sơ theo tháng 05–10/2026 và **dừng nếu một tháng MB14 không ra đúng tình huống thiết kế**: 05 `no-data`; 06 thiếu mẫu (6 hồ sơ) → `keep`; 07 độ nhạy thấp + độ chính xác dương chưa đủ mẫu → `dti-up, tc-weight`; 08 độ chính xác dương thấp → `dti-down, tc-weight`; 09 cả hai thấp → `weights-review, living-up`; 10 ổn định → `keep`. Thêm ca `model-accuracy?threshold=88` (độ chính xác dương `null`, độ nhạy có giá trị). 17 ca, `ReportApiTest` so JSON backend = mock cho cả giá trị `null`.
- **Flyway out-of-order** kiểm tra lại: chỉ có ở `application-dev.yml`; `application.yml` (mặc định) và `application-test.yml` không có; `ReportApiTest.devHistoryNotAppliedInTests` khẳng định `spring.flyway.out-of-order` = false và `spring.flyway.locations` = `classpath:db/migration` trong profile test.

### B8 – ngưỡng mẫu cho “tăng chi phí sinh hoạt” và bố cục ô chỉ số (23/10/2026)
- **Quy tắc `living-up`** chỉ xét khi có ít nhất 10 hồ sơ bị từ chối (lũy kế đến cuối kỳ), dưới ngưỡng thì không đề xuất; sửa cùng lúc `reportCalc.ts` (`rejects.length >= MIN_SAMPLE`), `ReportService` (`rejects >= MIN_SAMPLE`) và `quy-tac-tinh-toan.md` mục 15, nay có bảng ngưỡng mẫu của cả bốn quy tắc (+ đề xuất trọng số chung). Nguyên nhân: tháng 05/2026 trên DB dev từng đề xuất tăng chi phí sinh hoạt chỉ từ 1 hồ sơ bị từ chối.
- **Fixtures**: `gen-report-fixtures.ts` dựng lại bộ 69 hồ sơ để có ca biên và **dừng nếu không đúng**: tháng 8 lũy kế **9** hồ sơ bị từ chối với 3 hồ sơ (33% ≥ 30%) vì thu nhập → không có `living-up`; tháng 9 lũy kế **10** hồ sơ bị từ chối với 4 hồ sơ vì thu nhập → có `living-up`; tháng 10 vẫn `keep` (4 hồ sơ bị từ chối thêm vì lý do khác kéo tỷ lệ thu nhập xuống 4/14). `ReportApiTest` khớp mock cả 17 ca.
- **Giao diện** (`Reports.tsx`, `Indicator`): ba ô chỉ số cùng cao bằng nhau (ô kéo giãn theo hàng), tiêu đề có thể xuống dòng nhưng giá trị luôn nằm dưới đáy ô, cùng một hàng cao 40px; “Chưa đủ dữ liệu” không xuống dòng (`nowrap`).


### B9 – xong 23/10/2026
- **E2E với backend thật** (`dev,local`, DB vừa `docker compose down -v; up -d`, fe `VITE_USE_MOCK=false`): `python tests\e2e\full_flow.py --mode real` → `ERRORS []` ngay lần đầu, không cần sửa backend cho luồng chính. Đã xem ảnh chụp: tải lên, đọc giấy tờ, đối chiếu, bổ sung, duyệt, giải ngân, khóa Chi nhánh Đà Nẵng (chuyển 4 hồ sơ + 1 nhân viên), tác vụ định kỳ đều đúng. Ảnh thu nhỏ giấy tờ trông trống vì chính các ảnh mẫu trong `tests/e2e/files` gần như trắng, không phải lỗi `previewUrl`. Chế độ mock (`$env:VITE_USE_MOCK="true"`) cũng `ERRORS []`.
- **`tests/e2e/full_flow.py`** thêm tham số (kèm biến môi trường): `--mode mock|real` (`E2E_MODE`, mặc định `mock`), `--base-url` (`BASE_URL`), `--headed` (`E2E_HEADED=1`), `--ocr-timeout` (`E2E_OCR_TIMEOUT`, mặc định mock 5 s, real 60 s). `real` kiểm tra trước `/api/actuator/health` qua proxy Vite; cả hai chế độ dừng nếu frontend chạy khác chế độ đã chọn (có service worker MSW hay không). Tải giấy tờ nay chờ nút “Xác nhận thông tin đúng”/nhãn “Không đạt chất lượng” thay vì chờ cố định 1,3 s; nếu máy đọc từ chối ảnh (Gemini thật) thì dừng với lý do. Thoát mã 1 khi `ERRORS` khác rỗng.
- **So endpoint**: `tests/contract/compare_endpoints.py` (Python + PyYAML) so path+method của `/api/v3/api-docs` với `docs/openapi.yaml`, chuẩn hóa tên biến đường dẫn, bỏ qua actuator/api-docs/swagger; `/files/local` và `/files/cloud` chỉ có một cái tùy `app.storage.provider` nên thiếu cái kia không tính lỗi. Khác biệt tìm được và đã sửa: backend gộp `POST /admin/staff/{id}/{action}` trong khi openapi có 3 path `lock`, `unlock`, `reset-password` → `AdminStaffController` tách 3 mapping; mapping chung `{action}` giữ lại với `@Hidden` để action lạ vẫn trả 404 “Thao tác không hợp lệ.” như mock (`AdminStaffBranchApiTest` kiểm tra). Kết quả: 74 endpoint trong openapi, khớp ở cả `dev,local` và `dev`. openapi không đổi; redocly lint 0 lỗi.
- **`fe/.env.development`** đã là `VITE_USE_MOCK=false`; thêm comment cách bật mock (`$env:VITE_USE_MOCK="true"` – Vite ưu tiên biến môi trường có sẵn – hoặc `fe/.env.development.local`). `fe/.env.production` vẫn `true`. `fe/README.md` ghi rõ mặc định của từng lệnh.
- **README.md** viết lại: yêu cầu cài đặt, chạy DB/backend/frontend bằng PowerShell, `be/.env`, bảng profile `dev` / `dev,local` / `test`, tài khoản thử, test, e2e 2 chế độ, script so endpoint, cấu trúc thư mục.
- **Chạy luồng chính với `dev` (Cloudinary + Gemini thật)**, chỉ tệp trong `tests/e2e/files`: lần đầu (ảnh mẫu chỉ có khung) Gemini từ chối 4 ảnh JPG vì trống; lần hai (ảnh mẫu có nội dung, ghi “MẪU KIỂM THỬ”) Gemini từ chối `cccd.jpg` bằng `isReadable=false` vì là ảnh mẫu → xử lý ở mục “B9 – giấy tờ mẫu” bên dưới.
- **Còn tồn đọng**: không còn (e2e với Gemini thật đã chạy hết, xem mục “B9 – giấy tờ mẫu”). Không thêm dependency backend; script dùng `pyyaml` (Python, chỉ cho kiểm thử).
- Kiểm tra: `.\mvnw.cmd verify` → 181 test, 0 lỗi (2 bỏ qua: `CloudinaryLiveTest`, `GeminiOcrLiveTest` chỉ chạy khi có biến môi trường khóa); `npm run build` pass; `npx eslint src` 0 lỗi (4 cảnh báo `react-refresh` có từ trước).

### B9 – giấy tờ mẫu (`SPECIMEN`) và e2e với Gemini thật (23/10/2026)
- **Nguyên nhân**: ảnh mẫu mới trong `tests/e2e/files` ghi “MẪU KIỂM THỬ – KHÔNG CÓ GIÁ TRỊ”; Gemini trả `isReadable=false` kèm lý do “Ảnh là mẫu kiểm thử không có giá trị thực tế…” nên hệ thống ghi `BLURRY` (sai bản chất: ảnh rõ, chỉ là giấy tờ mẫu). **Đã chốt với người dùng**: không sửa ảnh, không nới prompt cho môi trường thật.
- **Cách làm**:
  - `responseSchema` thêm `isSpecimen` (bắt buộc trong schema; thiếu trong JSON thì `parse` coi false). Prompt yêu cầu báo `isSpecimen=true`, `isReadable=true` và vẫn đọc các trường khi ảnh rõ nhưng là giấy tờ mẫu (specimen, có chữ “MẪU”, “không có giá trị”, “dữ liệu giả”). `OcrResult` thêm `specimen` (constructor 4 tham số cũ giữ lại, `specimen=false`).
  - `OcrPolicy` (`document/`) quyết định nhận/từ chối: `isReadable=false` → `BLURRY`; sai loại → `WRONG_TYPE`; `isSpecimen` và `allow-specimen=false` → `REJECTED_QUALITY` / `SPECIMEN`, “Tệp là giấy tờ mẫu, không có giá trị, vui lòng tải giấy tờ thật.”; còn lại nhận. `DocumentUploadService` gọi `OcrPolicy.judge`, thay khối `if` cũ.
  - `app.ocr.allow-specimen` (mặc định `false` ở `application.yml`; `application-dev.yml` đặt `true`). Khi `true` và Gemini báo giấy tờ mẫu: giấy tờ vẫn `UPLOADED` / `OK` có `fields`, ghi nhật ký (`activity_log.action`) “Chấp nhận giấy tờ mẫu (môi trường thử nghiệm)” cùng tên giấy tờ và phiên bản. Profile `test` và `local` không đặt nên `false`.
  - `V11__giay_to_mau.sql`: thêm `SPECIMEN` vào CHECK `ck_application_document_quality`. `TienSangLocVayApplicationTests` cập nhật danh sách phiên bản Flyway thêm `11`.
  - Hợp đồng: `openapi.yaml` (enum `qualityStatus`, mô tả upload), `fe/src/api/types.ts`. Mock (`handlers/applications.ts`, `isSpecimenName`) trả `SPECIMEN` khi tên tệp (không phân biệt hoa thường) chứa “specimen” hoặc kết thúc bằng `-mau.<đuôi>` / `_mau.<đuôi>`; không khớp “mau” ở chỗ khác (`mau.jpg`, `cccd-mau-1.jpg`, `mauxanh.jpg` không bị nhận nhầm). Mock không có chế độ cho phép và không đọc nội dung ảnh; cách thử ghi ở `fe/README.md`. `DocumentCard` không cần sửa: đã hiện `qualityMessage` trong `Alert` cho mọi `REJECTED_QUALITY`.
  - `quy-tac-tinh-toan.md` mục 9 và 5.4 ở tài liệu này đã cập nhật.
- **Test**: `DocumentUnitTest.ocrPolicy_specimenDependsOnConfig` (cả `false` và `true`, kèm thứ tự với `BLURRY`/`WRONG_TYPE`); `GeminiOcrServiceTest` (schema/prompt/`parse` có `isSpecimen`, thiếu thì false); `ApplicationApiTest.upload_specimen_rejectedByDefault` (OCR giả trả `specimen=true` → `SPECIMEN`, không lưu tệp, không ghi nhật ký) và `upload_specimen_acceptedWhenAllowed` (đổi tạm cờ `allowSpecimen` của bean `OcrPolicy` bằng `ReflectionTestUtils` → `UPLOADED`, ghi đúng 1 dòng nhật ký). `OcrPolicyConfigTest` (xem “Ba việc nhỏ” bên dưới).
- **Kết quả e2e `dev` (Gemini thật), DB mới**: `python tests\e2e\full_flow.py --mode real` → `ERRORS []`, 90 s, hồ sơ HS2610-0511 đến `DISBURSED`. Gemini đọc: CCCD họ tên NGUYỄN VĂN MINH, số 001092012345, ngày sinh 15/05/1992, giới tính Nam, nơi thường trú 12 Láng Hạ, Đống Đa, Hà Nội (độ chắc chắn 100); cư trú địa chỉ như trên (99); HĐLĐ đơn vị “Công ty CP Công nghệ Sao Mai (giả lập)”, lương 21.000.000; sao kê ngân hàng “NGÂN HÀNG MẪU”, lương bình quân 18.800.000; hóa đơn bên thụ hưởng “Công ty TNHH Thương mại Hòa Bình (giả lập)”, 162.000.000. Cả 5 giấy tờ đều bị Gemini báo là mẫu và đều được nhận, có 6 dòng nhật ký “Chấp nhận giấy tờ mẫu (môi trường thử nghiệm)” (CCCD v2, cư trú, HĐLĐ, sao kê v1 và v2, hóa đơn). Đối chiếu giấy tờ: tất cả `MATCH`. Cảnh báo: (INFO) ngân hàng trên sao kê “NGÂN HÀNG MẪU” khác “Vietcombank” đã khai; (WARNING) thu nhập sao kê 18.800.000 thấp hơn khai báo 20.000.000 (6%), tính lại DTI sau vay 30,5%, hạn mức an toàn 282.000.000, điểm 85,9, vẫn đạt.
- **Ba việc nhỏ trước khi commit (23/10/2026)**:
  - Mock `isSpecimenName` thu hẹp: chỉ khớp tên chứa `specimen` hoặc kết thúc bằng `-mau.<đuôi>` / `_mau.<đuôi>` (đã thử 9 tên mẫu, `mau.jpg`, `cccd-mau-1.jpg`, `mauxanh.jpg` đều không khớp). Cách thử ghi ở `fe/README.md` (mục “Thử giấy tờ mẫu”), mô tả trong `openapi.yaml` và `quy-tac-tinh-toan.md` mục 9 sửa theo.
  - `OcrPolicyConfigTest`: hai lớp `@Nested` `@SpringBootTest(classes = OcrPolicy.class)` (không cần cơ sở dữ liệu) – một lớp đặt `@TestPropertySource(properties = "app.ocr.allow-specimen=true")` khẳng định bean `OcrPolicy` nhận giấy tờ mẫu và đánh dấu để ghi nhật ký; lớp kia không đặt gì khẳng định mặc định `false` thì từ chối `SPECIMEN`. Không dùng `ReflectionTestUtils`. (`ApplicationApiTest.upload_specimen_acceptedWhenAllowed` vẫn đổi cờ bằng `ReflectionTestUtils` để kiểm tra cả đường lưu DB và nhật ký.)
  - E2E sau thay đổi, DB mới: `--mode real` với `dev,local` → `ERRORS []`; `--mode mock` → `ERRORS []`. Trước đó `dev` (Gemini thật) cũng `ERRORS []` (xem trên).
  - Kiểm tra: `.\mvnw.cmd verify` → 187 test, 0 lỗi, 2 bỏ qua (live Cloudinary/Gemini); `npm run build` đạt; `npx eslint src` 0 lỗi (4 cảnh báo `react-refresh` có sẵn); redocly lint 0 lỗi.
