package vn.kltn.tiensanglocvay.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/** Schema {@code ApiError} trong openapi.yaml; {@code fields} chỉ có khi lỗi validate. */
public record ErrorBody(String message, @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, String> fields) {

    public static ErrorBody of(String message) {
        return new ErrorBody(message, null);
    }
}
