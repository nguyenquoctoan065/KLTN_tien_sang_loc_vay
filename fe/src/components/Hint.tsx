import { Tooltip } from 'antd';
import { QuestionCircleOutlined } from '@ant-design/icons';
import type { ReactNode } from 'react';

/** Nhãn kèm dấu hỏi; rê chuột hoặc chạm vào để xem giải thích bằng lời thường */
export default function Hint({ text, children }: { text: string; children: ReactNode }) {
  return (
    <span>
      {children}{' '}
      <Tooltip title={text}>
        <QuestionCircleOutlined style={{ color: '#94a3b8', cursor: 'help' }} aria-label={text} />
      </Tooltip>
    </span>
  );
}
