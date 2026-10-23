package vn.kltn.tiensanglocvay.screening;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Bảng screening_result (KẾT QUẢ GỢI Ý): bản ghi gọn của MB03 cho báo cáo (B8). API không đọc bảng này mà tính lại
 * từ input và phiên bản tham số (ScreeningService), nên lý do/hướng cải thiện đầy đủ không cần lưu ở đây.
 */
@Entity
@Table(name = "screening_result")
@IdClass(ScreeningResult.Key.class)
public class ScreeningResult {

    public record Key(Long screeningId, Integer productId) implements Serializable {
    }

    @Id
    @Column(name = "screening_id")
    private Long screeningId;

    @Id
    @Column(name = "product_id")
    private Integer productId;

    @Column(nullable = false)
    private boolean eligible;

    private String reason;

    private BigDecimal dti;

    @Column(name = "max_safe_amount")
    private BigDecimal maxSafeAmount;

    private BigDecimal score;

    @Column(name = "rank_no")
    private Integer rankNo;

    protected ScreeningResult() {
    }

    public ScreeningResult(Long screeningId, Integer productId, boolean eligible, String reason, BigDecimal dti, Long maxSafeAmount,
                           Double score, Integer rankNo) {
        this.screeningId = screeningId;
        this.productId = productId;
        this.eligible = eligible;
        this.reason = reason;
        this.dti = dti;
        this.maxSafeAmount = maxSafeAmount == null ? null : BigDecimal.valueOf(maxSafeAmount);
        this.score = score == null ? null : BigDecimal.valueOf(score);
        this.rankNo = rankNo;
    }
}
