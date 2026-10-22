import { Layout, Menu, Button, Dropdown, Space, Typography, Tag } from 'antd';
import { UserOutlined, LogoutOutlined, FileSearchOutlined, SafetyOutlined, KeyOutlined } from '@ant-design/icons';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { authApi } from '../api/endpoints';
import NotificationBell from '../components/NotificationBell';

const { Header, Content, Footer } = Layout;

export default function CustomerLayout() {
  const { user, signOut } = useAuth();
  const loc = useLocation();
  const nav = useNavigate();
  const sel = loc.pathname.startsWith('/sang-loc') || loc.pathname.startsWith('/mo-phong') ? 'sl'
    : loc.pathname.startsWith('/cua-toi') || loc.pathname.startsWith('/ho-so') || loc.pathname.startsWith('/phuong-an') ? 'me' : 'home';
  const items = [
    { key: 'home', label: <Link to="/">Trang chủ</Link> },
    { key: 'sl', label: <Link to="/sang-loc">Kiểm tra khả năng vay</Link> },
    ...(user?.role === 'CUSTOMER' ? [{ key: 'me', label: <Link to="/cua-toi">Kết quả và hồ sơ của tôi</Link> }] : []),
  ];
  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header className="app-header">
        <Link to="/" className="brand">
          <span className="brand-mark">₫</span>
          <span className="brand-text">Gợi ý &amp; tiền sàng lọc vay tín chấp</span>
          <Tag color="gold" style={{ marginLeft: 8 }}>PoC</Tag>
        </Link>
        <Menu mode="horizontal" selectedKeys={[sel]} items={items} className="app-menu" />
        <div className="header-right">
          {user?.role === 'CUSTOMER' ? (
            <Space size={4}>
              <NotificationBell dark />
              <Dropdown
                menu={{
                  items: [
                    { key: 'me', icon: <FileSearchOutlined />, label: 'Kết quả và hồ sơ của tôi', onClick: () => nav('/cua-toi') },
                    { key: 'data', icon: <SafetyOutlined />, label: 'Dữ liệu cá nhân', onClick: () => nav('/cua-toi?tab=du-lieu') },
                    { key: 'pw', icon: <KeyOutlined />, label: 'Tài khoản, đổi mật khẩu', onClick: () => nav('/cua-toi?tab=tai-khoan') },
                    { type: 'divider' },
                    { key: 'out', icon: <LogoutOutlined />, label: 'Đăng xuất', onClick: () => { authApi.logout().catch(() => {}); signOut(); nav('/'); } },
                  ],
                }}
              >
                <Button type="text" icon={<UserOutlined />} style={{ color: '#fff' }}>{user.name}</Button>
              </Dropdown>
            </Space>
          ) : (
            <Space>
              <Button type="text" style={{ color: '#fff' }} onClick={() => nav('/dang-nhap')}>Đăng nhập</Button>
              <Button onClick={() => nav('/dang-ky')}>Đăng ký</Button>
            </Space>
          )}
        </div>
      </Header>
      <Content className="app-content">
        <div className="container">
          <Outlet />
        </div>
      </Content>
      <Footer style={{ textAlign: 'center' }}>
        <Typography.Text type="secondary">
          Hệ thống web thử nghiệm phục vụ đồ án tốt nghiệp. Kết quả chỉ để tham khảo, không phải quyết định phê duyệt khoản vay.
          {' '}<Link to="/noi-bo/dang-nhap">Đăng nhập nội bộ</Link>
        </Typography.Text>
      </Footer>
    </Layout>
  );
}
