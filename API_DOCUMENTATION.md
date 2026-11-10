# SeatSync AI – REST API & WebSocket Specifications

## 1. Global API Conventions

### 1.1 Base URL & Content Negotiation
- **Base URL:** `/api/v1` (or `/api`)
- **Headers:**
  - `Content-Type: application/json`
  - `Accept: application/json`
  - `Authorization: Bearer <JWT_ACCESS_TOKEN>`

### 1.2 Standard Response Envelope (`ApiResponse<T>`)
All endpoints (except raw binary/file streams) return a unified payload envelope:

```json
{
  "success": true,
  "message": "Operation completed successfully",
  "data": { ... },
  "timestamp": "2026-11-07T11:00:00Z"
}
```

### 1.3 Standard Error Response Envelope
```json
{
  "success": false,
  "message": "Validation failed / Resource not found",
  "error": {
    "code": "SEAT_ALREADY_HELD",
    "details": [
      { "field": "seatIds", "reason": "Seat 102 is currently locked by another user" }
    ]
  },
  "timestamp": "2026-11-07T11:00:00Z"
}
```

---

## 2. Authentication & User Profile APIs

### 2.1 Register New User
- **`POST /api/auth/register`**
- **Auth:** Public
- **Request Body:**
```json
{
  "email": "manik@example.com",
  "password": "SecurePassword123!",
  "fullName": "Manikanta Sharma",
  "phoneNumber": "+919876543210",
  "role": "ROLE_CUSTOMER"
}
```
- **Response (201 Created):**
```json
{
  "success": true,
  "message": "User registered successfully",
  "data": {
    "userId": 1,
    "email": "manik@example.com",
    "fullName": "Manikanta Sharma",
    "roles": ["ROLE_CUSTOMER"]
  }
}
```

### 2.2 User Login
- **`POST /api/auth/login`**
- **Auth:** Public
- **Request Body:**
```json
{
  "email": "manik@example.com",
  "password": "SecurePassword123!"
}
```
- **Response (200 OK):**
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsIn...",
    "refreshToken": "d8f3e2b1-4c7a-...",
    "expiresIn": 900,
    "user": {
      "id": 1,
      "email": "manik@example.com",
      "fullName": "Manikanta Sharma",
      "roles": ["ROLE_CUSTOMER"]
    }
  }
}
```

### 2.3 Get Current User Profile
- **`GET /api/auth/me`**
- **Auth:** Bearer Token required
- **Response (200 OK):** Current user entity + preferences.

---

## 3. Events & Venues APIs

### 3.1 Discover Events (Search & Filter)
- **`GET /api/events`**
- **Query Parameters:**
  - `category` (optional, e.g. "concerts")
  - `city` (optional, e.g. "Hyderabad")
  - `minPrice`, `maxPrice` (optional)
  - `startDate`, `endDate` (optional, ISO format)
  - `page` (default: 0), `size` (default: 12), `sort` (default: "startTime,asc")
- **Response (200 OK):** Paginated event summaries.

### 3.2 Get Event Details by ID
- **`GET /api/events/{id}`**
- **Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "id": 10,
    "title": "Arijit Singh Symphony Night 2026",
    "slug": "arijit-singh-symphony-hyderabad-2026",
    "shortDescription": "Experience musical grandeur live under the open sky.",
    "category": { "id": 1, "name": "Concerts", "slug": "concerts" },
    "venue": {
      "id": 4,
      "name": "Gachibowli Stadium",
      "city": "Hyderabad",
      "state": "Telangana"
    },
    "startTime": "2026-11-14T19:00:00Z",
    "endTime": "2026-11-14T23:00:00Z",
    "basePrice": 999.00,
    "bannerImageUrl": "https://images.unsplash.com/...",
    "status": "PUBLISHED"
  }
}
```

### 3.3 Create Event (Admin / Organizer)
- **`POST /api/admin/events`**
- **Auth:** `ROLE_ADMIN` or `ROLE_ORGANIZER`

---

## 4. Seat Management & High-Concurrency Reservation APIs

