import { useState } from 'react';
import {
  Alert, App, Button, Card, Col, DatePicker, Descriptions, Empty, List, Row, Select, Skeleton, Slider, Space, Statistic, Table, Tabs, Tag, Typography,
} from 'antd';
import { DownloadOutlined, PrinterOutlined, ExperimentOutlined } from '@ant-design/icons';
import ReactECharts from 'echarts-for-react';
import dayjs, { type Dayjs } from 'dayjs';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { adminApi } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import type { AccuracyResponse, ReportResponse, StatsResponse } from '../../api/types';
import { APP_STATUS } from '../../utils/status';
import { dt, money, moneyShort } from '../../utils/format';

const C = { teal: '#0f766e', amber: '#f59e0b', red: '#dc2626', slate: '#64748b', blue: '#2563eb', green: '#16a34a' };

function Funnel({ s }: { s: StatsResponse }) {
  const t = s.totals;
  const data = [['Lượt sàng lọc', t.screenings], ['Phương án vay', t.plans], ['Hồ sơ tạo', t.applications], ['Hồ sơ đã nộp', t.submitted],
    ['Đã đối chiếu', t.verified], ['Được phê duyệt', t.approved], ['Đã giải ngân', t.disbursed]] as [string, number][];
  const rows = data.slice().reverse();
  return <ReactECharts style={{ height: 320 }} notMerge option={{
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 110, right: 90, top: 10, bottom: 10 },
    xAxis: { type: 'value', show: false },
    yAxis: { type: 'category', data: rows.map(r => r[0]), axisTick: { show: false }, axisLine: { show: false } },
    series: [{ type: 'bar', barWidth: 22, data: rows.map(([, v]) => v), itemStyle: { color: C.teal, borderRadius: [0, 4, 4, 0] },
      label: { show: true, position: 'right', formatter: (p: { value: number; dataIndex: number }) => {
        const i = data.length - 1 - p.dataIndex;
        return i === 0 ? `${p.value}` : `${p.value}  (${data[i - 1][1] ? Math.round((p.value / data[i - 1][1]) * 100) : 0}%)`;
      } } }],
  }} />;
}

function Monthly({ s }: { s: StatsResponse }) {
  return <ReactECharts style={{ height: 300 }} notMerge option={{
    tooltip: { trigger: 'axis' }, legend: { bottom: 0 }, grid: { left: 40, right: 20, top: 20, bottom: 50 },
    xAxis: { type: 'category', data: s.byMonth.map(m => m.month.split('-').reverse().join('/')) }, yAxis: { type: 'value', minInterval: 1 },
    series: [
      { name: 'Hồ sơ tạo mới', type: 'bar', data: s.byMonth.map(m => m.created), itemStyle: { color: C.teal } },
      { name: 'Giải ngân', type: 'line', data: s.byMonth.map(m => m.disbursed), itemStyle: { color: C.green } },
      { name: 'Từ chối', type: 'line', data: s.byMonth.map(m => m.rejected), itemStyle: { color: C.red } },
    ],
  }} />;
}

