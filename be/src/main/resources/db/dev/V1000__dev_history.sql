-- V1000__dev_history.sql – CHỈ profile dev (application-dev.yml thêm classpath:db/dev vào spring.flyway.locations; test không chạy file này).
-- Dữ liệu lịch sử cho màn hình Thống kê / Độ chính xác mô hình / Báo cáo hiệu chỉnh (QT09): khoảng 90 hồ sơ trong 6 tháng ở 3 chi nhánh,
-- bám theo hàm historical() trong fe/src/mocks/db.ts (cùng phân bố gói, số tiền, điểm, trạng thái, lý do từ chối, trình tự lịch sử).
-- Ngày tính lùi từ lúc chạy migration (now()); setseed cố định nên mỗi lần dựng lại DB cho cùng bộ số (theo cùng ngày chạy).
-- Khách hàng lịch sử: số điện thoại 0968800001…0968800090, mật khẩu 123456. Giấy tờ lịch sử không có tệp thật (storage_path rỗng).

DO $$
DECLARE
    ho        text[] := ARRAY['Nguyễn','Trần','Lê','Phạm','Hoàng','Vũ','Võ','Đặng','Bùi','Đỗ','Hồ','Ngô','Dương','Lý'];
    dem_nam   text[] := ARRAY['Văn','Minh','Quang','Đức','Hữu','Thành','Anh','Gia'];
    dem_nu    text[] := ARRAY['Thị','Thu','Ngọc','Thanh','Minh','Phương','Hải'];
    ten_nam   text[] := ARRAY['An','Bình','Cường','Dũng','Hùng','Huy','Khoa','Long','Nam','Phong','Quân','Sơn','Tuấn','Việt'];
    ten_nu    text[] := ARRAY['Anh','Chi','Giang','Hà','Hạnh','Hương','Lan','Linh','Mai','Ngân','Trang','Vy','Yến','Thảo'];
    streets   text[] := ARRAY['Láng Hạ','Trần Duy Hưng','Nguyễn Trãi','Lê Lợi','Hai Bà Trưng','Điện Biên Phủ','Nguyễn Văn Linh','Bạch Đằng'];
    purposes  text[] := ARRAY['Mua sắm đồ dùng, thiết bị','Mua xe máy, ô tô','Sửa chữa, nâng cấp nhà ở','Học tập','Chữa bệnh','Cưới hỏi'];
    banks     text[] := ARRAY['ABBANK','Vietcombank','Techcombank','BIDV','VietinBank'];
    open_st   text[] := ARRAY['DRAFTING','WAITING_VERIFY','WAITING_VERIFY','NEED_MORE','VERIFIED','VERIFIED','APPROVED'];
    reasons   text[] := ARRAY['Lịch sử tín dụng (CIC) có nợ cần chú ý','Thu nhập thực tế không đủ đáp ứng nghĩa vụ trả nợ',
                              'Vượt room tín dụng của chi nhánh trong kỳ'];
    -- mật khẩu 123456 (cùng hash với seed V2)
    pw        constant text := '$2b$10$HlSDoPkE1d9WhpUrxM53j.SHPexrEC0l5dx9oBlmE7Bvw9F.PkPjS';
    need_more constant text := 'Sao kê lương không có dấu xác nhận của ngân hàng';

    v_version int;  v_td int;  v_tc int;
    b_ids int[];  b_addr text[];  s_ids int[];  s_names text[];
    i int;  k int;  j int;
    male boolean;  nm text;  phone text;  cid bigint;  days_ago int;  created timestamptz;  t timestamptz;
    app_code text;  prod int;  is_td boolean;  income bigint;  amount bigint;  term int;  rate numeric;  score numeric;
    bi int;  st text;  p_appr double precision;  r double precision;  monthly bigint;  total_interest bigint;
    dob date;  addr text;  purpose text;  submitted boolean;  reason text;
    consent_a bigint;  consent_b bigint;  scr bigint;  plan bigint;  app bigint;  ver bigint;  doc record;  doc_id bigint;
    ev_st text[];  ev_at timestamptz[];  ev_kind text[];  ev_note text[];
    staff_id int;  staff_name text;  from_st text;  actor_id bigint;  actor_name text;
