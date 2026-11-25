import axios from 'axios';
import { clearSession, getToken } from './tokenStorage.js';

export const UNAUTHORIZED_EVENT = 'codepath:unauthorized';

const axiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
  timeout: 45000, // Submit runs every test case through Judge0 and can take a while.
  headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
});

// Attach the JWT to every request.
axiosInstance.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// A 401 on a protected endpoint means the token is missing, expired or invalid.
// /api/auth/* 401s are plain "wrong credentials" answers and must not log anyone out.
axiosInstance.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    const url = error.config?.url || '';
    if (status === 401 && !url.includes('/api/auth/')) {
      clearSession();
      window.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
    }
    return Promise.reject(error);
  }
);

/** Backend responses are wrapped as { success, message?, data? }; return only `data`. */
export function unwrap(response) {
  return response.data?.data;
}

/** Best human-readable message from a failed request. */
export function getErrorMessage(error, fallback = 'Something went wrong. Please try again.') {
  if (error?.response) {
    const body = error.response.data;
    if (body && typeof body.message === 'string' && body.message.trim()) {
      return body.message;
    }
    if (error.response.status === 503) {
      return 'A required service is unavailable right now. Please try again shortly.';
    }
    return fallback;
  }
  if (error?.code === 'ECONNABORTED') {
    return 'The request timed out. Please try again.';
  }
  if (error?.request) {
    return 'Cannot reach the CodePath server. Check that the backend is running on port 8080.';
  }
  return fallback;
}

/**
 * Field-level messages from a 400 validation response, as { fieldName: message }.
 * Returns {} for any other kind of failure.
 */
export function getFieldErrors(error) {
  const data = error?.response?.data?.data;
  if (error?.response?.status !== 400 || !data || typeof data !== 'object' || Array.isArray(data)) {
    return {};
  }
  const fields = {};
  for (const [key, value] of Object.entries(data)) {
    if (typeof value === 'string') {
      fields[key] = value;
    }
  }
  return fields;
}

export default axiosInstance;
