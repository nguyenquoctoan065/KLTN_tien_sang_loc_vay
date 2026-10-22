import { Badge, Button, Dropdown, Empty, List, Typography } from 'antd';
import { BellOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { noticeApi } from '../api/endpoints';
import { dt } from '../utils/format';

/** Chuông thông báo trong ứng dụng (khách hàng và nhân viên chi nhánh) */
export default function NotificationBell({ dark }: { dark?: boolean }) {
  const nav = useNavigate();
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['notifications'], queryFn: noticeApi.list, refetchInterval: 15000 });
  const read = useMutation({ mutationFn: noticeApi.read, onSuccess: () => qc.invalidateQueries({ queryKey: ['notifications'] }) });
  const readAll = useMutation({ mutationFn: noticeApi.readAll, onSuccess: () => qc.invalidateQueries({ queryKey: ['notifications'] }) });
  const items = q.data ?? [];
  const unread = items.filter(n => !n.read).length;
  return (
    <Dropdown trigger={['click']} placement="bottomRight" dropdownRender={() => (
      <div className="notice-panel">
        <div className="notice-head">
          <Typography.Text strong>Thông báo</Typography.Text>
          {unread > 0 && <Button size="small" type="link" onClick={() => readAll.mutate()}>Đánh dấu đã đọc</Button>}
        </div>
        {items.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Chưa có thông báo" /> : (
          <List size="small" dataSource={items.slice(0, 12)} style={{ maxHeight: 420, overflow: 'auto' }}
            renderItem={n => (
              <List.Item className={n.read ? '' : 'notice-unread'} style={{ cursor: n.link ? 'pointer' : 'default' }}
                onClick={() => { if (!n.read) read.mutate(n.id); if (n.link) nav(n.link); }}>
                <List.Item.Meta title={n.title} description={<><div>{n.body}</div><Typography.Text type="secondary" style={{ fontSize: 12 }}>{dt(n.createdAt)}</Typography.Text></>} />
              </List.Item>
            )} />
        )}
      </div>
    )}>
      <Badge count={unread} size="small" offset={[-4, 4]}>
        <Button type="text" aria-label="Thông báo" icon={<BellOutlined style={{ fontSize: 18, color: dark ? '#fff' : undefined }} />} />
      </Badge>
    </Dropdown>
  );
}