BEGIN
    PERFORM setseed(0.20261021);
    SELECT id INTO v_version FROM param_version WHERE version_no = '2026.10';
    IF v_version IS NULL THEN
        SELECT min(id) INTO v_version FROM param_version;
    END IF;
    SELECT id INTO v_td FROM loan_product WHERE code = 'TD';
    SELECT id INTO v_tc FROM loan_product WHERE code = 'TC';
    SELECT array_agg(b.id ORDER BY b.id), array_agg(b.address ORDER BY b.id),
           array_agg(s.id ORDER BY b.id), array_agg(s.full_name ORDER BY b.id)
      INTO b_ids, b_addr, s_ids, s_names
      FROM branch b
      JOIN staff s ON s.email = CASE b.code WHEN 'HN01' THEN 'nhanvien@tiensangloc.local'
                                            WHEN 'HCM01' THEN 'nv.hcm@tiensangloc.local'
                                            ELSE 'nv.danang@tiensangloc.local' END
     WHERE b.code IN ('HN01', 'HCM01', 'DN01');

    FOR i IN 1..90 LOOP
        male := random() < 0.5;
        IF male THEN
            nm := ho[1 + floor(random() * 14)::int] || ' ' || dem_nam[1 + floor(random() * 8)::int] || ' ' || ten_nam[1 + floor(random() * 14)::int];
        ELSE
            nm := ho[1 + floor(random() * 14)::int] || ' ' || dem_nu[1 + floor(random() * 7)::int] || ' ' || ten_nu[1 + floor(random() * 14)::int];
        END IF;
        phone := '09688' || lpad(i::text, 5, '0');
        days_ago := floor(power(random(), 0.8) * 180)::int + 1;
        IF i <= 7 THEN
            -- khác mock: 7 hồ sơ đầu luôn là hồ sơ gần đây mang lần lượt các trạng thái đang mở, để màn hình có đủ 9 trạng thái
            days_ago := 1 + days_ago % 25;
        END IF;
        created := now() - make_interval(days => days_ago);
        is_td := random() < 0.68;
        prod := CASE WHEN is_td THEN v_td ELSE v_tc END;
        income := round((9 + random() * 36) * 1e6 / 5e5) * 5e5;
        amount := CASE WHEN is_td THEN round((30 + random() * 320) / 5) * 5e6 ELSE round((10 + random() * 120) / 5) * 5e6 END;
        term := CASE WHEN is_td THEN (ARRAY[12, 24, 36, 48, 60])[1 + floor(random() * 5)::int] ELSE 12 END;
        rate := CASE WHEN is_td THEN 12.5 ELSE 18 END;
        score := round((35 + random() * 60)::numeric, 1);
        bi := 1 + floor(random() * array_length(b_ids, 1))::int;
        staff_id := s_ids[bi];
        staff_name := s_names[bi];

        -- trạng thái: hồ sơ cũ đã đóng, hồ sơ gần đây còn mở (xác suất duyệt theo điểm như mock)
        p_appr := 1 / (1 + exp(-(score - 58) / 7));
        IF days_ago > 25 THEN
            r := random();
            st := CASE WHEN r < 0.08 THEN 'CANCELLED' WHEN r < 0.13 THEN 'EXPIRED'
                       WHEN random() < p_appr THEN 'DISBURSED' ELSE 'REJECTED' END;
        ELSE
            st := open_st[1 + floor(random() * 7)::int];
            IF i <= 7 THEN
                st := open_st[i];
            END IF;
        END IF;
        monthly := CASE WHEN is_td THEN round(amount::numeric / term + amount * rate / 1200) ELSE round(amount * rate / 1200) END;
        total_interest := CASE WHEN is_td THEN round(amount * rate / 1200 * (term + 1) / 2) ELSE round(amount * rate / 100) END;
        dob := make_date(1965 + floor(random() * 37)::int, 1 + floor(random() * 12)::int, 1 + floor(random() * 27)::int);
        addr := (1 + floor(random() * 300)::int) || ' ' || streets[1 + floor(random() * 8)::int] || ', ' || b_addr[bi];
        purpose := CASE WHEN is_td THEN purposes[1 + floor(random() * 6)::int] ELSE 'Chi tiêu cá nhân linh hoạt' END;
        submitted := st <> 'DRAFTING';

        INSERT INTO customer (phone, password_hash, full_name, dob, gender, address, status, created_at)
        VALUES (phone, pw, nm, dob, CASE WHEN male THEN 'MALE' ELSE 'FEMALE' END, addr, 'ACTIVE', created - interval '1 day')
        RETURNING id INTO cid;

        -- lượt sàng lọc trước khi lập hồ sơ (2–4 lượt, lượt cuối là lượt gốc của phương án) như past.screenings của mock
        k := 2 + floor(random() * 3)::int;
        FOR j IN 1..k LOOP
            t := created - make_interval(secs => random() * 5 * 86400);
            INSERT INTO consent (customer_id, scope, doc_version, agreed_at) VALUES (cid, 'A', 'DK-1.0', t) RETURNING id INTO consent_a;
            INSERT INTO screening (customer_id, version_id, consent_id, nationality, dob, gender, marital_status, dependents, avg_income,
                                   salary_bank, is_abbank_staff, credit_history, purpose, desired_amount, desired_term, created_at)
            VALUES (cid, v_version, consent_a, 'Việt Nam', dob, CASE WHEN male THEN 'MALE' ELSE 'FEMALE' END,
                    CASE WHEN random() < 0.5 THEN 'MARRIED' ELSE 'SINGLE' END, floor(random() * 3)::int, income,
                    banks[1 + floor(random() * 5)::int], random() < 0.1,
                    CASE WHEN random() < 0.7 THEN 'ON_TIME' ELSE 'NEVER_BORROWED' END, purpose, amount, term, t)
            RETURNING id INTO scr;
        END LOOP;
        -- phương án đã xác nhận của hồ sơ (+ một nửa số khách có thêm một phương án nháp)
        INSERT INTO loan_plan (screening_id, product_id, amount, term_months, ref_rate, monthly_payment, total_interest, dti_after, score,
                               status, created_at)
        VALUES (scr, prod, amount, term, rate, monthly, total_interest, least(999.99, round(monthly::numeric / income * 1000) / 10), score,
                'CONFIRMED', created - make_interval(secs => random() * 86400))
        RETURNING id INTO plan;
        IF random() < 0.5 THEN
            INSERT INTO loan_plan (screening_id, product_id, amount, term_months, ref_rate, monthly_payment, total_interest, dti_after,
                                   score, status, created_at)
            VALUES (scr, prod, amount, term, rate, monthly, total_interest, least(999.99, round(monthly::numeric / income * 1000) / 10),
                    score, 'DRAFT', created - make_interval(secs => random() * 3 * 86400));
        END IF;

        -- lịch sử trạng thái (trình tự ev() của mock); mốc không vượt quá hiện tại
        ev_st := ARRAY['DRAFTING'];  ev_at := ARRAY[created];  ev_kind := ARRAY['CUSTOMER'];  ev_note := ARRAY['Tạo hồ sơ'];
        t := created + make_interval(secs => (1 + random() * 2) * 86400);
        IF submitted AND st <> 'CANCELLED' THEN
            ev_st := ev_st || 'WAITING_VERIFY'::text;  ev_at := ev_at || least(t, now());  ev_kind := ev_kind || 'CUSTOMER'::text;  ev_note := ev_note || NULL::text;
            t := t + make_interval(secs => (1 + random() * 3) * 86400);
        END IF;
        IF st = 'CANCELLED' THEN
            ev_st := ev_st || 'CANCELLED'::text;  ev_at := ev_at || least(t, now());  ev_kind := ev_kind || 'CUSTOMER'::text;  ev_note := ev_note || 'Khách hàng hủy hồ sơ'::text;
        END IF;
        IF st = 'EXPIRED' THEN
            ev_st := ev_st || 'EXPIRED'::text;  ev_at := ev_at || least(t, now());  ev_kind := ev_kind || 'SYSTEM'::text;  ev_note := ev_note || 'Giấy tờ quá thời hạn hiệu lực'::text;
        END IF;
        IF st = 'NEED_MORE' THEN
            ev_st := ev_st || 'NEED_MORE'::text;  ev_at := ev_at || least(t, now());  ev_kind := ev_kind || 'STAFF'::text;  ev_note := ev_note || 'Sao kê lương không có dấu xác nhận'::text;
        END IF;
        IF st IN ('VERIFIED', 'APPROVED', 'DISBURSED', 'REJECTED') THEN
            ev_st := ev_st || 'VERIFIED'::text;  ev_at := ev_at || least(t, now());  ev_kind := ev_kind || 'STAFF'::text;  ev_note := ev_note || NULL::text;
            t := t + make_interval(secs => (1 + random() * 2) * 86400);
        END IF;
        IF st IN ('APPROVED', 'DISBURSED') THEN
            ev_st := ev_st || 'APPROVED'::text;  ev_at := ev_at || least(t, now());  ev_kind := ev_kind || 'STAFF'::text;  ev_note := ev_note || NULL::text;
            t := t + make_interval(secs => (1 + random() * 3) * 86400);
        END IF;
        IF st = 'DISBURSED' THEN
            ev_st := ev_st || 'DISBURSED'::text;  ev_at := ev_at || least(t, now());  ev_kind := ev_kind || 'STAFF'::text;  ev_note := ev_note || NULL::text;
        END IF;
        reason := NULL;
        IF st = 'REJECTED' THEN
            reason := reasons[1 + floor(random() * 3)::int];
            ev_st := ev_st || 'REJECTED'::text;  ev_at := ev_at || least(t, now());  ev_kind := ev_kind || 'STAFF'::text;  ev_note := ev_note || reason;
        END IF;

        app_code := 'HS' || to_char(created AT TIME ZONE 'Asia/Ho_Chi_Minh', 'YYMM') || '-' || lpad(i::text, 4, '0');
        INSERT INTO consent (customer_id, scope, doc_version, agreed_at) VALUES (cid, 'B', 'DK-1.0', created) RETURNING id INTO consent_b;
        INSERT INTO loan_application (code, customer_id, plan_id, branch_id, consent_id, version_id, status, content_hash, locked_at, qr_token,
                                      expires_at, created_at, updated_at, full_name, national_id, dob, gender, address)
        VALUES (app_code, cid, plan, b_ids[bi], consent_b, v_version, st,
                CASE WHEN submitted THEN md5(random()::text) || md5(random()::text) END,
                CASE WHEN submitted THEN ev_at[2] END,
                CASE WHEN submitted THEN 'QRH' || upper(substr(md5(random()::text), 1, 12)) END,
                CASE WHEN submitted THEN created + interval '31 days' END,
                created, ev_at[array_length(ev_at, 1)], nm, '0' || lpad(floor(random() * 1e11)::bigint::text, 11, '0'), dob,
                CASE WHEN male THEN 'MALE' ELSE 'FEMALE' END, addr)
        RETURNING id INTO app;

        FOR j IN 1..array_length(ev_st, 1) LOOP
            actor_id := CASE ev_kind[j] WHEN 'CUSTOMER' THEN cid WHEN 'STAFF' THEN staff_id END;
            actor_name := CASE ev_kind[j] WHEN 'CUSTOMER' THEN nm WHEN 'STAFF' THEN staff_name ELSE 'Hệ thống' END;
            INSERT INTO application_status_history (application_id, from_status, to_status, actor_type, actor_id, actor_name, note, created_at)
            VALUES (app, CASE WHEN j > 1 THEN ev_st[j - 1] END, ev_st[j], ev_kind[j], actor_id, actor_name, ev_note[j], ev_at[j]);
            INSERT INTO activity_log (application_id, actor_type, actor_id, action, detail, created_at)
            VALUES (app, ev_kind[j], actor_id, 'Đổi trạng thái',
                    '→ ' || CASE ev_st[j] WHEN 'DRAFTING' THEN 'Đang lập hồ sơ' WHEN 'WAITING_VERIFY' THEN 'Chờ đối chiếu'
                                         WHEN 'NEED_MORE' THEN 'Cần bổ sung' WHEN 'VERIFIED' THEN 'Đã đối chiếu'
                                         WHEN 'APPROVED' THEN 'Được phê duyệt' WHEN 'DISBURSED' THEN 'Đã giải ngân'
                                         WHEN 'REJECTED' THEN 'Bị từ chối' WHEN 'CANCELLED' THEN 'Đã hủy' ELSE 'Hết hạn' END
                         || coalesce(' (' || ev_note[j] || ')', ''),
                    ev_at[j]);
        END LOOP;

        -- giấy tờ theo danh mục của gói; hồ sơ đã nộp có đủ giấy tờ, hồ sơ đang lập có khoảng một nửa
        ver := NULL;
        IF st = 'NEED_MORE' THEN
            INSERT INTO verification (application_id, staff_id, overall_result, note, verified_at)
            VALUES (app, staff_id, 'MISMATCH', 'Sao kê lương không có dấu xác nhận', ev_at[array_length(ev_at, 1)]) RETURNING id INTO ver;
        ELSIF st IN ('VERIFIED', 'APPROVED', 'DISBURSED', 'REJECTED') THEN
            INSERT INTO verification (application_id, staff_id, overall_result, note, verified_at)
            VALUES (app, staff_id, 'MATCH', 'Giấy tờ khớp bản gốc', ev_at[array_position(ev_st, 'VERIFIED')]) RETURNING id INTO ver;
        END IF;
        FOR doc IN SELECT pdt.document_type_id AS type_id, pdt.required FROM product_document_type pdt
                    WHERE pdt.product_id = prod ORDER BY pdt.document_type_id LOOP
            IF submitted OR random() < 0.5 THEN
                INSERT INTO application_document (application_id, document_type_id, file_name, file_format, file_size, storage_path, version_no,
                                                  issued_date, quality_status, uploaded_at, status, required, verify_result, verify_reason)
                VALUES (app, doc.type_id, CASE WHEN doc.type_id = 1 THEN 'giay-de-nghi-vay-von.pdf' ELSE 'tai-lieu-' || doc.type_id || '.pdf' END,
                        'PDF', 300 + floor(random() * 900)::int, NULL, 1, ((created - interval '3 days') AT TIME ZONE 'Asia/Ho_Chi_Minh')::date,
                        'OK', least(created + interval '1 day', now()), 'CONFIRMED', doc.required,
                        CASE WHEN ver IS NULL OR doc.type_id = 1 THEN NULL
                             WHEN st = 'NEED_MORE' AND doc.type_id = 5 THEN 'MISMATCH' ELSE 'MATCH' END,
                        CASE WHEN st = 'NEED_MORE' AND doc.type_id = 5 THEN need_more END)
                RETURNING id INTO doc_id;
                IF ver IS NOT NULL AND doc.type_id <> 1 THEN
                    INSERT INTO verification_detail (verification_id, document_id, result, reason)
                    VALUES (ver, doc_id, CASE WHEN st = 'NEED_MORE' AND doc.type_id = 5 THEN 'MISMATCH' ELSE 'MATCH' END,
                            CASE WHEN st = 'NEED_MORE' AND doc.type_id = 5 THEN need_more ELSE '' END);
                END IF;
            ELSE
                INSERT INTO application_document (application_id, document_type_id, version_no, status, required)
                VALUES (app, doc.type_id, 1, 'MISSING', doc.required);
            END IF;
        END LOOP;

        IF submitted THEN
            INSERT INTO loan_request_form (application_id, content, status, created_at, otp_confirmed_at)
            VALUES (app, '[]', 'CONFIRMED', created, ev_at[2]);
        END IF;

        IF st IN ('APPROVED', 'DISBURSED') THEN
            INSERT INTO approval_result (application_id, staff_id, result_type, approved_amount, approved_term, approved_rate, recorded_at,
                                         disbursed_at, disbursed_amount, beneficiary)
            VALUES (app, staff_id, 'APPROVED', amount, term, rate, ev_at[array_position(ev_st, 'APPROVED')],
                    CASE WHEN st = 'DISBURSED' THEN (ev_at[array_position(ev_st, 'DISBURSED')] AT TIME ZONE 'Asia/Ho_Chi_Minh')::date END,
                    CASE WHEN st = 'DISBURSED' THEN amount END,
                    CASE WHEN st = 'DISBURSED' THEN CASE WHEN is_td THEN 'Đơn vị cung cấp hàng hóa, dịch vụ' ELSE 'Tài khoản thanh toán của khách hàng' END END);
        ELSIF st = 'REJECTED' THEN
            INSERT INTO approval_result (application_id, staff_id, result_type, reason, recorded_at)
            VALUES (app, staff_id, 'REJECTED', reason, ev_at[array_length(ev_at, 1)]);
        END IF;
    END LOOP;

    -- 120 lượt sàng lọc ẩn danh rải trong 180 ngày (không lập hồ sơ)
    FOR j IN 1..120 LOOP
        t := now() - make_interval(secs => random() * 180 * 86400);
        INSERT INTO consent (customer_id, scope, doc_version, agreed_at) VALUES (NULL, 'A', 'DK-1.0', t) RETURNING id INTO consent_a;
        INSERT INTO screening (customer_id, version_id, consent_id, nationality, dob, gender, marital_status, dependents, avg_income,
                               salary_bank, is_abbank_staff, credit_history, purpose, desired_amount, desired_term, created_at)
        VALUES (NULL, v_version, consent_a, 'Việt Nam', make_date(1970 + floor(random() * 33)::int, 1 + floor(random() * 12)::int, 1 + floor(random() * 27)::int),
                CASE WHEN random() < 0.5 THEN 'MALE' ELSE 'FEMALE' END, CASE WHEN random() < 0.5 THEN 'MARRIED' ELSE 'SINGLE' END,
                floor(random() * 3)::int, round((7 + random() * 38) * 1e6 / 5e5) * 5e5, banks[1 + floor(random() * 5)::int], false,
                (ARRAY['ON_TIME','NEVER_BORROWED','UNSURE','LATE_OVER_90'])[1 + floor(random() * 4)::int],
                purposes[1 + floor(random() * 6)::int], round((20 + random() * 300) / 5) * 5e6, (ARRAY[12, 24, 36])[1 + floor(random() * 3)::int], t);
    END LOOP;
END $$;
