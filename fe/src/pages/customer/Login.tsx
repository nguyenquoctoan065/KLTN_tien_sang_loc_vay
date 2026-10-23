import { Button, Card, Form, Input, Typography, Alert, App } from 'antd';
import { useMutation } from '@tanstack/react-query';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { authApi } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';

export default function Login({ internal = false }: { internal?: boolean }) {
  const { signIn } = useAuth();
  const nav = useNavigate();
  const [sp] = useSearchParams();
  const { message } = App.useApp();
  const m = useMutation({
    mutationFn: (v: { username: string; password: string }) => authApi.login({ kind: internal ? 'STAFF' : 'CUSTOMER', ...v }),
    onSuccess: r => {
      signIn(r);
      message.success(`Xin chào ${r.user.name}`);
      const next = sp.get('next');
      nav(next || (r.user.role === 'ADMIN' ? '/noi-bo/bao-cao' : r.user.role === 'BRANCH_STAFF' ? '/noi-bo/ho-so' : '/cua-toi'), { replace: true });
    },
  });
  return (
    <div className="auth-wrap">
      <Card style={{ width: 400, maxWidth: '100%' }}>
        <Typography.Title level={3} style={{ marginTop: 0 }}>{internal ? 'Đăng nhập nội bộ' : 'Đăng nhập'}</Typography.Title>
        {sp.get('next') && !internal && (
          <Alert style={{ marginBottom: 16 }} type="info" showIcon message="Cần đăng nhập để lưu kết quả và phương án vay." />
        )}
        <Form layout="vertical" onFinish={v => m.mutate(v)} requiredMark={false}>
          <Form.Item name="username" label={internal ? 'Email' : 'Số điện thoại hoặc email'}
            rules={internal ? [{ required: true, message: 'Nhập email' }] : [{
              validator: (_, v: string | undefined) => {
                const s = (v ?? '').trim();
                return /^0\d{9}$/.test(s) || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(s)
                  ? Promise.resolve() : Promise.reject(new Error('Nhập số điện thoại (10 số) hoặc email'));
              },
            }]}>
            <Input autoFocus placeholder={internal ? 'nhanvien@tiensangloc.local' : 'Số điện thoại hoặc email'} />
          </Form.Item>
          <Form.Item name="password" label="Mật khẩu" rules={[{ required: true, message: 'Nhập mật khẩu' }]}>
            <Input.Password />
          </Form.Item>
          {m.isError && <Alert style={{ marginBottom: 16 }} type="error" message={errorMessage(m.error)} />}
          <Button type="primary" htmlType="submit" block loading={m.isPending}>Đăng nhập</Button>
        </Form>
        {import.meta.env.VITE_USE_MOCK === 'true' && (
          <Typography.Paragraph type="secondary" style={{ marginTop: 16, marginBottom: 0, fontSize: 12 }}>
            Tài khoản thử: {internal ? 'nhanvien@tiensangloc.local hoặc quantri@tiensangloc.local' : '0901234567 hoặc khachhang@tiensangloc.local'} / 123456
          </Typography.Paragraph>
        )}
        {!internal && (
          <div style={{ marginTop: 12 }}>
            Chưa có tài khoản? <Link to={`/dang-ky${sp.get('next') ? `?next=${encodeURIComponent(sp.get('next')!)}` : ''}`}>Đăng ký</Link>
          </div>
        )}
        {internal && <div style={{ marginTop: 12 }}><Link to="/">Về trang khách hàng</Link></div>}
      </Card>
    </div>
  );
}
