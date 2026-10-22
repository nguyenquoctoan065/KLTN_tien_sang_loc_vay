# Sửa frontend – đợt góp ý 22/10/2026

Làm lần lượt F1 → F6. Mỗi mục có: vấn đề, việc cần làm (kèm file), thay đổi hợp đồng API (nếu có), tiêu chí hoàn thành.
Các mục F1, F2 đổi hợp đồng API nên làm trước khi viết các module backend tương ứng.

Sau cùng (mục F7): cập nhật kiểm thử e2e, chạy build/lint, chạy lại toàn luồng.

---

## F1. Khách hàng đăng nhập bằng số điện thoại **hoặc email**

**Vấn đề.** Trang `/dang-nhap` chỉ có ô “Số điện thoại”, người dùng không tìm thấy chỗ nhập email.
Dòng “Tài khoản thử: 0901234567 / 123456” cũng hiện cả khi chạy thật.

**Việc cần làm**
- [x] `fe/src/pages/customer/Login.tsx`
  - Với khách hàng: nhãn **“Số điện thoại hoặc email”**, placeholder `Số điện thoại hoặc email`.
  - Kiểm tra: hoặc khớp `^0\d{9}$`, hoặc là email hợp lệ. Thông báo lỗi: “Nhập số điện thoại (10 số) hoặc email”.
  - Trang nội bộ giữ nguyên nhãn “Email”.
  - Dòng “Tài khoản thử …” chỉ hiện khi `import.meta.env.VITE_USE_MOCK === 'true'`, áp dụng cho cả hai trang.
- [x] `fe/src/pages/customer/Register.tsx`: thêm ô **Email (không bắt buộc)** dưới ô số điện thoại, kiểm tra định dạng email.
  Gửi `email` trong body đăng ký.
- [x] Mock `fe/src/mocks/handlers/auth.ts`:
  - Đăng nhập khách hàng: nếu `username` có `@` thì tìm theo email (không phân biệt hoa thường), ngược lại tìm theo số điện thoại.
  - Đăng ký: nếu email đã có thì trả 409 “Email đã được sử dụng”.
  - Thêm trường `email` vào `Account` trong `db.ts`. Khách mẫu 0901234567 có email `khachhang@poc.local`. Tăng `DB_VERSION`.
- [x] Trang “Tài khoản” trong `/cua-toi` (nếu đang hiện thông tin tài khoản) hiển thị email.

**Hợp đồng API**
- `LoginRequest.username`: mô tả “Khách hàng: số điện thoại hoặc email; nội bộ: email”.
- `RegisterRequest`: thêm `email` (string, format email, không bắt buộc). Thêm phản hồi 409.
- `AuthUser`: thêm `email` (nullable).
- `database/V2__seed.sql`: đặt email `khachhang@poc.local` cho khách mẫu. Backend cần unique index
  `lower(email)` trên `customer` (thêm vào V1 nếu backend chưa chạy lần nào, nếu không thì thêm V3).

**Tiêu chí hoàn thành.**
- Đăng nhập được bằng `0901234567` và bằng `KhachHang@poc.local`.
- Đăng ký kèm email đã có thì báo lỗi rõ ràng.
- Khi `VITE_USE_MOCK=false`, không còn dòng tài khoản thử.

---

## F2. Câu hỏi về lịch sử tín dụng – dễ hiểu với người chưa từng vay

**Vấn đề.** Bước 3 của sàng lọc hỏi “Hiện có nợ xấu từ nhóm 3 trở lên”, kèm tooltip “quá hạn từ 91 ngày trở lên
hoặc đã được cơ cấu lại”. Người chưa từng vay không biết mình “ở nhóm nào”. “Cơ cấu lại” là thuật ngữ ngân hàng,
khách không hiểu.

**Thiết kế mới** (`fe/src/pages/customer/ScreeningWizard.tsx`, bước “Thu nhập, nghĩa vụ nợ”):

Thay ô `selfDeclaredBadDebt` bằng hai câu hỏi.

1. **“Bạn đã từng vay ngân hàng, công ty tài chính hoặc dùng thẻ tín dụng chưa?”** Hai lựa chọn: `Chưa từng` | `Đã từng`.
2. Câu này chỉ hiện khi chọn “Đã từng”: **“Trong 5 năm gần đây, có lần nào bạn trả chậm một khoản vay hoặc thẻ tín dụng
   quá 3 tháng, hoặc phải xin ngân hàng giãn, hoãn, giảm số tiền phải trả vì không trả được không?”**
   Ba lựa chọn: `Không` | `Có` | `Không nhớ rõ`.
   - Dòng giải thích nhỏ dưới câu hỏi (không dùng tooltip):
     “Ngân hàng sẽ tra cứu lịch sử tín dụng của bạn tại Trung tâm Thông tin tín dụng Quốc gia (CIC) khi bạn ký hồ sơ ở chi nhánh.
     Bạn cũng có thể tự tra cứu qua ứng dụng CIC Credit Connect.”
   - Không dùng các chữ “nhóm 3”, “nợ xấu nhóm”, “cơ cấu lại” trên giao diện.
