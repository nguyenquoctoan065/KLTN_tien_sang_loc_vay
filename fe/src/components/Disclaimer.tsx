import { Alert } from 'antd';

/** Lưu ý bắt buộc trên MB03, MB04, MB07: kết quả chỉ để tham khảo, không phải quyết định phê duyệt */
export default function Disclaimer({ compact }: { compact?: boolean }) {
  return (
    <Alert
      type="info"
      showIcon
      message="Kết quả chỉ để tham khảo"
      description={compact ? undefined : 'Hệ thống không phê duyệt hay từ chối khoản vay. Quyết định cuối cùng thuộc về ngân hàng sau khi đối chiếu giấy tờ gốc và tra cứu thông tin tín dụng (CIC) với sự đồng ý của khách hàng.'}
    />
  );
}