### 4.1 Get Real-Time Event Seat Matrix
- **`GET /api/events/{id}/seats`**
- **Auth:** Public / Customer
- **Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "eventId": 10,
    "sections": [
      {
        "sectionId": 1,
        "name": "Platinum Center",
        "tierLevel": 1,
        "price": 1999.00,
        "seats": [
          {
            "eventSeatId": 501,
            "seatId": 101,
            "rowLabel": "A",
            "seatNumber": 1,
            "status": "AVAILABLE",
            "gridX": 10,
            "gridY": 2
          },
          {
            "eventSeatId": 502,
            "seatId": 102,
            "rowLabel": "A",
            "seatNumber": 2,
            "status": "HELD",
            "heldUntil": "2026-11-07T11:04:30Z",
            "gridX": 11,
            "gridY": 2
          }
        ]
      }
    ]
  }
}
```

### 4.2 Temporary Hold Seats (5-Minute Expiry Lock)
- **`POST /api/bookings/hold`**
- **Auth:** Authenticated User
- **Request Body:**
```json
{
  "eventId": 10,
  "eventSeatIds": [501, 502],
  "idempotencyKey": "hold-req-8a9d1c"
}
```
- **Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Seats locked for 5 minutes",
  "data": {
    "heldSeatIds": [501, 502],
    "expiresAt": "2026-11-07T11:05:00Z",
    "ttlSecondsRemaining": 300
  }
}
```
- **Conflict Response (409 Conflict):**
```json
{
  "success": false,
  "message": "One or more requested seats are no longer available",
  "error": {
    "code": "SEATS_ALREADY_RESERVED",
    "unavailableSeatIds": [502]
  }
}
```

### 4.3 Release Held Seats Manually
- **`DELETE /api/bookings/hold/{eventId}`**
- **Auth:** Authenticated User (releases all seats held by the current user for this event).
- **Request Body:**
```json
{
  "holdToken": "e5b8d2a1-7c4f-4d9a-9e12-3f8c7b6a5d4e"
}
```
- **Response (200 OK):** Releases holds via atomic Compare-and-Delete (CAD) Lua script and clears MySQL hold state.

### 4.4 Expired-Seat-Hold Reconciliation Protocol (Safe 9-Step Sequence)
When temporary seat holds expire (5 minutes) without checkout confirmation, the background reconciliation sweeper (`@Scheduled(fixedDelay = 15000)`) safely releases them using the following strictly ordered 9-step sequence:

1. **Start Transaction & Row Lock:** Start a MySQL transaction and lock the relevant `event_seats` row using `SELECT ... FOR UPDATE`:
   ```sql
   SELECT id, status, hold_token, held_until, held_by_user_id 
   FROM event_seats 
   WHERE id = :seatId 
   FOR UPDATE;
   ```
2. **Verify Status:** Verify that the seat is still `HELD`.
3. **Verify Hold Token:** Verify that `hold_token` matches the original expected token.
4. **Verify Timestamp Expiry:** Verify that `held_until <= NOW()`.
5. **Conditional State Reset:** If all conditions match, conditionally update the seat to `AVAILABLE` and clear the old hold information:
   ```sql
   UPDATE event_seats 
   SET status = 'AVAILABLE',
       held_by_user_id = NULL,
       held_until = NULL,
       hold_token = NULL,
       version = version + 1
   WHERE id = :seatId AND hold_token = :expectedHoldToken;
   ```
6. **Commit Transaction:** Commit the MySQL transaction.
7. **Atomic Redis CAD:** Execute Redis atomic compare-and-delete using the original expected hold token (`ARGV[1] = expectedHoldToken`).
8. **Preserve Newer Holds:** If Redis contains a different token, do not delete it. It may belong to a newer hold.
9. **Rollback & Retry on Condition Mismatch:** If the MySQL conditions do not match (e.g. the seat was booked, extended, or claimed with a different token), do not reset the seat or delete the Redis key. Roll back, re-read latest state, and retry safely if appropriate.

#### Concurrency Protection & Invariant Guarantees:
- **MySQL Row Locking (`FOR UPDATE`):** Prevents concurrent transactions (such as a late checkout or a competing hold) from modifying the same seat simultaneously during reconciliation.
- **Expected-Token Condition:** Stops a slow or delayed sweeper operation from wiping a newer user's hold that was acquired after an earlier expiration.
- **Redis Compare-and-Delete (CAD):** Prevents blind deletes by ensuring Redis keys are only deleted if the key currently holds the exact expired token.
- **MySQL Remains Authoritative:** MySQL is the single persistent source of truth. Even if Redis operations fail, time out, or lose network connectivity, MySQL's committed state prevents double-booking or inventory corruption.
- **Zero Blind Deletions:** Blind `DEL` calls are strictly prohibited across the entire platform.

