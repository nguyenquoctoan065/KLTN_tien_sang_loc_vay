package vn.kltn.tiensanglocvay.application;

import java.util.List;
import java.util.Map;
import vn.kltn.tiensanglocvay.catalog.DocumentType;
import vn.kltn.tiensanglocvay.engine.EngineInput;
import vn.kltn.tiensanglocvay.engine.ProductCode;
import vn.kltn.tiensanglocvay.plan.LoanPlan;
import vn.kltn.tiensanglocvay.screening.Screening;

/**
 * Hồ sơ kèm dữ liệu liên quan nạp một lần cho mỗi thao tác (tương ứng {@code AppRecord} của mock).
 * {@code screening}/{@code input} rỗng nếu lượt sàng lọc gốc không còn; {@code form} rỗng nếu chưa có giấy đề nghị.
 */
public final class AppBundle {

    final LoanApplication app;
    final List<ApplicationDocument> docs;
    /** Trường trích xuất theo mã tài liệu, theo thứ tự id. */
    final Map<Long, List<ExtractedField>> fields;
    final Map<Integer, DocumentType> types;
    final LoanPlan plan;
    final ProductCode productCode;
    final List<Integer> crossSellIds;
    final Screening screening;
    final EngineInput input;
    final String phone;
    LoanRequestForm form;

    AppBundle(LoanApplication app, List<ApplicationDocument> docs, Map<Long, List<ExtractedField>> fields, Map<Integer, DocumentType> types,
              LoanPlan plan, ProductCode productCode, List<Integer> crossSellIds, Screening screening, EngineInput input, String phone,
              LoanRequestForm form) {
        this.app = app;
        this.docs = docs;
        this.fields = fields;
        this.types = types;
        this.plan = plan;
        this.productCode = productCode;
        this.crossSellIds = crossSellIds;
        this.screening = screening;
        this.input = input;
        this.phone = phone;
        this.form = form;
    }

    List<ExtractedField> fieldsOf(ApplicationDocument d) {
        return fields.getOrDefault(d.getId(), List.of());
    }

    String typeName(ApplicationDocument d) {
        DocumentType t = types.get(d.getDocumentTypeId());
        return t == null ? "" : t.getName();
    }

    /** Mock {@code fieldVal}: giá trị của trường trên giấy tờ đã CONFIRMED đầu tiên cùng loại, hoặc null. */
    String value(int typeId, String name) {
        for (ApplicationDocument d : docs) {
            if (d.getDocumentTypeId() == typeId && ApplicationDocument.CONFIRMED.equals(d.getStatus())) {
                return fieldsOf(d).stream().filter(f -> f.getFieldName().equals(name)).findFirst().map(ExtractedField::value).orElse(null);
            }
        }
        return null;
    }
}
