import { createBrowserRouter, Navigate } from 'react-router-dom';
import CustomerLayout from '../layouts/CustomerLayout';
import InternalLayout from '../layouts/InternalLayout';
import { RequireRole } from '../auth/AuthContext';
import Home from '../pages/customer/Home';
import Login from '../pages/customer/Login';
import Register from '../pages/customer/Register';
import ScreeningWizard from '../pages/customer/ScreeningWizard';
import ScreeningResult from '../pages/customer/ScreeningResult';
import Simulation from '../pages/customer/Simulation';
import PlanDetail from '../pages/customer/PlanDetail';
import MyPage from '../pages/customer/MyPage';
import StaffApplications from '../pages/internal/StaffApplications';
import ParamVersions from '../pages/internal/ParamVersions';
import ParamVersionDetail from '../pages/internal/ParamVersionDetail';
import Catalog from '../pages/internal/Catalog';
import CreateApplication from '../pages/customer/CreateApplication';
import ApplicationDetail from '../pages/customer/ApplicationDetail';
import StaffApplicationDetail from '../pages/internal/StaffApplicationDetail';
import StaffAccounts from '../pages/internal/StaffAccounts';
import Branches from '../pages/internal/Branches';
import Jobs from '../pages/internal/Jobs';
import Logs from '../pages/internal/Logs';
import Reports from '../pages/internal/Reports';
import { useAuth } from '../auth/AuthContext';
import { Result, Button } from 'antd';

const NotFound = () => <Result status="404" title="Không tìm thấy trang" extra={<Button href="/">Về trang chủ</Button>} />;

export const router = createBrowserRouter([
  {
    path: '/',
    element: <CustomerLayout />,
    children: [
      { index: true, element: <Home /> },
      { path: 'dang-nhap', element: <Login /> },
      { path: 'dang-ky', element: <Register /> },
      { path: 'sang-loc', element: <ScreeningWizard /> },
      { path: 'sang-loc/:id', element: <ScreeningResult /> },
      { path: 'mo-phong/:id', element: <Simulation /> },
      { path: 'phuong-an/:id', element: <RequireRole roles={['CUSTOMER']}><PlanDetail /></RequireRole> },
      { path: 'cua-toi', element: <RequireRole roles={['CUSTOMER']}><MyPage /></RequireRole> },
      { path: 'ho-so/tao', element: <RequireRole roles={['CUSTOMER']}><CreateApplication /></RequireRole> },
      { path: 'ho-so/:id', element: <RequireRole roles={['CUSTOMER']}><ApplicationDetail /></RequireRole> },
      { path: '*', element: <NotFound /> },
    ],
  },
  { path: '/noi-bo/dang-nhap', element: <CustomerLayoutless><Login internal /></CustomerLayoutless> },
  {
    path: '/noi-bo',
    element: <RequireRole roles={['BRANCH_STAFF', 'ADMIN']}><InternalLayout /></RequireRole>,
    children: [
      { index: true, element: <InternalHome /> },
      { path: 'ho-so', element: <RequireRole roles={['BRANCH_STAFF']}><StaffApplications /></RequireRole> },
      { path: 'ho-so/:id', element: <RequireRole roles={['BRANCH_STAFF']}><StaffApplicationDetail /></RequireRole> },
      { path: 'doi-chieu', element: <RequireRole roles={['BRANCH_STAFF']}><StaffApplications key="v" title="Hồ sơ chờ đối chiếu" preset={['WAITING_VERIFY']} hint="Khách hàng mang bản gốc và phiếu MB08 tới quầy; quét mã QR hoặc chọn hồ sơ để đối chiếu từng giấy tờ (MB09)." /></RequireRole> },
      { path: 'ket-qua', element: <RequireRole roles={['BRANCH_STAFF']}><StaffApplications key="k" title="Kết quả xét duyệt và giải ngân" preset={['VERIFIED', 'APPROVED']} hint="Ghi nhận kết quả ngân hàng thông báo (MB16) cho hồ sơ đã đối chiếu, cập nhật giải ngân cho hồ sơ được phê duyệt." /></RequireRole> },
      { path: 'tham-so', element: <RequireRole roles={['ADMIN']}><ParamVersions /></RequireRole> },
      { path: 'tham-so/:id', element: <RequireRole roles={['ADMIN']}><ParamVersionDetail /></RequireRole> },
      { path: 'danh-muc', element: <RequireRole roles={['ADMIN']}><Catalog /></RequireRole> },
      { path: 'bao-cao', element: <RequireRole roles={['ADMIN']}><Reports /></RequireRole> },
      { path: 'nhan-vien', element: <RequireRole roles={['ADMIN']}><StaffAccounts /></RequireRole> },
      { path: 'chi-nhanh', element: <RequireRole roles={['ADMIN']}><Branches /></RequireRole> },
      { path: 'tac-vu', element: <RequireRole roles={['ADMIN']}><Jobs /></RequireRole> },
      { path: 'nhat-ky', element: <RequireRole roles={['ADMIN']}><Logs /></RequireRole> },
    ],
  },
]);

function InternalHome() {
  const { user } = useAuth();
  return <Navigate to={user?.role === 'ADMIN' ? '/noi-bo/bao-cao' : '/noi-bo/ho-so'} replace />;
}

function CustomerLayoutless({ children }: { children: React.ReactNode }) {
  return <div style={{ minHeight: '100vh', background: '#f1f5f9' }}>{children}</div>;
}
