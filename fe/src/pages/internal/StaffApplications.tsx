import { useState } from 'react';
import { App, Card, Col, Input, Row, Select, Space, Statistic, Table, Typography } from 'antd';
import { QrcodeOutlined } from '@ant-design/icons';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { staffApi } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import { APP_STATUS } from '../../utils/status';
import { dt, money } from '../../utils/format';
import StatusTag from '../../components/StatusTag';
import type { ApplicationStatus } from '../../api/types';

const dd = (v: string | null) => (v ? dt(v).slice(0, 10) : '—');

/** 5.1 Tra cứu hồ sơ; dùng lại cho hàng đợi đối chiếu (5.2) và kết quả xét duyệt (5.3) */
export default function StaffApplications({ title = 'Tra cứu hồ sơ vay', preset, hint }: { title?: string; preset?: ApplicationStatus[]; hint?: string }) {
  const nav = useNavigate();
  const { message } = App.useApp();
  const [status, setStatus] = useState<string[]>(preset ?? []);
  const [keyword, setKeyword] = useState('');
  const q = useQuery({ queryKey: ['staff-apps', status.join(','), keyword], queryFn: () => staffApi.applications({ status: status.join(',') || undefined, keyword }) });
  const sum = useQuery({ queryKey: ['staff-summary'], queryFn: staffApi.summary });
  const qr = useMutation({ mutationFn: staffApi.byQr, onSuccess: r => nav(`/noi-bo/ho-so/${r.id}`), onError: e => message.error(errorMessage(e)) });
  return (
    <div>
      <Typography.Title level={3} style={{ marginTop: 0 }}>{title}</Typography.Title>
      {hint && <Typography.Paragraph type="secondary">{hint}</Typography.Paragraph>}
      {!preset && (
        <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
          {[['Chờ đối chiếu', sum.data?.waitingVerify, 'WAITING_VERIFY'], ['Cần bổ sung', sum.data?.needMore, 'NEED_MORE'], ['Đã đối chiếu, chờ kết quả', sum.data?.verified, 'VERIFIED'], ['Được duyệt, chờ giải ngân', sum.data?.approved, 'APPROVED']].map(([t, v, s]) => (
            <Col xs={12} md={6} key={t as string}>
              <Card size="small" hoverable onClick={() => setStatus([s as string])}><Statistic title={t as string} value={(v as number) ?? 0} /></Card>
            </Col>
          ))}
        </Row>
      )}
      <Card>
        <Space wrap style={{ marginBottom: 16 }}>
          <Input.Search allowClear enterButton={<><QrcodeOutlined /> Mở theo mã QR</>} placeholder="Nhập hoặc quét mã QR trên phiếu tóm tắt" style={{ width: 380 }}
            loading={qr.isPending} onSearch={v => v.trim() && qr.mutate(v.trim())} />
          <Input.Search allowClear placeholder="Mã hồ sơ, tên, số điện thoại" style={{ width: 280 }} onSearch={setKeyword} />
          {!preset && (
            <Select mode="multiple" allowClear placeholder="Trạng thái" style={{ minWidth: 220 }} value={status} onChange={setStatus}
              options={Object.entries(APP_STATUS).filter(([k]) => k !== 'DRAFTING').map(([k, v]) => ({ value: k, label: v.label }))} />
          )}
        </Space>
        <Table loading={q.isLoading} rowKey="id" dataSource={q.data} scroll={{ x: 1020 }}
          onRow={r => ({ onClick: () => nav(`/noi-bo/ho-so/${r.id}`), style: { cursor: 'pointer' } })}
          columns={[
            { title: 'Mã hồ sơ', dataIndex: 'code', width: 120, fixed: 'left', className: 'nowrap-cell' },
            { title: 'Khách hàng', dataIndex: 'customerName', width: 170, render: (v, r) => <div>{v}<div style={{ color: '#64748b', fontSize: 12 }}>{r.customerPhone}</div></div> },
            { title: 'Gói vay', dataIndex: 'productName', width: 120, className: 'nowrap-cell', render: (v: string) => v.replace(/ không tài sản bảo đảm$/, '') },
            { title: 'Số tiền · Kỳ hạn', dataIndex: 'amount', width: 195, className: 'nowrap-cell', render: (v: number, r) => `${money(v)} · ${r.termMonths} tháng`, sorter: (a, b) => a.amount - b.amount },
            { title: 'Điểm', dataIndex: 'score', width: 70, sorter: (a, b) => a.score - b.score },
            { title: 'Hiệu lực đến', dataIndex: 'expiresAt', width: 110, className: 'nowrap-cell', render: dd },
            { title: 'Cập nhật', dataIndex: 'updatedAt', width: 110, className: 'nowrap-cell', render: dd, defaultSortOrder: 'descend', sorter: (a, b) => a.updatedAt.localeCompare(b.updatedAt) },
            { title: 'Trạng thái', dataIndex: 'status', width: 130, fixed: 'right', className: 'nowrap-cell', render: (v: ApplicationStatus) => <StatusTag status={v} /> },
          ]} />
      </Card>
    </div>
  );
}