function StatsTab() {
  const [range, setRange] = useState<[Dayjs, Dayjs]>([dayjs().subtract(180, 'day'), dayjs()]);
  const [branchId, setBranch] = useState<number>();
  const br = useQuery({ queryKey: ['admin-branches'], queryFn: adminApi.branches });
  const q = useQuery({ queryKey: ['admin-stats', range[0].format('YYYY-MM-DD'), range[1].format('YYYY-MM-DD'), branchId],
    queryFn: () => adminApi.stats({ from: range[0].format('YYYY-MM-DD'), to: range[1].format('YYYY-MM-DD'), branchId }) });
  const s = q.data;
  return (
    <div>
      <Space wrap style={{ marginBottom: 16 }}>
        <DatePicker.RangePicker value={range} format="DD/MM/YYYY" allowClear={false} onChange={v => v && setRange(v as [Dayjs, Dayjs])} />
        <Select allowClear placeholder="Tất cả chi nhánh" style={{ width: 220 }} value={branchId} onChange={setBranch} options={br.data?.map(b => ({ value: b.id, label: b.name }))} />
      </Space>
      {!s ? <Skeleton active /> : (
        <>
          <Row gutter={[16, 16]}>
            {[['Hồ sơ tạo', s.totals.applications], ['Đã nộp', s.totals.submitted], ['Được phê duyệt', s.totals.approved], ['Bị từ chối', s.totals.rejected],
              ['Tỷ lệ phê duyệt', s.totals.approved + s.totals.rejected ? `${Math.round(s.totals.approved / (s.totals.approved + s.totals.rejected) * 100)}%` : '—'],
              ['Thời gian xử lý TB', `${s.avgProcessingDays} ngày`]].map(([t, v]) => (
              <Col xs={12} md={8} xl={4} key={t as string}><Card size="small"><Statistic title={t as string} value={v as number | string} /></Card></Col>
            ))}
          </Row>
          <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
            <Col xs={24} xl={10}><Card size="small" title="Phễu chuyển đổi"><Funnel s={s} /></Card></Col>
            <Col xs={24} xl={14}><Card size="small" title="Theo tháng"><Monthly s={s} /></Card></Col>
            <Col xs={24} xl={12}>
              <Card size="small" title="Hồ sơ theo trạng thái hiện tại">
                <ReactECharts style={{ height: 280 }} notMerge option={{
                  tooltip: {}, grid: { left: 130, right: 30, top: 10, bottom: 20 }, xAxis: { type: 'value', minInterval: 1 },
                  yAxis: { type: 'category', data: s.byStatus.map(x => APP_STATUS[x.status].label), inverse: true },
                  series: [{ type: 'bar', data: s.byStatus.map(x => x.count), itemStyle: { color: C.teal }, label: { show: true, position: 'right' } }],
                }} />
              </Card>
            </Col>
            <Col xs={24} xl={12}>
              <Card size="small" title="Theo chi nhánh">
                <ReactECharts style={{ height: 280 }} notMerge option={{
                  tooltip: { trigger: 'axis' }, legend: { bottom: 0 }, grid: { left: 40, right: 20, top: 20, bottom: 50 },
                  xAxis: { type: 'category', data: s.byBranch.map(b => b.branchName.replace('Chi nhánh ', '')) }, yAxis: { type: 'value', minInterval: 1 },
                  series: [
                    { name: 'Tổng hồ sơ', type: 'bar', data: s.byBranch.map(b => b.count), itemStyle: { color: C.slate } },
                    { name: 'Phê duyệt', type: 'bar', data: s.byBranch.map(b => b.approved), itemStyle: { color: C.green } },
                    { name: 'Từ chối', type: 'bar', data: s.byBranch.map(b => b.rejected), itemStyle: { color: C.red } },
                  ],
                }} />
              </Card>
            </Col>
            <Col span={24}>
              <Card size="small" title="Theo gói vay">
                <Table size="small" rowKey="productCode" pagination={false} dataSource={s.byProduct} columns={[
                  { title: 'Gói vay', dataIndex: 'productCode', render: v => (v === 'TD' ? 'Vay tiêu dùng' : 'Vay thấu chi') },
                  { title: 'Số hồ sơ', dataIndex: 'count' },
                  { title: 'Đã giải ngân', dataIndex: 'amount', align: 'right', render: money },
                ]} />
              </Card>
            </Col>
          </Row>
        </>
      )}
    </div>
  );
}

function AccuracyView({ a }: { a: AccuracyResponse }) {
  const m = a.matrix;
  return (
    <Row gutter={[16, 16]}>
      <Col xs={24} xl={10}>
        <Card size="small" title={`Ma trận nhầm lẫn (ngưỡng điểm ${a.threshold}, ${a.sample} hồ sơ đã có kết quả)`}>
          <table className="cm-table">
            <thead><tr><th /><th>Ngân hàng duyệt</th><th>Ngân hàng từ chối</th></tr></thead>
            <tbody>
              <tr><th>Hệ thống đánh giá đạt</th><td className="cm-good">{m.tp}</td><td className="cm-bad">{m.fp}</td></tr>
              <tr><th>Hệ thống đánh giá chưa đạt</th><td className="cm-bad">{m.fn}</td><td className="cm-good">{m.tn}</td></tr>
            </tbody>
          </table>
          <Row gutter={16} style={{ marginTop: 16 }}>
            <Col span={8}><Statistic title="Độ chính xác" value={a.accuracy} suffix="%" decimalSeparator="," /></Col>
            <Col span={8}><Statistic title="Độ chính xác dương" value={a.precision} suffix="%" decimalSeparator="," /></Col>
            <Col span={8}><Statistic title="Độ nhạy" value={a.recall} suffix="%" decimalSeparator="," /></Col>
          </Row>
          <Typography.Paragraph type="secondary" style={{ fontSize: 12, marginTop: 12, marginBottom: 0 }}>
            Độ chính xác dương: trong số hồ sơ hệ thống đánh giá đạt, tỷ lệ được ngân hàng duyệt. Độ nhạy: trong số hồ sơ được duyệt, tỷ lệ hệ thống đã đánh giá đạt.
          </Typography.Paragraph>
        </Card>
      </Col>
      <Col xs={24} xl={14}>
        <Card size="small" title="Tỷ lệ được duyệt theo nhóm điểm tương thích">
          <ReactECharts style={{ height: 300 }} notMerge option={{
            tooltip: { trigger: 'axis' }, legend: { bottom: 0 }, grid: { left: 50, right: 50, top: 40, bottom: 50 },
            xAxis: { type: 'category', data: a.byBand.map(b => b.band) },
            yAxis: [{ type: 'value', name: 'Hồ sơ', minInterval: 1 }, { type: 'value', name: '%', max: 100, splitLine: { show: false } }],
            series: [
              { name: 'Số hồ sơ', type: 'bar', data: a.byBand.map(b => b.total), itemStyle: { color: C.slate } },
              { name: 'Tỷ lệ được duyệt', type: 'line', yAxisIndex: 1, data: a.byBand.map(b => b.rate), itemStyle: { color: C.teal }, label: { show: true, formatter: '{c}%' } },
            ],
          }} />
        </Card>
      </Col>
      <Col span={24}>
        <Card size="small" title="Theo gói vay">
          <Table size="small" rowKey="productCode" pagination={false} dataSource={a.byProduct} columns={[
            { title: 'Gói vay', dataIndex: 'productCode', render: v => (v === 'TD' ? 'Vay tiêu dùng' : 'Vay thấu chi') },
            { title: 'Số hồ sơ', dataIndex: 'sample' }, { title: 'Độ chính xác', dataIndex: 'accuracy', render: v => `${v}%` },
          ]} />
        </Card>
      </Col>
    </Row>
  );
}

