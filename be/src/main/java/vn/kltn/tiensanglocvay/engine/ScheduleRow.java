package vn.kltn.tiensanglocvay.engine;

/** Schema ScheduleRow (một kỳ của MB04). */
public record ScheduleRow(int period, long principal, long interest, long payment, long balance) {
}
