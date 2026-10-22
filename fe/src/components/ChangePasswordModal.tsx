import { App, Form, Input, Modal } from 'antd';
import { useMutation } from '@tanstack/react-query';
import { authApi } from '../api/endpoints';
import { errorMessage } from '../api/client';

/** 1.4 Đổi mật khẩu */
export default function ChangePasswordModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [form] = Form.useForm();
  const { message } = App.useApp();
  const m = useMutation({
    mutationFn: authApi.changePassword,
    onSuccess: () => { message.success('Đã đổi mật khẩu'); form.resetFields(); onClose(); },
    onError: e => message.error(errorMessage(e)),
  });
  return (
    <Modal open={open} title="Đổi mật khẩu" okText="Đổi mật khẩu" cancelText="Hủy" onCancel={onClose} confirmLoading={m.isPending}
      onOk={() => form.submit()} destroyOnClose>
      <Form form={form} layout="vertical" requiredMark={false} onFinish={v => m.mutate({ oldPassword: v.oldPassword, newPassword: v.newPassword })}>
        <Form.Item name="oldPassword" label="Mật khẩu hiện tại" rules={[{ required: true, message: 'Nhập mật khẩu hiện tại' }]}>
          <Input.Password autoFocus />
        </Form.Item>
        <Form.Item name="newPassword" label="Mật khẩu mới" rules={[{ required: true, min: 6, message: 'Tối thiểu 6 ký tự' }]}>
          <Input.Password />
        </Form.Item>
        <Form.Item name="confirm" label="Nhập lại mật khẩu mới" dependencies={['newPassword']}
          rules={[{ required: true, message: 'Nhập lại mật khẩu mới' }, ({ getFieldValue }) => ({
            validator: (_, v) => (v === getFieldValue('newPassword') ? Promise.resolve() : Promise.reject(new Error('Mật khẩu không khớp'))),
          })]}>
          <Input.Password />
        </Form.Item>
      </Form>
    </Modal>
  );
}
