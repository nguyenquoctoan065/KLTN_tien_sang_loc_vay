import { useState } from 'react';
import { Button, Card, Descriptions, Empty, Table, Tabs, Tag, Typography } from 'antd';
import { KeyOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { appApi, planApi, screeningApi } from '../../api/endpoints';
import { useAuth } from '../../auth/AuthContext';
import StatusTag from '../../components/StatusTag';
import ChangePasswordModal from '../../components/ChangePasswordModal';
import MyData from './MyData';
import { dt, money } from '../../utils/format';

export default function MyPage() {
  const nav = useNavigate();
  const { user } = useAuth();
  const [sp, setSp] = useSearchParams();
  const tab = sp.get('tab') || 'ho-so';
  const [pw, setPw] = useState(false);
  const apps = useQuery({ queryKey: ['my-apps'], queryFn: appApi.mine });
  const scr = useQuery({ queryKey: ['my-screenings'], queryFn: screeningApi.mine, enabled: tab === 'sang-loc' });
  const plans = useQuery({ queryKey: ['my-plans'], queryFn: planApi.mine, enabled: tab === 'phuong-an' });
  return (
    <div>
      <Typography.Title level={3}>Kết quả và hồ sơ của tôi</Typography.Title>
      <Card>
        <Tabs activeKey={tab} onChange={k => setSp({ tab: k })} items={[
          {
            key: 'ho-so', label: 'Hồ sơ vay', children: (
              <Table loading={apps.isLoading} rowKey="id" dataSource={apps.data} scroll={{ x: 700 }}
                locale={{ emptyText: <Empty description="Chưa có hồ sơ. Kiểm tra khả năng vay, chọn phương án rồi lập hồ sơ." /> }}
                onRow={r => ({ onClick: () => nav(`/ho-so/${r.id}`), style: { cursor: 'pointer' } })}
                columns={[
                  { title: 'Mã hồ sơ', dataIndex: 'code' },
                  { title: 'Gói vay', dataIndex: 'productName' },
                  { title: 'Số tiền', dataIndex: 'amount', align: 'right', render: money },
                  { title: 'Chi nhánh', dataIndex: 'branchName' },
                  { title: 'Trạng thái', dataIndex: 'status', render: v => <StatusTag status={v} /> },
                  { title: 'Cập nhật', dataIndex: 'updatedAt', render: dt },
                ]} />
            ),
          },
          {
            key: 'phuong-an', label: 'Phương án vay', children: (
              <Table loading={plans.isLoading} rowKey="id" dataSource={plans.data} locale={{ emptyText: <Empty description="Chưa có phương án nào" /> }}
                onRow={r => ({ onClick: () => nav(`/phuong-an/${r.id}`), style: { cursor: 'pointer' } })}
                columns={[
                  { title: 'Mã', dataIndex: 'id', render: v => `#${v}` },
                  { title: 'Gói vay', dataIndex: 'productName' },
                  { title: 'Số tiền', dataIndex: 'amount', align: 'right', render: money },
                  { title: 'Kỳ hạn', dataIndex: 'termMonths', render: v => `${v} tháng` },
                  { title: 'Trạng thái', dataIndex: 'status', render: v => (v === 'CONFIRMED' ? <Tag color="success">Đã xác nhận</Tag> : <Tag>Bản nháp</Tag>) },
                  { title: 'Ngày tạo', dataIndex: 'createdAt', render: dt },
                ]} />
            ),
          },
          {
            key: 'sang-loc', label: 'Kết quả sàng lọc', children: (
              <Table loading={scr.isLoading} rowKey="id" dataSource={scr.data} locale={{ emptyText: <Empty description="Chưa lưu kết quả nào" /> }}
                columns={[
                  { title: 'Lượt', dataIndex: 'id', render: v => `#${v}` },
                  { title: 'Thời điểm', dataIndex: 'createdAt', render: dt },
                  { title: 'Nhu cầu', render: (_, r) => `${money(r.input.desiredAmount)} · ${r.input.desiredTerm} tháng` },
                  { title: 'Gói gợi ý', render: (_, r) => r.results.filter(x => x.eligible).map(x => <Tag key={x.productCode}>{x.productCode === 'TD' ? 'Tiêu dùng' : 'Thấu chi'} · {x.score}</Tag>) },
                  { title: '', render: (_, r) => (
                    <span style={{ display: 'inline-flex', gap: 8, flexWrap: 'wrap' }}>
                      <Button size="small" onClick={() => nav(`/sang-loc/${r.id}`)}>Xem</Button>
                      <Button size="small" onClick={() => nav(`/sang-loc?tu=${r.id}`)}>Sàng lọc lại với thông tin này</Button>
                    </span>
                  ) },
                ]} />
            ),
          },
          { key: 'du-lieu', label: 'Dữ liệu cá nhân', children: tab === 'du-lieu' ? <MyData /> : null },
          {
            key: 'tai-khoan', label: 'Tài khoản', children: (
              <div>
                <Descriptions size="small" column={1} style={{ maxWidth: 480 }}>
                  <Descriptions.Item label="Tên hiển thị">{user?.name}</Descriptions.Item>
                  <Descriptions.Item label="Số điện thoại đăng nhập">{user?.username}</Descriptions.Item>
                  <Descriptions.Item label="Email">{user?.email || 'Chưa cập nhật'}</Descriptions.Item>
                </Descriptions>
                <Button icon={<KeyOutlined />} onClick={() => setPw(true)}>Đổi mật khẩu</Button>
                <ChangePasswordModal open={pw} onClose={() => setPw(false)} />
              </div>
            ),
          },
        ]} />
      </Card>
    </div>
  );
}
