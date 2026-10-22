import { Steps } from 'antd';
import type { Application } from '../../api/types';

/** Tiến trình hồ sơ theo vòng đời (Hình 2.2) */
export default function AppSteps({ app }: { app: Application }) {
  const personalOk = !!(app.personal.fullName && app.personal.nationalId && app.personal.address);
  const docsOk = app.documents.filter(d => d.required && d.documentTypeId !== 1).every(d => d.status === 'CONFIRMED');
  let current = 0;
  let status: 'process' | 'error' | 'finish' | 'wait' = 'process';
  switch (app.status) {
    case 'DRAFTING': current = !personalOk ? 0 : !docsOk ? 1 : 2; break;
    case 'WAITING_VERIFY': current = 3; break;
    case 'NEED_MORE': current = 3; status = 'error'; break;
    case 'EXPIRED': current = 3; status = 'error'; break;
    case 'VERIFIED': current = 4; break;
    case 'APPROVED': current = 5; break;
    case 'DISBURSED': current = 5; status = 'finish'; break;
    case 'REJECTED': current = 4; status = 'error'; break;
    case 'CANCELLED': current = app.history.some(h => h.status === 'WAITING_VERIFY') ? 3 : 2; status = 'error'; break;
  }
  return (
    <Steps size="small" current={current} status={status} responsive
      items={[
        { title: 'Thông tin cá nhân' }, { title: 'Giấy tờ' }, { title: 'Giấy đề nghị, nộp hồ sơ' },
        { title: 'Đối chiếu tại chi nhánh' }, { title: 'Ngân hàng xét duyệt' }, { title: 'Giải ngân' },
      ]} />
  );
}
