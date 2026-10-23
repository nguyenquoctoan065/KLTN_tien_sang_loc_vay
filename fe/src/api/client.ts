import axios, { AxiosError } from 'axios';
import type { ApiError } from './types';

export const TOKEN_KEY = 'tsl-token';

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 20000,
});

api.interceptors.request.use(cfg => {
  try {
    const t = localStorage.getItem(TOKEN_KEY);
    if (t) cfg.headers.Authorization = `Bearer ${t}`;
  } catch { /* bỏ qua */ }
  return cfg;
});

/** Lấy thông báo lỗi tiếng Việt từ phản hồi backend */
export function errorMessage(e: unknown): string {
  const ax = e as AxiosError<ApiError>;
  return ax?.response?.data?.message || ax?.message || 'Đã có lỗi xảy ra, vui lòng thử lại.';
}
