import { useEffect, useState } from 'react';
import {
  Alert, App, Button, Card, Col, DatePicker, Descriptions, Form, Image, Input, InputNumber, Radio, Result, Row, Skeleton, Space,
  Table, Tabs, Tag, Typography,
} from 'antd';
import { ArrowLeftOutlined, AuditOutlined, CheckCircleOutlined, DollarOutlined, FilePdfOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import { appApi, staffApi } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import type { Application, AppDocument } from '../../api/types';
import StatusTag from '../../components/StatusTag';
import StatusTimeline from '../../components/StatusTimeline';
import WarningsPanel from '../../components/app/WarningsPanel';
import { DOC_STATUS, VERIFY_TAG } from '../../components/app/DocumentCard';
import { dt, money, moneyFormatter, moneyParser, pct } from '../../utils/format';

type Res = 'MATCH' | 'MISMATCH' | 'MISSING';

/** 5.2 Phiếu đối chiếu hồ sơ tại chi nhánh (MB09) */
function VerifyForm({ app }: { app: Application }) {
  const docs = app.documents.filter(d => d.documentTypeId !== 1 && d.fileName);
  const [rows, setRows] = useState<Record<number, { result: Res; reason: string }>>({});
  const [note, setNote] = useState('');
  useEffect(() => { setRows(Object.fromEntries(docs.map(d => [d.id, { result: 'MATCH' as Res, reason: '' }]))); }, [app.id]); // eslint-disable-line react-hooks/exhaustive-deps
  const qc = useQueryClient();
  const { message } = App.useApp();
  const m = useMutation({
    mutationFn: () => staffApi.verify(app.id, { details: docs.map(d => ({ documentId: d.id, ...rows[d.id] })), note }),
    onSuccess: a => { qc.setQueryData(['application', app.id], a); qc.invalidateQueries({ queryKey: ['staff-apps'] }); message.success(a.status === 'VERIFIED' ? 'Hồ sơ đã đối chiếu xong' : 'Đã chuyển “Cần bổ sung” và thông báo khách hàng'); },
  });
  const allMatch = Object.values(rows).every(r => r.result === 'MATCH');
  return (
    <Card title={<Space><AuditOutlined />Phiếu đối chiếu giấy tờ bản gốc</Space>} size="small" style={{ marginBottom: 16 }}>
      <Table size="small" rowKey="id" pagination={false} dataSource={docs} columns={[
        { title: 'Giấy tờ', dataIndex: 'typeName', width: 170 },
        {
          title: 'Kết quả', width: 290, render: (_, d: AppDocument) => (
            <Radio.Group value={rows[d.id]?.result} onChange={e => setRows(r => ({ ...r, [d.id]: { ...r[d.id], result: e.target.value } }))}
              options={[{ value: 'MATCH', label: 'Khớp' }, { value: 'MISMATCH', label: 'Không khớp' }, { value: 'MISSING', label: 'Thiếu bản gốc' }]} style={{ whiteSpace: 'nowrap' }} />
          ),
        },
        {
          title: 'Lý do', render: (_, d: AppDocument) => rows[d.id]?.result !== 'MATCH'
            ? <Input.TextArea autoSize={{ minRows: 1, maxRows: 6 }} status={rows[d.id]?.reason ? undefined : 'error'} placeholder="Bắt buộc" value={rows[d.id]?.reason} onChange={e => setRows(r => ({ ...r, [d.id]: { ...r[d.id], reason: e.target.value } }))} /> : null,
        },
      ]} />
      <Input.TextArea style={{ marginTop: 12 }} rows={2} placeholder="Ghi chú chung" value={note} onChange={e => setNote(e.target.value)} />
      {m.isError && <Alert style={{ marginTop: 12 }} type="error" message={errorMessage(m.error)} />}
      <Space style={{ marginTop: 12 }}>
        <Button type="primary" loading={m.isPending} onClick={() => m.mutate()}>{allMatch ? 'Xác nhận khớp toàn bộ' : 'Lưu và yêu cầu bổ sung'}</Button>
        <Typography.Text type="secondary">{allMatch ? 'Hồ sơ chuyển “Đã đối chiếu”.' : 'Hồ sơ chuyển “Cần bổ sung”, khách hàng nhận thông báo kèm lý do.'}</Typography.Text>
      </Space>
    </Card>
  );
}

/** 5.3 Cập nhật kết quả xét duyệt theo thông báo của ngân hàng (MB16) */
function DecisionForm({ app }: { app: Application }) {
  const [form] = Form.useForm();
  const type = Form.useWatch('resultType', form);
  const qc = useQueryClient();
  const { message } = App.useApp();
  const m = useMutation({
    mutationFn: (v: { resultType: 'APPROVED' | 'REJECTED' | 'MORE_INFO'; approvedAmount?: number; approvedTerm?: number; approvedRate?: number; reason?: string }) => staffApi.decide(app.id, v),
    onSuccess: a => { qc.setQueryData(['application', app.id], a); qc.invalidateQueries({ queryKey: ['staff-apps'] }); message.success('Đã ghi nhận kết quả và thông báo khách hàng'); },
  });
  return (
    <Card title={<Space><CheckCircleOutlined />Kết quả xét duyệt</Space>} size="small" style={{ marginBottom: 16 }}>
      <Form form={form} layout="vertical" onFinish={m.mutate}
        initialValues={{ resultType: 'APPROVED', approvedAmount: app.plan.amount, approvedTerm: app.plan.termMonths, approvedRate: app.plan.refRate }}>
        <Form.Item name="resultType" label="Kết quả do ngân hàng thông báo">
          <Radio.Group optionType="button" buttonStyle="solid" options={[{ value: 'APPROVED', label: 'Phê duyệt' }, { value: 'REJECTED', label: 'Từ chối' }, { value: 'MORE_INFO', label: 'Yêu cầu bổ sung' }]} />
        </Form.Item>
        {type === 'APPROVED' ? (
          <Row gutter={12}>
            <Col xs={24} md={10}><Form.Item name="approvedAmount" label="Số tiền được duyệt" rules={[{ required: true }]}><InputNumber style={{ width: '100%' }} formatter={moneyFormatter} parser={moneyParser} addonAfter="đ" /></Form.Item></Col>
            <Col xs={12} md={7}><Form.Item name="approvedTerm" label="Kỳ hạn" rules={[{ required: true }]}><InputNumber style={{ width: '100%' }} min={1} addonAfter="tháng" /></Form.Item></Col>
            <Col xs={12} md={7}><Form.Item name="approvedRate" label="Lãi suất" rules={[{ required: true }]}><InputNumber style={{ width: '100%' }} min={0} step={0.1} addonAfter="%/năm" /></Form.Item></Col>
          </Row>
        ) : (
          <Form.Item name="reason" label={type === 'REJECTED' ? 'Lý do từ chối' : 'Nội dung cần bổ sung'} rules={[{ required: true, message: 'Nhập lý do' }]}>
            <Input.TextArea rows={2} />
          </Form.Item>
        )}
        {m.isError && <Alert style={{ marginBottom: 12 }} type="error" message={errorMessage(m.error)} />}
        <Button type="primary" htmlType="submit" loading={m.isPending}>Ghi nhận kết quả</Button>
      </Form>
    </Card>
  );
}

/** 5.3 Cập nhật thông tin giải ngân */
function DisburseForm({ app }: { app: Application }) {
  const qc = useQueryClient();
  const { message } = App.useApp();
  const m = useMutation({
    mutationFn: (v: { disbursedAt: dayjs.Dayjs; disbursedAmount: number; beneficiary: string }) =>
      staffApi.disburse(app.id, { ...v, disbursedAt: v.disbursedAt.format('YYYY-MM-DD') }),
    onSuccess: a => { qc.setQueryData(['application', app.id], a); qc.invalidateQueries({ queryKey: ['staff-apps'] }); message.success('Đã cập nhật giải ngân, hồ sơ đóng “Đã giải ngân”'); },
  });
  const ben = app.requestForm?.content.find(c => c.key === 'beneficiary')?.value || '';
  return (
    <Card title={<Space><DollarOutlined />Thông tin giải ngân</Space>} size="small" style={{ marginBottom: 16 }}>
      <Form layout="vertical" onFinish={m.mutate} initialValues={{ disbursedAt: dayjs(), disbursedAmount: app.approval?.approvedAmount, beneficiary: ben }}>
        <Row gutter={12}>
          <Col xs={24} md={6}><Form.Item name="disbursedAt" label="Ngày giải ngân" rules={[{ required: true }]}><DatePicker style={{ width: '100%' }} format="DD/MM/YYYY" /></Form.Item></Col>
          <Col xs={24} md={8}><Form.Item name="disbursedAmount" label="Số tiền giải ngân" rules={[{ required: true }]}><InputNumber style={{ width: '100%' }} formatter={moneyFormatter} parser={moneyParser} addonAfter="đ" /></Form.Item></Col>
          <Col xs={24} md={10}><Form.Item name="beneficiary" label="Bên thụ hưởng" rules={[{ required: true, message: 'Nhập bên thụ hưởng' }]}><Input /></Form.Item></Col>
        </Row>
        {m.isError && <Alert style={{ marginBottom: 12 }} type="error" message={errorMessage(m.error)} />}
        <Button type="primary" htmlType="submit" loading={m.isPending}>Xác nhận đã giải ngân</Button>
      </Form>
    </Card>
  );
}

export default function StaffApplicationDetail() {
  const { id } = useParams();
  const aid = Number(id);
  const nav = useNavigate();
  const q = useQuery({ queryKey: ['application', aid], queryFn: () => appApi.get(aid) });
  if (q.isLoading) return <Skeleton active />;
  if (!q.data) return <Result status="404" title="Không tìm thấy hồ sơ của chi nhánh" />;
  const a = q.data;
  const docs = a.documents;
  return (
    <div>
      <Button type="link" icon={<ArrowLeftOutlined />} style={{ paddingLeft: 0 }} onClick={() => nav(-1)}>Quay lại</Button>
      <Typography.Title level={3} style={{ marginTop: 0 }}>Hồ sơ {a.code} <StatusTag status={a.status} /></Typography.Title>
      <Row gutter={16}>
        <Col xs={24} xl={16}>
          {a.can.verify && <VerifyForm app={a} />}
          {a.can.decide && <DecisionForm app={a} />}
          {a.can.disburse && <DisburseForm app={a} />}
          <Card size="small" style={{ marginBottom: 16 }}>
            <Tabs items={[
              {
                key: 'docs', label: 'Giấy tờ và thông tin trích xuất', children: (
                  <Table size="small" rowKey="id" pagination={false} dataSource={docs} expandable={{
                    rowExpandable: d => d.fields.length > 0,
                    expandedRowRender: d => (
                      <Table size="small" rowKey="id" pagination={false} dataSource={d.fields} columns={[
                        { title: 'Trường', dataIndex: 'label' },
                        { title: 'Máy đọc', dataIndex: 'ocrValue', render: (v, f) => <span>{v} <Tag>{f.confidence}%</Tag></span> },
                        { title: 'Khách hàng xác nhận', dataIndex: 'confirmedValue', render: (v, f) => <span>{v ?? '—'} {v && v !== f.ocrValue && <Tag color="gold">đã sửa</Tag>}</span> },
                      ]} />
                    ),
                  }} columns={[
                    { title: 'Bản chụp', width: 90, render: (_, d) => d.previewUrl ? <Image src={d.previewUrl} width={64} height={48} style={{ objectFit: 'cover' }} /> : d.fileName ? <FilePdfOutlined style={{ fontSize: 24, color: '#94a3b8' }} /> : '—' },
                    { title: 'Giấy tờ', dataIndex: 'typeName', render: (v, d) => <div>{v}<div style={{ fontSize: 12, color: '#64748b' }}>{d.fileName ?? 'Chưa tải'}{d.fileName ? ` · v${d.versionNo} · ${dt(d.uploadedAt)}` : ''}</div></div> },
                    { title: 'Trạng thái', render: (_, d) => <Tag color={DOC_STATUS[d.status].color}>{DOC_STATUS[d.status].label}</Tag> },
                    { title: 'Đối chiếu', render: (_, d) => d.verifyResult ? <Tag color={VERIFY_TAG[d.verifyResult].color}>{VERIFY_TAG[d.verifyResult].label}</Tag> : '—' },
                  ]} />
                ),
              },
              {
                key: 'form', label: 'Giấy đề nghị', children: a.requestForm?.content.length ? (
                  <Descriptions size="small" bordered column={1}>
                    {a.requestForm.content.map(c => <Descriptions.Item key={c.key} label={c.label}>{c.value || '—'}</Descriptions.Item>)}
                    <Descriptions.Item label="Xác nhận OTP">{dt(a.requestForm.otpConfirmedAt)}</Descriptions.Item>
                  </Descriptions>
                ) : <Typography.Text type="secondary">Không có nội dung.</Typography.Text>,
              },
              { key: 'his', label: 'Lịch sử', children: <StatusTimeline history={a.history} /> },
              {
                key: 'ver', label: `Phiếu đối chiếu (${a.verifications.length})`, children: a.verifications.length ? a.verifications.slice().reverse().map(v => (
                  <Card key={v.id} size="small" style={{ marginBottom: 8 }} title={`${dt(v.verifiedAt)} · ${v.staffName}`} extra={<Tag color={v.overallResult === 'MATCH' ? 'success' : 'error'}>{v.overallResult === 'MATCH' ? 'Khớp' : 'Không khớp'}</Tag>}>
                    {v.details.map(x => <div key={x.documentId}>{x.typeName}: <Tag color={VERIFY_TAG[x.result].color}>{VERIFY_TAG[x.result].label}</Tag>{x.reason}</div>)}
                    {v.note && <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>Ghi chú: {v.note}</Typography.Paragraph>}
                  </Card>
                )) : <Typography.Text type="secondary">Chưa đối chiếu.</Typography.Text>,
              },
            ]} />
          </Card>
        </Col>
        <Col xs={24} xl={8}>
          <Card size="small" title="Khách hàng" style={{ marginBottom: 16 }}>
            <Descriptions size="small" column={1}>
              <Descriptions.Item label="Họ tên">{a.personal.fullName}</Descriptions.Item>
              <Descriptions.Item label="Số CCCD">{a.personal.nationalId}</Descriptions.Item>
              <Descriptions.Item label="Ngày sinh">{a.personal.dob?.split('-').reverse().join('/')}</Descriptions.Item>
              <Descriptions.Item label="Điện thoại">{a.personal.phone}</Descriptions.Item>
              <Descriptions.Item label="Địa chỉ">{a.personal.address}</Descriptions.Item>
            </Descriptions>
          </Card>
          <Card size="small" title="Phương án vay" style={{ marginBottom: 16 }}>
            <Descriptions size="small" column={1}>
              <Descriptions.Item label="Gói vay">{a.plan.productName}</Descriptions.Item>
              <Descriptions.Item label="Số tiền">{money(a.plan.amount)}</Descriptions.Item>
              <Descriptions.Item label="Kỳ hạn, lãi suất">{a.plan.termMonths} tháng · {a.plan.refRate}%/năm</Descriptions.Item>
              <Descriptions.Item label="DTI sau vay">{pct(a.plan.dtiAfter)}</Descriptions.Item>
              <Descriptions.Item label="Điểm tương thích">{a.plan.score}</Descriptions.Item>
              <Descriptions.Item label="Mục đích">{a.purpose}</Descriptions.Item>
              <Descriptions.Item label="Bán chéo">{a.plan.crossSells.map(c => c.name).join(', ') || 'Không'}</Descriptions.Item>
            </Descriptions>
          </Card>
          <Card size="small" title="Khóa nội dung" style={{ marginBottom: 16 }}>
            <Descriptions size="small" column={1}>
              <Descriptions.Item label="Mã QR">{a.qrToken ?? '—'}</Descriptions.Item>
              <Descriptions.Item label="Nộp lúc">{dt(a.lockedAt)}</Descriptions.Item>
              <Descriptions.Item label="Hiệu lực đến">{dt(a.expiresAt)}</Descriptions.Item>
              <Descriptions.Item label="Phiên bản tham số">{a.versionNo}</Descriptions.Item>
              <Descriptions.Item label="SHA-256"><Typography.Text code style={{ fontSize: 11, wordBreak: 'break-all' }}>{a.contentHash ?? '—'}</Typography.Text></Descriptions.Item>
            </Descriptions>
          </Card>
          <WarningsPanel app={a} compact />
        </Col>
      </Row>
    </div>
  );
}