function AccuracyTab() {
  const [t, setT] = useState(55);
  const q = useQuery({ queryKey: ['admin-accuracy', t], queryFn: () => adminApi.accuracy(t) });
  return (
    <div>
      <Space style={{ marginBottom: 16, width: '100%' }} align="center">
        <Typography.Text>Ngưỡng điểm coi là “đạt”:</Typography.Text>
        <Slider style={{ width: 300 }} min={30} max={90} value={t} onChange={setT} marks={{ 45: '45', 55: '55', 65: '65', 75: '75' }} />
        <Tag>{t}</Tag>
      </Space>
      {q.data ? <AccuracyView a={q.data} /> : <Skeleton active />}
    </div>
  );
}

function exportCsv(r: ReportResponse) {
  const rows: (string | number)[][] = [['Báo cáo thống kê và hiệu chỉnh mô hình', r.period], [],
    ['Chỉ tiêu', 'Giá trị'], ...Object.entries(r.stats.totals).map(([k, v]) => [k, v]),
    ['Thời gian xử lý trung bình (ngày)', r.stats.avgProcessingDays], ['Số tiền giải ngân', r.stats.disbursedAmount], [],
    ['Chi nhánh', 'Hồ sơ', 'Phê duyệt', 'Từ chối'], ...r.stats.byBranch.map(b => [b.branchName, b.count, b.approved, b.rejected]), [],
    ['Độ chính xác (%)', r.accuracy.accuracy], ['Độ chính xác dương (%)', r.accuracy.precision], ['Độ nhạy (%)', r.accuracy.recall], [],
    ['Nhóm điểm', 'Hồ sơ', 'Được duyệt', 'Tỷ lệ (%)'], ...r.accuracy.byBand.map(b => [b.band, b.total, b.approved, b.rate]), [],
    ['Đề xuất', 'Chi tiết', 'Tham số', 'Hiện tại', 'Đề xuất'], ...r.proposals.map(p => [p.title, p.detail, p.paramKey ?? '', p.currentValue ?? '', p.proposedValue ?? ''])];
  const csv = '﻿' + rows.map(x => x.map(v => `"${String(v).replace(/"/g, '""')}"`).join(',')).join('\n');
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
  a.download = `bao-cao-hieu-chinh-${r.period}.csv`; a.click();
}

