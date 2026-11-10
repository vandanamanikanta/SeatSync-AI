import { apiClient } from './client';
import { ApiResponse, EventCategory } from '../types';

export const categoryApi = {
  async getCategories(): Promise<EventCategory[]> {
    const res = await apiClient.get<ApiResponse<EventCategory[]>>('/api/categories');
    return res.data.data;
  },

  async getCategoryById(id: number): Promise<EventCategory> {
    const res = await apiClient.get<ApiResponse<EventCategory>>(`/api/categories/${id}`);
    return res.data.data;
  },
};

