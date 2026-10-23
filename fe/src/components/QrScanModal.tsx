import { useEffect, useRef, useState } from 'react';
import { Alert, Modal, Spin, Typography } from 'antd';
import { Html5Qrcode } from 'html5-qrcode';

const READER_ID = 'qr-scan-reader';

/** Thông báo tiếng Việt cho lỗi mở camera (html5-qrcode trả chuỗi hoặc DOMException) */
function cameraError(e: unknown): string {
  const text = `${(e as { name?: string })?.name ?? ''} ${String(e)}`;
  if (!window.isSecureContext && !['localhost', '127.0.0.1'].includes(location.hostname))
    return 'Trình duyệt chỉ cho dùng camera trên kết nối bảo mật (HTTPS). Vui lòng nhập mã bằng tay.';
  if (/NotAllowed|Permission|denied/i.test(text)) return 'Bạn đã từ chối quyền dùng camera. Hãy cấp quyền camera cho trang này trong cài đặt trình duyệt rồi thử lại.';
  if (/NotFound|no camera|Requested device not found|OverconstrainedError/i.test(text)) return 'Không tìm thấy camera trên thiết bị này. Vui lòng nhập mã bằng tay.';
  if (/NotReadable|TrackStart|in use/i.test(text)) return 'Không mở được camera (có thể đang được ứng dụng khác sử dụng). Hãy đóng ứng dụng đó rồi thử lại.';
  return 'Không mở được camera. Vui lòng thử lại hoặc nhập mã bằng tay.';
}

/** Quét mã QR trên phiếu tóm tắt bằng camera; gọi onScan(mã) một lần rồi tự đóng */
export default function QrScanModal({ open, onClose, onScan }: { open: boolean; onClose: () => void; onScan: (code: string) => void }) {
  const [error, setError] = useState<string | null>(null);
  const [starting, setStarting] = useState(false);
  const onScanRef = useRef(onScan);
  onScanRef.current = onScan;

  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    let scanner: Html5Qrcode | null = null;
    let started = false;
    setError(null); setStarting(true);
    // Chờ Modal dựng xong phần tử đọc rồi mới khởi tạo
    const timer = window.setTimeout(async () => {
      try {
        if (!navigator.mediaDevices?.getUserMedia) throw new Error('NotFound');
        scanner = new Html5Qrcode(READER_ID, { verbose: false });
        await scanner.start(
          { facingMode: 'environment' },
          { fps: 10, qrbox: (w, h) => { const s = Math.floor(Math.min(w, h) * 0.75); return { width: s, height: s }; } },
          text => {
            const code = text.trim();
            if (!code || cancelled) return;
            cancelled = true;
            onScanRef.current(code);
          },
          () => { /* khung hình chưa có mã: bỏ qua */ },
        );
        started = true;
        if (cancelled) await stop();
      } catch (e) {
        if (!cancelled) setError(cameraError(e));
      } finally {
        if (!cancelled) setStarting(false);
      }
    }, 150);
    async function stop() {
      try { if (scanner && (started || scanner.isScanning)) { await scanner.stop(); } scanner?.clear(); } catch { /* đã tắt */ }
      started = false;
    }
    return () => { cancelled = true; window.clearTimeout(timer); void stop(); };
  }, [open]);

  return (
    <Modal title="Quét mã QR bằng camera" open={open} onCancel={onClose} footer={null} destroyOnClose centered width={460}>
      <Typography.Paragraph type="secondary">Đưa mã QR trên phiếu tóm tắt vào khung hình. Trình duyệt sẽ hỏi quyền dùng camera ở lần đầu.</Typography.Paragraph>
      {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 12 }} />}
      <Spin spinning={starting && !error} tip="Đang mở camera…">
        <div id={READER_ID} style={{ width: '100%', minHeight: error ? 0 : 260, display: error ? 'none' : 'block' }} />
      </Spin>
    </Modal>
  );
}
