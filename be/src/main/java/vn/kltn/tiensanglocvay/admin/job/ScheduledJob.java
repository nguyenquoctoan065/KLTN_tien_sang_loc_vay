package vn.kltn.tiensanglocvay.admin.job;

/**
 * Một tác vụ định kỳ (docs/quy-tac-tinh-toan.md mục 13). Chạy theo lịch ({@link JobScheduler}) và chạy tay
 * (POST /admin/jobs/{code}/run) đều qua {@link JobService#run}; {@link #run()} trả câu kết quả ghi vào {@code last_result}.
 */
public interface ScheduledJob {

    String code();

    String run();
}