function Mb14Tab() {
  const [month, setMonth] = useState<Dayjs>(dayjs().subtract(1, 'month'));
  const nav = useNavigate();
  const { message } = App.useApp();
  const q = useQuery({ queryKey: ['mb14', month.format('YYYY-MM')], queryFn: () => adminApi.report(month.format('YYYY-MM')) });
  const versions = useQuery({ queryKey: ['versions'], queryFn: adminApi.versions });
  const draft = useMutation({
    mutationFn: (p: ReportResponse['proposals'][number]) => {
      const active = versions.data?.find(v => v.status === 'ACTIVE');
      return adminApi.clone(active!.id, { versionNo: `${dayjs().format('YYYY.MM.DD')}-${p.id}`, legalBasis: `Đề xuất hiệu chỉnh từ báo cáo hiệu chỉnh mô hình kỳ ${q.data?.period}: ${p.title}`,
        changes: [{ key: p.paramKey!, productCode: p.productCode, value: p.proposedValue! }] });
    },
    onSuccess: r => { message.success('Đã tạo bản nháp tham số; chạy thử trước khi kích hoạt'); nav(`/noi-bo/tham-so/${r.id}`); },
    onError: e => message.error(errorMessage(e)),
  });
  const r = q.data;
  return (
    <div>
      <Space wrap style={{ marginBottom: 16 }} className="no-print">
        <DatePicker picker="month" value={month} format="MM/YYYY" allowClear={false} onChange={v => v && setMonth(v)} />
        <Button icon={<DownloadOutlined />} disabled={!r} onClick={() => r && exportCsv(r)}>Xuất CSV (Excel)</Button>
        <Button icon={<PrinterOutlined />} disabled={!r} onClick={() => window.print()}>In / lưu PDF</Button>
      </Space>
      {!r ? <Skeleton active /> : (
        <div className="print-area">
          <Typography.Title level={4} style={{ marginTop: 0 }}>BÁO CÁO THỐNG KÊ VÀ HIỆU CHỈNH MÔ HÌNH – THÁNG {r.period.split('-').reverse().join('/')}</Typography.Title>
          <Typography.Text type="secondary">Lập lúc {dt(r.generatedAt)}</Typography.Text>
          <Card size="small" title="1. Kết quả hoạt động trong kỳ" style={{ marginTop: 12 }}>
            {r.stats.totals.applications === 0 ? <Empty description="Không có hồ sơ trong kỳ" /> : (
              <Descriptions size="small" bordered column={{ xs: 1, md: 3 }}>
                <Descriptions.Item label="Lượt sàng lọc">{r.stats.totals.screenings}</Descriptions.Item>
                <Descriptions.Item label="Phương án vay">{r.stats.totals.plans}</Descriptions.Item>
                <Descriptions.Item label="Hồ sơ tạo">{r.stats.totals.applications}</Descriptions.Item>
                <Descriptions.Item label="Hồ sơ đã nộp">{r.stats.totals.submitted}</Descriptions.Item>
                <Descriptions.Item label="Được phê duyệt">{r.stats.totals.approved}</Descriptions.Item>
                <Descriptions.Item label="Bị từ chối">{r.stats.totals.rejected}</Descriptions.Item>
                <Descriptions.Item label="Đã hủy / hết hạn">{r.stats.totals.cancelled} / {r.stats.totals.expired}</Descriptions.Item>
                <Descriptions.Item label="Số tiền giải ngân">{moneyShort(r.stats.disbursedAmount)}</Descriptions.Item>
                <Descriptions.Item label="Thời gian xử lý TB">{r.stats.avgProcessingDays} ngày</Descriptions.Item>
              </Descriptions>
            )}
          </Card>
          <Card size="small" title="2. Độ chính xác của mô hình (lũy kế đến cuối kỳ)" style={{ marginTop: 12 }}>
            <AccuracyView a={r.accuracy} />
          </Card>
          <Card size="small" title="3. Đề xuất hiệu chỉnh" style={{ marginTop: 12 }}>
            <List dataSource={r.proposals} renderItem={p => (
              <List.Item actions={p.paramKey ? [<Button key="d" className="no-print" icon={<ExperimentOutlined />} loading={draft.isPending} onClick={() => draft.mutate(p)}>Tạo bản nháp tham số</Button>] : []}>
                <List.Item.Meta title={p.title} description={<>{p.detail}{p.paramKey && <div><Tag>{p.paramKey}</Tag>{p.currentValue} → <b>{p.proposedValue}</b></div>}</>} />
              </List.Item>
            )} />
            <Alert type="info" showIcon style={{ marginTop: 8 }} message="Đề xuất chỉ để tham khảo; điều chỉnh chính thức cần văn bản của Phòng Chính sách tín dụng và phải chạy thử trước khi kích hoạt." />
          </Card>
        </div>
      )}
    </div>
  );
}

/** 8.1 Thống kê hồ sơ vay; 8.2 Đánh giá độ chính xác mô hình; 8.3 Lập báo cáo MB14 và đề xuất hiệu chỉnh */
export default function Reports() {
  const [sp, setSp] = useSearchParams();
  return (
    <div>
      <Typography.Title level={3} style={{ marginTop: 0 }} className="no-print">Thống kê và báo cáo</Typography.Title>
      <Card>
        <Tabs activeKey={sp.get('tab') || 'thong-ke'} onChange={k => setSp({ tab: k })} destroyInactiveTabPane items={[
          { key: 'thong-ke', label: 'Thống kê hồ sơ', children: <StatsTab /> },
          { key: 'do-chinh-xac', label: 'Độ chính xác mô hình', children: <AccuracyTab /> },
          { key: 'mb14', label: 'Báo cáo hiệu chỉnh mô hình', children: <Mb14Tab /> },
        ]} />
      </Card>
    </div>
  );
}
