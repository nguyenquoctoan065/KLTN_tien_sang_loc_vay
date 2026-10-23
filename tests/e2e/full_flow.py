"""Kiểm thử toàn luồng: sàng lọc -> phương án -> hồ sơ -> nộp -> đối chiếu -> bổ sung -> duyệt -> giải ngân; quản trị.

Cài đặt: pip install playwright ; python -m playwright install chromium
Chạy (frontend phải đang chạy ở --base-url):
  python tests\\e2e\\full_flow.py --mode real   # fe VITE_USE_MOCK=false + backend thật, DB mới (docker compose down -v; up -d)
  python tests\\e2e\\full_flow.py --mode mock   # fe chạy với $env:VITE_USE_MOCK="true"
Tham số cũng đặt được qua biến môi trường: E2E_MODE, BASE_URL, E2E_HEADED=1, E2E_OCR_TIMEOUT (giây chờ đọc giấy tờ).
In "ERRORS [...]"; thoát mã 1 nếu có lỗi."""
import argparse, asyncio, os, sys, urllib.request
from playwright.async_api import async_playwright, TimeoutError as PwTimeout

ap = argparse.ArgumentParser(description='Kiểm thử Playwright toàn luồng')
ap.add_argument('--mode', choices=['mock', 'real'], default=os.environ.get('E2E_MODE', 'mock'),
                help='mock: API giả lập (MSW); real: backend Spring Boot thật')
ap.add_argument('--base-url', default=os.environ.get('BASE_URL', 'http://localhost:5173'))
ap.add_argument('--headed', action='store_true', default=os.environ.get('E2E_HEADED') == '1', help='mở cửa sổ trình duyệt')
ap.add_argument('--ocr-timeout', type=int, default=int(os.environ.get('E2E_OCR_TIMEOUT', '0')) or None,
                help='giây chờ tải lên + đọc giấy tờ (mặc định mock 5, real 60 – Gemini thật chậm hơn)')
ARGS = ap.parse_args()
REAL = ARGS.mode == 'real'
OCR_MS = (ARGS.ocr_timeout or (60 if REAL else 5)) * 1000

HERE = os.path.dirname(os.path.abspath(__file__))
B = ARGS.base_url.rstrip('/')
S = os.path.join(HERE, 'shots') + '/'
T = os.path.join(HERE, 'files') + '/'
os.makedirs(S, exist_ok=True)

def preflight():
    """Chế độ real: backend phải trả lời qua proxy /api của Vite."""
    if not REAL:
        return
    try:
        with urllib.request.urlopen(B + '/api/actuator/health', timeout=5) as r:
            body = r.read().decode()
    except Exception as e:
        sys.exit(f'Không gọi được backend qua {B}/api/actuator/health ({e}). Chạy backend ở cổng 8080 và fe với VITE_USE_MOCK=false.')
    if '"UP"' not in body:
        sys.exit(f'Backend chưa sẵn sàng: {body}')
    print('Chế độ real: cần DB mới (docker compose down -v; docker compose up -d) vì luồng tạo hồ sơ, khóa chi nhánh Đà Nẵng.')

async def check_mode(pg):
    """MSW chạy bằng service worker: có controller = fe đang dùng API giả lập."""
    mocked = await pg.evaluate('!!(navigator.serviceWorker && navigator.serviceWorker.controller)')
    if mocked and REAL:
        sys.exit('Frontend đang chạy API giả lập (MSW) nhưng --mode real. Đặt VITE_USE_MOCK=false rồi chạy lại npm run dev.')
    if not mocked and not REAL:
        sys.exit('Frontend không chạy API giả lập nhưng --mode mock. Chạy fe với $env:VITE_USE_MOCK="true" hoặc dùng --mode real.')

async def shot(pg, name):
    await pg.wait_for_timeout(500)
    await pg.screenshot(path=S + name + '.png', full_page=True)

async def login(pg, user, internal=False):
    await pg.goto(B + ('/noi-bo/dang-nhap' if internal else '/dang-nhap'))
    await pg.fill('#username', user); await pg.fill('#password', '123456')
    await pg.click('form button[type=submit]')
    await pg.wait_for_timeout(900)

