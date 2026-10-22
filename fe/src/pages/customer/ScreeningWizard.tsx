import { useState } from 'react';
import {
  Alert, App, Button, Card, Checkbox, Col, DatePicker, Descriptions, Divider, Form, Input, InputNumber, Radio, Row, Select,
  Space, Steps, Switch, Typography,
} from 'antd';
import { MinusCircleOutlined, PlusOutlined } from '@ant-design/icons';
import dayjs, { type Dayjs } from 'dayjs';
import { useMutation } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { screeningApi } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import type { CreditHistory, ScreeningRequest } from '../../api/types';
import { money, moneyFormatter, moneyParser } from '../../utils/format';

const BANKS = ['ABBANK', 'Vietcombank', 'VietinBank', 'BIDV', 'Agribank', 'Techcombank', 'MB', 'ACB', 'VPBank', 'Sacombank', 'TPBank', 'Ngân hàng khác'];
const PURPOSES = ['Mua sắm đồ dùng, thiết bị', 'Mua xe máy, ô tô', 'Sửa chữa, nâng cấp nhà ở', 'Học tập', 'Chữa bệnh', 'Du lịch',
  'Cưới hỏi', 'Chi tiêu cá nhân linh hoạt', 'Kinh doanh'];
const CONSENT_VERSION = 'MB02-1.0';

interface FormValues {
  consentA: boolean;
  nationality: string;
  dob: Dayjs;
  gender: 'MALE' | 'FEMALE';
  maritalStatus: 'SINGLE' | 'MARRIED' | 'OTHER';
  dependents: number;
  avgIncome: number;
  salaryBank: string;
  isAbbankStaff: boolean;
  otherDebts: { lender: string; monthlyPayment: number }[];
  everBorrowed: 'NEVER' | 'EVER';
  lateHistory?: 'NO' | 'YES' | 'UNSURE';
  purpose: string;
  desiredAmount: number;
  desiredTerm: number;
}

const STEP_FIELDS: (keyof FormValues | (string | number)[])[][] = [
  ['consentA'],
  ['nationality', 'dob', 'gender', 'maritalStatus', 'dependents'],
  ['avgIncome', 'salaryBank', 'isAbbankStaff', 'otherDebts', 'everBorrowed', 'lateHistory'],
  ['purpose', 'desiredAmount', 'desiredTerm'],
  [],
];

const LABELS = { SINGLE: 'Độc thân', MARRIED: 'Đã kết hôn', OTHER: 'Khác', MALE: 'Nam', FEMALE: 'Nữ' } as const;

const HISTORY_LABELS: Record<CreditHistory, string> = {
  NEVER_BORROWED: 'Chưa từng vay', ON_TIME: 'Đã từng vay, trả đúng hạn',
  LATE_OVER_90: 'Đã từng trả chậm quá 3 tháng hoặc phải xin giãn, hoãn nợ', UNSURE: 'Đã từng vay, không nhớ rõ có trả chậm hay không',
};

function toCreditHistory(v: FormValues): CreditHistory {
  if (v.everBorrowed === 'NEVER') return 'NEVER_BORROWED';
  return v.lateHistory === 'YES' ? 'LATE_OVER_90' : v.lateHistory === 'UNSURE' ? 'UNSURE' : 'ON_TIME';
}

function toRequest(v: FormValues): ScreeningRequest {
  const creditHistory = toCreditHistory(v);
  return {
    consentA: { agreed: !!v.consentA, docVersion: CONSENT_VERSION },
    nationality: v.nationality, dob: v.dob.format('YYYY-MM-DD'), gender: v.gender, maritalStatus: v.maritalStatus,
    dependents: v.dependents ?? 0, avgIncome: v.avgIncome, salaryBank: v.salaryBank, isAbbankStaff: !!v.isAbbankStaff,
    creditHistory,
    otherDebts: creditHistory === 'NEVER_BORROWED' ? [] : (v.otherDebts ?? []).filter(d => d && d.monthlyPayment > 0).map(d => ({ lender: d.lender || 'Không ghi rõ', monthlyPayment: d.monthlyPayment })),
    purpose: v.purpose, desiredAmount: v.desiredAmount, desiredTerm: v.desiredTerm,
  };
}

const moneyInput = { style: { width: '100%' }, formatter: moneyFormatter, parser: moneyParser, addonAfter: 'đ', min: 0, step: 500000 };

