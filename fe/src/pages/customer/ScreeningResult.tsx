import { useEffect, useRef } from 'react';
import {
  Alert, App, Badge, Button, Card, Col, Descriptions, List, Progress, Result, Row, Skeleton, Space, Statistic, Tag, Typography,
} from 'antd';
import { CheckCircleTwoTone, CloseCircleTwoTone, SaveOutlined, BulbOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { screeningApi } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import Disclaimer from '../../components/Disclaimer';
import Hint from '../../components/Hint';
import { GLOSSARY } from '../../utils/glossary';
import { dt, money, pct, shortId } from '../../utils/format';
import { scoreLevel } from '../../utils/status';
import type { ScreeningResultItem } from '../../api/types';

function ProductCard({ r, onSim }: { r: ScreeningResultItem; onSim: () => void }) {
  const lv = scoreLevel(r.score);
  const card = (
    <Card
      style={{ height: '100%' }}
      title={<Space>{r.eligible ? <CheckCircleTwoTone twoToneColor="#16a34a" /> : <CloseCircleTwoTone twoToneColor="#dc2626" />}{r.productName}</Space>}
      extra={r.eligible ? undefined : <Tag color="error">Chưa đạt điều kiện</Tag>}
    >
      {r.eligible ? (
        <>
          <Row gutter={16} align="middle">
            <Col flex="140px">
              <Progress type="dashboard" percent={r.score ?? 0} strokeColor={{ success: '#16a34a', warning: '#f59e0b', error: '#dc2626', default: '#0f766e' }[lv.color]} format={p => <span style={{ fontSize: 22 }}>{p}<div style={{ fontSize: 12, color: '#64748b' }}>điểm</div></span>} />
            </Col>
            <Col flex="auto">
              <Statistic title={<Hint text={GLOSSARY.maxSafe}>Hạn mức tối đa an toàn</Hint>} value={r.maxSafeAmount ?? 0} suffix="đ" groupSeparator="." />
              <Typography.Text type="secondary">Tỷ lệ trả nợ trên thu nhập (DTI) ước tính với số tiền mong muốn: <Hint text={GLOSSARY.dti}><b>{pct(r.dti)}</b></Hint></Typography.Text>
              <div style={{ marginTop: 6 }}><Tag color={lv.color}>{lv.label}</Tag></div>
            </Col>
          </Row>
          <Descriptions size="small" column={2} style={{ marginTop: 12 }}>
            <Descriptions.Item label={<Hint text={GLOSSARY.refRate}>Lãi suất tham chiếu</Hint>}>{r.refRate}%/năm</Descriptions.Item>
            <Descriptions.Item label="Kỳ hạn tối đa">{r.maxTerm} tháng</Descriptions.Item>
          </Descriptions>
          {r.hints.length > 0 && (
            <Alert type="warning" showIcon icon={<BulbOutlined />} style={{ marginTop: 8 }}
              message={<List size="small" split={false} dataSource={r.hints} renderItem={x => <List.Item style={{ padding: '2px 0' }}>{x}</List.Item>} />} />
          )}
          <Button type="primary" block style={{ marginTop: 16 }} onClick={onSim}>Mô phỏng phương án vay</Button>
        </>
      ) : (
        <>
          <Typography.Text strong>Lý do:</Typography.Text>
          <ul style={{ marginTop: 4 }}>{r.reasons.map(x => <li key={x}>{x}</li>)}</ul>
          {r.hints.length > 0 && (<>
            <Typography.Text strong>Hướng cải thiện:</Typography.Text>
            <ul style={{ marginTop: 4 }}>{r.hints.map(x => <li key={x}>{x}</li>)}</ul>
          </>)}
        </>
      )}
    </Card>
  );
  return r.rankNo ? <Badge.Ribbon text={`Gợi ý #${r.rankNo}`} color={r.rankNo === 1 ? 'green' : 'blue'}>{card}</Badge.Ribbon> : card;
}

export default function ScreeningResult() {
  const { id } = useParams();
  const sid = id ?? '';
  const nav = useNavigate();
  const [sp, setSp] = useSearchParams();
  const { user } = useAuth();
  const { message } = App.useApp();
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['screening', sid], queryFn: () => screeningApi.get(sid) });
  const save = useMutation({
    mutationFn: () => screeningApi.save(sid),
    onSuccess: d => { qc.setQueryData(['screening', sid], d); message.success('Đã lưu kết quả vào tài khoản'); },
    onError: e => message.error(errorMessage(e)),
  });
  const tried = useRef(false);
  useEffect(() => {
    if (!tried.current && sp.get('luu') === '1' && user?.role === 'CUSTOMER' && q.data) {
      tried.current = true;
      sp.delete('luu'); setSp(sp, { replace: true });
      if (!q.data.savedToCustomerId) save.mutate();
    }
  }, [sp, user, q.data, save, setSp]);

  if (q.isLoading) return <Skeleton active />;
  if (q.isError || !q.data) {
    // Kết quả đã lưu vào tài khoản chỉ chủ tài khoản xem được: người chưa đăng nhập nhận 404 nên gợi ý đăng nhập
    const notFound = isAxiosError(q.error) && q.error.response?.status === 404;
    return (
      <Result status="404" title="Không tìm thấy kết quả sàng lọc"
        subTitle={notFound && !user ? 'Nếu bạn đã lưu kết quả này vào tài khoản, hãy đăng nhập để xem.' : undefined}
        extra={[
          notFound && !user && <Button key="login" type="primary" onClick={() => nav(`/dang-nhap?next=${encodeURIComponent(`/sang-loc/${sid}`)}`)}>Đăng nhập</Button>,
          <Button key="again" onClick={() => nav('/sang-loc')}>Sàng lọc lại</Button>,
        ]} />
    );
  }
  const s = q.data;
  const debt = s.input.otherDebts.reduce((a, d) => a + d.monthlyPayment, 0);
  const anyEligible = s.results.some(r => r.eligible);
  const onSave = () => {
    if (user?.role !== 'CUSTOMER') nav(`/dang-nhap?next=${encodeURIComponent(`/sang-loc/${sid}?luu=1`)}`);
    else save.mutate();
  };

  return (
    <div>
      <Space style={{ width: '100%', justifyContent: 'space-between' }} wrap>
        <div>
          <Typography.Title level={3} style={{ marginBottom: 0 }}>Kết quả gợi ý gói vay</Typography.Title>
          <Typography.Text type="secondary">Lượt sàng lọc {shortId(s.id)} · {dt(s.createdAt)} · Phiên bản tham số {s.versionNo}</Typography.Text>
        </div>
        <Space>
          <Button onClick={() => nav(`/sang-loc?tu=${sid}`)}>Sàng lọc lại</Button>
          {s.savedToCustomerId ? <Tag color="success" icon={<SaveOutlined />}>Đã lưu vào tài khoản</Tag>
            : <Button icon={<SaveOutlined />} loading={save.isPending} onClick={onSave}>Lưu kết quả</Button>}
        </Space>
      </Space>

      <Card style={{ marginTop: 16 }}>
        <Row gutter={[16, 16]}>
          <Col xs={12} md={6}><Statistic title="Thu nhập bình quân" value={s.input.avgIncome} suffix="đ" groupSeparator="." /></Col>
          <Col xs={12} md={6}><Statistic title={<Hint text={GLOSSARY.living}>Chi phí sinh hoạt định mức</Hint>} value={s.livingCost} suffix="đ" groupSeparator="." /></Col>
          <Col xs={12} md={6}><Statistic title={<Hint text={GLOSSARY.debt}>Nghĩa vụ nợ hiện có</Hint>} value={debt} suffix="đ" groupSeparator="." /></Col>
          <Col xs={12} md={6}>
            <Statistic title={<Hint text={GLOSSARY.disposable}>Thu nhập khả dụng</Hint>} value={s.disposableIncome} suffix="đ" groupSeparator="."
              valueStyle={{ color: s.disposableIncome > 0 ? '#16a34a' : '#dc2626' }} />
          </Col>
        </Row>
        <Typography.Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0 }}>
          Tỷ lệ trả nợ trên thu nhập (DTI) hiện tại, chưa tính khoản vay mới: <Hint text={GLOSSARY.dti}><b>{pct(s.currentDti)}</b></Hint>. Nhu cầu: {money(s.input.desiredAmount)}, {s.input.desiredTerm} tháng, mục đích “{s.input.purpose}”.
        </Typography.Paragraph>
      </Card>

      {(s.notes ?? []).map(n => (
        <Alert key={n.text} style={{ marginTop: 16 }} showIcon type={n.type === 'WARNING' ? 'warning' : 'info'} message={n.text} />
      ))}

      {!anyEligible && (
        <Alert style={{ marginTop: 16 }} type="error" showIcon message="Hiện chưa có gói vay phù hợp"
          description="Xem lý do và hướng cải thiện ở từng gói bên dưới. Bạn có thể điều chỉnh thông tin và sàng lọc lại." />
      )}

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        {s.results.map(r => (
          <Col xs={24} md={12} key={r.productCode}>
            <ProductCard r={r} onSim={() => nav(`/mo-phong/${sid}?goi=${r.productCode}`)} />
          </Col>
        ))}
      </Row>
      <div style={{ marginTop: 16 }}><Disclaimer /></div>
    </div>
  );
}
