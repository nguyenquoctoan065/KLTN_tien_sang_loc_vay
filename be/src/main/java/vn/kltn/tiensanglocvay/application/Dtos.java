package vn.kltn.tiensanglocvay.application;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.kltn.tiensanglocvay.engine.ProductCode;
import vn.kltn.tiensanglocvay.plan.LoanPlanDto;
import vn.kltn.tiensanglocvay.screening.ConsentInput;

/** DTO nhóm Hồ sơ vay (QT03); tên record và trường giống hệt schema trong docs/openapi.yaml. */
public final class Dtos {

    private Dtos() {
    }

    /** Schema Application. {@code verifications}, {@code approval} có dữ liệu từ B5. */
    public record Application(UUID id, String code, long customerId, ApplicationStatus status, long branchId, String branchName,
                              String branchAddress, LoanPlanDto plan, String purpose, PersonalInfo personal, OffsetDateTime consentBAt,
                              String versionNo, String contentHash, OffsetDateTime lockedAt, String qrToken, OffsetDateTime expiresAt,
                              OffsetDateTime createdAt, OffsetDateTime updatedAt, List<AppDocument> documents,
                              List<CrossCheckWarning> warnings, Recalculation recalc, LoanRequestFormDto requestForm,
                              List<Object> verifications, Object approval, List<StatusEvent> history, ApplicationPermissions can) {
    }

    public record ApplicationSummary(UUID id, String code, String customerName, String customerPhone, ProductCode productCode,
                                     String productName, long amount, int termMonths, double score, String branchName,
                                     ApplicationStatus status, String qrToken, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                                     OffsetDateTime expiresAt) {
    }

    /** Schema PersonalInfo; khi PUT, trường null nghĩa là giữ nguyên (mock gộp {@code {...app.personal, ...b}}). */
    public record PersonalInfo(String fullName, String nationalId, String dob, String gender, String address, String phone,
                               String email) {
    }

    public record ExtractedFieldDto(long id, String name, String label, String ocrValue, double confidence, String confirmedValue) {
    }

    public record AppDocument(long id, long documentTypeId, String typeName, String group, boolean required, String allowedFormats,
                              String status, String fileName, String fileFormat, Integer fileSize, int versionNo, LocalDate issuedDate,
                              String qualityStatus, String qualityMessage, OffsetDateTime uploadedAt, String previewUrl,
                              List<ExtractedFieldDto> fields, String verifyResult, String verifyReason) {
    }

    public record LoanRequestFormDto(String status, List<LoanRequestForm.Item> content, OffsetDateTime createdAt,
                                     OffsetDateTime otpConfirmedAt) {
    }

    public record StatusEvent(ApplicationStatus status, OffsetDateTime at, String actor, String note) {
    }

    public record ApplicationPermissions(boolean edit, boolean submit, boolean resubmit, boolean cancel, boolean verify, boolean decide,
                                         boolean disburse) {
    }

    // ---------------------------------------------------------------- request

    /** {@code planId} sai định dạng UUID → 400 "Dữ liệu không hợp lệ." (lỗi đọc JSON). */
    public record CreateApplicationRequest(UUID planId, Long branchId, ConsentInput consentB) {
    }

    public record ConfirmFieldsRequest(List<FieldValue> fields) {

        public record FieldValue(Long id, String confirmedValue) {
        }
    }

    public record UpdatePlanRequest(Long amount, Integer termMonths, List<Integer> crossSellIds) {
    }

    public record UpdateRequestFormRequest(Map<String, String> values) {
    }

    public record CancelRequest(String reason) {
    }

    public record OtpBody(String otp) {
    }
}
