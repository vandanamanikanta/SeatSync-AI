import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { ApiResponse } from '../types';

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8081';

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 15000,
});

// Request Interceptor: Attach Authorization Bearer token if available
apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('seatsync_access_token');
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response Interceptor: Format errors and handle auth invalidation
apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiResponse<unknown>>) => {
    if (error.response?.status === 401) {
      // Clear token on 401 Unauthorized
      const currentPath = window.location.pathname;
      if (!currentPath.includes('/login') && !currentPath.includes('/register')) {
        localStorage.removeItem('seatsync_access_token');
        localStorage.removeItem('seatsync_user');
      }
    }
    return Promise.reject(error);
  }
);

// Helper to extract error message from Axios error
export function getApiErrorMessage(error: unknown, fallback: string = 'An unexpected error occurred'): string {
  if (axios.isAxiosError(error)) {
    const apiResp = error.response?.data as ApiResponse<unknown> | undefined;
    if (apiResp?.message) {
      return apiResp.message;
    }
    if (typeof apiResp?.error === 'string') {
      return apiResp.error;
    }
    if (error.message) {
      return error.message;
    }
  }
  if (error instanceof Error) {
    return error.message;
  }
  return fallback;
}

