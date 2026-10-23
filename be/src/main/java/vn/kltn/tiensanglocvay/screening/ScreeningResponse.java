package vn.kltn.tiensanglocvay.screening;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import vn.kltn.tiensanglocvay.engine.ScreeningNote;
import vn.kltn.tiensanglocvay.engine.ScreeningOutcome;
import vn.kltn.tiensanglocvay.engine.ScreeningResultItem;

/** Schema ScreeningResponse (MB03); {@code id} là mã công khai (UUID). */
public record ScreeningResponse(UUID id, OffsetDateTime createdAt, String versionNo, ScreeningRequest input, int age, long livingCost,
                                long disposableIncome, double currentDti, List<ScreeningResultItem> results, List<ScreeningNote> notes,
                                Long savedToCustomerId) {

    static ScreeningResponse of(Screening s, ScreeningRequest input, ScreeningOutcome o, String versionNo) {
        return new ScreeningResponse(s.getPublicId(), s.getCreatedAt(), versionNo, input, o.age(), o.livingCost(), o.disposableIncome(),
                o.currentDti(), o.results(), o.notes(), s.getCustomerId());
    }
}
