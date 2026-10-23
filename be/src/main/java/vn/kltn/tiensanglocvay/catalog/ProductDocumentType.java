package vn.kltn.tiensanglocvay.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;

/** Bảng product_document_type (GÓI VAY_LOẠI GIẤY TỜ): giấy tờ nào cần cho gói nào, có bắt buộc không. */
@Entity
@Table(name = "product_document_type")
@IdClass(ProductDocumentType.Key.class)
public class ProductDocumentType {

    public record Key(Integer productId, Integer documentTypeId) implements Serializable {
    }

    @Id
    @Column(name = "product_id")
    private Integer productId;

    @Id
    @Column(name = "document_type_id")
    private Integer documentTypeId;

    @Column(nullable = false)
    private boolean required;

    protected ProductDocumentType() {
    }

    public Integer getProductId() {
        return productId;
    }

    public Integer getDocumentTypeId() {
        return documentTypeId;
    }

    public boolean isRequired() {
        return required;
    }
}
