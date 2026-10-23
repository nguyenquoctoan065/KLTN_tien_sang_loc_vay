package vn.kltn.tiensanglocvay.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.kltn.tiensanglocvay.common.ApiException;
import vn.kltn.tiensanglocvay.common.Hashing;

/**
 * OTP gửi tới số điện thoại (docs/be-huong-dan.md mục 5.2). Dùng hai bước: {@link #check} (lần nhập sai được ghi nhận
 * ngay cả khi giao dịch gọi bị hủy) rồi {@link #consume} trong giao dịch của thao tác cần xác nhận.
 */
@Service
public class OtpService {

    /** Đăng ký tài khoản (chưa đăng nhập). */
    public static final String PURPOSE_REGISTER = "REGISTER";
    /** Khách hàng đã đăng nhập xác nhận một thao tác: nộp hồ sơ, rút đồng ý. */
    public static final String PURPOSE_CONFIRM = "CONFIRM";

    static final String MSG_WRONG = "Mã OTP không đúng.";
    static final String MSG_EXPIRED = "Mã OTP đã hết hạn hoặc nhập sai quá nhiều lần, vui lòng gửi lại mã mới.";
    static final int MAX_ATTEMPTS = 5;
    static final long TTL_MINUTES = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpCodeRepository repo;
    private final SmsSender sms;
    private final Clock clock;
    private final String fixedCode;

    public OtpService(OtpCodeRepository repo, SmsSender sms, Clock clock, @Value("${app.otp.fixed-code:}") String fixedCode) {
        this.repo = repo;
        this.sms = sms;
        this.clock = clock;
        this.fixedCode = fixedCode == null ? "" : fixedCode.trim();
    }

    /** Sinh mã mới, vô hiệu mã cũ chưa dùng của cùng số điện thoại và mục đích. */
    @Transactional
    public OtpSent issue(String phone, String purpose) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        repo.invalidate(phone, purpose, now);
        String code = fixedCode.isEmpty() ? String.format("%06d", RANDOM.nextInt(1_000_000)) : fixedCode;
        repo.save(new OtpCode(phone, purpose, hash(phone, purpose, code), now, now.plusMinutes(TTL_MINUTES)));
        sms.send(phone, code);
        return new OtpSent(true, fixedCode.isEmpty() ? null : code);
    }

    /** Kiểm tra mã; trả mã OTP để {@link #consume}. Lỗi → 400, số lần sai được lưu lại. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
    public long check(String phone, String purpose, String code) {
        Optional<OtpCode> found = repo.findFirstByTargetAndPurposeAndUsedAtIsNullOrderByIdDesc(phone, purpose);
        if (found.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MSG_WRONG);
        }
        OtpCode otp = found.get();
        if (otp.getAttempts() >= MAX_ATTEMPTS || !otp.getExpiresAt().isAfter(OffsetDateTime.now(clock))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MSG_EXPIRED);
        }
        if (code == null || !Hashing.constantTimeEquals(otp.getCodeHash(), hash(phone, purpose, code))) {
            otp.increaseAttempts();
            throw new ApiException(HttpStatus.BAD_REQUEST, MSG_WRONG);
        }
        return otp.getId();
    }

    /** Đánh dấu đã dùng; chạy trong giao dịch của thao tác gọi, nên thao tác lỗi thì mã vẫn còn dùng được. */
    @Transactional
    public void consume(long otpId) {
        if (repo.markUsed(otpId, OffsetDateTime.now(clock)) == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MSG_WRONG);
        }
    }

    private static String hash(String phone, String purpose, String code) {
        return Hashing.sha256Hex(phone + ":" + purpose + ":" + code);
    }
}
