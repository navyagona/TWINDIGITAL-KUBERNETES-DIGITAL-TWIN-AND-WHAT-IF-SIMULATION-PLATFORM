import axios, { AxiosError } from 'axios';

/**
 * All requests go to the API gateway on port 3000.
 * - `npm run dev`: baseURL is '' and Vite proxies /api -> http://localhost:3000 (vite.config.js).
 * - Docker (nginx on host port 8088): nginx proxies /api -> gateway:3000 (nginx.conf).
 * Set VITE_API_BASE_URL (e.g. http://localhost:3000) to call the gateway directly instead;
 * the gateway's CORS settings must then allow the page's origin.
 */
export const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? '';

/** How often pages re-fetch live data. */
export const POLL_INTERVAL_MS = 5000;

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
  timeout: 15000,
});

// Attach the JWT to every request
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('td_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// On 401, drop the session and go to login, except for auth calls (e.g. wrong password)
api.interceptors.response.use(
  (res) => res,
  (err: AxiosError) => {
    const isAuthCall = err.config?.url?.includes('/auth') ?? false;
    if (err.response?.status === 401 && !isAuthCall) {
      localStorage.removeItem('td_token');
      localStorage.removeItem('td_user');
      window.location.href = '/login';
    }
    return Promise.reject(err);
  }
);

export default api;
