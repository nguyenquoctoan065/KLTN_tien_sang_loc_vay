# Quy tắc tính toán của bộ máy tiền sàng lọc

Tài liệu này là đặc tả để backend Java cài đặt lại. Bản tham chiếu hiện chạy ở frontend: `fe/src/mocks/engine.ts`
(cùng công thức, cùng tên tham số). Mọi ngưỡng đọc từ **phiên bản tham số đang áp dụng** (`param_version.status = 'ACTIVE'`),
không ghi cứng trong mã. Căn cứ: biên bản 01/2026/BBXN-ABBANK-ĐATN và mục 1.x, 2.2, 2.4 của báo cáo.

## 1. Tham số (MB11 – bảng `policy_param`)

| Mã | Phạm vi | Ý nghĩa | Giá trị mẫu |
|---|---|---|---|
| `DTI_MAX` | chung | Tỷ lệ DTI tối đa (65–80%) | 70 % |
| `LIVING_COST_BASE` | chung | Chi phí sinh hoạt định mức của người vay | 4.000.000 đ |
| `LIVING_COST_PER_DEPENDENT` | chung | Chi phí sinh hoạt mỗi người phụ thuộc | 2.000.000 đ |
| `RETIRE_AGE_MALE` / `RETIRE_AGE_FEMALE` | chung | Tuổi nghỉ hưu | 62 / 60 |
| `ROOM_FACTOR` | chung | Hệ số theo room tín dụng, tỷ trọng dư nợ tín chấp | 100 % |
| `MAX_RATE_DISCOUNT` | chung | Mức giảm lãi tối đa từ bán chéo | 2 %/năm |
| `DOC_VALIDITY_DAYS`, `RETENTION_MONTHS` | chung | Hiệu lực giấy tờ, thời hạn lưu trữ (dùng ở QT03, QT08) | 30 ngày, 12 tháng |
| `MIN_AGE` | từng gói | Tuổi tối thiểu | TD 22, TC 19 |
| `INCOME_MULTIPLIER` | TD | Hệ số hạn mức theo thu nhập | 15 |
| `MAX_AMOUNT` | từng gói | Trần hạn mức | TD 500 tr, TC 200 tr |
| `MIN_AMOUNT` | từng gói | Số tiền vay tối thiểu | 10 tr |
| `MAX_TERM`, `MIN_TERM` | từng gói | Kỳ hạn tối đa, tối thiểu (tháng) | TD 60/6, TC 12/12 |
| `REF_RATE` | từng gói | Lãi suất tham chiếu (%/năm) | TD 12,5; TC 18 |

Giá trị mẫu chỉ để chạy thử; số liệu thực do ngân hàng cấu hình (biên bản mục V.2).

## 2. Đại lượng cơ bản

- Tuổi = số năm tròn tính từ ngày sinh tới hôm nay.
- Số tháng tới tuổi nghỉ hưu `L` = tháng từ hôm nay tới ngày sinh + tuổi nghỉ hưu (theo giới tính).
- Nghĩa vụ nợ hiện có `D` = tổng `monthlyPayment` của các khoản nợ khác.
- Chi phí sinh hoạt `C` = `LIVING_COST_BASE` + `LIVING_COST_PER_DEPENDENT` × số người phụ thuộc.
- Thu nhập khả dụng `K` = thu nhập `I` − `D` − `C`.
- DTI hiện tại = `D / I` × 100.

## 3. Hệ số nghĩa vụ trên 1 đồng vốn vay `f`

- Vay tiêu dùng (gốc trả đều, lãi trên dư nợ giảm dần), kỳ hạn `n`, lãi suất năm `r`: lấy kỳ trả đầu (lớn nhất)
  `f = 1/n + r/12`.
- Thấu chi (lãi hằng tháng, gốc cuối kỳ): quy đổi thận trọng như trả dần trong 12 tháng `f = r/12 + 1/12`.

Nghĩa vụ hằng tháng của khoản vay `A` là `A × f`. DTI sau vay = `(D + A × f) / I` × 100.

## 4. Điều kiện loại trừ (bước 3) – trả lý do và hướng cải thiện

Một gói **không đạt** khi có bất kỳ điều kiện nào:
1. Quốc tịch khác Việt Nam.
2. Tuổi < `MIN_AGE` của gói.
3. `L` < kỳ hạn tối thiểu (tuổi đáo hạn vượt tuổi nghỉ hưu). Với vay tiêu dùng, kỳ hạn dùng để tính được rút về `min(kỳ hạn mong muốn, L)`.
4. Lịch sử tín dụng tự khai `creditHistory = LATE_OVER_90` (từng trả chậm quá 3 tháng hoặc phải xin giãn, hoãn nợ trong 5 năm gần đây).
   Lý do: “Bạn cho biết từng trả chậm quá 3 tháng hoặc phải xin giãn, hoãn nợ trong 5 năm gần đây.” Hướng cải thiện: “Tất toán khoản nợ đó và chờ thông tin tín dụng được cập nhật; bạn có thể tự kiểm tra trên ứng dụng CIC Credit Connect trước khi đề nghị vay.”
