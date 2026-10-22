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

Không thêm thư viện khác khi chưa hỏi.

## 3. Cấu trúc thư mục

```
be/
├── pom.xml, mvnw, .mvn/
├── CLAUDE.md
└── src/
    ├── main/java/vn/loanpoc/
    │   ├── LoanPocApplication.java
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
    │   ├── application.yml, application-dev.yml
    │   └── db/migration/  V1__init.sql, V2__seed.sql (chép từ ../database), V3__…, V4__dev_history.sql
    └── test/java/vn/loanpoc/…  (+ test/resources/engine-fixtures/*.json)
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
  "folder", "loan-poc/<môi trường>/app-<applicationId>", "public_id", "doc-<documentId>-v<version>", "overwrite", true))`.
  - Lưu vào DB: `public_id`, `resource_type`, `format`, `version`, `bytes`. Cột `storage_path` giữ `public_id`;
    các cột còn lại thêm vào V3 (`storage_resource_type`, `storage_version`).
  - Không lưu `secure_url` công khai.
- **Xem lại tệp**: `previewUrl` trong DTO là URL ký (signed) có hạn 10 phút. Tạo bằng
  `cloudinary.url().type("authenticated").resourceType(rt).signed(true).version(v)…` cùng tham số hết hạn,
  hoặc dùng `cloudinary.privateDownload(publicId, format, {type: authenticated, expires_at})`.
  - Ảnh xem trước dùng biến đổi `w_480,c_limit,q_auto,f_auto`.
  - PDF: trang đầu dùng `pg_1` để làm ảnh xem trước; nút “Mở tệp” dẫn tới URL tải PDF đã ký.
  - Mỗi lần trả hồ sơ thì sinh URL mới. FE dùng thẳng URL này trong thẻ `<img>`, nên không cần endpoint `/files` riêng.
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
- **Khóa**: biến môi trường `GEMINI_API_KEY` (tạo ở Google AI Studio). Tạo một bean dùng chung:
  `Client.builder().apiKey(env.getRequiredProperty("GEMINI_API_KEY")).build()`.
- **Model**: `app.ocr.model`, mặc định `gemini-3.5-flash-lite` (rẻ và nhanh). Nếu đọc sai nhiều thì đổi sang `gemini-3.8-flash`.
  Tên model đổi theo thời gian, nên chỉ để trong cấu hình, không viết cứng trong code.
