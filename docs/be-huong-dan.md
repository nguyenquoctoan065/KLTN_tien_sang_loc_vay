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
    │   ├── application.yml, application-dev.yml
    │   └── db/migration/  V1__init.sql, V2__seed.sql (chép từ ../database), V3__app_support.sql (B1), V4__sua_dinh_dang_sao_ke.sql (B2), V5__… (B4), V6__… (mỗi giai đoạn cần thêm bảng/cột một file mới); db/dev/V1000__dev_history.sql
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
    các cột còn lại thêm bằng migration của giai đoạn B4 (V5 trở đi) (`storage_resource_type`, `storage_version`).
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

### 5.7 Cơ sở dữ liệu bổ sung (V3, V5, V6…; V4 đã dùng ở B2)
Mỗi giai đoạn tự so DTO trong openapi.yaml với lược đồ hiện có và thêm phần thiếu vào **một file migration mới theo thứ tự**
(`V5__…`, `V6__…`); không sửa file đã chạy. `V3__app_support.sql` (B1) đã có `auth_session`, `otp_code` và unique index `lower(email)`. Còn cần:
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
- [ ] `engine/` + fixtures (5.5). `/screenings`, `/screenings/{id}`, `/screenings/{id}/save`, `/me/screenings` (consent A).
- [ ] `/simulations`, `/plans`, `/plans/{id}`, `/plans/{id}/confirm`, `/me/plans`.
- [ ] Bật FE với backend thật: chạy luồng sàng lọc → phương án.

### B4. Hồ sơ vay (QT03, 4.1–4.2)
- [ ] Migration mới (`V5__…`) cho phần còn lại của 5.7. `/applications` (tạo, consent B, sinh danh mục giấy tờ theo gói), `/me/applications`, `/applications/{id}`.
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
  (JPQL/native) thay vì nạp hết vào bộ nhớ; so kết quả với mock trên cùng dữ liệu `V1000__dev_history.sql`.
- [ ] `db/dev/V1000__dev_history.sql` (5.8).

### B9. Ghép nối và kiểm thử toàn luồng
- [ ] `fe/.env.development`: `VITE_USE_MOCK=false`. Chạy `python tests/e2e/full_flow.py` → `ERRORS []`.
- [ ] So từng endpoint với openapi: tải `/api/v3/api-docs` của springdoc, viết script so danh sách path+method với `docs/openapi.yaml` (không thiếu, không thừa).
- [ ] Cập nhật `README.md`: cách chạy backend, biến môi trường, tài khoản.

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
    folder: tien-sang-loc-vay/dev
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
- **Đã chốt (23/10/2026)**: `allowedFormats` của “Sao kê tài khoản nhận lương 03 tháng” theo mock là `PDF,JPG,PNG` (V2 ghi `PDF`). Thêm `V4__sua_dinh_dang_sao_ke.sql` cập nhật `document_type`, không sửa V2; migration của B4 là `V5__…`. Giá trị dùng khi kiểm tra tệp tải lên (B4) và ở `/admin/document-types` (B7).
- **Nhắc B7**: sau khi giao dịch kích hoạt phiên bản tham số commit, phải gọi `ActivePolicy.evict()`, nếu không backend vẫn dùng bản đã nạp.
- **Kiểm tra**: `.\mvnw.cmd verify` → 53 test pass (13 test mới `CatalogApiTest`: 4 endpoint đối chiếu từng trường với dữ liệu mẫu/mock, ẩn bản ghi `INACTIVE` của gói/bán chéo/chi nhánh, checklist TD/TC/mã lạ, `ActivePolicy` nạp đúng phiên bản 2026.10, tổng trọng số mỗi gói = 100, thiếu tham số báo rõ, `evict()` nạp lại). Chạy `spring-boot:run -Dspring-boot.run.profiles=dev` trên PostgreSQL Docker và FE `VITE_USE_MOCK=false` (biến môi trường, **chưa đổi** `fe/.env.development`): bằng Playwright trang chủ `/` gọi `GET /api/products` (không qua service worker MSW) và hiện hai thẻ gói vay, không lỗi trang. Để chắc chắn dữ liệu từ DB, tạm đổi tên gói TD trong DB thành “Vay tiêu dùng (kiểm tra B2)”, trang chủ hiện đúng tên đó, rồi đã trả lại tên cũ. Mô tả điều kiện trong thẻ (tuổi, hạn mức, kỳ hạn) do FE tự soạn, chưa đọc từ tham số.
