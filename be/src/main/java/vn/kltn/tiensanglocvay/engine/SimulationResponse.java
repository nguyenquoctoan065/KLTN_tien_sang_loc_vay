package vn.kltn.tiensanglocvay.engine;

import java.util.List;

/** Schema SimulationResponse (MB04 kèm kiểm tra ngưỡng và đề xuất điều chỉnh ngược). */
public record SimulationResponse(ProductCode productCode, String productName, long amount, int termMonths, double rate,
                                 long monthlyObligation, long firstPayment, long totalInterest, long totalPayment, double dtiAfter,
                                 double dtiMax, long disposableAfter, long maxSafeAmount, int maxTermAllowed, double score, boolean ok,
                                 List<String> issues, long extraCost, List<ScheduleRow> schedule, List<Suggestion> suggestions) {
}
