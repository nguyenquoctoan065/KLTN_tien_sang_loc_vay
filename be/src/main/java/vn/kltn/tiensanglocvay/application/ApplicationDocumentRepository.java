package vn.kltn.tiensanglocvay.application;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationDocumentRepository extends JpaRepository<ApplicationDocument, Long> {

    /** Thứ tự tạo dòng = thứ tự danh mục MB05. */
    List<ApplicationDocument> findByApplicationIdOrderById(Long applicationId);

    /** Số giấy tờ đã tải (có tên tệp, trừ giấy đề nghị hệ thống) theo hồ sơ: mỗi dòng {@code [applicationId, count]}. */
    @Query("select d.applicationId, count(d) from ApplicationDocument d where d.applicationId in :applicationIds "
            + "and d.fileName is not null and d.documentTypeId <> :systemType group by d.applicationId")
    List<Object[]> countFiles(@Param("applicationIds") Collection<Long> applicationIds, @Param("systemType") int systemType);
}
