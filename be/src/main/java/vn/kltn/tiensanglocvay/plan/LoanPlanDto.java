package vn.kltn.tiensanglocvay.plan;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import vn.kltn.tiensanglocvay.catalog.ChecklistItem;
import vn.kltn.tiensanglocvay.engine.ProductCode;

/** Schema LoanPlan (MB07); {@code id}, {@code screeningId}, {@code applicationId} là mã công khai (UUID). */
public record LoanPlanDto(UUID id, UUID screeningId, ProductCode productCode, String productName, long amount, int termMonths,
                          double refRate, long monthlyPayment, long totalInterest, double dtiAfter, double score, String status,
                          List<CrossSellItem> crossSells, OffsetDateTime createdAt, List<ChecklistItem> checklist, UUID applicationId) {

    public record CrossSellItem(int id, String name, long extraCost, String benefit) {
    }
}
