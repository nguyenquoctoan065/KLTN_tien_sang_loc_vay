import { Tag } from 'antd';
import type { ApplicationStatus } from '../api/types';
import { APP_STATUS } from '../utils/status';

export default function StatusTag({ status }: { status: ApplicationStatus }) {
  const s = APP_STATUS[status];
  return <Tag color={s.color}>{s.label}</Tag>;
}