async def logout_all(ctx):
    await ctx.clear_cookies()

async def otp(pg, label):
    await pg.wait_for_selector('.ant-modal .ant-otp')
    inputs = pg.locator('.ant-modal .ant-otp input')
    for i, ch in enumerate('123456'):
        await inputs.nth(i).fill(ch)
    await pg.click(f'.ant-modal-footer button:has-text("{label}")')

async def doc_card(pg, title):
    return pg.locator('.doc-card', has=pg.locator(f'.ant-card-head-title:has-text("{title}")'))

async def upload(pg, title, file, confirm=True):
    card = await doc_card(pg, title)
    await card.locator('input[type=file]').set_input_files(T + file)
    # Chờ kết quả tải lên + đọc giấy tờ thay vì chờ cố định (backend thật, nhất là Gemini, lâu hơn mock)
    target = card.locator('button:has-text("Xác nhận thông tin đúng")') if confirm \
        else card.locator('text=Không đạt chất lượng')
    try:
        await target.first.wait_for(state='visible', timeout=OCR_MS)
    except PwTimeout:
        if confirm and await card.locator('text=Không đạt chất lượng').count():
            # Gặp khi chạy Gemini thật (profile dev): máy đọc từ chối ảnh, luồng không đi tiếp được
            reason = (await card.locator('.ant-alert').first.inner_text()).strip() if await card.locator('.ant-alert').count() else ''
            await shot(pg, '22_docs')
            sys.exit(f'DỪNG: {title}/{file} bị đánh giá không đạt chất lượng: {reason}')
        print(f'  ! {title}/{file}: không thấy kết quả sau {OCR_MS // 1000}s')
    if confirm and await target.count():
        await target.click(); await pg.wait_for_timeout(700)

