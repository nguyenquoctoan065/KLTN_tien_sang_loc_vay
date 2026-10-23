package vn.kltn.tiensanglocvay.admin.job;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRecordRepository extends JpaRepository<JobRecord, String> {

    /** Theo mã: EXPIRE_DOCUMENTS, PURGE_RETENTION, REMIND_DRAFTS (đúng thứ tự của mock). */
    List<JobRecord> findAllByOrderByCode();
}
