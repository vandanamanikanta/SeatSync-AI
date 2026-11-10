import { apiClient } from './client';
import {
  ApiResponse,
  CreateEventPayload,
  EventItem,
  EventSearchFilters,
  EventStatus,
  PageResponse,
  UpdateEventPayload,
} from '../types';

export const eventApi = {
  async getEvents(filters: EventSearchFilters = {}): Promise<PageResponse<EventItem>> {
    const params: Record<string, unknown> = {};
    if (filters.search) params.search = filters.search;
    if (filters.category) params.category = filters.category;
    if (filters.city) params.city = filters.city;
    if (filters.minPrice !== undefined && filters.minPrice !== null) params.minPrice = filters.minPrice;
    if (filters.maxPrice !== undefined && filters.maxPrice !== null) params.maxPrice = filters.maxPrice;
    if (filters.startDate) params.startDate = filters.startDate;
    if (filters.endDate) params.endDate = filters.endDate;
    if (filters.status) params.status = filters.status;
    if (filters.page !== undefined) params.page = filters.page;
    if (filters.size !== undefined) params.size = filters.size;
    if (filters.sort) params.sort = filters.sort;

    const res = await apiClient.get<ApiResponse<PageResponse<EventItem>>>('/api/events', { params });
    return res.data.data;
  },

  async getEventById(id: number): Promise<EventItem> {
    const res = await apiClient.get<ApiResponse<EventItem>>(`/api/events/${id}`);
    return res.data.data;
  },

  async getEventBySlug(slug: string): Promise<EventItem> {
    const res = await apiClient.get<ApiResponse<EventItem>>(`/api/events/slug/${slug}`);
    return res.data.data;
  },

  async getMyEvents(page: number = 0, size: number = 20, sort: string = 'createdAt,desc'): Promise<PageResponse<EventItem>> {
    const res = await apiClient.get<ApiResponse<PageResponse<EventItem>>>('/api/events/my', {
      params: { page, size, sort },
    });
    return res.data.data;
  },

  async createEvent(payload: CreateEventPayload): Promise<EventItem> {
    const res = await apiClient.post<ApiResponse<EventItem>>('/api/events', payload);
    return res.data.data;
  },

  async updateEvent(id: number, payload: UpdateEventPayload): Promise<EventItem> {
    const res = await apiClient.put<ApiResponse<EventItem>>(`/api/events/${id}`, payload);
    return res.data.data;
  },

  async updateEventStatus(id: number, status: EventStatus): Promise<EventItem> {
    const res = await apiClient.patch<ApiResponse<EventItem>>(`/api/events/${id}/status`, null, {
      params: { status },
    });
    return res.data.data;
  },

  async deleteEvent(id: number): Promise<void> {
    await apiClient.delete<ApiResponse<void>>(`/api/events/${id}`);
  },
};