3. Nếu khách chưa từng vay thì ẩn luôn phần “Nghĩa vụ trả nợ hằng tháng tại tổ chức tín dụng khác” và xóa danh sách `otherDebts`.

Hai câu hỏi gộp thành một trường mới `creditHistory`:

| Câu 1 | Câu 2 | `creditHistory` |
|---|---|---|
| Chưa từng | – | `NEVER_BORROWED` |
| Đã từng | Không | `ON_TIME` |
| Đã từng | Có | `LATE_OVER_90` |
| Đã từng | Không nhớ rõ | `UNSURE` |

**Bộ máy tính** (`fe/src/mocks/engine.ts`, cùng các chỗ khác có dùng `selfDeclaredBadDebt`, tìm bằng grep):

| `creditHistory` | Kết quả sàng lọc |
|---|---|
| `LATE_OVER_90` | Điều kiện loại trừ (thay cho `selfDeclaredBadDebt = true`). Lý do: “Bạn cho biết từng trả chậm quá 3 tháng hoặc phải xin giãn, hoãn nợ trong 5 năm gần đây.” Hướng cải thiện: “Tất toán khoản nợ đó và chờ thông tin tín dụng được cập nhật; bạn có thể tự kiểm tra trên ứng dụng CIC Credit Connect trước khi đề nghị vay.” |
| `UNSURE` | Không loại trừ. Trang kết quả hiện Alert vàng: “Bạn chưa chắc về lịch sử trả nợ. Ngân hàng sẽ tra cứu CIC khi bạn ký hồ sơ; nếu có khoản trả chậm quá 3 tháng, hồ sơ có thể không được duyệt.” |
| `NEVER_BORROWED` | Không loại trừ. Trang kết quả hiện ghi chú xanh: “Bạn chưa có lịch sử vay – điều này không ảnh hưởng tới kết quả sàng lọc.” |
| `ON_TIME` | Như cũ. |

Để trang kết quả biết hiện ghi chú nào, `ScreeningResponse` thêm mảng `notes: { type: 'INFO' \| 'WARNING', text: string }[]`.

**Hợp đồng API và dữ liệu**
- `ScreeningRequest`: bỏ `selfDeclaredBadDebt`, thêm
  `creditHistory: 'NEVER_BORROWED' | 'ON_TIME' | 'LATE_OVER_90' | 'UNSURE'` (bắt buộc).
  Sửa ở `openapi.yaml`, `types.ts`, mock dữ liệu mẫu (`handlers/admin.ts` bộ hồ sơ chạy thử, `db.ts` nếu có).
- `ScreeningResponse`: thêm `notes` như mô tả ở trên.
- `database/V1__init.sql`, bảng `screening`:
  - Thay `self_declared_bad_debt boolean` bằng `credit_history varchar(20) NOT NULL CHECK (credit_history IN ('NEVER_BORROWED','ON_TIME','LATE_OVER_90','UNSURE'))`.
  - Sửa COMMENT.
  - Sửa V2 nếu có dữ liệu mẫu bảng này.
- `docs/quy-tac-tinh-toan.md` mục 4: thay dòng nợ xấu bằng bảng ở trên. Ghi chú để đối chiếu với báo cáo đồ án:
  `LATE_OVER_90` tương ứng “nợ nhóm 3 trở lên hoặc nợ đã cơ cấu”.

- [x] F2 hoàn thành (wizard, engine, notes ở trang kết quả, openapi, V1, quy-tắc-tính-toán).

**Tiêu chí hoàn thành**
- Chọn “Chưa từng” thì không hiện câu 2 và phần nợ khác.
- Chọn “Có” thì cả hai gói báo không đạt, kèm lý do bằng lời thường.
- Chọn “Không nhớ rõ” thì vẫn có gợi ý, kèm cảnh báo vàng.
- Grep toàn bộ `fe/src` không còn `selfDeclaredBadDebt`, “nhóm 3”, “cơ cấu lại”.

---

## F3. “Sàng lọc lại” phải giữ thông tin đã nhập