async def main():
    errs = []
    async with async_playwright() as p:
        b = await p.chromium.launch(headless=not ARGS.headed)
        ctx = await b.new_context(viewport={'width': 1366, 'height': 900})
        pg = await ctx.new_page()
        pg.on('pageerror', lambda e: errs.append(str(e)))
        pg.on('console', lambda m: errs.append(m.text) if m.type == 'error' else None)
        # ---------- QT01, QT02 ----------
        await login(pg, '0901234567')
        await pg.goto(B + '/sang-loc'); await check_mode(pg)
        await pg.click('text=Tôi đã đọc và đồng ý'); await pg.click('button:has-text("Tiếp tục")')
        await pg.fill('input[placeholder="dd/mm/yyyy"]', '15/05/1992'); await pg.keyboard.press('Enter')
        await pg.click('text=Đã kết hôn'); await pg.fill('#dependents', '1')
        await pg.click('button:has-text("Tiếp tục")')
        await pg.fill('#avgIncome', '20000000')
        await pg.click('#salaryBank'); await pg.click('.ant-select-item-option >> text=Vietcombank')
        await pg.click('.ant-radio-wrapper >> text="Đã từng"'); await pg.click('.ant-radio-wrapper >> text="Không"')
        await pg.click('button:has-text("Tiếp tục")')
        await pg.click('#purpose'); await pg.click('.ant-select-item-option >> text=Mua xe máy, ô tô')
        await pg.fill('#desiredAmount', '150000000'); await pg.fill('#desiredTerm', '36')
        await pg.click('button:has-text("Tiếp tục")')
        await pg.click('button:has-text("Xem gợi ý gói vay")')
        await pg.wait_for_selector('text=Kết quả gợi ý gói vay')
        await pg.click('button:has-text("Mô phỏng phương án vay") >> nth=0')
        await pg.wait_for_selector('text=Phương án nằm trong ngưỡng an toàn', timeout=8000)
        await pg.click('button:has-text("Chọn phương án này")')
        await pg.wait_for_selector('text=Phương án vay đề xuất')
        await pg.click('button:has-text("Xác nhận phương án")')
        await pg.click('button:has-text("Lập hồ sơ vay")')
        # ---------- 4.1 ----------
        await pg.wait_for_selector('text=Lập hồ sơ vay trực tuyến')
        await pg.click('.branch-option >> text=Chi nhánh Hà Nội')
        await pg.click('text=Tôi đồng ý cho hệ thống lưu trữ')
        await shot(pg, '20_create_app')
        await pg.click('button:has-text("Tạo hồ sơ")')
        await pg.wait_for_selector('text=Danh mục hồ sơ cá nhân hóa')
        await pg.fill('#fullName', 'Nguyễn Văn Minh'); await pg.fill('#nationalId', '001092012345')
        await pg.fill('#address', '12 Láng Hạ, Đống Đa, Hà Nội'); await pg.fill('#email', 'minh@example.com')
        await pg.click('button:has-text("Lưu thông tin")'); await pg.wait_for_timeout(600)
        await upload(pg, 'Căn cước công dân', 'mo.jpg', confirm=False)
        await shot(pg, '21_blurry')
        await upload(pg, 'Căn cước công dân', 'cccd.jpg')
        await upload(pg, 'Xác nhận cư trú', 'cutru.jpg')
        await upload(pg, 'Hợp đồng lao động', 'hdld.jpg')
        await upload(pg, 'Sao kê', 'saoke.pdf')
        await upload(pg, 'Hóa đơn', 'hoadon.jpg')
        await shot(pg, '22_docs')
        # PlanAdjust nếu phương án không còn đạt
        if await pg.locator('button:has-text("Cập nhật phương án")').count():
            await pg.click('button:has-text("Cập nhật phương án")'); await pg.wait_for_timeout(800)
        # ---------- 4.2 ----------
        await pg.click('button:has-text("Lập giấy đề nghị")'); await pg.wait_for_timeout(700)
        await shot(pg, '23_request_form')
        await pg.click('button:has-text("Xác nhận bằng OTP và nộp hồ sơ")')
        await otp(pg, 'Xác nhận và nộp')
        await pg.wait_for_selector('text=Chờ đối chiếu tại', timeout=8000)
        app_url = pg.url.split('?')[0]
        await pg.click('div[role=tab]:has-text("Phiếu tóm tắt")')
        await shot(pg, '24_mb08')
        qr = (await pg.locator('.print-area code').last.inner_text()).strip()
        print('QR', qr, app_url)
        # ---------- 5.2 nhân viên: không khớp ----------
        await pg.evaluate("localStorage.removeItem('tsl-token'); localStorage.removeItem('tsl-user')")
        await login(pg, 'nhanvien@tiensangloc.local', internal=True)
        await shot(pg, '25_staff_home')
        await pg.fill('input[placeholder="Nhập hoặc quét mã QR trên phiếu tóm tắt"]', qr); await pg.keyboard.press('Enter')
        await pg.wait_for_selector('text=Phiếu đối chiếu giấy tờ bản gốc')
        row = pg.locator('.ant-card:has-text("Phiếu đối chiếu giấy tờ bản gốc") tr:has-text("Sao kê")')
        await row.locator('label:has-text("Không khớp")').click()
        await row.locator('[placeholder="Bắt buộc"]').fill('Sao kê không có dấu xác nhận của ngân hàng')
        await shot(pg, '26_verify_form')
        await pg.click('button:has-text("Lưu và yêu cầu bổ sung")'); await pg.wait_for_timeout(800)
        # ---------- khách hàng bổ sung ----------
        await pg.evaluate("localStorage.removeItem('tsl-token'); localStorage.removeItem('tsl-user')")
        await login(pg, 'khachhang@tiensangloc.local')
        await pg.goto(app_url); await pg.wait_for_selector('text=Hồ sơ cần bổ sung')
        await shot(pg, '27_need_more')
        await upload(pg, 'Sao kê', 'saoke.pdf')
        await pg.click('button:has-text("Nộp lại hồ sơ")')
        await otp(pg, 'Nộp lại')
        await pg.wait_for_selector('text=Chờ đối chiếu tại', timeout=8000)
        # ---------- nhân viên: khớp, duyệt, giải ngân ----------
        await pg.evaluate("localStorage.removeItem('tsl-token'); localStorage.removeItem('tsl-user')")
        await login(pg, 'nhanvien@tiensangloc.local', internal=True)
        await pg.fill('input[placeholder="Nhập hoặc quét mã QR trên phiếu tóm tắt"]', qr); await pg.keyboard.press('Enter')
        await pg.wait_for_selector('text=Phiếu đối chiếu giấy tờ bản gốc')
        await pg.click('button:has-text("Xác nhận khớp toàn bộ")'); await pg.wait_for_timeout(800)
        await pg.wait_for_selector('text=Kết quả xét duyệt')
        await shot(pg, '28_decision')
        await pg.click('button:has-text("Ghi nhận kết quả")'); await pg.wait_for_timeout(800)
        await pg.wait_for_selector('text=Thông tin giải ngân')
        await pg.click('button:has-text("Xác nhận đã giải ngân")'); await pg.wait_for_timeout(800)
        await shot(pg, '29_disbursed_staff')
        # ---------- khách hàng: theo dõi, dữ liệu cá nhân ----------
        await pg.evaluate("localStorage.removeItem('tsl-token'); localStorage.removeItem('tsl-user')")
        await login(pg, '0901234567')
        await pg.goto(app_url + '?tab=theo-doi'); await pg.wait_for_selector('text=Lịch sử trạng thái')
        await shot(pg, '30_timeline')
        await pg.click('button[aria-label="Thông báo"]'); await pg.wait_for_timeout(500)
        await shot(pg, '31_notifications')
        await pg.keyboard.press('Escape')
        await pg.goto(B + '/cua-toi?tab=du-lieu'); await pg.wait_for_selector('text=Nhật ký truy cập và thao tác')
        await shot(pg, '32_mydata')
        # ---------- quản trị ----------
        await pg.evaluate("localStorage.removeItem('tsl-token'); localStorage.removeItem('tsl-user')")
        await login(pg, 'quantri@tiensangloc.local', internal=True)
        await pg.wait_for_selector('text=Phễu chuyển đổi'); await pg.wait_for_timeout(1200)
        await shot(pg, '40_stats')
        await pg.click('div[role=tab]:has-text("Độ chính xác mô hình")'); await pg.wait_for_timeout(1200)
        await shot(pg, '41_accuracy')
        await pg.click('div[role=tab]:has-text("Báo cáo hiệu chỉnh mô hình")'); await pg.wait_for_timeout(1500)
        await shot(pg, '42_mb14')
        await pg.goto(B + '/noi-bo/nhan-vien'); await pg.wait_for_timeout(800); await shot(pg, '43_staff')
        await pg.goto(B + '/noi-bo/chi-nhanh'); await pg.wait_for_timeout(800)
        await pg.click('tr:has-text("Chi nhánh Đà Nẵng") button:has-text("Ngừng hoạt động")')
        await pg.click('.ant-modal .ant-select'); await pg.click('.ant-select-item-option >> text=Chi nhánh Hồ Chí Minh')
        await pg.click('.ant-modal-footer button:has-text("Ngừng hoạt động")'); await pg.wait_for_timeout(800)
        await shot(pg, '44_branches')
        await pg.goto(B + '/noi-bo/tac-vu'); await pg.wait_for_timeout(600)
        await pg.click('tr:has-text("giấy tờ quá thời hạn") button'); await pg.wait_for_timeout(900)
        await shot(pg, '45_jobs')
        await pg.goto(B + '/noi-bo/danh-muc'); await pg.wait_for_timeout(600)
        await pg.click('div[role=tab]:has-text("Giấy tờ theo gói vay")'); await pg.wait_for_timeout(400)
        await shot(pg, '46_catalog')
        await pg.goto(B + '/noi-bo/nhat-ky'); await pg.wait_for_timeout(800); await shot(pg, '47_logs')
        m = await b.new_page(viewport={'width': 390, 'height': 844})
        await m.goto(B + '/'); await m.evaluate("1")
        errs = [e for e in errs if 'favicon' not in e]
        print('ERRORS', errs[:10])
        await b.close()
        return 1 if errs else 0

preflight()
sys.exit(asyncio.run(main()))
