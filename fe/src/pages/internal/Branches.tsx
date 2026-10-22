import { useState } from 'react';
import { Alert, App, Button, Card, Form, Input, Modal, Select, Space, Table, Tag, Typography } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import type { Branch } from '../../api/types';

/** 1.6 Quản lý danh mục chi nhánh; ngừng hoạt động thì chuyển hồ sơ đang xử lý sang chi nhánh khác */
export default function Branches() {
  const qc = useQueryClient();
  const { message } = App.useApp();
  const q = useQuery({ queryKey: ['admin-branches'], queryFn: adminApi.branches });
  const [edit, setEdit] = useState<Branch | 'new' | null>(null);
  const [closing, setClosing] = useState<Branch | null>(null);
  const [target, setTarget] = useState<number>();
  const [form] = Form.useForm();
  const refresh = () => { qc.invalidateQueries({ queryKey: ['admin-branches'] }); qc.invalidateQueries({ queryKey: ['branches'] }); };
  const save = useMutation({
    mutationFn: (v: Omit<Branch, 'id' | 'status'>) => (edit === 'new' ? adminApi.createBranch(v) : adminApi.updateBranch((edit as Branch).id, v)),
    onSuccess: () => { refresh(); setEdit(null); message.success('Đã lưu chi nhánh'); },
  });
  const close = useMutation({
    mutationFn: () => adminApi.deactivateBranch(closing!.id, target),
    onSuccess: r => { refresh(); setClosing(null); message.success(r.transferred ? `Đã ngừng hoạt động và chuyển ${r.transferred} hồ sơ` : 'Đã ngừng hoạt động chi nhánh'); },
  });
  const reopen = useMutation({ mutationFn: adminApi.activateBranch, onSuccess: () => { refresh(); message.success('Đã mở lại chi nhánh'); } });
  return (
    <div>
      <Typography.Title level={3} style={{ marginTop: 0 }}>Danh mục chi nhánh</Typography.Title>
      <Card extra={<Button type="primary" icon={<PlusOutlined />} onClick={() => { setEdit('new'); save.reset(); form.resetFields(); }}>Thêm chi nhánh</Button>}>
        <Table rowKey="id" loading={q.isLoading} dataSource={q.data} scroll={{ x: 800 }} columns={[
          { title: 'Mã', dataIndex: 'code' },
          { title: 'Tên chi nhánh', dataIndex: 'name' },
          { title: 'Địa chỉ', dataIndex: 'address' },
          { title: 'Điện thoại', dataIndex: 'phone' },
          { title: 'Hồ sơ đang xử lý', dataIndex: 'openApplications' },
          { title: 'Trạng thái', dataIndex: 'status', render: v => (v === 'ACTIVE' ? <Tag color="success">Hoạt động</Tag> : <Tag>Ngừng hoạt động</Tag>) },
          {
            title: '', key: 'x', render: (_, r) => (
              <Space>
                <Button size="small" onClick={() => { setEdit(r); save.reset(); form.setFieldsValue(r); }}>Sửa</Button>
                {r.status === 'ACTIVE'
                  ? <Button size="small" danger onClick={() => { setClosing(r); setTarget(undefined); close.reset(); }}>Ngừng hoạt động</Button>
                  : <Button size="small" onClick={() => reopen.mutate(r.id)}>Mở lại</Button>}
              </Space>
            ),
          },
        ]} />
      </Card>
      <Modal open={!!edit} title={edit === 'new' ? 'Thêm chi nhánh' : 'Sửa chi nhánh'} okText="Lưu" cancelText="Hủy" onCancel={() => setEdit(null)}
        onOk={() => form.submit()} confirmLoading={save.isPending} destroyOnClose>
        <Form form={form} layout="vertical" onFinish={save.mutate}>
          <Form.Item name="code" label="Mã chi nhánh" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="name" label="Tên chi nhánh" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="address" label="Địa chỉ" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="phone" label="Điện thoại"><Input /></Form.Item>
          {save.isError && <Alert type="error" message={errorMessage(save.error)} />}
        </Form>
      </Modal>
      <Modal open={!!closing} title={`Ngừng hoạt động ${closing?.name}?`} okText="Ngừng hoạt động" okButtonProps={{ danger: true }} cancelText="Hủy"
        onCancel={() => setClosing(null)} onOk={() => close.mutate()} confirmLoading={close.isPending}>
        {closing?.openApplications ? (
          <>
            <Alert type="warning" showIcon style={{ marginBottom: 12 }} message={`Chi nhánh còn ${closing.openApplications} hồ sơ đang xử lý. Chọn chi nhánh nhận chuyển; khách hàng và chi nhánh mới sẽ nhận thông báo.`} />
            <Select style={{ width: '100%' }} placeholder="Chi nhánh nhận hồ sơ" value={target} onChange={setTarget}
              options={q.data?.filter(b => b.status === 'ACTIVE' && b.id !== closing.id).map(b => ({ value: b.id, label: b.name }))} />
          </>
        ) : <Typography.Text>Chi nhánh không còn hồ sơ đang xử lý; khách hàng sẽ không chọn được chi nhánh này khi lập hồ sơ.</Typography.Text>}
        {close.isError && <Alert style={{ marginTop: 12 }} type="error" message={errorMessage(close.error)} />}
      </Modal>
    </div>
  );
}
