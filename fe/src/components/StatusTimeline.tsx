import { Timeline, Typography } from 'antd';
import type { StatusEvent } from '../api/types';
import { APP_STATUS } from '../utils/status';
import { dt } from '../utils/format';

const COLOR: Record<string, string> = { success: 'green', error: 'red', warning: 'orange', processing: 'blue', default: 'gray', cyan: 'cyan', green: 'green', magenta: 'red' };

/** 5.4 Dòng thời gian trạng thái hồ sơ */
export default function StatusTimeline({ history }: { history: StatusEvent[] }) {
  return (
    <Timeline
      items={history.slice().reverse().map(h => ({
        color: COLOR[APP_STATUS[h.status].color] ?? 'gray',
        children: (
          <div>
            <Typography.Text strong>{APP_STATUS[h.status].label}</Typography.Text>
            <Typography.Text type="secondary"> · {dt(h.at)} · {h.actor}</Typography.Text>
            {h.note && <div>{h.note}</div>}
          </div>
        ),
      }))}
    />
  );
}
