package vn.kltn.tiensanglocvay.engine;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

/** Định dạng số giống JavaScript để câu chữ khớp từng ký tự với engine.ts. */
public final class JsFormat {

    private JsFormat() {
    }

    /** {@code n.toLocaleString('vi-VN')} với số nguyên: 1234567 → "1.234.567". */
    public static String vnd(double n) {
        DecimalFormatSymbols s = new DecimalFormatSymbols();
        s.setGroupingSeparator('.');
        s.setDecimalSeparator(',');
        s.setMinusSign('-');
        return new DecimalFormat("#,##0", s).format(Math.round(n));
    }

    /** {@code String(x)} trong template string: 12.5 → "12.5", 11.0 → "11". */
    public static String num(double x) {
        if (x == Math.rint(x) && Math.abs(x) < 1e15) {
            return Long.toString((long) x);
        }
        return Double.toString(x);
    }
}
