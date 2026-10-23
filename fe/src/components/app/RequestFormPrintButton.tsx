import { Button } from 'antd';
import { PrinterOutlined } from '@ant-design/icons';
import type { Application } from '../../api/types';
import RequestFormPrint from './RequestFormPrint';

const PRINTING = 'printing-rfp';

/** Nút “In giấy đề nghị”; chỉ hiện khi hồ sơ đã nộp/khóa và có giấy đề nghị */
export default function RequestFormPrintButton({ app }: { app: Application }) {
  if (!app.lockedAt || !app.requestForm) return null;
  const print = () => {
    document.body.classList.add(PRINTING);
    window.addEventListener('afterprint', () => document.body.classList.remove(PRINTING), { once: true });
    window.print();
  };
  return (
    <>
      <Button icon={<PrinterOutlined />} onClick={print} className="no-print">In giấy đề nghị</Button>
      <RequestFormPrint app={app} />
    </>
  );
}