- **Gọi API**: một lần `client.models.generateContent(model, content, config)`.
  - `content` gồm `Part.fromBytes(data, mimeType)` (ảnh JPG/PNG hoặc PDF) và `Part.fromText(prompt)`.
  - `config` có `responseMimeType("application/json")`, `responseSchema(...)` và `temperature(0)`.
  - `responseSchema` sinh theo loại giấy tờ, dùng các `field_key` ở mục 9 của quy tắc:
    ```json
    { "isReadable": bool, "documentMatchesType": bool, "issue": string|null,
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
    chỉ kiểm tra cấu trúc kết quả.

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

### 5.7 Cơ sở dữ liệu bổ sung (V3)
Trước giai đoạn B2, so từng DTO trong openapi.yaml với V1 và gom phần thiếu vào **`V3__app_support.sql`**. Đã biết cần:
- `auth_session`, `otp_code`, `notification` (người nhận: customer/staff/branch, title, body, link, read_at),
  `application_status_history` (application_id, from_status, to_status, actor, note, created_at), `scheduled_job`.
- `loan_application`: `cancel_reason`, các cột còn thiếu so với `Application` DTO (ví dụ thông tin cá nhân khai cho hồ sơ nếu không lưu ở `customer`).
- `application_document`: `status`, `quality_status`, `quality_message`, `issued_date`, `version_no` nếu V1 chưa có.
- Unique index `lower(email)` trên `customer`; cột `credit_history` của `screening` (nếu chưa sửa thẳng V1 theo F2).
Ghi rõ trong comment SQL mỗi cột phục vụ DTO nào.

### 5.8 Dữ liệu lịch sử cho báo cáo
Màn hình thống kê/độ chính xác/MB14 cần dữ liệu nhiều tháng. Viết `V4__dev_history.sql` (chỉ chạy profile dev qua
`spring.flyway.locations` riêng `db/dev`) sinh ~90 hồ sơ 6 tháng ở 3 chi nhánh, bám theo hàm `historical()` trong `fe/src/mocks/db.ts`.

## 6. Giai đoạn

Mỗi giai đoạn hoàn thành khi: `./mvnw verify` pass (có test MockMvc + Testcontainers cho từng endpoint của giai đoạn),
các trang FE tương ứng chạy được với `VITE_USE_MOCK=false`, và thông báo lỗi giống mock.

### B0. Khung dự án
- [ ] Tạo Spring Boot (Maven wrapper, Java 21), các dependency mục 2, `application.yml` (datasource tới docker-compose, `server.servlet.context-path=/api`).
- [ ] Flyway: chép `database/V1__init.sql`, `V2__seed.sql`; `spring.jpa.hibernate.ddl-auto=validate`.
  Sửa docker-compose: bỏ mount `./database` vào initdb (Flyway sẽ tạo bảng) – hỏi người dùng trước khi sửa.
- [ ] `GlobalExceptionHandler`, `ApiException`, `CurrentUser`, SecurityConfig (mọi `/api/**` cần đăng nhập trừ các endpoint `security: []` trong openapi).
- [ ] `GET /api/actuator/health` (spring-boot-starter-actuator). Test khởi động với Testcontainers.

### B1. Tài khoản (1.1–1.4)
- [ ] V3 phần `auth_session`, `otp_code`. `/auth/login`, `/auth/logout`, `/auth/otp`, `/auth/register`, `/auth/me`, `/auth/change-password`.
- [ ] Test: đăng nhập đúng/sai/khóa, OTP sai, đăng ký trùng số/email, đổi mật khẩu thu hồi phiên cũ.

### B2. Danh mục và tham số đọc
- [ ] Entity: loan_product, cross_sell_product, branch, document_type, product_document_type, param_version, policy_param, scoring_weight.
- [ ] `/products`, `/products/{code}/checklist`, `/cross-sell-products`, `/branches`. `ActivePolicy` lấy phiên bản đang hiệu lực.

### B3. Sàng lọc, mô phỏng, phương án (QT01–QT02)
- [ ] `engine/` + fixtures (5.5). `/screenings`, `/screenings/{id}`, `/screenings/{id}/save`, `/me/screenings` (consent A).
- [ ] `/simulations`, `/plans`, `/plans/{id}`, `/plans/{id}/confirm`, `/me/plans`.
- [ ] Bật FE với backend thật: chạy luồng sàng lọc → phương án.

### B4. Hồ sơ vay (QT03, 4.1–4.2)
- [ ] V3 phần còn lại. `/applications` (tạo, consent B, sinh danh mục giấy tờ theo gói), `/me/applications`, `/applications/{id}`.
- [ ] `/personal`, upload `/documents/{docId}` (QualityChecker → StorageService → OcrService), `/fields`, `CrossCheckService` (mục 10),
  `/plan`, `/request-form`, `/submit` (OTP, khóa nội dung mục 11, QR, hạn), `/cancel`.
- [ ] `NotificationService` + `/notifications`, `/notifications/{id}/read`, `/notifications/read-all`; lịch sử trạng thái.
- [ ] `CloudinaryStorageService` + URL ký có hạn (5.3), `GeminiOcrService` (5.4); test chạy với `storage.provider=local`, `ocr.provider=fake`.

### B5. Chi nhánh (QT04–QT05)
- [ ] `/staff/applications` (lọc trạng thái nhiều giá trị, từ khóa, chi nhánh của nhân viên), `/staff/applications/by-qr/{token}`, `/staff/summary`.
- [ ] `/verification` (MATCH → VERIFIED, có MISMATCH/MISSING → NEED_MORE, bắt buộc lý do), `/decision`, `/disbursement`.

### B6. Quyền dữ liệu (QT06)
- [ ] `/me/data` (dữ liệu, các lần đồng ý, nhật ký truy cập), `/me/consents/withdraw` (mục 14, OTP).

### B7. Quản trị (QT07, 1.6, 7.x)
- [ ] Phiên bản tham số: list/get/clone (kèm `changes`)/update/delete nháp/test (bộ hồ sơ mẫu trong `handlers/admin.ts`)/activate.
- [ ] `/admin/staff` + lock/unlock/reset-password; `/admin/branches` + deactivate (chuyển hồ sơ đang mở sang `transferToBranchId`)/activate.
- [ ] `/admin/catalog`, `/admin/products/{id}`, `/admin/document-types`, `/admin/product-documents`, `/admin/cross-sell-products`.
- [ ] `/admin/jobs`, `/admin/jobs/{code}/run` + `@Scheduled`; `/admin/logs` (lọc, phân trang).

### B8. Báo cáo (QT09)
- [ ] `/admin/stats` (phễu, theo tháng/trạng thái/chi nhánh/gói), `/admin/model-accuracy` (mục 15), `/admin/reports/mb14` – viết bằng truy vấn SQL
  (JPQL/native) thay vì nạp hết vào bộ nhớ; so kết quả với mock trên cùng dữ liệu V4.
- [ ] `V4__dev_history.sql` (5.8).

### B9. Ghép nối và kiểm thử toàn luồng
- [ ] `fe/.env.development`: `VITE_USE_MOCK=false`. Chạy `python tests/e2e/full_flow.py` → `ERRORS []`.
- [ ] So từng endpoint với openapi: tải `/api/v3/api-docs` của springdoc, viết script so danh sách path+method với `docs/openapi.yaml` (không thiếu, không thừa).
- [ ] Cập nhật `README.md`: cách chạy backend, biến môi trường, tài khoản.

## 7. Cấu hình mẫu (`application.yml`)

```yaml
server: { port: 8080, servlet: { context-path: /api } }
spring:
  datasource: { url: jdbc:postgresql://localhost:5432/loan_prescreening, username: loan, password: loan123 }
  jpa: { hibernate: { ddl-auto: validate }, open-in-view: false, properties: { hibernate.jdbc.time_zone: Asia/Ho_Chi_Minh } }
  flyway: { locations: classpath:db/migration }
  servlet: { multipart: { max-file-size: 15MB, max-request-size: 16MB } }
  jackson: { time-zone: Asia/Ho_Chi_Minh, default-property-inclusion: always }
app:
  session-hours: 8
  otp: { fixed-code: ""}          # dev: 123456
  storage:
    provider: cloudinary          # cloudinary | local
    folder: loan-poc/dev
    signed-url-minutes: 10
    local-dir: ./data/uploads
  ocr:
    provider: gemini              # gemini | fake
    model: gemini-3.5-flash-lite
    timeout-seconds: 30
# Bí mật chỉ đặt qua biến môi trường (file be/.env đã thêm vào .gitignore, hoặc Run Configuration của IDE):
#   CLOUDINARY_URL=cloudinary://<api_key>:<api_secret>@<cloud_name>
#   GEMINI_API_KEY=<khóa từ Google AI Studio>
  jobs:
    expire-documents: "0 0 1 * * *"
    purge-retention: "0 0 2 1 * *"
    remind-drafts: "0 0 9 * * *"
```
`application-dev.yml`: `app.otp.fixed-code: "123456"`. Profile `test`: `app.storage.provider: local`, `app.ocr.provider: fake`, `spring.flyway.locations: classpath:db/migration,classpath:db/dev`.

## 8. Gợi ý khi dùng Claude Code

- Mở **thư mục gốc dự án** (chứa `fe/`, `be/`, `docs/`) để Claude Code đọc được cả mock lẫn tài liệu.
- Mỗi phiên một giai đoạn: “Đọc docs/be-huong-dan.md, làm giai đoạn B3”. Dùng chế độ lập kế hoạch (plan mode) trước khi viết code cho B4, B7.
- Yêu cầu Claude chạy `./mvnw verify` và đối chiếu với mock trước khi báo xong.

## 9. Ghi chú tiến độ
(Claude Code ghi lại tại đây: quyết định đã chốt với người dùng, việc còn tồn đọng.)