---

## 5. Booking Checkout & Payment APIs

### 5.1 Confirm Booking
- **`POST /api/bookings`**
- **Auth:** Authenticated User
- **Request Body:**
```json
{
  "eventId": 10,
  "eventSeatIds": [501],
  "idempotencyKey": "chk-99120-x2",
  "paymentMethod": "CREDIT_CARD"
}
```
- **Response (201 Created):**
```json
{
  "success": true,
  "message": "Booking created and waiting for payment",
  "data": {
    "bookingId": 45,
    "bookingReference": "SS-2026-Q9A18B",
    "totalAmount": 1999.00,
    "status": "PENDING",
    "items": [
      { "eventSeatId": 501, "row": "A", "seat": 1, "price": 1999.00 }
    ]
  }
}
```
*Note on Consistency & Non-Authoritative Redis: Confirmation executes within an atomic MySQL transaction using InnoDB row locking (`SELECT ... FOR UPDATE`) or an atomic conditional CAS update (`UPDATE event_seats SET status = 'BOOKED', current_booking_id = :bookingId, held_by_user_id = NULL, held_until = NULL, hold_token = NULL WHERE id IN (:seatIds) AND status = 'HELD' AND held_by_user_id = :userId AND held_until > NOW() AND current_booking_id IS NULL`). If the affected row count does not strictly equal the requested seat count, the entire transaction rolls back immediately (zero partial bookings). Redis is strictly non-authoritative; if Redis deletion fails post-commit, the key is asynchronously reconciled by the background reaper using observed-token atomic compare-and-delete (never blind DEL) while MySQL's committed state guarantees zero double-bookings.*

### 5.2 Execute Mock Payment & Finalize Tickets
- **`POST /api/payments/mock`**
- **Request Body:**
```json
{
  "bookingId": 45,
  "amount": 1999.00,
  "idempotencyKey": "pay-uuid-7712",
  "simulateFailure": false
}
```
- **Response (200 OK):**
```json
{
  "success": true,
  "message": "Payment successful. Tickets generated.",
  "data": {
    "paymentReference": "PAY-MOCK-99102",
    "bookingReference": "SS-2026-Q9A18B",
    "status": "SUCCESS",
    "tickets": [
      {
        "ticketNumber": "TCK-2026-9901-A1",
        "qrCodeData": "seatsync://verify?token=eyJhbGciOi...",
        "seatLabel": "Platinum Center - Row A Seat 1"
      }
    ]
  }
}
```

### 5.3 Cancel Booking & Release Seats for Rebooking
- **`POST /api/bookings/{id}/cancel`**
- **Auth:** Authenticated User (must own the booking or have `ROLE_ADMIN`)
- **Request Body:**
```json
{
  "reason": "Customer requested cancellation within 48h window",
  "idempotencyKey": "cancel-req-8891-b"
}
```
- **Response (200 OK):**
```json
{
  "success": true,
  "message": "Booking cancelled successfully. Refund initiated.",
  "data": {
    "bookingId": 45,
    "bookingReference": "SS-2026-Q9A18B",
    "status": "CANCELLED",
    "refundAmount": 1999.00,
    "releasedSeatIds": [501],
    "cancelledAt": "2026-11-07T11:20:00Z"
  }
}
```
*Note: This transaction marks the booking as `CANCELLED`, cancels its tickets, preserves `booking_items` with status `CANCELLED` for auditing, resets `event_seats` to `AVAILABLE`, sets `current_booking_id = NULL`, and immediately broadcasts `status: 'AVAILABLE'` to `/topic/events/{eventId}/seats` so other customers can book the seat instantly.*

### 5.4 Get User Bookings & Tickets
- **`GET /api/bookings/my`**: Lists current and past bookings for the authenticated user.
- **`GET /api/bookings/{id}`**: Returns full booking invoice, ticket references, and line item statuses.
- **`GET /api/tickets/{ticketNumber}/qr`**: Returns generated PNG QR code image or base64 verification data.

