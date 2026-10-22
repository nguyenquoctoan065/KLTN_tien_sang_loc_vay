import { useState } from 'react';
import { Alert, App, Button, Card, Form, Input, Modal, Table, Tag, Typography } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { adminApi } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import { dt } from '../../utils/format';

export const VERSION_STATUS = {
  DRAFT: { label: 'Bản nháp', color: 'gold' },
  ACTIVE: { label: 'Đang áp dụng', color: 'green' },
  EXPIRED: { label: 'Hết hiệu lực', color: 'default' },
} as const;

/** 7.5 Xem lịch sử phiên bản tham số; tạo bản nháp mới từ phiên bản đang áp dụng */
export default function ParamVersions() {
  const nav = useNavigate();
  const qc = useQueryClient();
  const { message } = App.useApp();
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm();
  const q = useQuery({ queryKey: ['versions'], queryFn: adminApi.versions });
  const active = q.data?.find(v => v.status === 'ACTIVE');
  const hasDraft = q.data?.some(v => v.status === 'DRAFT');
  const clone = useMutation({
    mutationFn: (b: { versionNo: string; legalBasis: string }) => adminApi.clone(active!.id, b),
    onSuccess: r => { qc.invalidateQueries({ queryKey: ['versions'] }); setOpen(false); message.success('Đã tạo bản nháp'); nav(`/noi-bo/tham-so/${r.id}`); },
  });
  return (
    <div>
      <Typography.Title level={3} style={{ marginTop: 0 }}>Phiên bản tham số chính sách</Typography.Title>
      <Typography.Paragraph type="secondary">
        Mỗi phiên bản gồm Bảng tham số chính sách (MB11) và Ma trận trọng số (MB12). Sửa trên bản nháp, chạy thử trên bộ hồ sơ mẫu rồi mới kích hoạt.
      </Typography.Paragraph>
      <Card extra={<Button type="primary" icon={<PlusOutlined />} disabled={!active || hasDraft} onClick={() => setOpen(true)}>Tạo bản nháp từ phiên bản đang áp dụng</Button>}>
        {hasDraft && <Alert style={{ marginBottom: 16 }} type="info" showIcon message="Đang có một bản nháp; hoàn tất hoặc xóa trước khi tạo bản mới." />}
        <Table loading={q.isLoading} rowKey="id" dataSource={q.data}
          onRow={r => ({ onClick: () => nav(`/noi-bo/tham-so/${r.id}`), style: { cursor: 'pointer' } })}
          columns={[
            { title: 'Phiên bản', dataIndex: 'versionNo' },
            { title: 'Căn cứ văn bản (MB10)', dataIndex: 'legalBasis' },
            { title: 'Trạng thái', dataIndex: 'status', render: (v: keyof typeof VERSION_STATUS) => <Tag color={VERSION_STATUS[v].color}>{VERSION_STATUS[v].label}</Tag> },
            { title: 'Người tạo', dataIndex: 'createdBy' },
            { title: 'Ngày tạo', dataIndex: 'createdAt', render: dt },
            { title: 'Ngày kích hoạt', dataIndex: 'activatedAt', render: dt },
          ]} />
      </Card>
      <Modal title="Tạo bản nháp tham số" open={open} onCancel={() => setOpen(false)} onOk={() => form.submit()} confirmLoading={clone.isPending} okText="Tạo" cancelText="Hủy">
        <Form form={form} layout="vertical" onFinish={v => clone.mutate(v)}>
          <Form.Item name="versionNo" label="Số phiên bản" rules={[{ required: true, message: 'Nhập số phiên bản' }]}>
            <Input placeholder="Ví dụ 2026.11" />
          </Form.Item>
          <Form.Item name="legalBasis" label="Căn cứ văn bản điều chỉnh (MB10)" rules={[{ required: true, message: 'Nhập căn cứ' }]}>
            <Input.TextArea rows={2} placeholder="Số, ngày văn bản của Phòng Chính sách tín dụng" />
          </Form.Item>
          {clone.isError && <Alert type="error" message={errorMessage(clone.error)} />}
        </Form>
      </Modal>
    </div>
  );
}
