package vn.kltn.tiensanglocvay.screening;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import vn.kltn.tiensanglocvay.engine.ProductCode;
import vn.kltn.tiensanglocvay.engine.SimulationInput;

/** Schema SimulationRequest (dùng cho /simulations và POST /plans); {@code screeningId} là mã công khai (UUID). */
public record SimulationRequest(
        @NotNull(message = "Không được để trống.") UUID screeningId,
        @NotNull(message = "Không được để trống.") ProductCode productCode,
        @NotNull(message = "Không được để trống.") @Min(value = 0, message = "Không được âm.")
        @Max(value = ScreeningRequest.MAX_MONEY, message = "Số tiền quá lớn.") Long amount,
        @NotNull(message = "Không được để trống.") @Min(value = 1, message = "Kỳ hạn phải lớn hơn 0.") Integer termMonths,
        @NotNull(message = "Không được để trống.") List<@NotNull(message = "Không được để trống.") Integer> crossSellIds) {

    public SimulationInput toEngine() {
        return new SimulationInput(productCode, amount, termMonths, List.copyOf(crossSellIds));
    }
}
