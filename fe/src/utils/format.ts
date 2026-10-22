import dayjs from 'dayjs';

export const money = (v: number | null | undefined) =>
  v == null ? '—' : `${Math.round(v).toLocaleString('vi-VN')} đ`;

/** rút gọn: 120.000.000 -> 120 triệu */
export const moneyShort = (v: number | null | undefined) => {
  if (v == null) return '—';
  if (Math.abs(v) >= 1e9) return `${(v / 1e9).toLocaleString('vi-VN', { maximumFractionDigits: 2 })} tỷ`;
  if (Math.abs(v) >= 1e6) return `${(v / 1e6).toLocaleString('vi-VN', { maximumFractionDigits: 1 })} triệu`;
  return money(v);
};

export const pct = (v: number | null | undefined, d = 1) =>
  v == null ? '—' : `${v.toLocaleString('vi-VN', { maximumFractionDigits: d })}%`;

export const dt = (s: string | null | undefined) => (s ? dayjs(s).format('DD/MM/YYYY HH:mm') : '—');

/** định dạng ô nhập tiền */
export const moneyFormatter = (v: string | number | undefined) =>
  `${v ?? ''}`.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
export const moneyParser = (v: string | undefined) => Number((v ?? '').replace(/\./g, '')) as unknown as 0;
