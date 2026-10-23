package vn.kltn.tiensanglocvay.document;

/**
 * Thông tin đã khai của hồ sơ. Chỉ {@link FakeOcrService} dùng để sinh giá trị máy đọc giả lập (như {@code ocrFields} của mock);
 * dịch vụ OCR thật bỏ qua. Ngày dạng yyyy-MM-dd; chuỗi rỗng nếu chưa khai. {@code avgIncome}, {@code salaryBank},
 * {@code screeningDob}, {@code screeningGender} rỗng (null) nếu không còn lượt sàng lọc gốc. {@code seed} = dung lượng KB.
 */
public record OcrContext(String fullName, String nationalId, String dob, String gender, String address, String screeningDob,
                         String screeningGender, Long avgIncome, String salaryBank, long planAmount, int seed) {

    /** Mọi trường trừ {@code seed} (chỉ biết sau khi kiểm tra dung lượng tệp). */
    public record Builder(String fullName, String nationalId, String dob, String gender, String address, String screeningDob,
                          String screeningGender, Long avgIncome, String salaryBank, long planAmount) {

        public OcrContext seed(int seed) {
            return new OcrContext(fullName, nationalId, dob, gender, address, screeningDob, screeningGender, avgIncome, salaryBank,
                    planAmount, seed);
        }
    }
}
