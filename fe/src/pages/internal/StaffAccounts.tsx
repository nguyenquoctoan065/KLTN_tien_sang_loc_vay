import { useState } from 'react';
import { Alert, App, Button, Card, Form, Input, Modal, Dropdown, Select, Space, Table, Tag, Typography } from 'antd';
import { CopyOutlined, MoreOutlined, PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import type { StaffAccount } from '../../api/types';
import { dt } from '../../utils/format';

/** 1.5 Quản lý tài khoản nhân viên */
export default function StaffAccounts() {
  const qc = useQueryClient();
  const { message, modal } = App.useApp();
  const q = useQuery({ queryKey: ['admin-staff'], queryFn: adminApi.staff });
  const br = useQuery({ queryKey: ['admin-branches'], queryFn: adminApi.branches });
  const [edit, setEdit] = useState<StaffAccount | 'new' | null>(null);
  /** Mật khẩu tạm chỉ hiện một lần: đóng Modal là mất (backend không lưu bản rõ) */
  const [temp, setTemp] = useState<{ title: string; email: string; password: string } | null>(null);
  const copyTemp = async () => {
    try {
      await navigator.clipboard.writeText(temp!.password);
      message.success('Đã sao chép mật khẩu tạm');
    } catch {
      message.error('Không sao chép được, hãy chọn và sao chép thủ công');
    }
  };
  const [form] = Form.useForm();
  const role = Form.useWatch('role', form);
  const refresh = () => qc.invalidateQueries({ queryKey: ['admin-staff'] });
  const save = useMutation({
    mutationFn: (v: { fullName: string; email: string; role: 'BRANCH_STAFF' | 'ADMIN'; branchId: number | null }) =>
      edit === 'new' ? adminApi.createStaff(v) : adminApi.updateStaff((edit as StaffAccount).id, v),
    onSuccess: (r: StaffAccount & { tempPassword?: string }) => {
      refresh(); setEdit(null);
      if (r.tempPassword) setTemp({ title: 'Đã tạo tài khoản', email: r.email, password: r.tempPassword });
      else message.success('Đã lưu');
    },
  });
  const act = useMutation({
    mutationFn: ({ id, action }: { id: number; action: 'lock' | 'unlock' | 'reset-password' }) => adminApi.staffAction(id, action),
    onSuccess: r => {
      refresh();
      if (r.tempPassword) setTemp({ title: 'Đã đặt lại mật khẩu', email: r.email, password: r.tempPassword });
      else message.success('Đã cập nhật');
    },
    onError: e => message.error(errorMessage(e)),
  });
  const open = (s: StaffAccount | 'new') => {
    setEdit(s); save.reset();
    form.setFieldsValue(s === 'new' ? { fullName: '', email: '', role: 'BRANCH_STAFF', branchId: undefined } : { ...s });
  };
  return (
    <div>
      <Typography.Title level={3} style={{ marginTop: 0 }}>Tài khoản nhân viên</Typography.Title>
      <Card extra={<Button type="primary" icon={<PlusOutlined />} onClick={() => open('new')}>Thêm nhân viên</Button>}>
        <Table rowKey="id" loading={q.isLoading} dataSource={q.data} scroll={{ x: 1025 }} columns={[
          { title: 'Họ tên', dataIndex: 'fullName', width: 150, className: 'nowrap-cell' },
          { title: 'Email đăng nhập', dataIndex: 'email', width: 180, className: 'nowrap-cell' },
          { title: 'Vai trò', dataIndex: 'role', width: 170, className: 'nowrap-cell', render: v => (v === 'ADMIN' ? <Tag color="purple">Quản trị viên chính sách</Tag> : <Tag color="blue">Nhân viên chi nhánh</Tag>) },
          { title: 'Chi nhánh', dataIndex: 'branchName', width: 165, className: 'nowrap-cell', render: v => v ?? '—' },
          { title: 'Trạng thái', dataIndex: 'status', width: 100, className: 'nowrap-cell', render: v => (v === 'ACTIVE' ? <Tag color="success">Hoạt động</Tag> : <Tag color="error">Đã khóa</Tag>) },
          { title: 'Ngày tạo', dataIndex: 'createdAt', width: 150, className: 'nowrap-cell', render: dt },
          {
            title: '', key: 'x', width: 110, fixed: 'right', render: (_, r) => (
              <Space>
                <Button size="small" onClick={() => open(r)}>Sửa</Button>
                <Dropdown trigger={['click']} menu={{ items: [
                  r.status === 'ACTIVE'
                    ? { key: 'lock', danger: true, label: 'Khóa tài khoản', onClick: () => modal.confirm({ title: 'Khóa tài khoản này?', okText: 'Khóa', cancelText: 'Hủy', onOk: () => act.mutate({ id: r.id, action: 'lock' }) }) }
                    : { key: 'unlock', label: 'Mở khóa', onClick: () => act.mutate({ id: r.id, action: 'unlock' }) },
                  { key: 'reset', label: 'Đặt lại mật khẩu', onClick: () => modal.confirm({ title: 'Đặt lại mật khẩu?', okText: 'Đặt lại', cancelText: 'Hủy', onOk: () => act.mutate({ id: r.id, action: 'reset-password' }) }) },
                ] }}><Button size="small" icon={<MoreOutlined />} aria-label="Thao tác khác" /></Dropdown>
              </Space>
            ),
          },
        ]} />
      </Card>
      <Modal open={!!temp} title={temp?.title} closable={false} maskClosable={false} keyboard={false} destroyOnClose
        footer={<Button type="primary" onClick={() => setTemp(null)}>Đã lưu lại, đóng</Button>}>
        <Typography.Paragraph>Tài khoản <b>{temp?.email}</b></Typography.Paragraph>
        <Typography.Paragraph style={{ marginBottom: 4 }}>Mật khẩu tạm thời:</Typography.Paragraph>
        <Space.Compact style={{ width: '100%' }}>
          <Input readOnly value={temp?.password} aria-label="Mật khẩu tạm thời" style={{ fontFamily: 'monospace', fontSize: 16 }}
            onFocus={e => e.target.select()} />
          <Button icon={<CopyOutlined />} onClick={copyTemp}>Sao chép</Button>
        </Space.Compact>
        <Alert style={{ marginTop: 16 }} type="warning" showIcon
          message="Mật khẩu chỉ hiển thị một lần. Hãy sao chép và gửi cho nhân viên; nhân viên đổi mật khẩu sau lần đăng nhập đầu." />
      </Modal>
      <Modal open={!!edit} title={edit === 'new' ? 'Thêm nhân viên' : 'Sửa tài khoản'} okText="Lưu" cancelText="Hủy" onCancel={() => setEdit(null)}
        onOk={() => form.submit()} confirmLoading={save.isPending} destroyOnClose>
        <Form form={form} layout="vertical" onFinish={save.mutate}>
          <Form.Item name="fullName" label="Họ tên" rules={[{ required: true, message: 'Nhập họ tên' }]}><Input /></Form.Item>
          <Form.Item name="email" label="Email đăng nhập" rules={[{ required: true, type: 'email', message: 'Email không hợp lệ' }]}><Input disabled={edit !== 'new'} /></Form.Item>
          <Form.Item name="role" label="Vai trò" rules={[{ required: true }]}>
            <Select options={[{ value: 'BRANCH_STAFF', label: 'Nhân viên chi nhánh (BP01)' }, { value: 'ADMIN', label: 'Quản trị viên chính sách và hệ thống (BP02)' }]} />
          </Form.Item>
          {role === 'BRANCH_STAFF' && (
            <Form.Item name="branchId" label="Chi nhánh" rules={[{ required: true, message: 'Chọn chi nhánh' }]}>
              <Select options={br.data?.filter(b => b.status === 'ACTIVE').map(b => ({ value: b.id, label: b.name }))} />
            </Form.Item>
          )}
          {save.isError && <Alert type="error" message={errorMessage(save.error)} />}
        </Form>
      </Modal>
    </div>
  );
}
