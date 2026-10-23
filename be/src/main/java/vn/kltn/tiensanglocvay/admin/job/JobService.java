package vn.kltn.tiensanglocvay.admin.job;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import vn.kltn.tiensanglocvay.admin.AdminDtos.JobInfo;
import vn.kltn.tiensanglocvay.audit.ActivityLogService;
import vn.kltn.tiensanglocvay.common.ApiException;
import vn.kltn.tiensanglocvay.security.CurrentUser;

/**
 * 1.7 Tác vụ định kỳ: danh sách và chạy (tay hoặc theo lịch). Lịch hiển thị sinh từ cron {@code app.jobs.*} trong application.yml.
 * Một tác vụ không chạy chồng lên chính nó (một tiến trình backend): đang chạy thì lần gọi sau nhận 409.
 */
@Service
public class JobService {

    static final String MSG_NOT_FOUND = "Không có tác vụ.";
    static final String MSG_RUNNING = "Tác vụ đang chạy, vui lòng thử lại sau.";

    private static final Pattern DAILY = Pattern.compile("0 (\\d{1,2}) (\\d{1,2}) \\* \\* \\*");
    private static final Pattern MONTHLY = Pattern.compile("0 (\\d{1,2}) (\\d{1,2}) (\\d{1,2}) \\* \\*");

    private final Map<String, ScheduledJob> jobs;
    private final Map<String, String> crons;
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final JobRecordRepository records;
    private final ActivityLogService activity;
    private final TransactionTemplate tx;
    private final Clock clock;

    public JobService(List<ScheduledJob> jobs, JobRecordRepository records, ActivityLogService activity, TransactionTemplate tx, Clock clock,
                      @Value("${app.jobs.expire-documents}") String expireCron, @Value("${app.jobs.purge-retention}") String purgeCron,
                      @Value("${app.jobs.remind-drafts}") String remindCron) {
        this.jobs = jobs.stream().collect(Collectors.toMap(ScheduledJob::code, Function.identity()));
        this.crons = Map.of("EXPIRE_DOCUMENTS", expireCron, "PURGE_RETENTION", purgeCron, "REMIND_DRAFTS", remindCron);
        this.records = records;
        this.activity = activity;
        this.tx = tx;
        this.clock = clock;
    }

    public List<JobInfo> list() {
        return records.findAllByOrderByCode().stream().map(this::info).toList();
    }

    /** Chạy một tác vụ; {@code actor} là quản trị viên khi chạy tay, {@code null} (hệ thống) khi chạy theo lịch. */
    public JobInfo run(String code, CurrentUser actor) {
        ScheduledJob job = jobs.get(code);
        if (job == null || !records.existsById(code)) {
            throw new ApiException(HttpStatus.NOT_FOUND, MSG_NOT_FOUND);
        }
        ReentrantLock lock = locks.computeIfAbsent(code, k -> new ReentrantLock());
        if (!lock.tryLock()) {
            throw new ApiException(HttpStatus.CONFLICT, MSG_RUNNING);
        }
        try {
            String result = job.run();
            return tx.execute(status -> {
                JobRecord r = records.findById(code).orElseThrow();
                r.ran(OffsetDateTime.now(clock), result);
                activity.log(actor, "Chạy tác vụ định kỳ", r.getName() + ": " + result, null);
                return info(r);
            });
        } finally {
            lock.unlock();
        }
    }

    private JobInfo info(JobRecord r) {
        return new JobInfo(r.getCode(), r.getName(), describe(crons.get(r.getCode())), r.getLastRunAt(), r.getLastResult());
    }

    /** "0 0 1 * * *" → "Hằng ngày lúc 01:00"; "0 0 2 1 * *" → "Ngày 1 hằng tháng lúc 02:00"; "-" (tắt) → "Không chạy tự động". */
    static String describe(String cron) {
        if (cron == null || cron.isBlank() || "-".equals(cron.trim())) {
            return "Không chạy tự động";
        }
        String c = cron.trim().replaceAll("\\s+", " ");
        Matcher m = DAILY.matcher(c);
        if (m.matches()) {
            return "Hằng ngày lúc " + hhmm(m.group(2), m.group(1));
        }
        m = MONTHLY.matcher(c);
        if (m.matches()) {
            return "Ngày " + Integer.parseInt(m.group(3)) + " hằng tháng lúc " + hhmm(m.group(2), m.group(1));
        }
        return "Theo lịch " + c;
    }

    private static String hhmm(String hour, String minute) {
        return String.format("%02d:%02d", Integer.parseInt(hour), Integer.parseInt(minute));
    }
}
