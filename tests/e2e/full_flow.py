"""Chạy: pip install playwright && python tests/e2e/full_flow.py  (BASE_URL mặc định http://localhost:5173)
Kiểm thử toàn luồng: sàng lọc -> phương án -> hồ sơ -> nộp -> đối chiếu -> bổ sung -> duyệt -> giải ngân; quản trị."""
import asyncio, re, sys
from playwright.async_api import async_playwright
import os
HERE = os.path.dirname(os.path.abspath(__file__))
B = os.environ.get('BASE_URL', 'http://localhost:5173')
S = os.path.join(HERE, 'shots') + '/'
T = os.path.join(HERE, 'files') + '/'
os.makedirs(S, exist_ok=True)

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
    await pg.wait_for_timeout(1300)
    if confirm:
        btn = card.locator('button:has-text("Xác nhận thông tin đúng")')
        if await btn.count():
            await btn.click(); await pg.wait_for_timeout(700)

async def main():
    errs = []
    async with async_playwright() as p:
        b = await p.chromium.launch()
        ctx = await b.new_context(viewport={'width': 1366, 'height': 900})
        pg = await ctx.new_page()
        pg.on('pageerror', lambda e: errs.append(str(e)))
        pg.on('console', lambda m: errs.append(m.text) if m.type == 'error' else None)
        # ---------- QT01, QT02 ----------
        await login(pg, '0901234567')
        await pg.goto(B + '/sang-loc')
        await pg.click('text=Tôi đã đọc và đồng ý'); await pg.click('button:has-text("Tiếp tục")')
        await pg.fill('input[placeholder="dd/mm/yyyy"]', '15/05/1992'); await pg.keyboard.press('Enter')
        await pg.click('text=Đã kết hôn'); await pg.fill('#dependents', '1')
        await pg.click('button:has-text("Tiếp tục")')
        await pg.fill('#avgIncome', '20000000')
        await pg.click('#salaryBank'); await pg.click('.ant-select-item-option >> text=Vietcombank')
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
        await pg.evaluate("localStorage.removeItem('poc-token'); localStorage.removeItem('poc-user')")
        await login(pg, 'nhanvien@poc.local', internal=True)
        await shot(pg, '25_staff_home')
        await pg.fill('input[placeholder="Nhập hoặc quét mã QR trên phiếu MB08"]', qr); await pg.keyboard.press('Enter')
        await pg.wait_for_selector('text=Phiếu đối chiếu giấy tờ bản gốc')
        row = pg.locator('.ant-card:has-text("Phiếu đối chiếu giấy tờ bản gốc") tr:has-text("Sao kê")')
        await row.locator('label:has-text("Không khớp")').click()
        await row.locator('input[placeholder="Bắt buộc"]').fill('Sao kê không có dấu xác nhận của ngân hàng')
        await shot(pg, '26_verify_form')
        await pg.click('button:has-text("Lưu và yêu cầu bổ sung")'); await pg.wait_for_timeout(800)
        # ---------- khách hàng bổ sung ----------
        await pg.evaluate("localStorage.removeItem('poc-token'); localStorage.removeItem('poc-user')")
        await login(pg, '0901234567')
        await pg.goto(app_url); await pg.wait_for_selector('text=Hồ sơ cần bổ sung')
        await shot(pg, '27_need_more')
        await upload(pg, 'Sao kê', 'saoke.pdf')
        await pg.click('button:has-text("Nộp lại hồ sơ")')
        await otp(pg, 'Nộp lại')
        await pg.wait_for_selector('text=Chờ đối chiếu tại', timeout=8000)
        # ---------- nhân viên: khớp, duyệt, giải ngân ----------
        await pg.evaluate("localStorage.removeItem('poc-token'); localStorage.removeItem('poc-user')")
        await login(pg, 'nhanvien@poc.local', internal=True)
        await pg.fill('input[placeholder="Nhập hoặc quét mã QR trên phiếu MB08"]', qr); await pg.keyboard.press('Enter')
        await pg.wait_for_selector('text=Phiếu đối chiếu giấy tờ bản gốc')
        await pg.click('button:has-text("Xác nhận khớp toàn bộ")'); await pg.wait_for_timeout(800)
        await pg.wait_for_selector('text=Kết quả xét duyệt (MB16)')
        await shot(pg, '28_decision')
        await pg.click('button:has-text("Ghi nhận kết quả")'); await pg.wait_for_timeout(800)
        await pg.wait_for_selector('text=Thông tin giải ngân')
        await pg.click('button:has-text("Xác nhận đã giải ngân")'); await pg.wait_for_timeout(800)
        await shot(pg, '29_disbursed_staff')
        # ---------- khách hàng: theo dõi, dữ liệu cá nhân ----------
        await pg.evaluate("localStorage.removeItem('poc-token'); localStorage.removeItem('poc-user')")
        await login(pg, '0901234567')
        await pg.goto(app_url + '?tab=theo-doi'); await pg.wait_for_selector('text=Lịch sử trạng thái')
        await shot(pg, '30_timeline')
        await pg.click('button[aria-label="Thông báo"]'); await pg.wait_for_timeout(500)
        await shot(pg, '31_notifications')
        await pg.keyboard.press('Escape')
        await pg.goto(B + '/cua-toi?tab=du-lieu'); await pg.wait_for_selector('text=Nhật ký truy cập và thao tác')
        await shot(pg, '32_mydata')
        # ---------- quản trị ----------
        await pg.evaluate("localStorage.removeItem('poc-token'); localStorage.removeItem('poc-user')")
        await login(pg, 'quantri@poc.local', internal=True)
        await pg.wait_for_selector('text=Phễu chuyển đổi'); await pg.wait_for_timeout(1200)
        await shot(pg, '40_stats')
        await pg.click('div[role=tab]:has-text("Độ chính xác mô hình")'); await pg.wait_for_timeout(1200)
        await shot(pg, '41_accuracy')
        await pg.click('div[role=tab]:has-text("Báo cáo MB14")'); await pg.wait_for_timeout(1500)
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
        print('ERRORS', [e for e in errs if 'favicon' not in e][:10])
        await b.close()

asyncio.run(main())
