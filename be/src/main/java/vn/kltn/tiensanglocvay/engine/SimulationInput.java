package vn.kltn.tiensanglocvay.engine;

import java.util.List;

/** Phần SimulationRequest mà bộ máy dùng (không có screeningId). */
public record SimulationInput(ProductCode productCode, long amount, int termMonths, List<Integer> crossSellIds) {
}
