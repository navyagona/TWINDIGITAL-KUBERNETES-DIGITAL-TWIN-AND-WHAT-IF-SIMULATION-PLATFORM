import { create } from 'zustand';
import api from '../api/client';

export type Role = 'ADMIN' | 'VIEWER' | string;

export interface AuthUser {
  username: string;
  role: Role;
}

interface LoginResponse {
  accessToken: string;
  refreshToken?: string;
  username: string;
  role: Role;
}

interface AuthState {
  token: string | null;
  user: AuthUser | null;
  login: (username: string, password: string) => Promise<LoginResponse>;
  logout: () => Promise<void>;
  isAuthenticated: () => boolean;
}

function parseToken(token: string | null): AuthUser | null {
  if (!token) return null;
  try {
    const parts = token.split('.');
    if (parts.length === 3) {
      const payload = JSON.parse(atob(parts[1]));
      return {
        username: payload.sub || payload.username || '',
        role: payload.role || (Array.isArray(payload.roles) ? payload.roles[0] : 'VIEWER'),
      };
    }
  } catch {
    // ignore malformed token
  }
  return null;
}

const initialToken = localStorage.getItem('td_token');
const initialUser: AuthUser | null = (() => {
  try {
    const saved = localStorage.getItem('td_user');
    if (saved) return JSON.parse(saved) as AuthUser;
  } catch {
    // ignore parse error
  }
  return parseToken(initialToken);
})();

const useAuthStore = create<AuthState>((set) => ({
  token: initialToken,
  user: initialUser,

  login: async (username, password) => {
    const { data } = await api.post<LoginResponse>('/api/v1/auth/login', { username, password });
    localStorage.setItem('td_token', data.accessToken);
    const userData: AuthUser = { username: data.username, role: data.role };
    localStorage.setItem('td_user', JSON.stringify(userData));
    set({ token: data.accessToken, user: userData });
    return data;
  },

  logout: async () => {
    try {
      await api.post('/api/v1/auth/logout');
    } catch {
      /* ignore network errors on logout */
    }
    localStorage.removeItem('td_token');
    localStorage.removeItem('td_user');
    set({ token: null, user: null });
  },

  isAuthenticated: () => !!localStorage.getItem('td_token'),
}));

export const isViewerRole = (user: AuthUser | null): boolean => user?.role?.toUpperCase() === 'VIEWER';

export default useAuthStore;