**Vấn đề.** Trên trang kết quả, bấm “Sàng lọc lại” sẽ mở form trống, khách phải nhập lại từ đầu.

**Việc cần làm**
- [x] `fe/src/pages/customer/ScreeningResult.tsx`: nút “Sàng lọc lại” chuyển tới `/sang-loc?tu=<id lượt sàng lọc>`.
  Làm tương tự cho nút “Xem” ở tab “Kết quả sàng lọc” của `/cua-toi`: thêm nút “Sàng lọc lại với thông tin này”.
- [x] `fe/src/pages/customer/ScreeningWizard.tsx`:
  - Có `?tu=` thì gọi `screeningApi.get(id)` và lấy `input` để điền sẵn toàn bộ form:
    - `dob` chuyển sang `dayjs`;
    - `creditHistory` tách lại thành 2 câu hỏi;
    - `consentA` coi như đã đồng ý.
  - Khi điền sẵn, mở thẳng bước 2 “Thông tin cá nhân”. Khách bấm “Quay lại” vẫn về được bước đồng ý.
  - Lưu nháp: mỗi khi giá trị form đổi, ghi vào `sessionStorage` khóa `screening-draft` (bọc try/catch).
    Khi vào `/sang-loc` không có `?tu=` mà có nháp thì điền lại và hiện Alert: “Đã khôi phục thông tin bạn nhập trước đó”,
    kèm nút “Nhập mới” để xóa nháp.
  - Gửi sàng lọc thành công thì giữ nháp. Lần sau sửa vài ô là được, không phải nhập lại.
  - Nút “Nhập mới” xóa nháp và reset form.
- Không cần đổi API: `GET /screenings/{id}` đã trả `input`.

**Tiêu chí hoàn thành.**
- Hoàn thành một lượt sàng lọc, bấm “Sàng lọc lại”: mọi ô đã có dữ liệu cũ, kể cả danh sách nợ khác và câu hỏi lịch sử tín dụng.
- Đổi số tiền mong muốn rồi gửi lại được ngay.
- F5 giữa chừng không mất dữ liệu.

---

## F4. Bỏ mã biểu mẫu (MB01…MB16) khỏi chữ hiển thị

**Vấn đề.** Giao diện hiện mã tài liệu nội bộ, ví dụ “Đồng ý lưu trữ giấy tờ và lập hồ sơ – **MB02 phần (b)**”,
và việc này xảy ra ở rất nhiều chỗ. Khách hàng không hiểu các mã này.

**Việc cần làm.** [x] Xóa mã khỏi mọi chữ hiển thị: tiêu đề, nhãn, tab, toast, mô tả. Áp dụng cho cả khu vực khách hàng
lẫn khu vực nội bộ để thống nhất. Giữ mã trong comment code và trong `docs/`.

Danh sách đã biết (chạy `grep -rnE "MB0[0-9]|MB1[0-9]" fe/src --include=*.tsx` để rà sót):

