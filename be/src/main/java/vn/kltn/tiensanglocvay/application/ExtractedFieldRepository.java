package vn.kltn.tiensanglocvay.application;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExtractedFieldRepository extends JpaRepository<ExtractedField, Long> {

    List<ExtractedField> findByDocumentIdInOrderById(Collection<Long> documentIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from ExtractedField f where f.documentId = :documentId")
    void deleteByDocument(@Param("documentId") Long documentId);
}
