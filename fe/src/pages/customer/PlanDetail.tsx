import { Alert, App, Button, Card, Col, Descriptions, List, Result, Row, Skeleton, Space, Tag, Typography } from 'antd';
import { CheckCircleOutlined, FileTextOutlined, PrinterOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import { planApi } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import Disclaimer from '../../components/Disclaimer';
import { dt, money, pct } from '../../utils/format';

/** MB07 – Phương án vay đề xuất, kèm danh mục giấy tờ cần chuẩn bị */
export default function PlanDetail() {
  const { id } = useParams();
  const pid = Number(id);
  const nav = useNavigate();
  const qc = useQueryClient();
  const { message } = App.useApp();
  const q = useQuery({ queryKey: ['plan', pid], queryFn: () => planApi.get(pid) });
  const confirm = useMutation({
    mutationFn: () => planApi.confirm(pid),
    onSuccess: d => { qc.setQueryData(['plan', pid], d); message.success('Đã xác nhận phương án vay'); },
    onError: e => message.error(errorMessage(e)),
  });
  if (q.isLoading) return <Skeleton active />;
  if (!q.data) return <Result status="404" title="Không tìm thấy phương án" />;
  const p = q.data;
  const groups = Array.from(new Set(p.checklist.map(c => c.group)));
  return (
    <div>
      <Space style={{ width: '100%', justifyContent: 'space-between' }} wrap>
        <div>
          <Typography.Title level={3} style={{ marginBottom: 0 }}>Phương án vay đề xuất</Typography.Title>
          <Typography.Text type="secondary">MB07 · Phương án #{p.id} · {dt(p.createdAt)}</Typography.Text>
        </div>
        <Space>
          <Button icon={<PrinterOutlined />} onClick={() => window.print()}>In</Button>
          <Button onClick={() => nav(`/mo-phong/${p.screeningId}?goi=${p.productCode}&a=${p.amount}&t=${p.termMonths}${p.crossSells.length ? `&cs=${p.crossSells.map(c => c.id).join(',')}` : ''}`)}>Điều chỉnh lại</Button>
        </Space>
      </Space>
      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={14}>
          <Card title={p.productName} extra={p.status === 'CONFIRMED' ? <Tag color="success" icon={<CheckCircleOutlined />}>Đã xác nhận</Tag> : <Tag>Bản nháp</Tag>}>
            <Descriptions column={{ xs: 1, sm: 1, md: 2, lg: 2, xl: 2, xxl: 2 }} size="small" bordered>
              <Descriptions.Item label="Số tiền vay">{money(p.amount)}</Descriptions.Item>
              <Descriptions.Item label="Kỳ hạn">{p.termMonths} tháng</Descriptions.Item>
              <Descriptions.Item label="Lãi suất tham chiếu">{p.refRate}%/năm</Descriptions.Item>
              <Descriptions.Item label={p.productCode === 'TD' ? 'Trả kỳ đầu' : 'Lãi hằng tháng'}>{money(p.monthlyPayment)}</Descriptions.Item>
              <Descriptions.Item label="Tổng lãi dự kiến">{money(p.totalInterest)}</Descriptions.Item>
              <Descriptions.Item label="DTI sau vay">{pct(p.dtiAfter)}</Descriptions.Item>
              <Descriptions.Item label="Điểm tương thích">{p.score}</Descriptions.Item>
              <Descriptions.Item label="Sản phẩm bán chéo">
                {p.crossSells.length ? p.crossSells.map(c => <div key={c.id}>{c.name}: {c.benefit}{c.extraCost ? ` (chi phí ~${money(c.extraCost)}/năm)` : ''}</div>) : 'Không'}
              </Descriptions.Item>
            </Descriptions>
            {p.status === 'DRAFT' ? (
              <Button type="primary" style={{ marginTop: 16 }} loading={confirm.isPending} onClick={() => confirm.mutate()}>Xác nhận phương án</Button>
            ) : (
              <Alert style={{ marginTop: 16 }} type="success" showIcon message="Phương án đã được xác nhận"
                description="Bước tiếp theo: lập hồ sơ vay trực tuyến – chọn chi nhánh, đồng ý lưu trữ giấy tờ (MB02 phần b), tải giấy tờ lên."
                action={p.applicationId
                  ? <Button type="primary" onClick={() => nav(`/ho-so/${p.applicationId}`)}>Mở hồ sơ</Button>
                  : <Button type="primary" onClick={() => nav(`/ho-so/tao?phuongAn=${p.id}`)}>Lập hồ sơ vay</Button>} />
            )}
          </Card>
        </Col>
        <Col xs={24} lg={10}>
          <Card title={<Space><FileTextOutlined />Giấy tờ cần chuẩn bị</Space>}>
            {groups.map(g => (
              <div key={g} style={{ marginBottom: 12 }}>
                <Typography.Text strong>{g}</Typography.Text>
                <List size="small" dataSource={p.checklist.filter(c => c.group === g)} renderItem={c => (
                  <List.Item>{c.name} {c.required ? <Tag color="red">Bắt buộc</Tag> : <Tag>Nếu có</Tag>}</List.Item>
                )} />
              </div>
            ))}
            <Typography.Paragraph type="secondary" style={{ fontSize: 12, marginBottom: 0 }}>
              Mang bản gốc đến chi nhánh để nhân viên đối chiếu. Danh mục đầy đủ (MB05) sẽ được lập khi tạo hồ sơ.
            </Typography.Paragraph>
          </Card>
        </Col>
      </Row>
      <div style={{ marginTop: 16 }}><Disclaimer /></div>
    </div>
  );
}
