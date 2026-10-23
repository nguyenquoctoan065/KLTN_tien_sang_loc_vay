package vn.kltn.tiensanglocvay.document;

import java.util.List;
import java.util.Map;

/**
 * Trường trích xuất theo loại giấy tờ (docs/quy-tac-tinh-toan.md mục 9; khóa và nhãn giống {@code ocrFields} của fe/src/mocks/ctx.ts).
 * Dùng chung cho {@link FakeOcrService}, schema và prompt của {@link GeminiOcrService}, và dòng trống khi máy đọc lỗi (khách nhập tay).
 */
public final class DocumentFields {

    /** Kiểu giá trị: quyết định gợi ý định dạng trong prompt và cách chuẩn hóa kết quả máy đọc. */
    public enum Kind { TEXT, NAME, ID, DATE, GENDER, MONEY }

    public record Def(String key, String label, Kind kind) {
    }

    private static final Map<Integer, List<Def>> BY_TYPE = Map.of(
            2, List.of(
                    new Def("full_name", "Họ và tên", Kind.NAME),
                    new Def("national_id", "Số CCCD", Kind.ID),
                    new Def("dob", "Ngày sinh", Kind.DATE),
                    new Def("gender", "Giới tính", Kind.GENDER),
                    new Def("address", "Nơi thường trú", Kind.TEXT)),
            3, List.of(new Def("address", "Địa chỉ cư trú", Kind.TEXT)),
            4, List.of(
                    new Def("employer", "Đơn vị công tác", Kind.TEXT),
                    new Def("salary", "Mức lương theo hợp đồng", Kind.MONEY)),
            5, List.of(
                    new Def("salary_bank", "Ngân hàng trả lương", Kind.TEXT),
                    new Def("avg_income", "Lương chuyển khoản bình quân 03 tháng", Kind.MONEY)),
            6, List.of(
                    new Def("beneficiary", "Bên thụ hưởng", Kind.TEXT),
                    new Def("invoice_amount", "Giá trị hóa đơn, hợp đồng", Kind.MONEY)));

    private DocumentFields() {
    }

    /** Các trường của loại giấy tờ; rỗng nếu loại không có trường trích xuất (giấy đề nghị, loại mới thêm ở B7). */
    public static List<Def> of(int documentTypeId) {
        return BY_TYPE.getOrDefault(documentTypeId, List.of());
    }

    /** Nhãn của trường, hoặc chính khóa nếu không có trong danh mục. */
    public static String label(int documentTypeId, String key) {
        return of(documentTypeId).stream().filter(d -> d.key().equals(key)).map(Def::label).findFirst().orElse(key);
    }

    /** Dòng trống (giá trị rỗng, độ tin cậy 0) để khách nhập tay khi máy đọc lỗi. */
    public static List<OcrResult.Field> blank(int documentTypeId) {
        return of(documentTypeId).stream().map(d -> new OcrResult.Field(d.key(), d.label(), "", 0)).toList();
    }
}