export default function ScreeningWizard() {
  const [form] = Form.useForm<FormValues>();
  const [step, setStep] = useState(0);
  const nav = useNavigate();
  const { message } = App.useApp();
  const m = useMutation({
    mutationFn: screeningApi.create,
    onSuccess: r => { message.success('Đã có kết quả gợi ý'); nav(`/sang-loc/${r.id}`); },
  });
  const next = async () => {
    try {
      await form.validateFields(STEP_FIELDS[step] as never);
      setStep(s => s + 1);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch { /* lỗi hiện tại ô nhập */ }
  };
  const debts = Form.useWatch('otherDebts', form) as FormValues['otherDebts'] | undefined;
  const everBorrowed = Form.useWatch('everBorrowed', form) as FormValues['everBorrowed'] | undefined;
  const v = step === 4 ? (form.getFieldsValue(true) as FormValues) : undefined;
  const debtSum = ((step === 4 ? v?.otherDebts : debts) ?? []).reduce((s, d) => s + (d?.monthlyPayment || 0), 0);

  return (
    <div>
      <Typography.Title level={3}>Kiểm tra khả năng vay</Typography.Title>
      <Typography.Paragraph type="secondary">Phiếu thông tin sàng lọc (MB01). Không cần đăng nhập, không khai họ tên, số CCCD, số điện thoại.</Typography.Paragraph>
      <Card>
        <Steps current={step} size="small" style={{ marginBottom: 24 }} responsive
          items={[{ title: 'Đồng ý xử lý dữ liệu' }, { title: 'Thông tin cá nhân' }, { title: 'Thu nhập, nghĩa vụ nợ' }, { title: 'Nhu cầu vay' }, { title: 'Xem lại' }]} />
        <Form form={form} layout="vertical" requiredMark="optional" scrollToFirstError
          initialValues={{ nationality: 'Việt Nam', gender: 'MALE', maritalStatus: 'SINGLE', dependents: 0, isAbbankStaff: false,
            otherDebts: [], desiredTerm: 24 }}>
          {step === 0 && (
            <div>
              <div className="consent-box">
                <Typography.Title level={5} style={{ marginTop: 0 }}>Văn bản xác nhận đồng ý xử lý dữ liệu cá nhân – Phần (a): tiền sàng lọc</Typography.Title>
                <ul>
                  <li>Mục đích: kiểm tra điều kiện, tính tỷ lệ nợ trên thu nhập (DTI), hạn mức an toàn và gợi ý gói vay phù hợp.</li>
                  <li>Dữ liệu xử lý: quốc tịch, ngày sinh, giới tính, tình trạng hôn nhân, số người phụ thuộc, thu nhập, nghĩa vụ trả nợ, nhu cầu vay. Không thu thập họ tên, số CCCD, số điện thoại ở bước này.</li>
                  <li>Kết quả chỉ để tham khảo; hệ thống không tra cứu CIC và không phê duyệt khoản vay.</li>
                  <li>Kết quả chỉ được lưu vào tài khoản khi bạn chọn lưu; bạn có thể rút lại sự đồng ý bất kỳ lúc nào.</li>
                  <li>Việc lưu trữ giấy tờ và lập hồ sơ vay cần đồng ý riêng ở Phần (b) khi tạo hồ sơ.</li>
                </ul>
                <Typography.Text type="secondary">Phiên bản văn bản: {CONSENT_VERSION}</Typography.Text>
              </div>
              <Form.Item name="consentA" valuePropName="checked" style={{ marginTop: 16 }}
                rules={[{ validator: (_, x) => (x ? Promise.resolve() : Promise.reject(new Error('Cần đồng ý để tiếp tục sàng lọc'))) }]}>
                <Checkbox>Tôi đã đọc và đồng ý cho hệ thống xử lý dữ liệu nêu trên để tiền sàng lọc.</Checkbox>
              </Form.Item>
            </div>
          )}
          {step === 1 && (
            <Row gutter={16}>
              <Col xs={24} md={12}>
                <Form.Item name="nationality" label="Quốc tịch" rules={[{ required: true }]}>
                  <Select options={[{ value: 'Việt Nam' }, { value: 'Khác' }]} />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item name="dob" label="Ngày sinh" rules={[{ required: true, message: 'Chọn ngày sinh' }]}>
                  <DatePicker style={{ width: '100%' }} format="DD/MM/YYYY" placeholder="dd/mm/yyyy"
                    disabledDate={d => d.isAfter(dayjs().subtract(16, 'year'))} defaultPickerValue={dayjs().subtract(30, 'year')} />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item name="gender" label="Giới tính" rules={[{ required: true }]}>
                  <Radio.Group options={[{ value: 'MALE', label: 'Nam' }, { value: 'FEMALE', label: 'Nữ' }]} />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item name="maritalStatus" label="Tình trạng hôn nhân" rules={[{ required: true }]}>
                  <Radio.Group options={[{ value: 'SINGLE', label: 'Độc thân' }, { value: 'MARRIED', label: 'Đã kết hôn' }, { value: 'OTHER', label: 'Khác' }]} />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item name="dependents" label="Số người phụ thuộc" tooltip="Con cái, cha mẹ… do bạn nuôi dưỡng" rules={[{ required: true }]}>
                  <InputNumber min={0} max={10} style={{ width: '100%' }} />
                </Form.Item>
              </Col>
            </Row>
          )}
          {step === 2 && (
            <div>
              <Row gutter={16}>
                <Col xs={24} md={12}>
                  <Form.Item name="avgIncome" label="Thu nhập lương chuyển khoản bình quân 03 tháng"
                    tooltip="Ngân hàng chỉ chấp nhận lương chuyển khoản có sao kê, không chấp nhận lương tiền mặt"
                    rules={[{ required: true, message: 'Nhập thu nhập' }, { type: 'number', min: 1_000_000, message: 'Thu nhập tối thiểu 1.000.000 đ' }]}>
                    <InputNumber {...moneyInput} />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item name="salaryBank" label="Ngân hàng nhận lương" rules={[{ required: true, message: 'Chọn ngân hàng' }]}>
                    <Select showSearch options={BANKS.map(b => ({ value: b }))} placeholder="Chọn ngân hàng" />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item name="isAbbankStaff" label="Là cán bộ nhân viên ABBANK" valuePropName="checked">
                    <Switch checkedChildren="Có" unCheckedChildren="Không" />
                  </Form.Item>
                </Col>
                <Col xs={24}>
                  <Form.Item name="everBorrowed" label="Bạn đã từng vay ngân hàng, công ty tài chính hoặc dùng thẻ tín dụng chưa?"
                    rules={[{ required: true, message: 'Chọn một đáp án' }]}>
                    <Radio.Group options={[{ value: 'NEVER', label: 'Chưa từng' }, { value: 'EVER', label: 'Đã từng' }]}
                      onChange={e => { if (e.target.value === 'NEVER') form.setFieldValue('otherDebts', []); }} />
                  </Form.Item>
                </Col>
                {everBorrowed === 'EVER' && (
                  <Col xs={24}>
                    <Form.Item name="lateHistory" rules={[{ required: true, message: 'Chọn một đáp án' }]}
                      label="Trong 5 năm gần đây, có lần nào bạn trả chậm một khoản vay hoặc thẻ tín dụng quá 3 tháng, hoặc phải xin ngân hàng giãn, hoãn, giảm số tiền phải trả vì không trả được không?"
                      extra="Ngân hàng sẽ tra cứu lịch sử tín dụng của bạn tại Trung tâm Thông tin tín dụng Quốc gia (CIC) khi bạn ký hồ sơ ở chi nhánh. Bạn cũng có thể tự tra cứu qua ứng dụng CIC Credit Connect.">
                      <Radio.Group options={[{ value: 'NO', label: 'Không' }, { value: 'YES', label: 'Có' }, { value: 'UNSURE', label: 'Không nhớ rõ' }]} />
                    </Form.Item>
                  </Col>
                )}
              </Row>
              {everBorrowed === 'EVER' && (<>
              <Divider orientation="left" plain>Nghĩa vụ trả nợ hằng tháng tại tổ chức tín dụng khác</Divider>
              <Form.List name="otherDebts">
                {(fields, { add, remove }) => (
                  <div>
                    {fields.map(f => (
                      <Space key={f.key} align="start" style={{ display: 'flex', marginBottom: 8 }} wrap>
                        <Form.Item name={[f.name, 'lender']} style={{ marginBottom: 0, width: 260 }}>
                          <Input placeholder="Tổ chức tín dụng / thẻ tín dụng" />
                        </Form.Item>
                        <Form.Item name={[f.name, 'monthlyPayment']} style={{ marginBottom: 0, width: 240 }}
                          rules={[{ required: true, message: 'Nhập số tiền trả hằng tháng' }]}>
                          <InputNumber {...moneyInput} placeholder="Số tiền trả/tháng" />
                        </Form.Item>
                        <Button icon={<MinusCircleOutlined />} onClick={() => remove(f.name)} aria-label="Xóa khoản nợ" />
                      </Space>
                    ))}
                    <Button type="dashed" icon={<PlusOutlined />} onClick={() => add({ lender: '', monthlyPayment: undefined })}>Thêm khoản nợ</Button>
                    {debtSum > 0 && <Typography.Paragraph style={{ marginTop: 8 }}>Tổng nghĩa vụ hằng tháng: <b>{money(debtSum)}</b></Typography.Paragraph>}
                    {fields.length === 0 && <Typography.Paragraph type="secondary" style={{ marginTop: 8 }}>Bỏ qua nếu bạn không có khoản vay hay dư nợ thẻ tín dụng nào.</Typography.Paragraph>}
                  </div>
                )}
              </Form.List>
              </>)}
            </div>
          )}
          {step === 3 && (
            <Row gutter={16}>
              <Col xs={24} md={12}>
                <Form.Item name="purpose" label="Mục đích vay" rules={[{ required: true, message: 'Chọn mục đích' }]}
                  extra="Vay tiêu dùng cần mục đích cụ thể có hóa đơn, hợp đồng; thấu chi dùng cho chi tiêu linh hoạt.">
                  <Select options={PURPOSES.map(p => ({ value: p }))} placeholder="Chọn mục đích" />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item name="desiredAmount" label="Số tiền mong muốn vay"
                  rules={[{ required: true, message: 'Nhập số tiền' }, { type: 'number', min: 10_000_000, message: 'Tối thiểu 10.000.000 đ' }]}>
                  <InputNumber {...moneyInput} step={5_000_000} />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item name="desiredTerm" label="Kỳ hạn mong muốn (tháng)" rules={[{ required: true }]}
                  extra="Vay tiêu dùng tối đa 60 tháng; thấu chi duy trì hạn mức 12 tháng.">
                  <InputNumber min={6} max={60} style={{ width: '100%' }} addonAfter="tháng" />
                </Form.Item>
              </Col>
            </Row>
          )}
          {step === 4 && v && (
            <Descriptions bordered column={{ xs: 1, md: 2 }} size="small">
              <Descriptions.Item label="Quốc tịch">{v.nationality}</Descriptions.Item>
              <Descriptions.Item label="Ngày sinh">{v.dob?.format('DD/MM/YYYY')}</Descriptions.Item>
              <Descriptions.Item label="Giới tính">{LABELS[v.gender]}</Descriptions.Item>
              <Descriptions.Item label="Hôn nhân">{LABELS[v.maritalStatus]}</Descriptions.Item>
              <Descriptions.Item label="Người phụ thuộc">{v.dependents}</Descriptions.Item>
              <Descriptions.Item label="Thu nhập bình quân">{money(v.avgIncome)}</Descriptions.Item>
              <Descriptions.Item label="Ngân hàng nhận lương">{v.salaryBank}</Descriptions.Item>
              <Descriptions.Item label="CBNV ABBANK">{v.isAbbankStaff ? 'Có' : 'Không'}</Descriptions.Item>
              {v.everBorrowed === 'EVER' && <Descriptions.Item label="Nghĩa vụ nợ khác">{money(debtSum)}/tháng</Descriptions.Item>}
              <Descriptions.Item label="Lịch sử vay và trả nợ">{HISTORY_LABELS[toCreditHistory(v)]}</Descriptions.Item>
              <Descriptions.Item label="Mục đích">{v.purpose}</Descriptions.Item>
              <Descriptions.Item label="Số tiền, kỳ hạn">{money(v.desiredAmount)} · {v.desiredTerm} tháng</Descriptions.Item>
            </Descriptions>
          )}
        </Form>
        {m.isError && <Alert style={{ marginTop: 16 }} type="error" message={errorMessage(m.error)} />}
        <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 24 }}>
          <Button disabled={step === 0} onClick={() => setStep(s => s - 1)}>Quay lại</Button>
          {step < 4 ? (
            <Button type="primary" onClick={next}>Tiếp tục</Button>
          ) : (
            <Button type="primary" loading={m.isPending} onClick={() => m.mutate(toRequest(form.getFieldsValue(true)))}>Xem gợi ý gói vay</Button>
          )}
        </div>
      </Card>
    </div>
  );
}
