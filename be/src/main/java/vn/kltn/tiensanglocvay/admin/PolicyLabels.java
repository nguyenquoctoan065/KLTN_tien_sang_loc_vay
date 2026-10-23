package vn.kltn.tiensanglocvay.admin;

import java.util.Map;

/** Nhãn tham số (MB11) và tiêu chí chấm điểm (MB12), chép từ {@code PARAM_LABELS}, {@code CRITERIA_LABELS} trong fe/src/mocks/db.ts. */
final class PolicyLabels {

    private PolicyLabels() {
    }

    static final Map<String, String> PARAM = Map.ofEntries(
            Map.entry("DTI_MAX", "Tỷ lệ DTI tối đa"),
            Map.entry("LIVING_COST_BASE", "Chi phí sinh hoạt định mức của người vay"),
            Map.entry("LIVING_COST_PER_DEPENDENT", "Chi phí sinh hoạt định mức mỗi người phụ thuộc"),
            Map.entry("RETIRE_AGE_MALE", "Tuổi nghỉ hưu – nam"),
            Map.entry("RETIRE_AGE_FEMALE", "Tuổi nghỉ hưu – nữ"),
            Map.entry("ROOM_FACTOR", "Hệ số room tín dụng / tỷ trọng dư nợ tín chấp"),
            Map.entry("MAX_RATE_DISCOUNT", "Mức giảm lãi suất tối đa từ bán chéo"),
            Map.entry("DOC_VALIDITY_DAYS", "Thời hạn hiệu lực giấy tờ"),
            Map.entry("RETENTION_MONTHS", "Thời hạn lưu trữ hồ sơ đã đóng"),
            Map.entry("MIN_AGE", "Tuổi tối thiểu"),
            Map.entry("INCOME_MULTIPLIER", "Hệ số hạn mức theo thu nhập"),
            Map.entry("MAX_AMOUNT", "Trần hạn mức"),
            Map.entry("MIN_AMOUNT", "Số tiền vay tối thiểu"),
            Map.entry("MAX_TERM", "Kỳ hạn tối đa"),
            Map.entry("MIN_TERM", "Kỳ hạn tối thiểu"),
            Map.entry("REF_RATE", "Lãi suất tham chiếu"));

    static final Map<String, String> CRITERIA = Map.of(
            "DTI", "Tỷ lệ DTI sau vay",
            "AMOUNT_RATIO", "Số tiền mong muốn so với hạn mức an toàn",
            "SALARY_BANK", "Ngân hàng nhận lương",
            "STAFF", "Cán bộ nhân viên ABBANK",
            "TERM_TO_RETIRE", "Thời gian còn lại đến tuổi nghỉ hưu");

    static String param(String key) {
        return PARAM.getOrDefault(key, key);
    }

    static String criterion(String code) {
        return CRITERIA.getOrDefault(code, code);
    }
}
