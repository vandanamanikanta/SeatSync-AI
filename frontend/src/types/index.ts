// Common API Envelope Types
export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  error?: string | null;
  timestamp: string;
}

export interface PageResponse<T> {
  content: T[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

// User & Authentication Types
export interface UserSummary {
  id: number;
  email: string;
  fullName: string;
  roles: string[];
}

export interface UserProfile {
  id: number;
  email: string;
  fullName: string;
  phoneNumber?: string | null;
  status: 'ACTIVE' | 'SUSPENDED' | 'LOCKED';
  roles: string[];
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserSummary;
}

export interface LoginPayload {
  email: string;
  password: string;
}

export interface RegisterPayload {
  email: string;
  password: string;
  fullName: string;
  phoneNumber?: string;
  role?: string;
}

// Category & Venue Types
export interface EventCategory {
  id: number;
  name: string;
  slug: string;
  description?: string;
  iconName?: string;
  displayOrder: number;
  isActive: boolean;
}

export interface Venue {
  id: number;
  name: string;
  slug: string;
  address: string;
  city: string;
  state: string;
  postalCode?: string;
  country: string;
  capacity: number;
  timezone: string;
  latitude?: number | null;
  longitude?: number | null;
}

export interface CreateVenuePayload {
  name: string;
  slug?: string;
  address: string;
  city: string;
  state: string;
  postalCode: string;
  country?: string;
  capacity: number;
  timezone?: string;
  latitude?: number;
  longitude?: number;
}

// Event Types
export type EventStatus = 'DRAFT' | 'PUBLISHED' | 'POSTPONED' | 'CANCELLED' | 'COMPLETED';

export interface EventCategorySummary {
  id: number;
  name: string;
  slug: string;
  iconName?: string;
}

export interface VenueSummary {
  id: number;
  name: string;
  slug: string;
  address: string;
  city: string;
  state: string;
  capacity: number;
  timezone: string;
}

export interface OrganizerSummary {
  id: number;
  fullName: string;
  email: string;
}

export interface EventItem {
  id: number;
  title: string;
  slug: string;
  shortDescription: string;
  fullDescription?: string;
  bannerImageUrl?: string;
  status: EventStatus;
  startTime: string;
  endTime: string;
  doorsOpenTime?: string | null;
  basePrice: number;
  category: EventCategorySummary;
  venue: VenueSummary;
  organizer: OrganizerSummary;
  createdAt: string;
  updatedAt: string;
}

export interface CreateEventPayload {
  categoryId: number;
  venueId: number;
  title: string;
  slug?: string;
  shortDescription: string;
  fullDescription?: string;
  bannerImageUrl?: string;
  startTime: string;
  endTime: string;
  doorsOpenTime?: string;
  basePrice: number;
  status?: EventStatus;
}

export interface UpdateEventPayload {
  categoryId?: number;
  venueId?: number;
  title?: string;
  slug?: string;
  shortDescription?: string;
  fullDescription?: string;
  bannerImageUrl?: string;
  startTime?: string;
  endTime?: string;
  doorsOpenTime?: string;
  basePrice?: number;
  status?: EventStatus;
}

export interface EventSearchFilters {
  search?: string;
  category?: string;
  city?: string;
  minPrice?: number;
  maxPrice?: number;
  startDate?: string;
  endDate?: string;
  status?: EventStatus;
  page?: number;
  size?: number;
  sort?: string;
}

// Seating & Booking Types
export type SeatStatus = 'AVAILABLE' | 'HELD' | 'BOOKED';

export interface SeatItem {
  eventSeatId: number;
  seatId: number;
  rowLabel: string;
  seatNumber: number;
  gridX: number;
  gridY: number;
  price: number;
  status: SeatStatus;
}

export interface SectionSeatMap {
  sectionId: number;
  sectionName: string;
  sectionCode: string;
  tierLevel: number;
  totalSeats: number;
  availableSeats: number;
  seats: SeatItem[];
}

export interface EventSeatMatrix {
  eventId: number;
  eventTitle: string;
  venueName: string;
  totalSeats: number;
  availableSeats: number;
  heldSeats: number;
  bookedSeats: number;
  sections: SectionSeatMap[];
}

export interface SeatHoldPayload {
  eventId: number;
  eventSeatIds: number[];
  holdToken?: string;
}

export interface SeatHoldResult {
  holdToken: string;
  eventId: number;
  heldSeatIds: number[];
  expiresAt: string;
  ttlSecondsRemaining: number;
}

export interface SeatReleasePayload {
  eventId: number;
  holdToken: string;
  eventSeatIds?: number[];
}

export interface SeatReleaseResult {
  eventId: number;
  releasedSeatIds: number[];
  releasedCount: number;
}

export interface CreateBookingPayload {
  eventId: number;
  eventSeatIds: number[];
  holdToken: string;
  idempotencyKey: string;
  paymentMethod?: string;
}

export interface BookingLineItem {
  itemId: number;
  eventSeatId: number;
  seatId: number;
  sectionName: string;
  rowLabel: string;
  seatNumber: number;
  unitPrice: number;
  status: string;
}

export interface BookingDetail {
  bookingId: number;
  bookingReference: string;
  eventId: number;
  eventTitle: string;
  venueName: string;
  userId: number;
  customerEmail: string;
  totalAmount: number;
  currency: string;
  status: 'PENDING' | 'CONFIRMED' | 'CANCELLED';
  idempotencyKey: string;
  createdAt: string;
  items: BookingLineItem[];
}

export interface SeatUpdateWsMessage {
  messageId: string;
  eventId: number;
  seatId?: number;
  seatIds?: number[];
  status: SeatStatus;
  timestamp: string;
}

// AI Assistant Types
export interface AiChatPayload {
  message: string;
}

export interface ExtractedEventFilters {
  intent?: string;
  category?: string | null;
  city?: string | null;
  keyword?: string | null;
  maxBudget?: number | null;
  minBudget?: number | null;
  timeframe?: string | null;
}

export interface AiEventCard {
  id: number;
  title: string;
  slug: string;
  shortDescription: string;
  bannerImageUrl?: string | null;
  startTime: string;
  basePrice: number;
  categoryName?: string;
  categorySlug?: string;
  venueName?: string;
  city?: string;
}

export interface AiChatResponseData {
  response: string;
  intent: string;
  extractedFilters: ExtractedEventFilters;
  events: AiEventCard[];
  totalMatches: number;
  grounded: boolean;
  liveGeminiUsed: boolean;
  provider?: string;
}

