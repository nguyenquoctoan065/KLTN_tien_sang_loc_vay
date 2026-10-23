package vn.kltn.tiensanglocvay.application;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationDocumentRepository extends JpaRepository<ApplicationDocument, Long> {

    /** Thứ tự tạo dòng = thứ tự danh mục MB05. */
    List<ApplicationDocument> findByApplicationIdOrderById(Long applicationId);
}
