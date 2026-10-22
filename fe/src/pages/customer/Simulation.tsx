import { useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert, App, Button, Card, Checkbox, Col, Empty, InputNumber, List, Progress, Result, Row, Segmented, Skeleton, Slider, Space,
  Statistic, Table, Tabs, Tag, Tooltip, Typography,
} from 'antd';
import { CheckCircleOutlined, ExclamationCircleOutlined, PlusOutlined, DeleteOutlined, ArrowLeftOutlined } from '@ant-design/icons';
import { keepPreviousData, useMutation, useQuery } from '@tanstack/react-query';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { catalogApi, planApi, screeningApi } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import type { ProductCode, SimulationRequest, SimulationResponse, Suggestion } from '../../api/types';
import Disclaimer from '../../components/Disclaimer';
import ScheduleChart from '../../components/ScheduleChart';
import { money, moneyFormatter, moneyParser, moneyShort, pct } from '../../utils/format';
import { scoreLevel } from '../../utils/status';

const CAP: Record<ProductCode, number> = { TD: 500_000_000, TC: 200_000_000 };
const SUG_COLOR: Record<string, string> = { REDUCE_AMOUNT: 'blue', EXTEND_TERM: 'purple', CROSS_SELL: 'gold', SWITCH_PRODUCT: 'cyan' };
const SUG_LABEL: Record<string, string> = { REDUCE_AMOUNT: 'Giảm số tiền', EXTEND_TERM: 'Kéo dài kỳ hạn', CROSS_SELL: 'Bán chéo', SWITCH_PRODUCT: 'Đổi gói' };

function useDebounced<T>(v: T, ms = 300) {
  const [d, setD] = useState(v);
  useEffect(() => { const t = setTimeout(() => setD(v), ms); return () => clearTimeout(t); }, [v, ms]);
  return d;
}