---

## 6. Spring AI & Generative AI APIs

*Note: All AI capabilities are powered by **Spring AI 1.0.0 (GA)** and Google Gemini using the supported production model **`gemini-3.5-flash`** via `spring-ai-starter-model-google-genai`.*

### 6.1 Conversational AI Booking Assistant
- **`POST /api/ai/chat`**
- **Auth:** Authenticated / Guest (Session-isolated)
- **Request Body:**
```json
{
  "sessionId": "sess-user-1-9871",
  "message": "Find two concert tickets in Hyderabad this Saturday under ₹1,000."
}
```
- **Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "reply": "I found 2 great concert events in Hyderabad this Saturday under ₹1,000 for you!",
    "intent": "EVENT_SEARCH",
    "extractedParameters": {
      "city": "Hyderabad",
      "category": "Concerts",
      "budget": 1000.00,
      "date": "2026-11-14"
    },
    "matchedEvents": [
      {
        "id": 10,
        "title": "Indie Acoustic Sunset Live",
        "venue": "Gachibowli Amphitheatre, Hyderabad",
        "date": "Saturday, Nov 14, 2026",
        "price": 699.00,
        "availableSeats": 42
      }
    ],
    "suggestedActions": [
      { "label": "View Available Seats", "action": "NAVIGATE_SEATMAP", "eventId": 10 }
    ]
  }
}
```

### 6.2 Semantic Event Search (Vector Cosine Distance)
- **`POST /api/ai/search`**
- **Request Body:**
```json
{
  "query": "peaceful acoustic music events suitable for couples",
  "limit": 5
}
```
- **Response (200 OK):** List of events ranked by semantic similarity score with explanation tags.

### 6.3 Smart Adjacent Seat Recommendation
- **`POST /api/ai/seats/recommend`**
- **Request Body:**
```json
{
  "eventId": 10,
  "seatCount": 3,
  "maxBudgetPerSeat": 1500.00,
  "preferredArea": "MIDDLE_ROWS"
}
```
- **Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "recommendedClusters": [
      {
        "section": "Gold Center",
        "rowLabel": "C",
        "seats": [12, 13, 14],
        "pricePerSeat": 1200.00,
        "totalPrice": 3600.00,
        "score": 0.94,
        "aiRationale": "Rows C seats 12-14 in Gold Center give you contiguous seating with clear, unobstructed stage sightlines well below your ₹1,500 limit."
      }
    ]
  }
}
```

### 6.4 RAG-Grounded Customer Support
- **`POST /api/ai/support`**
- **Request Body:**
```json
{
  "question": "What happens if a concert is cancelled due to rain?"
}
```
- **Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "answer": "According to Section 4.2 of the Event Cancellation Policy, if an outdoor event is officially cancelled due to severe weather conditions, all confirmed tickets are automatically refunded in full to the original payment method within 5-7 business days.",
    "citations": [
      { "document": "SeatSync Cancellation & Weather Policy.md", "section": "Section 4.2" }
    ]
  }
}
```

---

## 7. Real-Time WebSocket Specifications (STOMP over SockJS)

### 7.1 Broker Configuration
- **Handshake Endpoint:** `/ws-seatsync`
- **Application Destination Prefix:** `/app`
- **Simple Message Broker Destinations:** `/topic`, `/queue`

### 7.2 Subscriptions

#### 1. Public Event Seat Map Stream
- **Topic:** `/topic/events/{eventId}/seats`
- **Incoming Broadcast Event:**
```json
{
  "eventId": 10,
  "seatId": 501,
  "status": "HELD",
  "heldUntil": "2026-11-07T11:05:00Z"
}
```

#### 2. Private User Notifications Queue
- **Topic:** `/user/queue/notifications`
- **Incoming Broadcast Event:**
```json
{
  "type": "HOLD_EXPIRING_SOON",
  "message": "Your hold on Seat A1 & A2 expires in 60 seconds. Complete checkout to avoid release.",
  "secondsRemaining": 60
}
```

#### 3. Real-Time Admin Metrics Stream
- **Topic:** `/topic/admin/analytics`
- **Payload:** Real-time ticket sales counter, live revenue increments, active concurrent users.

