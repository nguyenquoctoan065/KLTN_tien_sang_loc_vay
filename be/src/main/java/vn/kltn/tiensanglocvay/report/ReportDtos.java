package vn.kltn.tiensanglocvay.report;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** DTO nhóm Báo cáo (QT08/QT09) – tên trường giống hệt schema StatsResponse, AccuracyResponse, ReportResponse trong openapi.yaml. */
public final class ReportDtos {

    private ReportDtos() {
    }

    public record Totals(long screenings, long plans, long applications, long submitted, long verified, long approved,
                         long disbursed, long rejected, long cancelled, long expired) {
    }

    public record StatusCount(String status, long count) {
    }

    public record ProductStat(String productCode, long count, long amount) {
    }

    public record BranchStat(String branchName, long count, long approved, long rejected) {
    }

    public record MonthStat(String month, long created, long disbursed, long rejected) {
    }

    public record StatsResponse(LocalDate from, LocalDate to, Totals totals, List<StatusCount> byStatus, List<ProductStat> byProduct,
                                List<BranchStat> byBranch, List<MonthStat> byMonth, double avgProcessingDays, long disbursedAmount) {
    }

    public record Matrix(long tp, long fp, long tn, long fn) {
    }

    public record BandStat(String band, long total, long approved, double rate) {
    }

    public record ProductAccuracy(String productCode, long sample, double accuracy) {
    }

    /** accuracy, precision, recall là null khi chưa đủ dữ liệu (docs/quy-tac-tinh-toan.md mục 15). */
    public record AccuracyResponse(double threshold, long sample, Matrix matrix, Double accuracy, Double precision, Double recall,
                                   List<BandStat> byBand, List<ProductAccuracy> byProduct) {
    }

    public record ReportProposal(String id, String title, String detail, String paramKey, String productCode,
                                 String currentValue, String proposedValue) {
    }

    public record ReportResponse(String period, OffsetDateTime generatedAt, StatsResponse stats, AccuracyResponse accuracy,
                                 List<ReportProposal> proposals) {
    }
}