5. Thu nhập lương chuyển khoản = 0.
6. Mục đích “Kinh doanh” (cả hai gói); vay tiêu dùng không nhận mục đích “Chi tiêu cá nhân linh hoạt” (cần chứng từ cụ thể).
7. `K` ≤ 0, hoặc hạn mức an toàn (mục 5) < `MIN_AMOUNT`.

### Lịch sử tín dụng tự khai (`creditHistory`)

Gộp từ hai câu hỏi: “đã từng vay/dùng thẻ tín dụng chưa?” và “trong 5 năm gần đây có trả chậm quá 3 tháng hoặc phải xin giãn, hoãn, giảm nợ không?”.

| `creditHistory` | Kết quả sàng lọc |
|---|---|
| `LATE_OVER_90` | Điều kiện loại trừ số 4 (cả hai gói). Tương ứng “nợ nhóm 3 trở lên hoặc nợ đã cơ cấu” trong báo cáo đồ án. |
| `UNSURE` | Không loại trừ. `notes` thêm WARNING: “Bạn chưa chắc về lịch sử trả nợ. Ngân hàng sẽ tra cứu CIC khi bạn ký hồ sơ; nếu có khoản trả chậm quá 3 tháng, hồ sơ có thể không được duyệt.” |
| `NEVER_BORROWED` | Không loại trừ. `notes` thêm INFO: “Bạn chưa có lịch sử vay – điều này không ảnh hưởng tới kết quả sàng lọc.” Giao diện ẩn và bỏ qua phần nghĩa vụ nợ khác (`otherDebts` rỗng). |
| `ON_TIME` | Không loại trừ, không có ghi chú. |

## 5. Hạn mức tối đa an toàn

```
byDti        = (DTI_MAX/100 × I − D) / f
byDisposable = K / f
byMultiplier = INCOME_MULTIPLIER × I      (chỉ vay tiêu dùng)
maxSafe      = floor_triệu( min(byDti, byDisposable, byMultiplier, MAX_AMOUNT) × ROOM_FACTOR/100 )
```

## 6. Điểm tương thích (MB12 – bảng `scoring_weight`)

Mỗi tiêu chí cho điểm 0–100, sau đó lấy trung bình có trọng số theo ma trận của gói, cộng điểm bán chéo, giới hạn 0–100.

| Tiêu chí | Điểm |
|---|---|
| `DTI` | DTI sau vay ≤ 40% → 100; ≥ `DTI_MAX` → 0; ở giữa nội suy tuyến tính |
| `AMOUNT_RATIO` | tỷ lệ số tiền / hạn mức an toàn: ≤ 0,5 → 100; 0,5–1 → giảm tuyến tính về 60; > 1 → giảm tiếp 60 điểm cho mỗi 1,0 vượt |
| `SALARY_BANK` | nhận lương qua ABBANK → 100, ngân hàng khác → 50 |
| `STAFF` | CBNV ABBANK → 100, không → 40 |
| `TERM_TO_RETIRE` | `L` ≥ 3 × kỳ hạn → 100; `L` ≤ kỳ hạn → 0; ở giữa nội suy |

Trọng số mẫu: TD 35/25/15/10/15, TC 30/20/25/20/5 (tổng mỗi gói = 100%).
Mức hiển thị: ≥ 75 tương thích cao, 55–75 trung bình, < 55 thấp. Các gói đạt được xếp hạng theo điểm (bước 8).

## 7. Mô phỏng và đề xuất điều chỉnh ngược (bước 12–14)

- Lãi suất áp dụng = `REF_RATE` − tổng `rate_discount` của sản phẩm bán chéo đã chọn (tối đa `MAX_RATE_DISCOUNT`).
- Lịch trả nợ: tiêu dùng gốc `A/n` mỗi kỳ, lãi = dư nợ × r/12; thấu chi trả lãi hằng tháng, gốc ở kỳ cuối.
- Phương án **đạt** khi: `A` ≤ hạn mức an toàn, DTI sau vay ≤ `DTI_MAX`, nghĩa vụ hằng tháng ≤ `K`, `A` ≥ `MIN_AMOUNT`,
  kỳ hạn ≤ `L` (tiêu dùng).
