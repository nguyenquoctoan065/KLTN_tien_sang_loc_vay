package vn.kltn.tiensanglocvay.privacy;

import java.time.OffsetDateTime;
import java.util.List;
import vn.kltn.tiensanglocvay.application.ApplicationStatus;
import vn.kltn.tiensanglocvay.application.Dtos.PersonalInfo;
import vn.kltn.tiensanglocvay.audit.ActivityLogDto;

/** DTO nhóm Dữ liệu cá nhân (QT06); tên record và trường giống hệt schema trong docs/openapi.yaml. */
public final class PrivacyDtos {

    private PrivacyDtos() {
    }

    public record ConsentRecord(long id, String scope, String docVersion, OffsetDateTime agreedAt, OffsetDateTime withdrawnAt,
                                String applicationCode) {
    }

    public record Account(String phone, String name, OffsetDateTime createdAt) {
    }

    public record MyApplication(String code, ApplicationStatus status, int documents) {
    }

    /** Schema MyData. */
    public record MyData(Account account, PersonalInfo personal, List<ConsentRecord> consents, long screenings, long plans,
                         List<MyApplication> applications, List<ActivityLogDto> logs) {
    }

    /** {@code scope} là chuỗi để scope lạ trả 400 "Dữ liệu không hợp lệ." thay vì lỗi đọc JSON. */
    public record WithdrawConsentRequest(String scope, String otp) {
    }

    public record WithdrawConsentResult(String scope, int deletedScreenings, int deletedDocuments, List<String> cancelledApplications,
                                        List<String> keptApplications, String message) {
    }
}
