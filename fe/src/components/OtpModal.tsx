import { useEffect, useState } from 'react';
import { Alert, Input, Modal, Typography } from 'antd';
import { authApi } from '../api/endpoints';
import { errorMessage } from '../api/client';

/** Hộp xác nhận bằng OTP gửi tới số điện thoại của khách hàng đang đăng nhập */
export default function OtpModal({ open, title, description, okText = 'Xác nhận', loading, error, onOk, onCancel }: {
  open: boolean; title: string; description?: React.ReactNode; okText?: string; loading?: boolean; error?: string | null;
  onOk: (otp: string) => void; onCancel: () => void;
}) {
  const [otp, setOtp] = useState('');
  const [hint, setHint] = useState<string | null>(null);
  const [sendErr, setSendErr] = useState<string | null>(null);
  useEffect(() => {
    if (!open) return;
    setOtp(''); setHint(null); setSendErr(null);
    authApi.myOtp().then(r => setHint(r.devOtp ?? null)).catch(e => setSendErr(errorMessage(e)));
  }, [open]);
  return (
    <Modal open={open} title={title} okText={okText} cancelText="Hủy" onCancel={onCancel} confirmLoading={loading}
      okButtonProps={{ disabled: otp.length !== 6 }} onOk={() => onOk(otp)} destroyOnClose>
      {description && <div style={{ marginBottom: 12 }}>{description}</div>}
      <Typography.Paragraph>Nhập mã OTP gồm 6 chữ số đã gửi tới số điện thoại đăng ký.</Typography.Paragraph>
      <Input.OTP length={6} value={otp} onChange={setOtp} autoFocus />
      {hint && <Alert style={{ marginTop: 12 }} type="warning" showIcon message={`Môi trường thử: mã OTP là ${hint}`} />}
      {(error || sendErr) && <Alert style={{ marginTop: 12 }} type="error" showIcon message={error || sendErr} />}
    </Modal>
  );
}
