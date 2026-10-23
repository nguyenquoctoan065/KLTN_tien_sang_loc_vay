import { useState } from 'react';
import { App, Button, Card, Col, Descriptions, Input, Modal, Result, Row, Skeleton, Space, Tabs, Typography } from 'antd';
import { ArrowLeftOutlined, StopOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { appApi } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import StatusTag from '../../components/StatusTag';
import StatusTimeline from '../../components/StatusTimeline';
import AppSteps from '../../components/app/AppSteps';
import StatusAlert from '../../components/app/StatusAlert';
import PersonalForm from '../../components/app/PersonalForm';
import DocumentCard from '../../components/app/DocumentCard';
import WarningsPanel from '../../components/app/WarningsPanel';
import PlanAdjust from '../../components/app/PlanAdjust';
import RequestFormPanel from '../../components/app/RequestFormPanel';
import SummarySlip from '../../components/app/SummarySlip';
import { dt, money, pct } from '../../utils/format';

/** Hồ sơ vay của khách hàng: 4.1, 4.2, 5.4, 6.2 */
export default function ApplicationDetail() {
  const { id } = useParams();
  const aid = id ?? '';
  const nav = useNavigate();
  const [sp, setSp] = useSearchParams();
  const qc = useQueryClient();
  const { message } = App.useApp();
  const q = useQuery({ queryKey: ['application', aid], queryFn: () => appApi.get(aid) });
  const [cancelOpen, setCancelOpen] = useState(false);
  const [reason, setReason] = useState('');
  const cancel = useMutation({
    mutationFn: () => appApi.cancel(aid, reason),
    onSuccess: a => { qc.setQueryData(['application', aid], a); setCancelOpen(false); message.success('Đã hủy hồ sơ'); },
    onError: e => message.error(errorMessage(e)),
  });
  if (q.isLoading) return <Skeleton active />;
  if (!q.data) return <Result status="404" title="Không tìm thấy hồ sơ" extra={<Button onClick={() => nav('/cua-toi')}>Về trang của tôi</Button>} />;
  const a = q.data;
  const editable = a.can.edit;
  const tab = sp.get('tab') || (a.lockedAt && !editable ? 'phieu' : 'ho-so');
  const docs = a.documents;

  return (
    <div>
      <Button type="link" icon={<ArrowLeftOutlined />} style={{ paddingLeft: 0 }} onClick={() => nav('/cua-toi')}>Hồ sơ của tôi</Button>
      <Space style={{ width: '100%', justifyContent: 'space-between' }} wrap>
        <div>
          <Typography.Title level={3} style={{ margin: 0 }}>Hồ sơ {a.code} <StatusTag status={a.status} /></Typography.Title>
          <Typography.Text type="secondary">{a.branchName} · tạo {dt(a.createdAt)} · cập nhật {dt(a.updatedAt)}</Typography.Text>
        </div>
        {a.can.cancel && <Button danger icon={<StopOutlined />} onClick={() => setCancelOpen(true)}>Hủy hồ sơ</Button>}
        {(a.status === 'VERIFIED' || a.status === 'APPROVED') && (
          <Typography.Text type="secondary">Hồ sơ đã đối chiếu xong; muốn hủy, vui lòng liên hệ {a.branchName}.</Typography.Text>
        )}
      </Space>
      <Card size="small" style={{ margin: '16px 0' }}><AppSteps app={a} /></Card>
      <StatusAlert app={a} />
      <Card size="small" style={{ margin: '16px 0' }}>
        <Descriptions size="small" column={{ xs: 1, sm: 2, md: 3, lg: 6 }}>
          <Descriptions.Item label="Gói vay">{a.plan.productName}</Descriptions.Item>
          <Descriptions.Item label="Số tiền">{money(a.plan.amount)}</Descriptions.Item>
          <Descriptions.Item label="Kỳ hạn">{a.plan.termMonths} tháng</Descriptions.Item>
          <Descriptions.Item label="Lãi suất tham chiếu">{a.plan.refRate}%/năm</Descriptions.Item>
          <Descriptions.Item label="DTI sau vay">{pct(a.plan.dtiAfter)}</Descriptions.Item>
          <Descriptions.Item label="Mục đích">{a.purpose}</Descriptions.Item>
        </Descriptions>
      </Card>
      <Tabs activeKey={tab} onChange={k => { sp.set('tab', k); setSp(sp, { replace: true }); }} items={[
        {
          key: 'ho-so', label: 'Hồ sơ và giấy tờ', children: (
            <div>
              <PersonalForm app={a} editable={editable} />
              <Typography.Title level={5}>Danh mục hồ sơ cá nhân hóa</Typography.Title>
              <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
                {docs.map(d => <Col xs={24} xl={12} key={d.id}><DocumentCard app={a} doc={d} editable={editable} /></Col>)}
              </Row>
              <WarningsPanel app={a} extra={editable && a.recalc && !a.recalc.ok ? <PlanAdjust app={a} /> : null} />
              <RequestFormPanel app={a} />
            </div>
          ),
        },
        { key: 'phieu', label: 'Phiếu tóm tắt', disabled: !a.qrToken, children: <SummarySlip app={a} /> },
        {
          key: 'theo-doi', label: 'Theo dõi trạng thái', children: (
            <Row gutter={16}>
              <Col xs={24} lg={12}><Card size="small" title="Lịch sử trạng thái"><StatusTimeline history={a.history} /></Card></Col>
              <Col xs={24} lg={12}>
                {a.verifications.length > 0 && (
                  <Card size="small" title="Kết quả đối chiếu tại chi nhánh" style={{ marginBottom: 16 }}>
                    {a.verifications.slice().reverse().map(v => (
                      <div key={v.id} style={{ marginBottom: 8 }}>
                        <Typography.Text strong>{dt(v.verifiedAt)} · {v.staffName}</Typography.Text> – {v.overallResult === 'MATCH' ? 'Khớp toàn bộ' : 'Có giấy tờ không khớp'}
                        <ul style={{ margin: '4px 0' }}>{v.details.filter(x => x.result !== 'MATCH').map(x => <li key={x.documentId}>{x.typeName}: {x.reason}</li>)}</ul>
                      </div>
                    ))}
                  </Card>
                )}
                {a.approval && (
                  <Card size="small" title="Kết quả xét duyệt">
                    <Descriptions size="small" column={1}>
                      <Descriptions.Item label="Kết quả">{{ APPROVED: 'Phê duyệt', REJECTED: 'Từ chối', MORE_INFO: 'Yêu cầu bổ sung' }[a.approval.resultType]}</Descriptions.Item>
                      {a.approval.approvedAmount && <Descriptions.Item label="Được duyệt">{money(a.approval.approvedAmount)} · {a.approval.approvedTerm} tháng · {a.approval.approvedRate}%/năm</Descriptions.Item>}
                      {a.approval.reason && <Descriptions.Item label="Lý do">{a.approval.reason}</Descriptions.Item>}
                      {a.approval.disbursedAt && <Descriptions.Item label="Giải ngân">{money(a.approval.disbursedAmount)} ngày {a.approval.disbursedAt.split('-').reverse().join('/')} – {a.approval.beneficiary}</Descriptions.Item>}
                    </Descriptions>
                  </Card>
                )}
              </Col>
            </Row>
          ),
        },
      ]} />
      <Modal open={cancelOpen} title={`Hủy hồ sơ ${a.code}?`} okText="Hủy hồ sơ" okButtonProps={{ danger: true }} cancelText="Không"
        confirmLoading={cancel.isPending} onOk={() => cancel.mutate()} onCancel={() => setCancelOpen(false)}>
        <Typography.Paragraph>Hồ sơ sẽ chuyển trạng thái “Đã hủy” và chi nhánh được thông báo. Thao tác không hoàn tác được.</Typography.Paragraph>
        <Input.TextArea rows={2} placeholder="Lý do (không bắt buộc)" value={reason} onChange={e => setReason(e.target.value)} />
      </Modal>
    </div>
  );
}
