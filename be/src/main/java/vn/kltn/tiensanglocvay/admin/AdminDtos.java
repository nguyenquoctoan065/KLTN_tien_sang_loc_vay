package vn.kltn.tiensanglocvay.admin;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import vn.kltn.tiensanglocvay.catalog.CrossSellProductDto;
import vn.kltn.tiensanglocvay.catalog.LoanProductDto;

/** DTO của /admin/** (trừ báo cáo B8); tên trường giống hệt schema trong docs/openapi.yaml. */
public final class AdminDtos {

    private AdminDtos() {
    }

    // ---------------------------------------------------------------- phiên bản tham số (QT07)

    public record ParamVersionDto(int id, String versionNo, String legalBasis, String status, String createdBy, OffsetDateTime createdAt,
                                  OffsetDateTime activatedAt) {
    }

    public record PolicyParamDto(long id, String productCode, String key, String label, String value, String unit) {
    }

    public record ScoringWeightDto(long id, String productCode, String criterion, String label, double weight) {
    }

    public record TestRunRow(String profile, String productCode, boolean activeEligible, boolean draftEligible, Long activeMaxSafe,
                             Long draftMaxSafe, Double activeScore, Double draftScore) {
    }

    public record TestRunResult(OffsetDateTime runAt, int changed, List<TestRunRow> rows) {
    }

    /** ParamVersion + MB11, MB12 và kết quả chạy thử. */
    public record ParamVersionDetail(int id, String versionNo, String legalBasis, String status, String createdBy, OffsetDateTime createdAt,
                                     OffsetDateTime activatedAt, List<PolicyParamDto> params, List<ScoringWeightDto> weights,
                                     TestRunResult testResult) {
    }

    public record ParamChange(String key, String productCode, String value) {
    }

    public record CloneParamVersionRequest(String versionNo, String legalBasis, List<ParamChange> changes) {
    }

    /** Một dòng tham số gửi lên khi sửa bản nháp; khớp dòng hiện có theo {@code key + productCode} (id, label, unit bỏ qua). */
    public record ParamInput(String productCode, String key, String value) {
    }

    public record WeightInput(String productCode, String criterion, BigDecimal weight) {
    }

    public record UpdateParamVersionRequest(List<ParamInput> params, List<WeightInput> weights, String legalBasis) {
    }

    public record IdResult(long id) {
    }

    // ---------------------------------------------------------------- tài khoản nhân viên (1.5)

    /** StaffAccountWithPassword; {@code tempPassword} chỉ có khi tạo mới hoặc đặt lại mật khẩu. */
    public record StaffAccount(long id, String fullName, String email, String role, Long branchId, String branchName, String status,
                               OffsetDateTime createdAt, String tempPassword) {
    }

    public record CreateStaffRequest(String fullName, String email, String role, Long branchId) {
    }

    public record UpdateStaffRequest(String fullName, String role, Long branchId) {
    }

    // ---------------------------------------------------------------- chi nhánh (1.6)

    public record AdminBranch(long id, String code, String name, String address, String phone, String status, long openApplications) {
    }

    public record BranchView(long id, String code, String name, String address, String phone, String status) {
    }

    public record BranchInput(String code, String name, String address, String phone) {
    }

    public record DeactivateBranchRequest(Long transferToBranchId) {
    }

    public record BranchDeactivateResult(long id, String code, String name, String address, String phone, String status, int transferred,
                                         int transferredStaff) {
    }

    // ---------------------------------------------------------------- danh mục (7.3)

    public record DocumentTypeDto(long id, String name, String group, String allowedFormats, Integer validityDays, String status) {
    }

    public record ProductDocRule(String productCode, Long documentTypeId, Boolean required) {
    }

    public record AdminCatalog(List<LoanProductDto> products, List<DocumentTypeDto> documentTypes, List<ProductDocRule> productDocuments,
                               List<CrossSellProductDto> crossSells) {
    }

    public record UpdateProductRequest(String name, String description, String repaymentMethod, String status) {
    }

    public record DocumentTypeInput(String name, String group, String allowedFormats, Integer validityDays, String status) {
    }

    public record CrossSellProductInput(String name, String productType, BigDecimal estCost, BigDecimal scoreBonus, BigDecimal rateDiscount,
                                        String status) {
    }

    // ---------------------------------------------------------------- tác vụ định kỳ (1.7)

    public record JobInfo(String code, String name, String schedule, OffsetDateTime lastRunAt, String lastResult) {
    }
}
