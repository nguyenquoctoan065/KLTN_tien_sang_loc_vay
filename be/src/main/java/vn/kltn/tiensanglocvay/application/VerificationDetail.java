package vn.kltn.tiensanglocvay.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;

/** Bảng verification_detail (V1) – một dòng VerificationDetail: kết quả đối chiếu một giấy tờ. */
@Entity
@Table(name = "verification_detail")
@IdClass(VerificationDetail.Key.class)
public class VerificationDetail {

    @Id
    @Column(name = "verification_id")
    private Long verificationId;

    @Id
    @Column(name = "document_id")
    private Long documentId;

    @Column(nullable = false, updatable = false)
    private String result;

    @Column(updatable = false)
    private String reason;

    protected VerificationDetail() {
    }

    public VerificationDetail(Long verificationId, Long documentId, String result, String reason) {
        this.verificationId = verificationId;
        this.documentId = documentId;
        this.result = result;
        this.reason = reason;
    }

    public Long getVerificationId() {
        return verificationId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public String getResult() {
        return result;
    }

    public String getReason() {
        return reason;
    }

    public static class Key implements Serializable {

        private Long verificationId;
        private Long documentId;

        public Key() {
        }

        public Key(Long verificationId, Long documentId) {
            this.verificationId = verificationId;
            this.documentId = documentId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && java.util.Objects.equals(verificationId, k.verificationId)
                    && java.util.Objects.equals(documentId, k.documentId);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(verificationId, documentId);
        }
    }
}