- Khi không đạt, đề xuất (mỗi đề xuất kèm điểm mới và chi phí bán chéo):
  1. **Giảm số tiền** về hạn mức an toàn của kỳ hạn hiện tại.
  2. **Kéo dài kỳ hạn** tới kỳ hạn nhỏ nhất đủ để giữ số tiền (không vượt `MAX_TERM` và `L`); nếu không đủ thì kỳ hạn tối đa với hạn mức tương ứng.
  3. **Bán chéo**: thêm sản phẩm có mức giảm lãi lớn nhất chưa chọn, nếu làm tăng hạn mức an toàn.
  4. **Đổi gói** sang gói còn lại nếu gói đó đạt điều kiện.
- Khi lưu phương án (`POST /plans`) backend tính lại toàn bộ, không tin số liệu từ frontend.

## 8. Chạy thử phiên bản (chức năng 7.4)

Chạy bộ hồ sơ mẫu (6 hồ sơ × 2 gói) với phiên bản đang áp dụng và bản nháp; lưu vào `param_version.test_result` (JSON)
các cột: đạt/không, hạn mức an toàn, điểm của hai phiên bản. Chỉ cho kích hoạt sau khi đã chạy thử; khi kích hoạt,
phiên bản cũ chuyển `EXPIRED`. Các lượt sàng lọc lưu `version_id` đã dùng; hồ sơ khóa phiên bản tại thời điểm nộp.

## 9. Kiểm tra chất lượng tệp tải lên (chức năng 4.1)

Thứ tự kiểm tra; trượt bước nào thì giấy tờ ở trạng thái `REJECTED_QUALITY`, ghi `qualityStatus` và lời nhắc, không trích xuất:

| Bước | Điều kiện | `qualityStatus` |
|---|---|---|
| 1 | Đuôi tệp (JPEG coi là JPG) không thuộc `document_type.allowed_formats` | `WRONG_FORMAT` |
| 2 | Dung lượng > 10 MB | `TOO_LARGE` |
| 3 | Ảnh (không phải PDF) < 25 KB – coi là mờ hoặc độ phân giải thấp. Backend thật nên thay bằng đo độ nét (phương sai Laplacian) | `BLURRY` |

Ở backend thật, sau 3 bước trên tệp được gửi Gemini đọc thông tin (xem `docs/be-huong-dan.md` 5.4): Gemini báo ảnh không đọc được → `BLURRY`;
báo không đúng loại giấy tờ → `WRONG_TYPE`.

Đạt thì giấy tờ sang `UPLOADED`, `version_no` tăng 1, chạy trích xuất (MB06) theo loại giấy tờ. Mỗi trường có `ocrValue` và `confidence`; khách hàng sửa thành `confirmedValue` rồi xác nhận, giấy tờ chuyển `CONFIRMED`. Tải lại tệp xóa kết quả đối chiếu cũ của giấy tờ đó.

Trường trích xuất theo loại giấy tờ (mã `field_key`):
CCCD `full_name, national_id, dob, gender, address`; xác nhận cư trú `address`; HĐLĐ `employer, salary`;
sao kê `salary_bank, avg_income`; hóa đơn/hợp đồng mua bán `beneficiary, invoice_amount`.

## 10. Đối chiếu tự động MB06 ↔ MB01 (cảnh báo)

Chạy lại sau mỗi lần lưu thông tin cá nhân, tải lên hoặc xác nhận giấy tờ. Mức độ: `BLOCKING` (chặn nộp), `WARNING`, `INFO`.

| Quy tắc | Mức |
|---|---|
| Họ tên trên CCCD (không phân biệt hoa thường) ≠ họ tên đã khai | WARNING |
| Số CCCD trên giấy tờ ≠ số đã khai, hoặc không đủ 12 chữ số | BLOCKING |
| Ngày sinh trên CCCD ≠ ngày sinh khai khi sàng lọc | WARNING |
| Tuổi theo ngày sinh thực tế < tuổi tối thiểu của gói (TD 22, TC 19) | BLOCKING |
| Ngân hàng trên sao kê ≠ ngân hàng nhận lương đã khai | INFO |
| Giá trị hóa đơn > 0 và < số tiền vay | BLOCKING |
| \|thu nhập sao kê − thu nhập khai\| / thu nhập khai ≥ 5% | WARNING |
| Chạy lại mô phỏng (mục 7) với thu nhập sao kê; phương án không đạt ngưỡng | BLOCKING |

Kết quả tính lại (`recalc`: thu nhập kiểm chứng, DTI, hạn mức an toàn, điểm, đạt/không) trả cùng hồ sơ. Khi không đạt, khách hàng điều chỉnh số tiền hoặc kỳ hạn (PUT `/applications/{id}/plan`).

Điều kiện được nộp: đủ thông tin cá nhân (họ tên, CCCD, ngày sinh, địa chỉ); mọi giấy tờ bắt buộc ở `CONFIRMED`; không còn cảnh báo `BLOCKING`; đã lập giấy đề nghị MB15; xác nhận OTP.

## 11. Khóa nội dung khi nộp

