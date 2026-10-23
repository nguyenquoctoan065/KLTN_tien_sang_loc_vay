package vn.kltn.tiensanglocvay.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Bảng document_type (LOẠI GIẤY TỜ). */
@Entity
@Table(name = "document_type")
public class DocumentType {

    /** Giấy đề nghị vay vốn do hệ thống lập ở bước 4.2, không tải lên (SYSTEM_DOC_TYPE trong fe/src/mocks/db.ts). */
    public static final int SYSTEM_DOC_TYPE = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String name;

    @Column(name = "doc_group", nullable = false)
    private String group;

    @Column(name = "allowed_formats", nullable = false)
    private String allowedFormats;

    @Column(name = "validity_days")
    private Integer validityDays;

    @Column(nullable = false)
    private String status;

    protected DocumentType() {
    }

    public DocumentType(String name, String group, String allowedFormats, Integer validityDays, String status) {
        update(name, group, allowedFormats, validityDays, status);
    }

    public void update(String name, String group, String allowedFormats, Integer validityDays, String status) {
        this.name = name;
        this.group = group;
        this.allowedFormats = allowedFormats;
        this.validityDays = validityDays;
        this.status = status;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getGroup() {
        return group;
    }

    public String getAllowedFormats() {
        return allowedFormats;
    }

    public Integer getValidityDays() {
        return validityDays;
    }

    public String getStatus() {
        return status;
    }
}
