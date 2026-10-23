package vn.kltn.tiensanglocvay.notification;

import java.time.OffsetDateTime;

/** Schema AppNotification. */
public record AppNotification(long id, String title, String body, String link, OffsetDateTime createdAt, boolean read) {

    static AppNotification of(Notification n) {
        return new AppNotification(n.getId(), n.getTitle(), n.getBody(), n.getLink(), n.getCreatedAt(), n.getReadAt() != null);
    }
}
