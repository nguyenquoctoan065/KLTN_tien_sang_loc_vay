package vn.kltn.tiensanglocvay.engine;

/** TD: vay tiêu dùng; TC: vay thấu chi. */
public enum ProductCode {
    TD, TC;

    public ProductCode other() {
        return this == TD ? TC : TD;
    }
}
