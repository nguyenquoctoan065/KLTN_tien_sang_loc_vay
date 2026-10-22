import { App, Button, Card, Table, Typography } from 'antd';
import { PlayCircleOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import { dt } from '../../utils/format';

/** 1.7 Xử lý định kỳ của hệ thống – xem lịch, kết quả lần chạy gần nhất, chạy ngay để kiểm thử */
export default function Jobs() {
  const qc = useQueryClient();
  const { message } = App.useApp();
  const q = useQuery({ queryKey: ['admin-jobs'], queryFn: adminApi.jobs });
  const run = useMutation({
    mutationFn: adminApi.runJob,
    onSuccess: j => { qc.invalidateQueries({ queryKey: ['admin-jobs'] }); message.success(`${j.name}: ${j.lastResult}`); },
    onError: e => message.error(errorMessage(e)),
  });
  return (
    <div>
      <Typography.Title level={3} style={{ marginTop: 0 }}>Tác vụ định kỳ</Typography.Title>
      <Typography.Paragraph type="secondary">
        Hệ thống tự chạy theo lịch: chuyển hồ sơ có giấy tờ quá thời hạn hiệu lực sang “Hết hạn”, xóa dữ liệu hết thời hạn lưu trữ, nhắc khách hàng hoàn tất hồ sơ.
        Thời hạn lấy từ Bảng tham số chính sách đang áp dụng. Nút “Chạy ngay” dùng để kiểm thử.
      </Typography.Paragraph>
      <Card>
        <Table rowKey="code" loading={q.isLoading} dataSource={q.data} pagination={false} scroll={{ x: 700 }} columns={[
          { title: 'Tác vụ', dataIndex: 'name' },
          { title: 'Lịch chạy', dataIndex: 'schedule' },
          { title: 'Lần chạy gần nhất', dataIndex: 'lastRunAt', render: dt },
          { title: 'Kết quả', dataIndex: 'lastResult', render: v => v ?? '—' },
          { title: '', key: 'x', render: (_, r) => <Button icon={<PlayCircleOutlined />} loading={run.isPending && run.variables === r.code} onClick={() => run.mutate(r.code)}>Chạy ngay</Button> },
        ]} />
      </Card>
    </div>
  );
}
