import { Card, Col, Descriptions, QRCode, Row, Typography, List, Button, Space } from 'antd';
import { PrinterOutlined } from '@ant-design/icons';
import type { Application } from '../../api/types';
import { dt, money } from '../../utils/format';

/** MB08 – Phiếu tóm tắt hồ sơ có mã QR, khách hàng mang theo khi đến quầy */
export default function SummarySlip({ app }: { app: Application }) {
  if (!app.qrToken) return <Typography.Text type="secondary">Phiếu tóm tắt được lập khi nộp hồ sơ.</Typography.Text>;
  const docs = app.documents.filter(d => d.fileName);
  return (
    <div>
      <Space style={{ marginBottom: 12 }} className="no-print">
        <Button icon={<PrinterOutlined />} onClick={() => window.print()}>In phiếu</Button>
      </Space>
      <Card className="print-area">
        <Row gutter={24} align="top">
          <Col flex="auto">
            <Typography.Title level={4} style={{ marginTop: 0 }}>PHIẾU TÓM TẮT HỒ SƠ VAY (MB08)</Typography.Title>
            <Descriptions size="small" column={1} bordered>
              <Descriptions.Item label="Mã hồ sơ"><b>{app.code}</b></Descriptions.Item>
              <Descriptions.Item label="Khách hàng">{app.personal.fullName} · {app.personal.phone}</Descriptions.Item>
              <Descriptions.Item label="Số CCCD">{app.personal.nationalId}</Descriptions.Item>
              <Descriptions.Item label="Gói vay">{app.plan.productName}</Descriptions.Item>
              <Descriptions.Item label="Số tiền, kỳ hạn">{money(app.plan.amount)} · {app.plan.termMonths} tháng · {app.plan.refRate}%/năm tham chiếu</Descriptions.Item>
              <Descriptions.Item label="Chi nhánh">{app.branchName} – {app.branchAddress}</Descriptions.Item>
              <Descriptions.Item label="Nộp lúc">{dt(app.lockedAt)}</Descriptions.Item>
              <Descriptions.Item label="Hiệu lực đến">{dt(app.expiresAt)}</Descriptions.Item>
              <Descriptions.Item label="Mã băm nội dung (SHA-256)"><Typography.Text code style={{ fontSize: 11, wordBreak: 'break-all' }}>{app.contentHash}</Typography.Text></Descriptions.Item>
            </Descriptions>
          </Col>
          <Col flex="200px" style={{ textAlign: 'center' }}>
            <QRCode value={app.qrToken} size={180} />
            <Typography.Text code>{app.qrToken}</Typography.Text>
          </Col>
        </Row>
        <Typography.Title level={5}>Bản gốc cần mang theo</Typography.Title>
        <List size="small" dataSource={docs} renderItem={d => <List.Item>☐ {d.typeName}</List.Item>} />
        <Typography.Paragraph type="secondary" style={{ fontSize: 12, marginBottom: 0 }}>
          Phiếu dùng để nhân viên chi nhánh mở hồ sơ và đối chiếu giấy tờ. Kết quả phê duyệt do ngân hàng quyết định sau khi tra cứu thông tin tín dụng (CIC) với sự đồng ý của khách hàng tại quầy.
        </Typography.Paragraph>
      </Card>
    </div>
  );
}
