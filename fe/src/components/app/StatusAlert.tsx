import { Alert } from 'antd';
import type { Application } from '../../api/types';
import { dt, money } from '../../utils/format';

/** Thông điệp theo trạng thái hồ sơ cho khách hàng */
export default function StatusAlert({ app }: { app: Application }) {
  const last = app.history[app.history.length - 1];
  switch (app.status) {
    case 'DRAFTING':
      return <Alert type="info" showIcon message="Hồ sơ đang lập" description="Khai thông tin cá nhân, tải và xác nhận từng giấy tờ, sau đó lập giấy đề nghị vay vốn và xác nhận bằng OTP để nộp." />;
    case 'WAITING_VERIFY':
      return <Alert type="info" showIcon message={`Chờ đối chiếu tại ${app.branchName}`}
        description={`Mang bản gốc các giấy tờ và Phiếu tóm tắt hồ sơ (MB08) tới chi nhánh trước ${dt(app.expiresAt)}. Nhân viên sẽ quét mã QR để mở hồ sơ.`} />;
    case 'NEED_MORE':
      return <Alert type="warning" showIcon message="Hồ sơ cần bổ sung" description={<>{last.note}<br />Tải lại các giấy tờ được đánh dấu không khớp rồi bấm “Nộp lại”.</>} />;
    case 'EXPIRED':
      return <Alert type="warning" showIcon message="Hồ sơ hết hạn" description="Giấy tờ đã quá thời hạn hiệu lực. Tải bản mới của các giấy tờ hết hạn rồi nộp lại, hoặc hủy hồ sơ." />;
    case 'VERIFIED':
      return <Alert type="info" showIcon message="Đã đối chiếu xong" description="Giấy tờ khớp bản gốc. Hồ sơ đang được ngân hàng thẩm định (tra cứu CIC, thẩm định thu nhập), thường trong 1–3 ngày làm việc." />;
    case 'APPROVED':
      return <Alert type="success" showIcon message="Hồ sơ được phê duyệt"
        description={`Số tiền ${money(app.approval?.approvedAmount)}, kỳ hạn ${app.approval?.approvedTerm} tháng, lãi suất ${app.approval?.approvedRate}%/năm. Chi nhánh sẽ liên hệ để ký hợp đồng và giải ngân.`} />;
    case 'DISBURSED':
      return <Alert type="success" showIcon message="Đã giải ngân"
        description={`${money(app.approval?.disbursedAmount)} ngày ${app.approval?.disbursedAt?.split('-').reverse().join('/')} vào ${app.approval?.beneficiary}.`} />;
    case 'REJECTED':
      return <Alert type="error" showIcon message="Hồ sơ không được phê duyệt" description={`Lý do: ${app.approval?.reason ?? last.note ?? ''}. Bạn có thể sàng lọc lại sau khi cải thiện hồ sơ.`} />;
    case 'CANCELLED':
      return <Alert type="error" showIcon message="Hồ sơ đã hủy" description={last.note ?? undefined} />;
  }
}
