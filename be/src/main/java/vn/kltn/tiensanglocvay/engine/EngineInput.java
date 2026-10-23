package vn.kltn.tiensanglocvay.engine;

import java.time.LocalDate;
import java.util.List;

/** Phần MB01 mà bộ máy dùng tới (tên trường giống ScreeningRequest trong openapi.yaml). */
public record EngineInput(String nationality, LocalDate dob, Gender gender, int dependents, double avgIncome, String salaryBank,
                          boolean isAbbankStaff, CreditHistory creditHistory, List<Debt> otherDebts, String purpose,
                          double desiredAmount, int desiredTerm) {

    /** Một khoản nợ khác; {@code monthlyPayment} rỗng tính là 0 như engine.ts. */
    public record Debt(String lender, Double monthlyPayment) {
    }
}
