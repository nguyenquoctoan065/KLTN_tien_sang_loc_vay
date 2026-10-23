package vn.kltn.tiensanglocvay.screening;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Bảng other_debt (NGHĨA VỤ NỢ KHÁC) của một lượt sàng lọc. */
@Entity
@Table(name = "other_debt")
public class OtherDebt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "screening_id", nullable = false)
    private Long screeningId;

    private String lender;

    @Column(name = "monthly_payment", nullable = false)
    private BigDecimal monthlyPayment;

    protected OtherDebt() {
    }

    public OtherDebt(Long screeningId, String lender, long monthlyPayment) {
        this.screeningId = screeningId;
        this.lender = lender;
        this.monthlyPayment = BigDecimal.valueOf(monthlyPayment);
    }

    public String getLender() {
        return lender;
    }

    public BigDecimal getMonthlyPayment() {
        return monthlyPayment;
    }
}
