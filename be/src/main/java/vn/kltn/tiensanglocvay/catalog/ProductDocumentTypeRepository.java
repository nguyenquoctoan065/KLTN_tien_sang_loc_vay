package vn.kltn.tiensanglocvay.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductDocumentTypeRepository extends JpaRepository<ProductDocumentType, ProductDocumentType.Key> {

    /**
     * Loại giấy tờ của một gói kèm cờ bắt buộc, dạng {@code [DocumentType, Boolean]}. Giấy tờ bắt buộc đứng trước,
     * rồi theo mã loại; khớp thứ tự dữ liệu mẫu của mock (gói thấu chi: bắt buộc trước, tùy chọn sau).
     */
    @Query("""
            select t, r.required from ProductDocumentType r, DocumentType t, LoanProduct p
            where p.code = :code and r.productId = p.id and t.id = r.documentTypeId
            order by r.required desc, t.id
            """)
    List<Object[]> findRulesByProductCode(@Param("code") String code);

    /** Mọi quy tắc (GET /admin/catalog): theo gói, rồi bắt buộc trước, rồi theo mã loại giấy tờ (như checklist). */
    @Query("select r from ProductDocumentType r order by r.productId, r.required desc, r.documentTypeId")
    List<ProductDocumentType> findAllOrdered();
}
