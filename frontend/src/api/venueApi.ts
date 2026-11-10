import { apiClient } from './client';
import { ApiResponse, CreateVenuePayload, PageResponse, Venue } from '../types';

export const venueApi = {
  async getVenues(city?: string, page: number = 0, size: number = 50): Promise<PageResponse<Venue>> {
    const params: Record<string, string | number> = { page, size };
    if (city) {
      params.city = city;
    }
    const res = await apiClient.get<ApiResponse<PageResponse<Venue>>>('/api/venues', { params });
    return res.data.data;
  },

  async getVenueById(id: number): Promise<Venue> {
    const res = await apiClient.get<ApiResponse<Venue>>(`/api/venues/${id}`);
    return res.data.data;
  },

  async createVenue(payload: CreateVenuePayload): Promise<Venue> {
    const res = await apiClient.post<ApiResponse<Venue>>('/api/venues', payload);
    return res.data.data;
  },
};

