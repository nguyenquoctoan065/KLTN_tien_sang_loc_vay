package vn.kltn.tiensanglocvay.screening;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import vn.kltn.tiensanglocvay.engine.CreditHistory;
import vn.kltn.tiensanglocvay.engine.EngineInput;
import vn.kltn.tiensanglocvay.engine.Gender;

/**
 * Schema ScreeningRequest (MB01 không định danh + đồng ý MB02 phần a). {@code consentA.agreed}, {@code avgIncome} và
 * {@code desiredAmount} được ScreeningService kiểm tra trước, theo thứ tự và câu chữ của mock; các ràng buộc còn lại
 * (độ dài theo cột của bảng screening) trả "Dữ liệu không hợp lệ." kèm {@code fields}.
 */
public record ScreeningRequest(
        @Valid ConsentInput consentA,
        @NotBlank(message = "Không được để trống.") @Size(max = 50, message = "Tối đa 50 ký tự.") String nationality,
        @NotNull(message = "Không được để trống.") LocalDate dob,
        @NotNull(message = "Không được để trống.") Gender gender,
        @NotNull(message = "Không được để trống.") MaritalStatus maritalStatus,
        @NotNull(message = "Không được để trống.") @Min(value = 0, message = "Không được âm.") Integer dependents,
        @Max(value = MAX_MONEY, message = "Số tiền quá lớn.") Long avgIncome,
        @NotBlank(message = "Không được để trống.") @Size(max = 100, message = "Tối đa 100 ký tự.") String salaryBank,
        @NotNull(message = "Không được để trống.") Boolean isAbbankStaff,
        @NotNull(message = "Không được để trống.") CreditHistory creditHistory,
        @NotNull(message = "Không được để trống.") List<@Valid @NotNull(message = "Không được để trống.") OtherDebtInput> otherDebts,
        @NotBlank(message = "Không được để trống.") @Size(max = 100, message = "Tối đa 100 ký tự.") String purpose,
        @Max(value = MAX_MONEY, message = "Số tiền quá lớn.") Long desiredAmount,
        @NotNull(message = "Không được để trống.") @Min(value = 1, message = "Kỳ hạn phải lớn hơn 0.") Integer desiredTerm) {

    /** Giới hạn của cột decimal(15,0). */
    static final long MAX_MONEY = 999_999_999_999_999L;

    /** Schema OtherDebt. */
    public record OtherDebtInput(
            @Size(max = 100, message = "Tối đa 100 ký tự.") String lender,
            @NotNull(message = "Không được để trống.") @Min(value = 0, message = "Không được âm.")
            @Max(value = MAX_MONEY, message = "Số tiền quá lớn.") Long monthlyPayment) {
    }

    EngineInput toEngine() {
        return new EngineInput(nationality, dob, gender, dependents, avgIncome, salaryBank, isAbbankStaff, creditHistory,
                otherDebts.stream().map(d -> new EngineInput.Debt(d.lender(), d.monthlyPayment().doubleValue())).toList(),
                purpose, desiredAmount, desiredTerm);
    }
}
