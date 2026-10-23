package vn.kltn.tiensanglocvay.engine;

import java.util.List;

/** Kết quả hàm {@code screen()}: phần tính toán của ScreeningResponse. */
public record ScreeningOutcome(List<ScreeningNote> notes, int age, long livingCost, long disposableIncome, double currentDti,
                               List<ScreeningResultItem> results) {
}
