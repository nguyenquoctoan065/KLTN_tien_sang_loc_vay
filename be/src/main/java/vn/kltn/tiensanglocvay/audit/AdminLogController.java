package vn.kltn.tiensanglocvay.audit;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.kltn.tiensanglocvay.common.ApiException;
import vn.kltn.tiensanglocvay.security.RequireRole;
import vn.kltn.tiensanglocvay.security.Role;

/**
 * GET /admin/logs (x-roles ADMIN): nhật ký hệ thống, tối đa 500 dòng mới nhất như mock; FE tự phân trang (20 dòng/trang).
 * {@code actorType} ngoài CUSTOMER / STAFF / SYSTEM → 400 kèm {@code fields} (mock trả rỗng).
 */
@RestController
@RequireRole(Role.ADMIN)
public class AdminLogController {

    private static final Set<String> TYPES = Set.of("CUSTOMER", "STAFF", "SYSTEM");

    private final ActivityLogRepository logs;

    public AdminLogController(ActivityLogRepository logs) {
        this.logs = logs;
    }

    @GetMapping("/admin/logs")
    @Transactional(readOnly = true)
    public List<ActivityLogDto> list(@RequestParam(required = false) String keyword, @RequestParam(required = false) String actorType) {
        String type = actorType == null ? "" : actorType;
        if (!type.isEmpty() && !TYPES.contains(type)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ.", Map.of("actorType", "Phải là CUSTOMER, STAFF hoặc SYSTEM."));
        }
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        String like = kw.isEmpty() ? "" : "%" + kw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        List<Object[]> rows = logs.searchAdmin(type, like);
        Map<Long, ActivityLog> byId = logs.findAllById(rows.stream().map(r -> ((Number) r[0]).longValue()).toList()).stream()
                .collect(Collectors.toMap(ActivityLog::getId, Function.identity()));
        return rows.stream().map(r -> {
            ActivityLog l = byId.get(((Number) r[0]).longValue());
            return new ActivityLogDto(l.getId(), (String) r[1], l.getActorType(), (String) r[2], l.getAction(),
                    l.getDetail() == null ? "" : l.getDetail(), l.getCreatedAt());
        }).toList(); // giữ thứ tự của truy vấn: mới nhất trước
    }
}
