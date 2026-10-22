import { useState } from 'react';
import { Layout, Menu, Button, Typography, Space, Tag, Dropdown } from 'antd';
import {
  FolderOpenOutlined, AuditOutlined, CheckSquareOutlined, SettingOutlined, AppstoreOutlined, BarChartOutlined,
  TeamOutlined, BankOutlined, LogoutOutlined, ScheduleOutlined, FileSearchOutlined, UserOutlined, KeyOutlined,
} from '@ant-design/icons';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { authApi } from '../api/endpoints';
import NotificationBell from '../components/NotificationBell';
import ChangePasswordModal from '../components/ChangePasswordModal';

const { Sider, Header, Content } = Layout;

export default function InternalLayout() {
  const { user, signOut } = useAuth();
  const loc = useLocation();
  const nav = useNavigate();
  const [pw, setPw] = useState(false);
  const staff = [
    { key: '/noi-bo/ho-so', icon: <FolderOpenOutlined />, label: <Link to="/noi-bo/ho-so">Tra cứu hồ sơ</Link> },
    { key: '/noi-bo/doi-chieu', icon: <AuditOutlined />, label: <Link to="/noi-bo/doi-chieu">Chờ đối chiếu</Link> },
    { key: '/noi-bo/ket-qua', icon: <CheckSquareOutlined />, label: <Link to="/noi-bo/ket-qua">Kết quả, giải ngân</Link> },
  ];
  const admin = [
    { key: '/noi-bo/bao-cao', icon: <BarChartOutlined />, label: <Link to="/noi-bo/bao-cao">Thống kê, báo cáo</Link> },
    { key: '/noi-bo/tham-so', icon: <SettingOutlined />, label: <Link to="/noi-bo/tham-so">Phiên bản tham số</Link> },
    { key: '/noi-bo/danh-muc', icon: <AppstoreOutlined />, label: <Link to="/noi-bo/danh-muc">Danh mục sản phẩm</Link> },
    { key: '/noi-bo/nhan-vien', icon: <TeamOutlined />, label: <Link to="/noi-bo/nhan-vien">Tài khoản nhân viên</Link> },
    { key: '/noi-bo/chi-nhanh', icon: <BankOutlined />, label: <Link to="/noi-bo/chi-nhanh">Chi nhánh</Link> },
    { key: '/noi-bo/tac-vu', icon: <ScheduleOutlined />, label: <Link to="/noi-bo/tac-vu">Tác vụ định kỳ</Link> },
    { key: '/noi-bo/nhat-ky', icon: <FileSearchOutlined />, label: <Link to="/noi-bo/nhat-ky">Nhật ký hệ thống</Link> },
  ];
  const items = user?.role === 'ADMIN' ? admin : staff;
  const sel = items.map(i => i.key).find(k => loc.pathname.startsWith(k)) || (user?.role === 'BRANCH_STAFF' ? '/noi-bo/ho-so' : items[0].key);
  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider width={240} theme="light" breakpoint="lg" collapsedWidth={0} style={{ borderRight: '1px solid #f0f0f0' }} className="no-print">
        <div className="sider-brand">
          <div style={{ fontWeight: 600 }}>Khu vực nội bộ</div>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {user?.role === 'ADMIN' ? 'Quản trị viên chính sách (BP02)' : 'Nhân viên chi nhánh (BP01)'}
          </Typography.Text>
        </div>
        <Menu mode="inline" selectedKeys={[sel]} items={items} style={{ borderRight: 0 }} />
      </Sider>
      <Layout>
        <Header className="internal-header no-print">
          <Space>
            {user?.branchName && <Tag color="cyan">{user.branchName}</Tag>}
          </Space>
          <Space>
            {user?.role === 'BRANCH_STAFF' && <NotificationBell />}
            <Dropdown menu={{
              items: [
                { key: 'pw', icon: <KeyOutlined />, label: 'Đổi mật khẩu', onClick: () => setPw(true) },
                { key: 'out', icon: <LogoutOutlined />, label: 'Đăng xuất', onClick: () => { authApi.logout().catch(() => {}); signOut(); nav('/noi-bo/dang-nhap'); } },
              ],
            }}>
              <Button type="text" icon={<UserOutlined />}>{user?.name}</Button>
            </Dropdown>
          </Space>
        </Header>
        <Content style={{ padding: 24 }}>
          <Outlet />
        </Content>
      </Layout>
      <ChangePasswordModal open={pw} onClose={() => setPw(false)} />
    </Layout>
  );
}
