import { useEffect, useState } from 'react';
import { Alert, App, Button, Card, Input, List, Space, Table, Tag, Typography } from 'antd';
import { SafetyOutlined } from '@ant-design/icons';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { appApi } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import type { Application } from '../../api/types';
import OtpModal from '../OtpModal';
import { dt } from '../../utils/format';

export function missingItems(app: Application): string[] {
  const m: string[] = [];
  const p = app.personal;
  if (!p.fullName || !p.nationalId || !p.address || !p.dob) m.push('Khai đủ thông tin cá nhân (họ tên, số CCCD, ngày sinh, địa chỉ).');
  for (const d of app.documents) {
    if (d.documentTypeId === 1 || !d.required) continue;
    if (d.status !== 'CONFIRMED') m.push(`${d.status === 'UPLOADED' ? 'Xác nhận thông tin' : 'Tải lên'}: ${d.typeName}.`);
  }
  for (const d of app.documents) if (d.verifyResult === 'MISMATCH' || d.verifyResult === 'MISSING') m.push(`Tải lại giấy tờ không khớp: ${d.typeName}.`);
  if (app.warnings.some(w => w.severity === 'BLOCKING')) m.push('Xử lý các cảnh báo “Cần xử lý” ở mục Đối chiếu tự động.');
  return m;
}

/** 4.2 Giấy đề nghị vay vốn (MB15) tự điền, xác nhận OTP và nộp/nộp lại hồ sơ */
export default function RequestFormPanel({ app }: { app: Application }) {
  const qc = useQueryClient();
  const { message } = App.useApp();
  const [vals, setVals] = useState<Record<string, string>>({});
  const [otpOpen, setOtpOpen] = useState(false);
  useEffect(() => { setVals(Object.fromEntries((app.requestForm?.content ?? []).map(c => [c.key, c.value]))); }, [app.requestForm]);
  const set = (a: Application) => qc.setQueryData(['application', app.id], a);
  const create = useMutation({ mutationFn: () => appApi.createForm(app.id), onSuccess: set, onError: e => message.error(errorMessage(e)) });
  const save = useMutation({ mutationFn: () => appApi.updateForm(app.id, vals), onSuccess: a => { set(a); message.success('Đã lưu giấy đề nghị'); }, onError: e => message.error(errorMessage(e)) });
  const submit = useMutation({
    mutationFn: async (otp: string) => {
      if (app.requestForm?.status === 'DRAFT') await appApi.updateForm(app.id, vals);
      return appApi.submit(app.id, otp);
    },
    onSuccess: a => { set(a); setOtpOpen(false); qc.invalidateQueries({ queryKey: ['notifications'] }); message.success(app.can.resubmit ? 'Đã nộp lại hồ sơ' : 'Đã nộp hồ sơ, xem Phiếu tóm tắt để mang tới chi nhánh'); },
  });
  const miss = missingItems(app);
  const resub = app.can.resubmit;
  const form = app.requestForm;

  return (
    <Card title="Giấy đề nghị vay vốn" size="small" style={{ marginBottom: 16 }}
      extra={form && <Tag color={form.status === 'CONFIRMED' ? 'success' : 'gold'}>{form.status === 'CONFIRMED' ? `Đã xác nhận OTP ${dt(form.otpConfirmedAt)}` : 'Bản nháp'}</Tag>}>
      {(app.can.submit || resub) && miss.length > 0 && (
        <Alert type="warning" showIcon style={{ marginBottom: 12 }} message="Cần hoàn tất trước khi nộp"
          description={<List size="small" split={false} dataSource={miss} renderItem={x => <List.Item style={{ padding: '2px 0' }}>• {x}</List.Item>} />} />
      )}
      {!form && app.can.submit && (
        <Space direction="vertical">
          <Typography.Text type="secondary">Hệ thống điền sẵn giấy đề nghị từ thông tin cá nhân, giấy tờ đã xác nhận và phương án vay.</Typography.Text>
          <Button type="primary" disabled={miss.length > 0} loading={create.isPending} onClick={() => create.mutate()}>Lập giấy đề nghị</Button>
        </Space>
      )}
      {form && (
        <Table size="small" rowKey="key" pagination={false} dataSource={form.content} showHeader={false}
          columns={[
            { dataIndex: 'label', width: '32%', render: (v: string, r) => <span>{v}{r.editable && form.status === 'DRAFT' && <Tag style={{ marginLeft: 6 }}>sửa được</Tag>}</span> },
            {
              dataIndex: 'value', render: (v: string, r) => r.editable && form.status === 'DRAFT' && app.can.submit
                ? <Input value={vals[r.key] ?? ''} onChange={e => setVals(x => ({ ...x, [r.key]: e.target.value }))} /> : (v || '—'),
            },
          ]} />
      )}
      {form?.status === 'DRAFT' && app.can.submit && (
        <Space style={{ marginTop: 12 }} wrap>
          <Button onClick={() => save.mutate()} loading={save.isPending}>Lưu</Button>
          <Button onClick={() => create.mutate()} loading={create.isPending}>Lập lại từ thông tin mới nhất</Button>
          <Button type="primary" icon={<SafetyOutlined />} disabled={miss.length > 0} onClick={() => setOtpOpen(true)}>Xác nhận bằng OTP và nộp hồ sơ</Button>
        </Space>
      )}
      {resub && (
        <div style={{ marginTop: 12 }}>
          <Button type="primary" icon={<SafetyOutlined />} disabled={miss.length > 0} onClick={() => setOtpOpen(true)}>Nộp lại hồ sơ</Button>
        </div>
      )}
      <OtpModal open={otpOpen} title={resub ? 'Xác nhận nộp lại hồ sơ' : 'Xác nhận giấy đề nghị vay vốn'} okText={resub ? 'Nộp lại' : 'Xác nhận và nộp'}
        description={!resub && 'Khi xác nhận, nội dung hồ sơ được khóa (lưu mã băm SHA-256) và chuyển tới chi nhánh. Bạn vẫn ký tươi bản in tại quầy.'}
        loading={submit.isPending} error={submit.isError ? errorMessage(submit.error) : null}
        onOk={otp => submit.mutate(otp)} onCancel={() => { setOtpOpen(false); submit.reset(); }} />
    </Card>
  );
}
