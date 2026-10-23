import { createPortal } from 'react-dom';
import { QRCode } from 'antd';
import dayjs from 'dayjs';
import type { Application } from '../../api/types';
import { money } from '../../utils/format';
import { moneyInWords } from '../../utils/numberToWords';

const SYSTEM_DOC_TYPE = 1;
const dmy = (s: string | null | undefined) => (s ? dayjs(s).format('DD/MM/YYYY') : '');

/**
 * MB15 – Giấy đề nghị vay vốn kiêm phương án trả nợ, mẫu A4 dọc.
 * Vẽ vào document.body (ẩn trên màn hình), chỉ hiện khi in với body có class "printing-rfp" – xem RequestFormPrintButton.
 */
export default function RequestFormPrint({ app }: { app: Application }) {
  const content = app.requestForm?.content ?? [];
  const f = (key: string, fallback = '') => content.find(c => c.key === key)?.value || fallback;
  const p = app.personal;
  const docs = app.documents.filter(d => d.fileName && d.documentTypeId !== SYSTEM_DOC_TYPE);
  const method = f('method', app.plan.productCode === 'TD' ? 'Gốc trả đều hằng tháng, lãi trên dư nợ giảm dần' : 'Lãi trả hằng tháng, gốc trả cuối kỳ');
  const rows = (items: [string, string][]) => (
    <table className="rfp-table"><tbody>
      {items.map(([k, v]) => <tr key={k}><th>{k}</th><td>{v || '…'}</td></tr>)}
    </tbody></table>
  );

  return createPortal(
    <div className="rfp-root">
      <div className="rfp-page">
        <div className="rfp-nation">
          <b>CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM</b>
          <div><b>Độc lập – Tự do – Hạnh phúc</b></div>
        </div>
        <h1 className="rfp-title">GIẤY ĐỀ NGHỊ VAY VỐN KIÊM PHƯƠNG ÁN TRẢ NỢ</h1>
        <p className="rfp-to">Kính gửi: <b>{app.branchName}</b>{app.branchAddress ? ` – ${app.branchAddress}` : ''}</p>

        <h2>I. Thông tin khách hàng</h2>
        {rows([
          ['Họ và tên', f('fullName', p.fullName)],
          ['Số CCCD', f('nationalId', p.nationalId)],
          ['Ngày sinh', f('dob', dmy(p.dob))],
          ['Địa chỉ thường trú', f('address', p.address)],
          ['Số điện thoại', f('phone', p.phone)],
        ])}

        <h2>II. Thông tin khoản vay</h2>
        <table className="rfp-table"><tbody>
          <tr><th>Gói vay</th><td>{app.plan.productName}</td></tr>
          <tr><th>Số tiền đề nghị vay</th><td><b>{money(app.plan.amount)}</b><br />Bằng chữ: <i>{moneyInWords(app.plan.amount)}.</i></td></tr>
          <tr><th>Thời hạn vay</th><td>{app.plan.termMonths} tháng</td></tr>
          <tr><th>Lãi suất tham chiếu</th><td>{app.plan.refRate}%/năm (theo thông báo của ngân hàng tại thời điểm giải ngân)</td></tr>
          <tr><th>Mục đích sử dụng vốn</th><td>{f('purpose', app.purpose) || '…'}</td></tr>
          <tr><th>Phương thức trả nợ</th><td>{method}</td></tr>
          <tr><th>Nguồn trả nợ</th><td>{f('repaymentSource', 'Thu nhập hằng tháng của người vay') || '…'}</td></tr>
        </tbody></table>

        <h2>III. Giấy tờ kèm theo</h2>
        {docs.length ? (
          <ol className="rfp-docs">{docs.map(d => <li key={d.id}>{d.typeName}</li>)}</ol>
        ) : <p>Chưa có giấy tờ kèm theo.</p>}

        <h2>IV. Cam kết của khách hàng</h2>
        <ol className="rfp-commit">
          <li>Các thông tin, giấy tờ nêu trên là đúng sự thật; tôi chịu hoàn toàn trách nhiệm về tính chính xác của chúng.</li>
          <li>Tôi sử dụng vốn vay đúng mục đích đã đề nghị và trả nợ gốc, lãi đầy đủ, đúng hạn theo phương án trên.</li>
          <li>Tôi đồng ý để ngân hàng kiểm tra, đối chiếu giấy tờ bản gốc và tra cứu thông tin tín dụng (CIC) để thẩm định khoản vay.</li>
          <li>Tôi hiểu rằng lãi suất ghi trên giấy này chỉ để tham chiếu; kết quả phê duyệt, số tiền và lãi suất cuối cùng do ngân hàng quyết định.</li>
        </ol>

        <div className="rfp-sign">
          <div className="rfp-sign-box">
            <div className="rfp-date">………, ngày …… tháng …… năm ……</div>
            <b>Khách hàng</b>
            <div><i>(ký, ghi rõ họ tên)</i></div>
          </div>
          <div className="rfp-sign-box">
            <div className="rfp-date">&nbsp;</div>
            <b>Nhân viên tiếp nhận</b>
            <div><i>(ký, ghi rõ họ tên)</i></div>
          </div>
        </div>

        <div className="rfp-foot">
          <div className="rfp-foot-text">
            <div>Mã hồ sơ: <b>{app.code}</b></div>
            <div>Mã tra cứu: <b>{app.qrToken ?? '—'}</b></div>
            <div>Ngày nộp: <b>{dmy(app.lockedAt) || '—'}</b></div>
            <div>Mã băm rút gọn: <b>{app.contentHash ? `${app.contentHash.slice(0, 16)}…` : '—'}</b></div>
          </div>
          {app.qrToken && <QRCode type="svg" value={app.qrToken} size={72} bordered={false} />}
        </div>
      </div>
    </div>,
    document.body,
  );
}
