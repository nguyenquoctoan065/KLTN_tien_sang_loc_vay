import type { ApplicationStatus } from '../api/types';

/** Trạng thái hồ sơ theo vòng đời ở Hình 2.2 */
export const APP_STATUS: Record<ApplicationStatus, { label: string; color: string }> = {
  DRAFTING: { label: 'Đang lập hồ sơ', color: 'default' },
  WAITING_VERIFY: { label: 'Chờ đối chiếu', color: 'processing' },
  NEED_MORE: { label: 'Cần bổ sung', color: 'warning' },
  VERIFIED: { label: 'Đã đối chiếu', color: 'cyan' },
  APPROVED: { label: 'Được phê duyệt', color: 'green' },
  DISBURSED: { label: 'Đã giải ngân', color: 'success' },
  REJECTED: { label: 'Bị từ chối', color: 'error' },
  CANCELLED: { label: 'Đã hủy', color: 'default' },
  EXPIRED: { label: 'Hết hạn', color: 'magenta' },
};

export const scoreLevel = (s: number | null) =>
  s == null ? { label: '—', color: 'default' } : s >= 75 ? { label: 'Tương thích cao', color: 'success' }
    : s >= 55 ? { label: 'Tương thích trung bình', color: 'warning' } : { label: 'Tương thích thấp', color: 'error' };
