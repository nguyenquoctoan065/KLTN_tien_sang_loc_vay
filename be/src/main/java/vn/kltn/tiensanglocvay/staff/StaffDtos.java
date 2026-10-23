package vn.kltn.tiensanglocvay.staff;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** DTO nhóm Nhân viên chi nhánh (QT04–QT05); tên record và trường giống hệt schema trong docs/openapi.yaml. */
public final class StaffDtos {

    private StaffDtos() {
    }

    public record PublicIdResult(UUID id) {
    }

    public record StaffSummary(long waitingVerify, long needMore, long verified, long approved) {
    }

    public record VerificationRequest(List<Detail> details, String note) {

        public record Detail(Long documentId, String result, String reason) {
        }
    }

    /** {@code approvedRate} giữ BigDecimal để kiểm tra tối đa 2 chữ số thập phân (cột decimal(5,2)). */
    public record DecisionRequest(String resultType, Long approvedAmount, Integer approvedTerm, BigDecimal approvedRate, String reason) {
    }

    /** {@code disbursedAt} nhận chuỗi để thiếu ngày trả đúng thông báo của mock, sai định dạng mới là 400 "Dữ liệu không hợp lệ.". */
    public record DisbursementRequest(String disbursedAt, Long disbursedAmount, String beneficiary) {
    }
}
