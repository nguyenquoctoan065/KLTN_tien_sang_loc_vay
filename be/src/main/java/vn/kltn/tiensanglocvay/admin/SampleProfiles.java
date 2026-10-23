package vn.kltn.tiensanglocvay.admin;

import java.time.LocalDate;
import java.util.List;
import vn.kltn.tiensanglocvay.engine.CreditHistory;
import vn.kltn.tiensanglocvay.engine.EngineInput;
import vn.kltn.tiensanglocvay.engine.Gender;

/** Bộ hồ sơ mẫu chạy thử phiên bản tham số (7.4), chép từ {@code SAMPLE_PROFILES} trong fe/src/mocks/handlers/admin.ts. */
final class SampleProfiles {

    record Profile(String name, EngineInput input) {
    }

    private SampleProfiles() {
    }

    static final List<Profile> ALL = build();

    private static List<Profile> build() {
        return List.of(
                new Profile("Nhân viên văn phòng, thu nhập 20 triệu",
                        input("1992-05-10", Gender.MALE, 1, 20_000_000, "Vietcombank", false, List.of(), "Mua sắm đồ dùng, thiết bị", 100_000_000, 36)),
                new Profile("Có nghĩa vụ nợ 10 triệu/tháng (ví dụ biên bản)",
                        input("1992-05-10", Gender.MALE, 1, 15_000_000, "Vietcombank", false,
                                List.of(new EngineInput.Debt("TCTD khác", 10_000_000.0)), "Mua sắm đồ dùng, thiết bị", 50_000_000, 36)),
                new Profile("CBNV ABBANK, thu nhập 30 triệu",
                        input("1992-05-10", Gender.MALE, 1, 30_000_000, "ABBANK", true, List.of(), "Mua sắm đồ dùng, thiết bị", 300_000_000, 60)),
                new Profile("Sinh viên mới đi làm 20 tuổi",
                        input("2006-03-01", Gender.MALE, 0, 9_000_000, "Vietcombank", false, List.of(), "Chi tiêu cá nhân linh hoạt", 20_000_000, 12)),
                new Profile("Gần tuổi nghỉ hưu (nữ, 57 tuổi)",
                        input("1969-08-20", Gender.FEMALE, 1, 25_000_000, "Vietcombank", false, List.of(), "Mua sắm đồ dùng, thiết bị", 150_000_000, 48)),
                new Profile("Thu nhập cao, vay lớn",
                        input("1992-05-10", Gender.MALE, 2, 60_000_000, "Vietcombank", false,
                                List.of(new EngineInput.Debt("Thẻ tín dụng", 5_000_000.0)), "Mua sắm đồ dùng, thiết bị", 500_000_000, 60)));
    }

    /** Các trường không đổi giữa các hồ sơ mẫu: quốc tịch Việt Nam, trả nợ đúng hạn (tình trạng hôn nhân bộ máy không dùng). */
    private static EngineInput input(String dob, Gender gender, int dependents, double income, String salaryBank, boolean staff,
                                     List<EngineInput.Debt> debts, String purpose, double amount, int term) {
        return new EngineInput("Việt Nam", LocalDate.parse(dob), gender, dependents, income, salaryBank, staff, CreditHistory.ON_TIME,
                debts, purpose, amount, term);
    }
}
