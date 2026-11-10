import { apiClient } from './client';
import {
  ApiResponse,
  EventSeatMatrix,
  SeatHoldPayload,
  SeatHoldResult,
  SeatReleasePayload,
  SeatReleaseResult,
  CreateBookingPayload,
  BookingDetail,
} from '../types';

export const bookingApi = {
  /**
   * Fetch live authoritative seat matrix for an event
   * GET /api/events/{eventId}/seats
   */
  getSeatMatrix: async (eventId: number): Promise<EventSeatMatrix> => {
    const response = await apiClient.get<ApiResponse<EventSeatMatrix>>(`/api/events/${eventId}/seats`);
    return response.data.data;
  },

  /**
   * Acquire a 5-minute ephemeral hold on selected seats
   * POST /api/bookings/hold
   */
  holdSeats: async (payload: SeatHoldPayload): Promise<SeatHoldResult> => {
    const response = await apiClient.post<ApiResponse<SeatHoldResult>>('/api/bookings/hold', payload);
    return response.data.data;
  },

  /**
   * Release seat holds manually with holdToken
   * DELETE /api/bookings/hold
   */
  releaseSeats: async (payload: SeatReleasePayload): Promise<SeatReleaseResult> => {
    const response = await apiClient.delete<ApiResponse<SeatReleaseResult>>('/api/bookings/hold', {
      data: payload,
    });
    return response.data.data;
  },

  /**
   * Complete checkout and confirm booking from active hold
   * POST /api/bookings
   */
  createBooking: async (payload: CreateBookingPayload): Promise<BookingDetail> => {
    const response = await apiClient.post<ApiResponse<BookingDetail>>('/api/bookings', payload);
    return response.data.data;
  },

  /**
   * Retrieve current user's booking history
   * GET /api/bookings/my
   */
  getMyBookings: async (): Promise<BookingDetail[]> => {
    const response = await apiClient.get<ApiResponse<BookingDetail[]>>('/api/bookings/my');
    return response.data.data;
  },

  /**
   * Retrieve single booking details by ID
   * GET /api/bookings/{bookingId}
   */
  getBookingById: async (bookingId: number): Promise<BookingDetail> => {
    const response = await apiClient.get<ApiResponse<BookingDetail>>(`/api/bookings/${bookingId}`);
    return response.data.data;
  },
};

