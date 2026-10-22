import ReactECharts from 'echarts-for-react';
import type { ScheduleRow } from '../api/types';

/** Biểu đồ kế hoạch trả nợ: cột chồng gốc + lãi theo kỳ, đường dư nợ */
export default function ScheduleChart({ rows }: { rows: ScheduleRow[] }) {
  const option = {
    tooltip: {
      trigger: 'axis',
      valueFormatter: (v: number) => `${Math.round(v).toLocaleString('vi-VN')} đ`,
    },
    legend: { data: ['Gốc', 'Lãi', 'Dư nợ còn lại'], bottom: 0 },
    grid: { left: 70, right: 70, top: 30, bottom: 50 },
    xAxis: { type: 'category', data: rows.map(r => `Kỳ ${r.period}`), axisLabel: { interval: rows.length > 24 ? 5 : rows.length > 12 ? 2 : 0 } },
    yAxis: [
      { type: 'value', name: 'Trả mỗi kỳ', axisLabel: { formatter: (v: number) => `${(v / 1e6).toLocaleString('vi-VN')} tr` } },
      { type: 'value', name: 'Dư nợ', axisLabel: { formatter: (v: number) => `${(v / 1e6).toLocaleString('vi-VN')} tr` }, splitLine: { show: false } },
    ],
    series: [
      { name: 'Gốc', type: 'bar', stack: 'p', data: rows.map(r => r.principal), itemStyle: { color: '#0f766e' } },
      { name: 'Lãi', type: 'bar', stack: 'p', data: rows.map(r => r.interest), itemStyle: { color: '#f59e0b' } },
      { name: 'Dư nợ còn lại', type: 'line', yAxisIndex: 1, data: rows.map(r => r.balance), smooth: true, symbol: 'none', lineStyle: { color: '#475569', width: 2 } },
    ],
  };
  return <ReactECharts option={option} style={{ height: 320 }} notMerge />;
}
