package vn.kltn.tiensanglocvay.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import vn.kltn.tiensanglocvay.engine.Engine;
import vn.kltn.tiensanglocvay.engine.JsFormat;
import vn.kltn.tiensanglocvay.engine.ProductCode;

/** Lập nội dung giấy đề nghị vay vốn MB15 tự điền – chép {@code buildForm} trong fe/src/mocks/handlers/applications.ts. */
@Component
public class RequestFormBuilder {

    static final int EMPLOYMENT = 4;

    public List<LoanRequestForm.Item> build(AppBundle b, String branchName) {
        LoanApplication p = b.app;
        Map<String, String> old = new HashMap<>();
        if (b.form != null) {
            for (LoanRequestForm.Item c : b.form.getContent()) {
                if (c.editable()) {
                    old.put(c.key(), c.value());
                }
            }
        }
        String address = nz(p.getAddress());
        String income = or(val(b, CrossCheckService.STATEMENT, "avg_income"),
                p.getRecalc() != null ? JsFormat.vnd(p.getRecalc().verifiedIncome()) : "");
        boolean td = b.productCode == ProductCode.TD;
        String beneficiary = old.containsKey("beneficiary") ? old.get("beneficiary")
                : or(val(b, CrossCheckService.INVOICE, "beneficiary"), b.productCode == ProductCode.TC ? "Tài khoản thanh toán của người vay" : "");
        List<LoanRequestForm.Item> rows = new ArrayList<>();
        row(rows, "fullName", "Họ và tên người vay", nz(p.getFullName()), false);
        row(rows, "nationalId", "Số CCCD", nz(p.getNationalId()), false);
        row(rows, "dob", "Ngày sinh", p.getDob() == null ? "" : CrossCheckService.reverse(p.getDob().toString(), "-", "/"), false);
        row(rows, "address", "Địa chỉ thường trú", address, false);
        row(rows, "contactAddress", "Địa chỉ liên hệ", old.getOrDefault("contactAddress", address), true);
        row(rows, "phone", "Số điện thoại", b.phone, false);
        row(rows, "email", "Email", old.getOrDefault("email", nz(p.getEmail())), true);
        row(rows, "employer", "Đơn vị công tác", val(b, EMPLOYMENT, "employer"), false);
        row(rows, "income", "Thu nhập bình quân (theo sao kê)", income, false);
        row(rows, "product", "Sản phẩm vay", Engine.PRODUCT_NAMES.get(b.productCode), false);
        row(rows, "amount", "Số tiền đề nghị vay", JsFormat.vnd(b.plan.getAmount().longValueExact()) + " đồng", false);
        row(rows, "term", "Thời hạn vay", b.plan.getTermMonths() + " tháng", false);
        row(rows, "rate", "Lãi suất tham chiếu",
                JsFormat.num(b.plan.getRefRate().doubleValue()) + "%/năm (theo thông báo của ngân hàng tại thời điểm giải ngân)", false);
        row(rows, "purpose", "Mục đích sử dụng vốn", b.screening == null ? "" : b.screening.getPurpose(), false);
        row(rows, "method", "Phương thức trả nợ",
                td ? "Gốc trả đều hằng tháng, lãi trên dư nợ giảm dần" : "Lãi trả hằng tháng, gốc trả cuối kỳ", false);
        row(rows, "beneficiary", "Bên thụ hưởng giải ngân", beneficiary, true);
        row(rows, "repaymentSource", "Nguồn trả nợ", old.getOrDefault("repaymentSource", "Lương chuyển khoản hằng tháng"), true);
        row(rows, "branch", "Chi nhánh tiếp nhận", nz(branchName), false);
        row(rows, "note", "Ghi chú", old.getOrDefault("note", ""), true);
        return rows;
    }

    private static void row(List<LoanRequestForm.Item> rows, String key, String label, String value, boolean editable) {
        rows.add(new LoanRequestForm.Item(label, key, value == null ? "" : value, editable));
    }

    /** Mock {@code val()}: giá trị trên giấy tờ đã xác nhận, chuỗi rỗng nếu không có. */
    private static String val(AppBundle b, int typeId, String name) {
        return nz(b.value(typeId, name));
    }

    private static String or(String a, String b) {
        return a == null || a.isEmpty() ? b : a;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