- `content_hash` = SHA-256 của JSON `{form: nội dung MB15, plan, docs: [[id, version_no, [giá trị đã xác nhận…]]…]}`.
- `qr_token` = `"QR" + 8 ký tự [A-Z0-9]`, giữ nguyên khi nộp lại.
- `expires_at` = thời điểm nộp + `DOC_VALIDITY_DAYS` (mặc định 30 ngày).
- Ghi số phiên bản tham số đang hiệu lực vào hồ sơ.

## 12. Trạng thái hồ sơ

```
DRAFTING ──nộp (OTP)──▶ WAITING_VERIFY ──đối chiếu khớp──▶ VERIFIED ──phê duyệt──▶ APPROVED ──giải ngân──▶ DISBURSED
    ▲                       │    │                            │
    │                       │    └─có giấy tờ không khớp/thiếu─▶ NEED_MORE ──khách nộp lại (OTP)──▶ WAITING_VERIFY
    │                       │                                 ├─từ chối──▶ REJECTED
    │                       │                                 └─yêu cầu bổ sung──▶ NEED_MORE
    └───────── khách hủy (DRAFTING, WAITING_VERIFY, NEED_MORE) ─▶ CANCELLED
WAITING_VERIFY, NEED_MORE quá expires_at ──tác vụ định kỳ──▶ EXPIRED
```

Hồ sơ chỉ sửa được ở `DRAFTING` và `NEED_MORE`. Mỗi lần đổi trạng thái ghi `application_status_history`, nhật ký và thông báo cho khách hàng (và nhân viên chi nhánh khi hồ sơ nộp, nộp lại, hủy).
Nhân viên chỉ thấy hồ sơ thuộc chi nhánh của mình; quản trị viên không thao tác nghiệp vụ trên hồ sơ.

## 13. Tác vụ định kỳ (chức năng 7.6)

| Mã | Lịch đề xuất | Việc làm |
|---|---|---|
| `EXPIRE_DOCUMENTS` | 01:00 hằng ngày | Hồ sơ `WAITING_VERIFY`/`NEED_MORE` có `expires_at` < hiện tại → `EXPIRED`, báo khách hàng |
| `PURGE_RETENTION` | 02:00 ngày 1 hằng tháng | Hồ sơ `REJECTED`/`CANCELLED`/`EXPIRED` cập nhật lần cuối quá `RETENTION_MONTHS` (mặc định 12) tháng: xóa tệp và trường trích xuất, xóa số CCCD và địa chỉ |
| `REMIND_DRAFTS` | 09:00 hằng ngày | Hồ sơ `DRAFTING` không cập nhật quá 3 ngày → gửi thông báo nhắc |

Backend dùng `@Scheduled` của Spring; API `POST /admin/jobs/{code}/run` để chạy tay.

## 14. Rút quyền đồng ý (chức năng 6.1)

- Rút phạm vi **A** (sàng lọc): xóa kết quả sàng lọc và phương án chưa gắn hồ sơ đang mở.
- Rút phạm vi **B** (hồ sơ): hủy mọi hồ sơ đang mở (`DRAFTING`, `WAITING_VERIFY`, `NEED_MORE`), xóa tệp giấy tờ của chúng. Hồ sơ đã đối chiếu xong phải liên hệ chi nhánh.
- Cả hai đều yêu cầu OTP và ghi `consent_record` với `withdrawn_at`.

## 15. Độ chính xác mô hình và báo cáo MB14 (QT09)

- Mẫu: hồ sơ đã có kết quả ngân hàng (`DISBURSED`, `APPROVED` coi là duyệt; `REJECTED` là từ chối).
- Hệ thống đánh giá “đạt” khi điểm tương thích lúc nộp ≥ ngưỡng (mặc định 55, tham số `threshold`).
- Ma trận nhầm lẫn 2×2; độ chính xác = (TP+TN)/n; độ chính xác dương = TP/(TP+FP); độ nhạy = TP/(TP+FN).
- Nhóm điểm: <45, 45–55, 55–65, 65–75, 75–85, ≥85 – số hồ sơ và tỷ lệ được duyệt mỗi nhóm.
- Đề xuất hiệu chỉnh MB14 (gợi ý, quản trị viên quyết định): nếu ≥ 30% hồ sơ bị từ chối vì thu nhập không đủ trả nợ → đề xuất tăng `LIVING_COST_BASE` 500.000 đ; nếu độ nhạy < 75% và nhóm 45–55 có tỷ lệ duyệt ≥ 40% → đề xuất nới DTI tối đa thêm 2 điểm % (không quá 80%); nếu gói thấu chi có ≥ 5 hồ sơ và độ chính xác thấp hơn mức chung quá 5 điểm % → xem lại trọng số của gói. “Tạo bản nháp tham số” nhân bản phiên bản hiện hành kèm các thay đổi đề xuất.
