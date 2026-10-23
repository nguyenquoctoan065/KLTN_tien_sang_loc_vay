package vn.kltn.tiensanglocvay.report;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.kltn.tiensanglocvay.common.ApiException;
import vn.kltn.tiensanglocvay.report.ReportDtos.AccuracyResponse;
import vn.kltn.tiensanglocvay.report.ReportDtos.ReportResponse;
import vn.kltn.tiensanglocvay.report.ReportDtos.StatsResponse;
import vn.kltn.tiensanglocvay.security.RequireRole;
import vn.kltn.tiensanglocvay.security.Role;

/**
 * 8.1–8.3 /admin/stats, /admin/model-accuracy, /admin/reports/mb14 (x-roles ADMIN). Tham số nhận dạng chuỗi để tự kiểm tra:
 * sai định dạng → 400 "Dữ liệu không hợp lệ." kèm {@code fields} (mock không kiểm tra); {@code threshold} như mock {@code Number(x) || 55}.
 */
@RestController
@RequireRole(Role.ADMIN)
public class ReportController {

    private static final String INVALID = "Dữ liệu không hợp lệ.";
    /** Giới hạn khoảng thống kê (danh sách tháng byMonth sinh theo khoảng này). */
    private static final long MAX_DAYS = 3660;

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/admin/stats")
    StatsResponse stats(@RequestParam(required = false) String from, @RequestParam(required = false) String to,
                        @RequestParam(required = false) String branchId) {
        Map<String, String> fields = new LinkedHashMap<>();
        LocalDate today = reports.today();
        LocalDate t = date(to, today, "to", fields);
        LocalDate f = date(from, today.minusDays(180), "from", fields);
        Long b = null;
        if (branchId != null && !branchId.isBlank()) {
            try {
                b = Long.parseLong(branchId.trim());
            } catch (NumberFormatException e) {
                fields.put("branchId", "Phải là số.");
            }
        }
        if (fields.isEmpty() && ChronoUnit.DAYS.between(f, t) > MAX_DAYS) {
            fields.put("from", "Khoảng thời gian tối đa 10 năm.");
        }
        if (!fields.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, INVALID, fields);
        }
        // mock: Number(branchId) || null – 0 nghĩa là toàn hệ thống
        return reports.stats(f, t, b == null || b == 0 ? null : b);
    }

    @GetMapping("/admin/model-accuracy")
    AccuracyResponse accuracy(@RequestParam(required = false) String threshold) {
        return reports.accuracy(threshold(threshold), null);
    }

    @GetMapping("/admin/reports/mb14")
    ReportResponse mb14(@RequestParam(required = false) String month) {
        YearMonth m;
        if (month == null || month.isBlank()) {
            m = YearMonth.from(reports.today());
        } else {
            try {
                if (!month.matches("\\d{4}-\\d{2}")) {
                    throw new DateTimeParseException("", month, 0);
                }
                m = YearMonth.parse(month);
            } catch (DateTimeParseException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, INVALID, Map.of("month", "Tháng phải có dạng YYYY-MM."));
            }
        }
        return reports.mb14(m);
    }

    private static LocalDate date(String value, LocalDate fallback, String field, Map<String, String> fields) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            fields.put(field, "Ngày phải có dạng YYYY-MM-DD.");
            return fallback;
        }
    }

    /** {@code Number(x) || 55}: thiếu, không phải số hữu hạn hoặc 0 → 55. */
    static double threshold(String value) {
        if (value == null || value.isBlank()) {
            return ReportService.DEFAULT_THRESHOLD;
        }
        try {
            double t = Double.parseDouble(value.trim());
            return Double.isFinite(t) && t != 0 ? t : ReportService.DEFAULT_THRESHOLD;
        } catch (NumberFormatException e) {
            return ReportService.DEFAULT_THRESHOLD;
        }
    }
}
