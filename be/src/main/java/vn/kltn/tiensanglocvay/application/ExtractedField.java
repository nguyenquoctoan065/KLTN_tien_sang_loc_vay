package vn.kltn.tiensanglocvay.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Bảng extracted_field (TRƯỜNG TRÍCH XUẤT, một dòng của MB06). */
@Entity
@Table(name = "extracted_field")
public class ExtractedField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false, updatable = false)
    private Long documentId;

    @Column(name = "field_name", nullable = false, updatable = false)
    private String fieldName;

    private String label;

    @Column(name = "ocr_value")
    private String ocrValue;

    private BigDecimal confidence;

    @Column(name = "confirmed_value")
    private String confirmedValue;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    protected ExtractedField() {
    }

    public ExtractedField(Long documentId, String fieldName, String label, String ocrValue, double confidence) {
        this.documentId = documentId;
        this.fieldName = fieldName;
        this.label = label;
        this.ocrValue = ocrValue;
        this.confidence = BigDecimal.valueOf(confidence);
    }

    public Long getId() {
        return id;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getLabel() {
        return label;
    }

    public String getOcrValue() {
        return ocrValue;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public String getConfirmedValue() {
        return confirmedValue;
    }

    void confirm(String value, OffsetDateTime at) {
        this.confirmedValue = value;
        this.confirmedAt = at;
    }

    /** Giá trị dùng để đối chiếu: đã xác nhận, nếu chưa thì máy đọc (mock {@code confirmedValue ?? ocrValue}). */
    public String value() {
        return confirmedValue != null ? confirmedValue : ocrValue;
    }
}
