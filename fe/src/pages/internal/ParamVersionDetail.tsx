import { useEffect, useMemo, useState } from 'react';
import {
  Alert, App, Button, Card, Descriptions, InputNumber, Popconfirm, Result, Skeleton, Space, Table, Tabs, Tag, Typography,
} from 'antd';
import { ArrowLeftOutlined, ExperimentOutlined, RocketOutlined, SaveOutlined, DeleteOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import { adminApi } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import type { PolicyParam, ScoringWeight, TestRunRow } from '../../api/types';
import { dt, moneyFormatter, moneyParser, moneyShort } from '../../utils/format';
import { VERSION_STATUS } from './ParamVersions';

const PRODUCT = { TD: 'Vay tiêu dùng', TC: 'Vay thấu chi' } as const;
const MONEY_KEYS = new Set(['MAX_AMOUNT', 'MIN_AMOUNT', 'LIVING_COST_BASE', 'LIVING_COST_PER_DEPENDENT']);

/** 7.1, 7.2, 7.4: sửa MB11, MB12 trên bản nháp; chạy thử và kích hoạt */
export default function ParamVersionDetail() {
  const { id } = useParams();
  const vid = Number(id);
  const nav = useNavigate();
  const qc = useQueryClient();
  const { message } = App.useApp();
  const q = useQuery({ queryKey: ['version', vid], queryFn: () => adminApi.version(vid) });
  const [params, setParams] = useState<PolicyParam[]>([]);
  const [weights, setWeights] = useState<ScoringWeight[]>([]);
  const [dirty, setDirty] = useState(false);
  useEffect(() => { if (q.data) { setParams(q.data.params); setWeights(q.data.weights); setDirty(false); } }, [q.data]);
  const draft = q.data?.status === 'DRAFT';
  const refresh = () => { qc.invalidateQueries({ queryKey: ['version', vid] }); qc.invalidateQueries({ queryKey: ['versions'] }); };
  const onErr = (e: unknown) => message.error(errorMessage(e));
  const save = useMutation({ mutationFn: () => adminApi.update(vid, { params, weights }), onSuccess: () => { message.success('Đã lưu bản nháp'); refresh(); }, onError: onErr });
  const test = useMutation({ mutationFn: () => adminApi.test(vid), onSuccess: () => { message.success('Đã chạy thử trên bộ hồ sơ mẫu'); refresh(); }, onError: onErr });
  const activate = useMutation({ mutationFn: () => adminApi.activate(vid), onSuccess: () => { message.success('Đã kích hoạt phiên bản'); refresh(); }, onError: onErr });
  const remove = useMutation({ mutationFn: () => adminApi.remove(vid), onSuccess: () => { message.success('Đã xóa bản nháp'); qc.invalidateQueries({ queryKey: ['versions'] }); nav('/noi-bo/tham-so'); }, onError: onErr });
  const sums = useMemo(() => ({
    TD: weights.filter(w => w.productCode === 'TD').reduce((s, w) => s + Number(w.weight), 0),
    TC: weights.filter(w => w.productCode === 'TC').reduce((s, w) => s + Number(w.weight), 0),
  }), [weights]);

  if (q.isLoading) return <Skeleton active />;
  if (!q.data) return <Result status="404" title="Không tìm thấy phiên bản" />;
  const v = q.data;
  const setP = (pid: number, val: number | null) => { setParams(ps => ps.map(p => (p.id === pid ? { ...p, value: String(val ?? '') } : p))); setDirty(true); };
  const setW = (wid: number, val: number | null) => { setWeights(ws => ws.map(w => (w.id === wid ? { ...w, weight: val ?? 0 } : w))); setDirty(true); };

  const paramCols = [
    { title: 'Tham số', dataIndex: 'label' },
    { title: 'Mã', dataIndex: 'key', render: (x: string) => <Typography.Text code>{x}</Typography.Text> },
    {
      title: 'Giá trị', dataIndex: 'value', width: 240, render: (x: string, r: PolicyParam) => draft ? (
        <InputNumber style={{ width: '100%' }} value={Number(x)} onChange={val => setP(r.id, val)}
          {...(MONEY_KEYS.has(r.key) ? { formatter: moneyFormatter, parser: moneyParser, step: 1_000_000 } : { step: r.key === 'REF_RATE' ? 0.1 : 1 })}
          min={0} />
      ) : (MONEY_KEYS.has(r.key) ? Number(x).toLocaleString('vi-VN') : x),
    },
    { title: 'Đơn vị', dataIndex: 'unit', width: 120 },
  ];
  const groupParams = (code: 'TD' | 'TC' | null) => params.filter(p => p.productCode === code);

  const testCols = [
    { title: 'Hồ sơ mẫu', dataIndex: 'profile' },
    { title: 'Gói', dataIndex: 'productCode', render: (c: 'TD' | 'TC') => PRODUCT[c] },
    { title: 'Đạt (hiện hành → nháp)', render: (_: unknown, r: TestRunRow) => <Space>{r.activeEligible ? <Tag color="success">Đạt</Tag> : <Tag color="error">Không</Tag>}→{r.draftEligible ? <Tag color="success">Đạt</Tag> : <Tag color="error">Không</Tag>}</Space> },
    { title: 'Hạn mức an toàn', render: (_: unknown, r: TestRunRow) => `${moneyShort(r.activeMaxSafe)} → ${moneyShort(r.draftMaxSafe)}` },
    { title: 'Điểm', render: (_: unknown, r: TestRunRow) => `${r.activeScore ?? '—'} → ${r.draftScore ?? '—'}` },
  ];

  return (
    <div>
      <Button type="link" icon={<ArrowLeftOutlined />} style={{ paddingLeft: 0 }} onClick={() => nav('/noi-bo/tham-so')}>Danh sách phiên bản</Button>
      <Space style={{ width: '100%', justifyContent: 'space-between' }} wrap>
        <Typography.Title level={3} style={{ margin: 0 }}>Phiên bản {v.versionNo} <Tag color={VERSION_STATUS[v.status].color}>{VERSION_STATUS[v.status].label}</Tag></Typography.Title>
        {draft && (
          <Space wrap>
            <Button icon={<SaveOutlined />} disabled={!dirty} loading={save.isPending} onClick={() => save.mutate()}>Lưu</Button>
            <Button icon={<ExperimentOutlined />} disabled={dirty} loading={test.isPending} onClick={() => test.mutate()}>Chạy thử</Button>
            <Popconfirm title="Kích hoạt phiên bản này?" description="Phiên bản đang áp dụng sẽ hết hiệu lực; các lượt sàng lọc mới dùng tham số mới."
              okText="Kích hoạt" cancelText="Hủy" onConfirm={() => activate.mutate()} disabled={dirty || !v.testResult}>
              <Button type="primary" icon={<RocketOutlined />} disabled={dirty || !v.testResult} loading={activate.isPending}>Kích hoạt</Button>
            </Popconfirm>
            <Popconfirm title="Xóa bản nháp?" okText="Xóa" cancelText="Hủy" onConfirm={() => remove.mutate()}>
              <Button danger icon={<DeleteOutlined />} />
            </Popconfirm>
          </Space>
        )}
      </Space>
      <Descriptions size="small" style={{ marginTop: 12 }} column={{ xs: 1, md: 3 }}>
        <Descriptions.Item label="Căn cứ">{v.legalBasis}</Descriptions.Item>
        <Descriptions.Item label="Người tạo">{v.createdBy} · {dt(v.createdAt)}</Descriptions.Item>
        <Descriptions.Item label="Kích hoạt">{dt(v.activatedAt)}</Descriptions.Item>
      </Descriptions>
      {draft && dirty && <Alert type="warning" showIcon style={{ marginBottom: 12 }} message="Có thay đổi chưa lưu. Lưu rồi chạy thử trước khi kích hoạt." />}
      {draft && !dirty && !v.testResult && <Alert type="info" showIcon style={{ marginBottom: 12 }} message="Cần chạy thử trên bộ hồ sơ mẫu trước khi kích hoạt." />}
      <Card>
        <Tabs items={[
          {
            key: 'p', label: 'Bảng tham số (MB11)', children: (
              <Space direction="vertical" style={{ width: '100%' }} size={16}>
                <Table size="small" title={() => <b>Tham số chung</b>} rowKey="id" pagination={false} dataSource={groupParams(null)} columns={paramCols} />
                <Table size="small" title={() => <b>{PRODUCT.TD}</b>} rowKey="id" pagination={false} dataSource={groupParams('TD')} columns={paramCols} />
                <Table size="small" title={() => <b>{PRODUCT.TC}</b>} rowKey="id" pagination={false} dataSource={groupParams('TC')} columns={paramCols} />
              </Space>
            ),
          },
          {
            key: 'w', label: 'Ma trận trọng số (MB12)', children: (
              <Space direction="vertical" style={{ width: '100%' }} size={16}>
                {(['TD', 'TC'] as const).map(c => (
                  <Table key={c} size="small" rowKey="id" pagination={false} dataSource={weights.filter(w => w.productCode === c)}
                    title={() => <Space><b>{PRODUCT[c]}</b><Tag color={Math.abs(sums[c] - 100) < 0.01 ? 'success' : 'error'}>Tổng {sums[c]}%</Tag></Space>}
                    columns={[
                      { title: 'Tiêu chí', dataIndex: 'label' },
                      { title: 'Mã', dataIndex: 'criterion', render: (x: string) => <Typography.Text code>{x}</Typography.Text> },
                      {
                        title: 'Trọng số (%)', dataIndex: 'weight', width: 200, render: (x: number, r: ScoringWeight) => draft
                          ? <InputNumber min={0} max={100} value={x} onChange={val => setW(r.id, val)} addonAfter="%" /> : `${x}%`,
                      },
                    ]} />
                ))}
              </Space>
            ),
          },
          {
            key: 't', label: 'Kết quả chạy thử', children: v.testResult ? (
              <div>
                <Typography.Paragraph>Chạy lúc {dt(v.testResult.runAt)} · {v.testResult.changed} dòng thay đổi so với phiên bản đang áp dụng.</Typography.Paragraph>
                <Table size="small" rowKey={r => r.profile + r.productCode} pagination={false} dataSource={v.testResult.rows} columns={testCols}
                  rowClassName={r => (r.activeEligible !== r.draftEligible || r.activeMaxSafe !== r.draftMaxSafe || r.activeScore !== r.draftScore ? 'row-changed' : '')} />
              </div>
            ) : <Typography.Text type="secondary">{draft ? 'Chưa chạy thử.' : 'Không có kết quả chạy thử cho phiên bản này.'}</Typography.Text>,
          },
        ]} />
      </Card>
    </div>
  );
}