export default function Simulation() {
  const { id } = useParams();
  const sid = Number(id);
  const [sp, setSp] = useSearchParams();
  const nav = useNavigate();
  const { user } = useAuth();
  const { message } = App.useApp();
  const scr = useQuery({ queryKey: ['screening', sid], queryFn: () => screeningApi.get(sid) });
  const cs = useQuery({ queryKey: ['cross-sells'], queryFn: catalogApi.crossSells });

  const eligible = scr.data?.results.filter(r => r.eligible) ?? [];
  const code = ((sp.get('goi') as ProductCode) || eligible[0]?.productCode || 'TD') as ProductCode;
  const res = scr.data?.results.find(r => r.productCode === code);
  const amount = Number(sp.get('a')) || (scr.data ? Math.min(scr.data.input.desiredAmount, CAP[code]) : 0);
  const term = code === 'TC' ? 12 : Number(sp.get('t')) || Math.min(Math.max(scr.data?.input.desiredTerm ?? 24, 6), 60);
  const csIds = useMemo(() => (sp.get('cs') || '').split(',').filter(Boolean).map(Number), [sp]);

  const set = (patch: Record<string, string | number | number[] | null>) => {
    const n = new URLSearchParams(sp);
    for (const [k, v] of Object.entries(patch)) {
      if (v == null || (Array.isArray(v) && v.length === 0)) n.delete(k);
      else n.set(k, Array.isArray(v) ? v.join(',') : String(v));
    }
    setSp(n, { replace: true });
  };

  const req: SimulationRequest = useDebounced({ screeningId: sid, productCode: code, amount, termMonths: term, crossSellIds: csIds });
  const sim = useQuery({
    queryKey: ['sim', req],
    queryFn: () => planApi.simulate(req),
    enabled: !!scr.data && !!res?.eligible && req.amount > 0,
    placeholderData: keepPreviousData,
  });

  const [compare, setCompare] = useState<(SimulationResponse & { cs: number[] })[]>([]);
  const create = useMutation({
    mutationFn: planApi.create,
    onSuccess: p => { message.success('Đã lưu phương án vay'); nav(`/phuong-an/${p.id}`); },
    onError: e => message.error(errorMessage(e)),
  });
  const choose = () => {
    if (user?.role !== 'CUSTOMER') {
      const n = new URLSearchParams(sp); n.set('chon', '1');
      nav(`/dang-nhap?next=${encodeURIComponent(`/mo-phong/${sid}?${n.toString()}`)}`);
      return;
    }
    create.mutate({ screeningId: sid, productCode: code, amount, termMonths: term, crossSellIds: csIds });
  };
  const tried = useRef(false);
  useEffect(() => {
    if (!tried.current && sp.get('chon') === '1' && user?.role === 'CUSTOMER' && sim.data?.ok) {
      tried.current = true;
      set({ chon: null });
      create.mutate({ screeningId: sid, productCode: code, amount, termMonths: term, crossSellIds: csIds });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sp, user, sim.data]);

  if (scr.isLoading) return <Skeleton active />;
  if (!scr.data) return <Result status="404" title="Không tìm thấy lượt sàng lọc" />;
  if (!res?.eligible) {
    return <Result status="warning" title="Gói vay này chưa phù hợp với hồ sơ" subTitle={res?.reasons.join(' ')}
      extra={<Button onClick={() => nav(`/sang-loc/${sid}`)}>Về kết quả gợi ý</Button>} />;
  }
  const s = sim.data;
  const applySug = (g: Suggestion) => set({ goi: g.productCode, a: g.amount, t: g.productCode === 'TC' ? null : g.termMonths, cs: g.crossSellIds });
  const lv = scoreLevel(s?.score ?? null);

  return (
    <div>
      <Button type="link" icon={<ArrowLeftOutlined />} style={{ paddingLeft: 0 }} onClick={() => nav(`/sang-loc/${sid}`)}>Kết quả gợi ý</Button>
      <Typography.Title level={3} style={{ marginTop: 0 }}>Mô phỏng phương án vay</Typography.Title>
      <Typography.Paragraph type="secondary">Kế hoạch trả nợ dự kiến. Điều chỉnh số tiền, kỳ hạn, sản phẩm bán chéo; hệ thống tính lại ngay.</Typography.Paragraph>
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={9}>
          <Card title="Thông số phương án">
            <Typography.Text strong>Gói vay</Typography.Text>
            <Segmented block style={{ margin: '8px 0 16px' }} value={code}
              options={scr.data.results.map(r => ({ value: r.productCode, label: r.productCode === 'TD' ? 'Vay tiêu dùng' : 'Vay thấu chi', disabled: !r.eligible }))}
              onChange={v => set({ goi: v as string, a: Math.min(amount, CAP[v as ProductCode]), t: null })} />
            <Typography.Text strong>Số tiền vay</Typography.Text>
            <InputNumber style={{ width: '100%', margin: '8px 0' }} value={amount} min={10_000_000} max={CAP[code]} step={5_000_000}
              formatter={moneyFormatter} parser={moneyParser} addonAfter="đ" onChange={v => v && set({ a: v })} />
            <Slider min={10_000_000} max={CAP[code]} step={1_000_000} value={amount} onChange={v => set({ a: v })}
              tooltip={{ formatter: v => moneyShort(v ?? 0) }}
              marks={res.maxSafeAmount ? { [Math.min(res.maxSafeAmount, CAP[code])]: { label: 'an toàn', style: { fontSize: 11 } } } : undefined} />
            <div style={{ marginTop: 24 }}>
              <Typography.Text strong>Kỳ hạn: {term} tháng</Typography.Text>
              {code === 'TC' ? (
                <Typography.Paragraph type="secondary">Thấu chi duy trì hạn mức 12 tháng, có thể gia hạn khi đáo hạn.</Typography.Paragraph>
              ) : (
                <Slider min={6} max={s?.maxTermAllowed ?? 60} value={term} onChange={v => set({ t: v })}
                  marks={{ 6: '6', 12: '12', 24: '24', 36: '36', 48: '48', 60: '60' }} />
              )}
            </div>
            <Typography.Text strong>Sản phẩm bán chéo (tùy chọn)</Typography.Text>
            <Checkbox.Group style={{ width: '100%', marginTop: 8 }} value={csIds} onChange={v => set({ cs: v as number[] })}>
              <Space direction="vertical" style={{ width: '100%' }}>
                {cs.data?.map(c => (
                  <Checkbox key={c.id} value={c.id}>
                    {c.name}{' '}
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      −{c.rateDiscount}%/năm, +{c.scoreBonus} điểm{c.estCost ? `, chi phí ~${moneyShort(c.estCost)}/năm` : ''}
                    </Typography.Text>
                  </Checkbox>
                ))}
              </Space>
            </Checkbox.Group>
          </Card>
        </Col>
        <Col xs={24} lg={15}>
          {!s ? <Card><Skeleton active /></Card> : (
            <Space direction="vertical" style={{ width: '100%' }} size={16}>
              {s.ok ? (
                <Alert type="success" showIcon icon={<CheckCircleOutlined />} message="Phương án nằm trong ngưỡng an toàn"
                  description={`Hạn mức tối đa an toàn với kỳ hạn này: ${money(s.maxSafeAmount)}.`} />
              ) : (
                <Alert type="error" showIcon icon={<ExclamationCircleOutlined />} message="Phương án vượt ngưỡng"
                  description={<ul style={{ margin: 0, paddingLeft: 18 }}>{s.issues.map(x => <li key={x}>{x}</li>)}</ul>} />
              )}
              <Card>
                <Row gutter={[16, 16]}>
                  <Col xs={12} md={6}><Statistic title={code === 'TD' ? 'Trả kỳ đầu' : 'Lãi hằng tháng'} value={s.firstPayment} suffix="đ" groupSeparator="." /></Col>
                  <Col xs={12} md={6}><Statistic title="Tổng lãi dự kiến" value={s.totalInterest} suffix="đ" groupSeparator="." /></Col>
                  <Col xs={12} md={6}><Statistic title="Lãi suất tham chiếu" value={s.rate} suffix="%/năm" precision={2} decimalSeparator="," /></Col>
                  <Col xs={12} md={6}>
                    <Statistic title="Điểm tương thích" value={s.score} decimalSeparator="," />
                    <Tag color={lv.color}>{lv.label}</Tag>
                  </Col>
                </Row>
                <div style={{ marginTop: 16 }}>
                  <Space style={{ justifyContent: 'space-between', width: '100%' }}>
                    <Typography.Text>DTI sau vay: <b>{pct(s.dtiAfter)}</b></Typography.Text>
                    <Typography.Text type="secondary">Ngưỡng tối đa {s.dtiMax}%</Typography.Text>
                  </Space>
                  <Tooltip title={`Nghĩa vụ hằng tháng quy đổi: ${money(s.monthlyObligation)}`}>
                    <Progress percent={Math.min(100, s.dtiAfter / s.dtiMax * 100)} showInfo={false}
                      strokeColor={s.dtiAfter > s.dtiMax ? '#dc2626' : s.dtiAfter > s.dtiMax * 0.85 ? '#f59e0b' : '#16a34a'} />
                  </Tooltip>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    Thu nhập khả dụng sau khi trả nợ: {money(s.disposableAfter)}{s.extraCost ? ` · Chi phí bán chéo ước tính ${money(s.extraCost)}/năm` : ''}
                  </Typography.Text>
                </div>
              </Card>
              {s.suggestions.length > 0 && (
                <Card title="Đề xuất điều chỉnh">
                  <List dataSource={s.suggestions} renderItem={g => (
                    <List.Item actions={[<Button key="a" type="primary" ghost onClick={() => applySug(g)}>Áp dụng</Button>]}>
                      <List.Item.Meta
                        title={<Space><Tag color={SUG_COLOR[g.type]}>{SUG_LABEL[g.type]}</Tag>{g.title}</Space>}
                        description={<>
                          {g.detail}<br />
                          {money(g.amount)} · {g.termMonths} tháng · điểm {g.score}{g.extraCost ? ` · chi phí bán chéo ~${moneyShort(g.extraCost)}/năm` : ''}
                        </>} />
                    </List.Item>
                  )} />
                </Card>
              )}
              <Card>
                <Tabs items={[
                  { key: 'c', label: 'Biểu đồ', children: <ScheduleChart rows={s.schedule} /> },
                  {
                    key: 't', label: 'Lịch trả nợ', children: (
                      <Table size="small" rowKey="period" dataSource={s.schedule} pagination={{ pageSize: 12, hideOnSinglePage: true }}
                        columns={[
                          { title: 'Kỳ', dataIndex: 'period', width: 60 },
                          { title: 'Gốc', dataIndex: 'principal', align: 'right', render: money },
                          { title: 'Lãi', dataIndex: 'interest', align: 'right', render: money },
                          { title: 'Tổng trả', dataIndex: 'payment', align: 'right', render: money },
                          { title: 'Dư nợ còn lại', dataIndex: 'balance', align: 'right', render: money },
                        ]}
                        summary={() => (
                          <Table.Summary.Row>
                            <Table.Summary.Cell index={0}><b>Tổng</b></Table.Summary.Cell>
                            <Table.Summary.Cell index={1} align="right">{money(s.amount)}</Table.Summary.Cell>
                            <Table.Summary.Cell index={2} align="right">{money(s.totalInterest)}</Table.Summary.Cell>
                            <Table.Summary.Cell index={3} align="right">{money(s.totalPayment)}</Table.Summary.Cell>
                            <Table.Summary.Cell index={4} />
                          </Table.Summary.Row>
                        )} />
                    ),
                  },
                ]} />
              </Card>
              <Space wrap>
                <Button icon={<PlusOutlined />} disabled={compare.length >= 3} onClick={() => setCompare(c => [...c, { ...s, cs: csIds }])}>Thêm vào so sánh</Button>
                <Button type="primary" disabled={!s.ok} loading={create.isPending} onClick={choose}>Chọn phương án này</Button>
                {!s.ok && <Typography.Text type="secondary">Áp dụng một đề xuất điều chỉnh để chọn được phương án.</Typography.Text>}
              </Space>
            </Space>
          )}
        </Col>
      </Row>

      <Card title="So sánh phương án" style={{ marginTop: 16 }}>
        {compare.length === 0 ? <Empty description="Thêm tối đa 3 phương án để so sánh" /> : (
          <Table size="small" pagination={false} rowKey={(_, i) => String(i)} dataSource={compare} scroll={{ x: 700 }}
            columns={[
              { title: 'Gói', dataIndex: 'productName' },
              { title: 'Số tiền', dataIndex: 'amount', align: 'right', render: money },
              { title: 'Kỳ hạn', dataIndex: 'termMonths', render: v => `${v} tháng` },
              { title: 'Lãi suất', dataIndex: 'rate', render: v => `${v}%` },
              { title: 'Trả kỳ đầu', dataIndex: 'firstPayment', align: 'right', render: money },
              { title: 'Tổng lãi', dataIndex: 'totalInterest', align: 'right', render: money },
              { title: 'DTI', dataIndex: 'dtiAfter', render: v => pct(v) },
              { title: 'Điểm', dataIndex: 'score' },
              { title: 'Đạt', dataIndex: 'ok', render: v => (v ? <Tag color="success">Đạt</Tag> : <Tag color="error">Vượt</Tag>) },
              {
                title: '', key: 'x', render: (_, r, i) => (
                  <Space>
                    <Button size="small" onClick={() => set({ goi: r.productCode, a: r.amount, t: r.productCode === 'TC' ? null : r.termMonths, cs: r.cs })}>Mở lại</Button>
                    <Button size="small" danger icon={<DeleteOutlined />} onClick={() => setCompare(c => c.filter((_, k) => k !== i))} />
                  </Space>
                ),
              },
            ]} />
        )}
      </Card>
      <div style={{ marginTop: 16 }}><Disclaimer compact /></div>
    </div>
  );
}
