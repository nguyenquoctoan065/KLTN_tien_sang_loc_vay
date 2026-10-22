import { Button, Card, Col, Row, Typography, Steps, List, Skeleton } from 'antd';
import { SafetyCertificateOutlined, CalculatorOutlined, FileDoneOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { catalogApi } from '../../api/endpoints';

const FACTS: Record<string, string[]> = {
  TD: ['Từ đủ 22 tuổi, tuổi khi đáo hạn không vượt tuổi nghỉ hưu', 'Tối đa 15 lần thu nhập hoặc 500 triệu đồng', 'Kỳ hạn tối đa 60 tháng', 'Cần chứng từ mục đích mua sắm, dịch vụ'],
  TC: ['Từ đủ 19 tuổi', 'Hạn mức tối đa 200 triệu đồng', 'Duy trì hạn mức 12 tháng', 'Chi tiêu linh hoạt, không dùng cho kinh doanh'],
};

export default function Home() {
  const nav = useNavigate();
  const { data, isLoading } = useQuery({ queryKey: ['products'], queryFn: catalogApi.products });
  return (
    <div>
      <section className="hero">
        <Typography.Title level={2} style={{ marginTop: 0 }}>Biết trước khả năng vay tín chấp của bạn</Typography.Title>
        <Typography.Paragraph style={{ fontSize: 16, maxWidth: 720 }}>
          Khai báo vài thông tin về thu nhập và nhu cầu, hệ thống gợi ý gói vay phù hợp, tính hạn mức an toàn theo tỷ lệ nợ trên thu nhập (DTI)
          và lập kế hoạch trả nợ dự kiến. Không cần khai họ tên, số CCCD hay số điện thoại.
        </Typography.Paragraph>
        <Button type="primary" size="large" onClick={() => nav('/sang-loc')}>Bắt đầu kiểm tra</Button>
      </section>

      <Card style={{ marginTop: 24 }}>
        <Steps
          responsive
          items={[
            { title: 'Khai báo thông tin', description: 'Thu nhập, nghĩa vụ nợ, nhu cầu vay', icon: <SafetyCertificateOutlined />, status: 'process' },
            { title: 'Nhận gợi ý gói vay', description: 'Điểm tương thích, hạn mức an toàn', icon: <CalculatorOutlined />, status: 'process' },
            { title: 'Mô phỏng và chuẩn bị hồ sơ', description: 'Kế hoạch trả nợ, danh mục giấy tờ', icon: <FileDoneOutlined />, status: 'process' },
          ]}
        />
      </Card>

      <Typography.Title level={4} style={{ marginTop: 32 }}>Gói vay trong phạm vi hệ thống</Typography.Title>
      {isLoading ? <Skeleton active /> : (
        <Row gutter={[16, 16]}>
          {data?.map(p => (
            <Col xs={24} md={12} key={p.id}>
              <Card title={p.name} style={{ height: '100%' }}>
                <Typography.Paragraph type="secondary">{p.description}</Typography.Paragraph>
                <List size="small" dataSource={FACTS[p.code] ?? []} renderItem={x => <List.Item>{x}</List.Item>} />
                <Typography.Text type="secondary">Phương thức trả nợ: {p.repaymentMethod}</Typography.Text>
              </Card>
            </Col>
          ))}
        </Row>
      )}
    </div>
  );
}