| File | Hiện tại | Đổi thành |
|---|---|---|
| pages/customer/CreateApplication.tsx | `2. Đồng ý lưu trữ giấy tờ và lập hồ sơ – MB02 phần (b)` | `2. Đồng ý lưu trữ giấy tờ và lập hồ sơ` |
| pages/customer/ScreeningWizard.tsx | `Phiếu thông tin sàng lọc (MB01). …` | bỏ cụm `Phiếu thông tin sàng lọc (MB01).` |
| pages/customer/ScreeningResult.tsx | `MB03 · Lượt sàng lọc #…` | `Lượt sàng lọc #…` |
| pages/customer/Simulation.tsx | `MB04 – Kế hoạch trả nợ dự kiến…`, toast `Đã lưu phương án vay (MB07)` | `Kế hoạch trả nợ dự kiến…`, `Đã lưu phương án vay` |
| pages/customer/PlanDetail.tsx | `MB07 · Phương án #…`, `Danh mục đầy đủ (MB05)…` | `Phương án #…`, `Danh mục đầy đủ…` |
| pages/customer/ApplicationDetail.tsx | `(MB05)`, tab `Phiếu tóm tắt (MB08)`, `Kết quả xét duyệt (MB16)` | bỏ phần trong ngoặc |
| pages/customer/MyData.tsx | `Các lần đồng ý xử lý dữ liệu (MB02)` | `Các lần đồng ý xử lý dữ liệu` |
| components/app/SummarySlip.tsx | `PHIẾU TÓM TẮT HỒ SƠ VAY (MB08)` | `PHIẾU TÓM TẮT HỒ SƠ VAY` |
| components/app/DocumentCard.tsx | `Thông tin trích xuất (MB06)` | `Thông tin đọc từ giấy tờ` |
| components/app/RequestFormPanel.tsx | `Giấy đề nghị vay vốn (MB15)` | `Giấy đề nghị vay vốn` |
| components/app/StatusAlert.tsx | `Phiếu tóm tắt hồ sơ (MB08)` | `Phiếu tóm tắt hồ sơ` |
| pages/internal/* | `(MB09)`, `(MB11)`, `(MB12)`, `(MB13)`, `(MB16)`, tab `Báo cáo MB14`… | bỏ ngoặc. `Báo cáo MB14` đổi thành `Báo cáo hiệu chỉnh mô hình`; tên file CSV đổi thành `bao-cao-hieu-chinh-<tháng>.csv` |

Sau khi đổi tên tab, sửa selector trong `tests/e2e/full_flow.py`: `Phiếu tóm tắt`, `Kết quả xét duyệt`, `Báo cáo MB14`…

**Tiêu chí hoàn thành.** Lệnh grep ở trên chỉ còn kết quả nằm trong comment (`//`, `/** */`) hoặc hằng số không hiển thị.
Nếu `CONSENT_VERSION = 'MB02-1.0'` có hiện trên trang dữ liệu cá nhân thì đổi thành `DK-1.0`. Đổi luôn trong mock
và V2 cho khớp.

---

## F5. Bỏ chữ “thử nghiệm/đồ án” khỏi giao diện khách hàng

**Vấn đề.** Chân trang khách hàng ghi “Hệ thống web thử nghiệm phục vụ đồ án tốt nghiệp… · Đăng nhập nội bộ”.
Dòng này không nên xuất hiện ở giao diện khách hàng.

**Việc cần làm**
- [x] `fe/src/layouts/CustomerLayout.tsx`:
  - Xóa toàn bộ `<Footer>…</Footer>`. Nếu muốn giữ chân trang thì chỉ để tên hệ thống và năm, không có link nội bộ.
  - Xóa luôn `<Tag>PoC</Tag>` cạnh tên hệ thống ở header.
- [x] Nhân viên và quản trị vào thẳng `/noi-bo/dang-nhap`; ghi đường dẫn này trong README.
- [x] Vẫn giữ lưu ý pháp lý “kết quả chỉ để tham khảo, quyết định thuộc về ngân hàng” (`components/Disclaimer.tsx`)
  trên trang kết quả sàng lọc, mô phỏng và phương án. Đây là nội dung nghiệp vụ, không phải chữ “thử nghiệm”.

**Tiêu chí hoàn thành.** Mọi trang khách hàng không còn chữ “thử nghiệm”, “đồ án”, “PoC”, “Đăng nhập nội bộ”.
Kiểm tra bằng `grep -rn "thử nghiệm\|đồ án\|PoC" fe/src/layouts fe/src/pages/customer fe/src/components`.

---

## F6. Rà chữ khó hiểu còn lại (nên làm) [x]

Rà các tooltip và nhãn trong `ScreeningWizard.tsx`, `ScreeningResult.tsx`, `Simulation.tsx`, `PlanDetail.tsx`.
Thuật ngữ nào khách không hiểu thì thêm câu giải thích bằng lời thường. Ví dụ:
- “DTI”: “Tỷ lệ tiền trả nợ hằng tháng trên thu nhập”.
- “Hạn mức an toàn”: “Số tiền tối đa bạn có thể vay mà vẫn đủ chi tiêu sinh hoạt”.
- “Lãi suất tham chiếu”: “Lãi suất để ước tính, lãi suất thực tế do ngân hàng quyết định”.

Có thể hiện chữ đầy đủ ở lần đầu xuất hiện và giữ chữ viết tắt ở bảng.

---

## F7. Kiểm tra cuối

- [ ] `cd fe && npm run build && npx eslint src`: 0 lỗi.
- [ ] `npx -y @redocly/cli@1.25.11 lint docs/openapi.yaml`: 0 lỗi.
- [ ] Sửa `tests/e2e/full_flow.py` theo F2 (chọn “Đã từng” → “Không”), F4 (tên tab mới), F1 (đăng nhập bằng email ở lần đăng nhập thứ hai).
  Chạy `npm run dev`, sau đó chạy `python tests/e2e/full_flow.py`; kết quả in ra phải là `ERRORS []`.
- [ ] Xem ảnh trong `tests/e2e/shots/` để kiểm tra bằng mắt các trang đã sửa.
- [x] Thêm thử nghiệm tay F3: sàng lọc, “Sàng lọc lại”, kiểm tra dữ liệu còn nguyên.
