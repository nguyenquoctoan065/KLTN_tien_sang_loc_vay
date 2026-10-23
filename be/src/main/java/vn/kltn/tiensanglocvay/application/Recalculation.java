package vn.kltn.tiensanglocvay.application;

import java.util.List;

/** Schema Recalculation (tính lại theo thu nhập kiểm chứng); lưu ở cột jsonb loan_application.recalc. */
public record Recalculation(long verifiedIncome, double dtiAfter, long maxSafeAmount, double score, boolean ok, List<String> issues) {
}
