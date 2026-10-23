package vn.kltn.tiensanglocvay.common;

/** Schema OkResult: {@code { "ok": true }}. */
public record OkResult(boolean ok) {

    public static OkResult yes() {
        return new OkResult(true);
    }
}
