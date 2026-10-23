package vn.kltn.tiensanglocvay.document;

/**
 * Tệp đã lưu trong kho (docs/be-huong-dan.md 5.3). Lưu vào application_document: {@code publicId} → storage_path,
 * {@code resourceType} → storage_resource_type, {@code version} → storage_version; {@code format} là đuôi chữ thường.
 */
public record StoredFile(String publicId, String resourceType, String format, long version, long bytes) {

    public static final String IMAGE = "image";
    public static final String RAW = "raw";

    public boolean isImage() {
        return IMAGE.equals(resourceType);
    }
}
