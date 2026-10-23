package vn.kltn.tiensanglocvay.screening;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Schema ConsentInput (đồng ý MB02). {@code agreed} được ScreeningService kiểm tra trước để trả đúng thông báo của mock. */
public record ConsentInput(
        Boolean agreed,
        @NotBlank(message = "Không được để trống.") @Size(max = 20, message = "Tối đa 20 ký tự.") String docVersion) {
}
