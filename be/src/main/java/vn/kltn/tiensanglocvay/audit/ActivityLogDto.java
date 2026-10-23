package vn.kltn.tiensanglocvay.audit;

import java.time.OffsetDateTime;

/** Schema ActivityLog (GET /me/data, sau này GET /admin/logs); {@code detail} luôn là chuỗi (rỗng nếu không có). */
public record ActivityLogDto(long id, String applicationCode, String actorType, String actorName, String action, String detail,
                             OffsetDateTime createdAt) {
}
