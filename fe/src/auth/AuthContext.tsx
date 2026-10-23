import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { TOKEN_KEY } from '../api/client';
import type { AuthUser, LoginResponse, Role } from '../api/types';
import { Navigate, useLocation } from 'react-router-dom';
import { Result } from 'antd';

const USER_KEY = 'tsl-user';

interface AuthCtx {
  user: AuthUser | null;
  signIn: (r: LoginResponse) => void;
  signOut: () => void;
}

const Ctx = createContext<AuthCtx>({ user: null, signIn: () => {}, signOut: () => {} });

function readUser(): AuthUser | null {
  try {
    const s = localStorage.getItem(USER_KEY);
    return s ? (JSON.parse(s) as AuthUser) : null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(readUser);
  const signIn = useCallback((r: LoginResponse) => {
    try {
      localStorage.setItem(TOKEN_KEY, r.token);
      localStorage.setItem(USER_KEY, JSON.stringify(r.user));
    } catch { /* bỏ qua */ }
    setUser(r.user);
  }, []);
  const signOut = useCallback(() => {
    try {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(USER_KEY);
    } catch { /* bỏ qua */ }
    setUser(null);
  }, []);
  const v = useMemo(() => ({ user, signIn, signOut }), [user, signIn, signOut]);
  return <Ctx.Provider value={v}>{children}</Ctx.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export const useAuth = () => useContext(Ctx);

/**
 * Chặn trang theo vai trò: chưa đăng nhập thì chuyển tới trang đăng nhập rồi quay lại; đã đăng nhập nhưng sai vai trò thì
 * hiện lỗi 403 tại chỗ (không chuyển sang trang khác).
 */
export function RequireRole({ roles, children }: { roles: Role[]; children: ReactNode }) {
  const { user } = useAuth();
  const loc = useLocation();
  const internal = roles.some(r => r !== 'CUSTOMER');
  if (!user) {
    const to = internal ? '/noi-bo/dang-nhap' : '/dang-nhap';
    return <Navigate to={`${to}?next=${encodeURIComponent(loc.pathname + loc.search)}`} replace />;
  }
  if (!roles.includes(user.role)) {
    return <Result status="403" title="403" subTitle="Bạn không có quyền truy cập trang này." />;
  }
  return <>{children}</>;
}
