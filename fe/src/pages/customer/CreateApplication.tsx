import { useState } from 'react';
import { Alert, App, Button, Card, Checkbox, Descriptions, Radio, Result, Skeleton, Space, Typography } from 'antd';
import { EnvironmentOutlined, PhoneOutlined } from '@ant-design/icons';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { appApi, catalogApi, planApi } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import { money } from '../../utils/format';

const CONSENT_B = 'MB02-1.0';

/** 4.1 Tạo hồ sơ vay: chọn chi nhánh, đồng ý phần (b) */
export default function CreateApplication() {
  const [sp] = useSearchParams();
  const planId = Number(sp.get('phuongAn'));
  const nav = useNavigate();
  const { message } = App.useApp();
  const plan = useQuery({ queryKey: ['plan', planId], queryFn: () => planApi.get(planId), enabled: !!planId });
  const branches = useQuery({ queryKey: ['branches'], queryFn: catalogApi.branches });
  const [branchId, setBranchId] = useState<number>();
  const [agree, setAgree] = useState(false);
  const m = useMutation({
    mutationFn: () => appApi.create({ planId, branchId: branchId!, consentB: { agreed: agree, docVersion: CONSENT_B } }),
    onSuccess: a => { message.success(`Đã tạo hồ sơ ${a.code}`); nav(`/ho-so/${a.id}`, { replace: true }); },
  });
  if (!planId) return <Result status="warning" title="Chưa chọn phương án vay" extra={<Button onClick={() => nav('/cua-toi')}>Về trang của tôi</Button>} />;
  if (plan.isLoading || branches.isLoading) return <Skeleton active />;
  if (!plan.data) return <Result status="404" title="Không tìm thấy phương án" />;
  if (plan.data.applicationId) return <Result status="info" title="Phương án này đã có hồ sơ" extra={<Button type="primary" onClick={() => nav(`/ho-so/${plan.data!.applicationId}`)}>Mở hồ sơ</Button>} />;
  const p = plan.data;
  return (
    <div>
      <Typography.Title level={3}>Lập hồ sơ vay trực tuyến</Typography.Title>
      <Card size="small" style={{ marginBottom: 16 }}>
        <Descriptions size="small" column={{ xs: 1, md: 4 }}>
          <Descriptions.Item label="Gói vay">{p.productName}</Descriptions.Item>
          <Descriptions.Item label="Số tiền">{money(p.amount)}</Descriptions.Item>
          <Descriptions.Item label="Kỳ hạn">{p.termMonths} tháng</Descriptions.Item>
          <Descriptions.Item label="Lãi suất tham chiếu">{p.refRate}%/năm</Descriptions.Item>
        </Descriptions>
      </Card>
      <Card title="1. Chọn chi nhánh tiếp nhận hồ sơ" style={{ marginBottom: 16 }}>
        <Radio.Group value={branchId} onChange={e => setBranchId(e.target.value)} style={{ width: '100%' }}>
          <Space direction="vertical" style={{ width: '100%' }}>
            {branches.data?.map(b => (
              <Radio key={b.id} value={b.id} className="branch-option">
                <div><b>{b.name}</b></div>
                <Typography.Text type="secondary"><EnvironmentOutlined /> {b.address} · <PhoneOutlined /> {b.phone}</Typography.Text>
              </Radio>
            ))}
          </Space>
        </Radio.Group>
        <Typography.Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0 }}>
          Bạn sẽ mang bản gốc giấy tờ tới chi nhánh này để nhân viên đối chiếu.
        </Typography.Paragraph>
      </Card>
      <Card title="2. Đồng ý lưu trữ giấy tờ và lập hồ sơ – MB02 phần (b)">
        <div className="consent-box">
          <ul>
            <li>Mục đích: lưu trữ giấy tờ, trích xuất thông tin, lập giấy đề nghị vay vốn và chuyển hồ sơ tới chi nhánh đã chọn.</li>
            <li>Dữ liệu: họ tên, số CCCD, ngày sinh, địa chỉ, số điện thoại, email và ảnh các giấy tờ pháp lý, thu nhập, mục đích vay.</li>
            <li>Giấy tờ được mã hóa khi lưu; chỉ nhân viên chi nhánh được giao xử lý hồ sơ mới xem được, mọi lượt xem đều được ghi nhật ký.</li>
            <li>Hệ thống không tra cứu CIC; việc tra cứu CIC do ngân hàng thực hiện tại quầy khi bạn ký cam kết.</li>
            <li>Bạn có thể hủy hồ sơ hoặc rút lại sự đồng ý khi hồ sơ chưa đối chiếu xong; giấy tờ và thông tin định danh sẽ bị xóa.</li>
          </ul>
          <Typography.Text type="secondary">Phiên bản văn bản: {CONSENT_B}</Typography.Text>
        </div>
        <Checkbox style={{ marginTop: 16 }} checked={agree} onChange={e => setAgree(e.target.checked)}>
          Tôi đồng ý cho hệ thống lưu trữ giấy tờ và xử lý dữ liệu nêu trên để lập hồ sơ vay.
        </Checkbox>
        {m.isError && <Alert style={{ marginTop: 16 }} type="error" message={errorMessage(m.error)} />}
        <div style={{ marginTop: 16 }}>
          <Button type="primary" disabled={!branchId || !agree} loading={m.isPending} onClick={() => m.mutate()}>Tạo hồ sơ</Button>
        </div>
      </Card>
    </div>
  );
}
