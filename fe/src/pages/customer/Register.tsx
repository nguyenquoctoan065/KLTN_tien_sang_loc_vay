import { Button, Card, Form, Input, Typography, Alert, App, Space } from 'antd';
import { useMutation } from '@tanstack/react-query';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useState } from 'react';
import { authApi } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';

export default function Register() {
  const { signIn } = useAuth();
  const nav = useNavigate();
  const [sp] = useSearchParams();
  const { message } = App.useApp();
  const [form] = Form.useForm();
  const [otpHint, setOtpHint] = useState<string | null>(null);
  const otp = useMutation({
    mutationFn: (phone: string) => authApi.sendOtp(phone),
    onSuccess: r => { setOtpHint(r.devOtp ?? null); message.success('Đã gửi mã OTP'); },
  });
  const reg = useMutation({
    mutationFn: authApi.register,
    onSuccess: r => { signIn(r); message.success('Đăng ký thành công'); nav(sp.get('next') || '/cua-toi', { replace: true }); },
  });
  return (
    <div className="auth-wrap">
      <Card style={{ width: 420, maxWidth: '100%' }}>
        <Typography.Title level={3} style={{ marginTop: 0 }}>Đăng ký tài khoản</Typography.Title>
        <Form form={form} layout="vertical" requiredMark={false} onFinish={v => reg.mutate({ phone: v.phone, email: v.email?.trim() || undefined, password: v.password, otp: v.otp })}>
          <Form.Item name="phone" label="Số điện thoại" rules={[{ required: true, pattern: /^0\d{9}$/, message: 'Số điện thoại gồm 10 chữ số, bắt đầu bằng 0' }]}>
            <Input placeholder="09xxxxxxxx" />
          </Form.Item>
          <Form.Item name="email" label="Email (không bắt buộc)" rules={[{ type: 'email', message: 'Email không hợp lệ' }]}>
            <Input placeholder="ten@example.com" />
          </Form.Item>
          <Form.Item name="password" label="Mật khẩu" rules={[{ required: true, min: 6, message: 'Mật khẩu tối thiểu 6 ký tự' }]}>
            <Input.Password />
          </Form.Item>
          <Form.Item name="confirm" label="Nhập lại mật khẩu" dependencies={['password']}
            rules={[{ required: true, message: 'Nhập lại mật khẩu' }, ({ getFieldValue }) => ({
              validator: (_, v) => (v === getFieldValue('password') ? Promise.resolve() : Promise.reject(new Error('Mật khẩu không khớp'))),
            })]}>
            <Input.Password />
          </Form.Item>
          <Form.Item label="Mã OTP" required>
            <Space.Compact style={{ width: '100%' }}>
              <Form.Item name="otp" noStyle rules={[{ required: true, len: 6, message: 'Mã OTP gồm 6 chữ số' }]}>
                <Input placeholder="6 chữ số" />
              </Form.Item>
              <Button loading={otp.isPending} onClick={async () => {
                try { await form.validateFields(['phone']); otp.mutate(form.getFieldValue('phone')); } catch { /* lỗi hiển thị ở ô nhập */ }
              }}>Gửi mã</Button>
            </Space.Compact>
          </Form.Item>
          {otpHint && <Alert style={{ marginBottom: 16 }} type="warning" showIcon message={`Môi trường thử: mã OTP là ${otpHint}`} />}
          {(reg.isError || otp.isError) && <Alert style={{ marginBottom: 16 }} type="error" message={errorMessage(reg.error || otp.error)} />}
          <Button type="primary" htmlType="submit" block loading={reg.isPending}>Đăng ký</Button>
        </Form>
        <div style={{ marginTop: 12 }}>Đã có tài khoản? <Link to={`/dang-nhap${sp.get('next') ? `?next=${encodeURIComponent(sp.get('next')!)}` : ''}`}>Đăng nhập</Link></div>
      </Card>
    </div>
  );
}
