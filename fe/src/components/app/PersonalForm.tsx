import { useEffect } from 'react';
import { App, Button, Card, Col, DatePicker, Form, Input, Radio, Row } from 'antd';
import dayjs from 'dayjs';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { appApi } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import type { Application } from '../../api/types';

/** Thông tin định danh của người vay (chỉ thu thập sau khi đồng ý phần b) */
export default function PersonalForm({ app, editable }: { app: Application; editable: boolean }) {
  const [form] = Form.useForm();
  const qc = useQueryClient();
  const { message } = App.useApp();
  useEffect(() => {
    form.setFieldsValue({ ...app.personal, dob: app.personal.dob ? dayjs(app.personal.dob) : null });
  }, [app.personal, form]);
  const m = useMutation({
    mutationFn: (v: Record<string, unknown>) => appApi.personal(app.id, { ...app.personal, ...v, dob: (v.dob as dayjs.Dayjs)?.format('YYYY-MM-DD') ?? '' } as Application['personal']),
    onSuccess: a => { qc.setQueryData(['application', app.id], a); message.success('Đã lưu thông tin cá nhân'); },
    onError: e => message.error(errorMessage(e)),
  });
  return (
    <Card title="Thông tin cá nhân" size="small" style={{ marginBottom: 16 }}>
      <Form form={form} layout="vertical" disabled={!editable} onFinish={m.mutate} requiredMark="optional">
        <Row gutter={16}>
          <Col xs={24} md={8}><Form.Item name="fullName" label="Họ và tên (như trên CCCD)" rules={[{ required: true, message: 'Nhập họ tên' }]}><Input /></Form.Item></Col>
          <Col xs={24} md={8}><Form.Item name="nationalId" label="Số CCCD" rules={[{ required: true, pattern: /^\d{12}$/, message: 'Số CCCD gồm 12 chữ số' }]}><Input maxLength={12} /></Form.Item></Col>
          <Col xs={24} md={8}><Form.Item name="dob" label="Ngày sinh" rules={[{ required: true, message: 'Chọn ngày sinh' }]}><DatePicker style={{ width: '100%' }} format="DD/MM/YYYY" /></Form.Item></Col>
          <Col xs={24} md={8}><Form.Item name="gender" label="Giới tính"><Radio.Group options={[{ value: 'MALE', label: 'Nam' }, { value: 'FEMALE', label: 'Nữ' }]} /></Form.Item></Col>
          <Col xs={24} md={16}><Form.Item name="address" label="Địa chỉ thường trú" rules={[{ required: true, message: 'Nhập địa chỉ' }]}><Input /></Form.Item></Col>
          <Col xs={24} md={8}><Form.Item name="phone" label="Số điện thoại"><Input disabled /></Form.Item></Col>
          <Col xs={24} md={8}><Form.Item name="email" label="Email" rules={[{ type: 'email', message: 'Email không hợp lệ' }]}><Input /></Form.Item></Col>
        </Row>
        {editable && <Button type="primary" htmlType="submit" loading={m.isPending}>Lưu thông tin</Button>}
      </Form>
    </Card>
  );
}
