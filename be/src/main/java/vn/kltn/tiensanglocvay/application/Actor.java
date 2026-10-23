package vn.kltn.tiensanglocvay.application;

/** Người thực hiện một lần chuyển trạng thái: {@code type} CUSTOMER / STAFF / SYSTEM, {@code name} hiển thị ở StatusEvent.actor. */
public record Actor(String type, Long id, String name) {

    public static final Actor SYSTEM = new Actor("SYSTEM", null, "Hệ thống");
}
