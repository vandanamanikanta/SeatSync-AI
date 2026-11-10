import { apiClient } from './client';
import { ApiResponse, AuthResponse, LoginPayload, RegisterPayload, UserProfile } from '../types';

export const authApi = {
  async login(payload: LoginPayload): Promise<AuthResponse> {
    const res = await apiClient.post<ApiResponse<AuthResponse>>('/api/auth/login', payload);
    return res.data.data;
  },

  async register(payload: RegisterPayload): Promise<AuthResponse> {
    const res = await apiClient.post<ApiResponse<AuthResponse>>('/api/auth/register', payload);
    return res.data.data;
  },

  async getCurrentUser(): Promise<UserProfile> {
    const res = await apiClient.get<ApiResponse<UserProfile>>('/api/auth/me');
    return res.data.data;
  },

  async refreshToken(refreshToken: string): Promise<AuthResponse> {
    const res = await apiClient.post<ApiResponse<AuthResponse>>('/api/auth/refresh', { refreshToken });
    return res.data.data;
  },
};

