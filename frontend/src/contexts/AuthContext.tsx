import { createContext, useContext, useState, useEffect, useRef, useCallback, ReactNode } from 'react';
import api from '../api';

interface AuthUser {
  userId: number;
  username: string;
  displayName: string;
  email: string;
  role: string;
}

interface AuthContextType {
  user: AuthUser | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  isAdmin: boolean;
  login: (username: string, password: string) => Promise<void>;
  register: (username: string, email: string, password: string, displayName?: string) => Promise<void>;
  logout: () => void;
  changePassword: (currentPassword: string, newPassword: string) => Promise<void>;
  verifyPassword: (password: string) => Promise<boolean>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [token, setToken] = useState<string | null>(() => localStorage.getItem('token'));
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    if (token) {
      api.defaults.headers.common['Authorization'] = `Bearer ${token}`;
      api.get('/auth/me')
        .then(res => setUser(res.data))
        .catch(() => { logout(); })
        .finally(() => setIsLoading(false));
    } else {
      setIsLoading(false);
    }
  }, []);

  const login = async (username: string, password: string) => {
    const res = await api.post('/auth/login', { username, password });
    const { token: newToken, ...userData } = res.data;
    setToken(newToken);
    setUser(userData);
    localStorage.setItem('token', newToken);
    api.defaults.headers.common['Authorization'] = `Bearer ${newToken}`;
  };

  const register = async (username: string, email: string, password: string, displayName?: string) => {
    const res = await api.post('/auth/register', { username, email, password, displayName });
    const { token: newToken, ...userData } = res.data;
    setToken(newToken);
    setUser(userData);
    localStorage.setItem('token', newToken);
    api.defaults.headers.common['Authorization'] = `Bearer ${newToken}`;
  };

  const logout = () => {
    setToken(null);
    setUser(null);
    localStorage.removeItem('token');
    delete api.defaults.headers.common['Authorization'];
  };

  // ─── Inactivity auto-logout (1 hour) ───
  const INACTIVITY_TIMEOUT = 60 * 60 * 1000; // 1 hour in ms
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const resetInactivityTimer = useCallback(() => {
    if (timeoutRef.current) clearTimeout(timeoutRef.current);
    if (token && user) {
      timeoutRef.current = setTimeout(() => {
        logout();
      }, INACTIVITY_TIMEOUT);
    }
  }, [token, user]);

  useEffect(() => {
    if (!token || !user) return;

    const events = ['mousedown', 'keydown', 'touchstart', 'scroll', 'mousemove'];
    const handleActivity = () => resetInactivityTimer();

    events.forEach(e => window.addEventListener(e, handleActivity, { passive: true }));
    resetInactivityTimer(); // Start timer on mount / login

    return () => {
      events.forEach(e => window.removeEventListener(e, handleActivity));
      if (timeoutRef.current) clearTimeout(timeoutRef.current);
    };
  }, [token, user, resetInactivityTimer]);

  const changePassword = async (currentPassword: string, newPassword: string) => {
    await api.post('/auth/change-password', { currentPassword, newPassword });
  };

  const verifyPassword = async (password: string): Promise<boolean> => {
    const res = await api.post('/auth/verify-password', { password });
    return res.data.valid;
  };

  return (
    <AuthContext.Provider value={{
      user, token, isAuthenticated: !!token && !!user, isLoading,
      isAdmin: user?.role === 'ADMIN',
      login, register, logout, changePassword, verifyPassword
    }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
}
