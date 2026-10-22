import { Alert, Card, Descriptions, List, Tag, Typography } from 'antd';
import type { Application } from '../../api/types';
import { money, pct } from '../../utils/format';

const SEV = { BLOCKING: { c: 'error', t: 'Cần xử lý' }, WARNING: { c: 'warning', t: 'Lưu ý' }, INFO: { c: 'blue', t: 'Thông tin' } } as const;

/** Cảnh báo đối chiếu tự động MB06 ↔ MB01 và kết quả tính lại theo giá trị kiểm chứng */
export default function WarningsPanel({ app, extra, compact }: { app: Application; extra?: React.ReactNode; compact?: boolean }) {
  if (!app.warnings.length && !app.recalc) return null;
  return (
    <Card title="Đối chiếu tự động" size="small" style={{ marginBottom: 16 }}>
      {app.warnings.length === 0 ? <Alert type="success" showIcon message="Thông tin trên giấy tờ khớp với thông tin đã khai." /> : (
        <List size="small" dataSource={app.warnings} renderItem={w => (
          <List.Item>
            <div>
              <Tag color={SEV[w.severity].c}>{SEV[w.severity].t}</Tag><b>{w.field}</b>: {w.message}
              <div><Typography.Text type="secondary">Khai báo: {w.declared || '—'} · Trên giấy tờ: {w.verified || '—'}</Typography.Text></div>
            </div>
          </List.Item>
        )} />
      )}
      {app.recalc && (
        <Descriptions size="small" style={{ marginTop: 12 }} column={compact ? 1 : { xs: 1, md: 2, xl: 4 }} title="Tính lại theo thu nhập kiểm chứng">
          <Descriptions.Item label="Thu nhập theo sao kê">{money(app.recalc.verifiedIncome)}</Descriptions.Item>
          <Descriptions.Item label="DTI sau vay">{pct(app.recalc.dtiAfter)}</Descriptions.Item>
          <Descriptions.Item label="Hạn mức an toàn">{money(app.recalc.maxSafeAmount)}</Descriptions.Item>
          <Descriptions.Item label="Điểm tương thích">{app.recalc.score} {app.recalc.ok ? <Tag color="success">Đạt</Tag> : <Tag color="error">Không đạt</Tag>}</Descriptions.Item>
        </Descriptions>
      )}
      {extra}
    </Card>
  );
}
