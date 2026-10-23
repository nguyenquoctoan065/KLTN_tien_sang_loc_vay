import { Button } from 'antd';
import { PrinterOutlined } from '@ant-design/icons';
import type { Application } from '../../api/types';
import RequestFormPrint from './RequestFormPrint';

const PRINTING = 'printing-rfp';

/**
 * Nút “In giấy đề nghị”; chỉ hiện khi hồ sơ đã nộp/khóa và có giấy đề nghị.
 * `sheet={false}` khi trang có nhiều nút: trang tự vẽ một `<RequestFormPrint>` để bản in không bị lặp.
 */
export default function RequestFormPrintButton({ app, sheet = true }: { app: Application; sheet?: boolean }) {
  if (!app.lockedAt || !app.requestForm) return null;
  const print = () => {
    document.body.classList.add(PRINTING);
    window.addEventListener('afterprint', () => document.body.classList.remove(PRINTING), { once: true });
    window.print();
  };
  return (
    <>
      <Button icon={<PrinterOutlined />} onClick={print} className="no-print">In giấy đề nghị</Button>
      {sheet && <RequestFormPrint app={app} />}
    </>
  );
}
