import { useState } from 'react';
import { Alert, App, Button, Card, Col, Descriptions, Modal, Radio, Result, Row, Skeleton, Space, Table, Tag, Typography } from 'antd';
import { DownloadOutlined, RollbackOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { myDataApi } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import type { WithdrawConsentResult } from '../../api/types';
import OtpModal from '../../components/OtpModal';
import StatusTag from '../../components/StatusTag';
import { dt } from '../../utils/format';

/** 6.1 Xem dữ liệu cá nhân và nhật ký truy cập; 6.3 Rút lại sự đồng ý */
export default function MyData() {
  const qc = useQueryClient();
  const { message } = App.useApp();
  const q = useQuery({ queryKey: ['my-data'], queryFn: myDataApi.get });
  const [scope, setScope] = useState<'A' | 'B' | null>(null);
  const [pick, setPick] = useState(false);
  const [otp, setOtp] = useState(false);
  const [result, setResult] = useState<WithdrawConsentResult | null>(null);
  const w = useMutation({
    mutationFn: (code: string) => myDataApi.withdraw(scope!, code),
    onSuccess: r => { setOtp(false); setResult(r); qc.invalidateQueries(); },
  });
  if (q.isLoading) return <Skeleton active />;
  if (!q.data) return <Result status="error" title="Không tải được dữ liệu" />;
  const d = q.data;
  const exportJson = () => {
    const blob = new Blob([JSON.stringify(d, null, 2)], { type: 'application/json' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob); a.download = `du-lieu-ca-nhan-${d.account.phone}.json`; a.click();
    message.success('Đã tải tệp dữ liệu');
  };
  const activeA = d.consents.some(c => c.scope === 'A' && !c.withdrawnAt);
  const activeB = d.consents.some(c => c.scope === 'B' && !c.withdrawnAt);
  return (
    <div>
      <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 12 }} wrap>
        <Typography.Text type="secondary">Dữ liệu hệ thống đang lưu về bạn và các lượt truy cập vào dữ liệu đó.</Typography.Text>
        <Space>
          <Button icon={<DownloadOutlined />} onClick={exportJson}>Tải dữ liệu (JSON)</Button>
          <Button danger icon={<RollbackOutlined />} disabled={!activeA && !activeB} onClick={() => { setScope(activeA ? 'A' : 'B'); setPick(true); }}>Rút lại sự đồng ý</Button>
        </Space>
      </Space>
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <Card size="small" title="Tài khoản và thông tin định danh" style={{ height: '100%' }}>
            <Descriptions size="small" column={1}>
              <Descriptions.Item label="Số điện thoại">{d.account.phone}</Descriptions.Item>
              <Descriptions.Item label="Ngày đăng ký">{dt(d.account.createdAt)}</Descriptions.Item>
              <Descriptions.Item label="Họ tên">{d.personal?.fullName || '—'}</Descriptions.Item>
              <Descriptions.Item label="Số CCCD">{d.personal?.nationalId || '—'}</Descriptions.Item>
              <Descriptions.Item label="Địa chỉ">{d.personal?.address || '—'}</Descriptions.Item>
              <Descriptions.Item label="Dữ liệu khác">{d.screenings} lượt sàng lọc đã lưu · {d.plans} phương án · {d.applications.length} hồ sơ</Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card size="small" title="Các lần đồng ý xử lý dữ liệu (MB02)" style={{ height: '100%' }}>
            <Table size="small" rowKey="id" pagination={{ pageSize: 5, hideOnSinglePage: true }} dataSource={d.consents} columns={[
              { title: 'Phần', dataIndex: 'scope', render: v => (v === 'A' ? '(a) Tiền sàng lọc' : '(b) Lưu giấy tờ, lập hồ sơ') },
              { title: 'Hồ sơ', dataIndex: 'applicationCode', render: v => v ?? '—' },
              { title: 'Đồng ý lúc', dataIndex: 'agreedAt', render: dt },
              { title: 'Trạng thái', dataIndex: 'withdrawnAt', render: v => (v ? <Tag>Đã rút {dt(v)}</Tag> : <Tag color="success">Hiệu lực</Tag>) },
            ]} />
          </Card>
        </Col>
        <Col span={24}>
          <Card size="small" title="Hồ sơ và giấy tờ đang lưu">
            <Table size="small" rowKey="code" pagination={false} dataSource={d.applications} locale={{ emptyText: 'Chưa có hồ sơ' }} columns={[
              { title: 'Mã hồ sơ', dataIndex: 'code' },
              { title: 'Trạng thái', dataIndex: 'status', render: v => <StatusTag status={v} /> },
              { title: 'Số giấy tờ đang lưu', dataIndex: 'documents' },
            ]} />
          </Card>
        </Col>
        <Col span={24}>
          <Card size="small" title="Nhật ký truy cập và thao tác">
            <Table size="small" rowKey="id" dataSource={d.logs} pagination={{ pageSize: 10 }} scroll={{ x: 700 }} columns={[
              { title: 'Thời điểm', dataIndex: 'createdAt', render: dt, width: 150 },
              { title: 'Người thực hiện', dataIndex: 'actorName', render: (v, r) => <span>{v} <Tag>{{ CUSTOMER: 'Bạn', STAFF: 'Nhân viên', SYSTEM: 'Hệ thống' }[r.actorType]}</Tag></span> },
              { title: 'Hành động', dataIndex: 'action' },
              { title: 'Hồ sơ', dataIndex: 'applicationCode', render: v => v ?? '—' },
              { title: 'Chi tiết', dataIndex: 'detail' },
            ]} />
          </Card>
        </Col>
      </Row>
      <Modal open={pick} title="Rút lại sự đồng ý" okText="Tiếp tục" cancelText="Hủy" onCancel={() => setPick(false)}
        onOk={() => { setPick(false); setOtp(true); }}>
        <Radio.Group value={scope} onChange={e => setScope(e.target.value)} style={{ width: '100%' }}>
          <Space direction="vertical" style={{ width: '100%' }}>
            <Radio value="A" disabled={!activeA}><b>Phần (a) – Tiền sàng lọc</b><div className="radio-desc">Xóa các lượt sàng lọc và phương án chưa gắn với hồ sơ vay.</div></Radio>
            <Radio value="B" disabled={!activeB}><b>Phần (b) – Lưu trữ giấy tờ, lập hồ sơ</b><div className="radio-desc">Hủy các hồ sơ chưa đối chiếu xong, xóa giấy tờ và số CCCD, địa chỉ. Hồ sơ đã đối chiếu tại chi nhánh được ngân hàng lưu theo quy định.</div></Radio>
          </Space>
        </Radio.Group>
        <Alert style={{ marginTop: 12 }} type="warning" showIcon message="Dữ liệu bị xóa không khôi phục được." />
      </Modal>
      <OtpModal open={otp} title="Xác nhận rút lại sự đồng ý" okText="Rút đồng ý" loading={w.isPending} error={w.isError ? errorMessage(w.error) : null}
        onOk={c => w.mutate(c)} onCancel={() => { setOtp(false); w.reset(); }} />
      <Modal open={!!result} title="Đã xử lý yêu cầu" footer={<Button type="primary" onClick={() => setResult(null)}>Đóng</Button>} onCancel={() => setResult(null)}>
        {result && (
          <div>
            <Typography.Paragraph>{result.message}</Typography.Paragraph>
            {result.cancelledApplications.length > 0 && <div>Hồ sơ đã hủy: {result.cancelledApplications.join(', ')}</div>}
            {result.keptApplications.length > 0 && <div>Hồ sơ ngân hàng tiếp tục lưu: {result.keptApplications.join(', ')}</div>}
          </div>
        )}
      </Modal>
    </div>
  );
}
