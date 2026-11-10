import React, { useEffect, useState, useRef, useCallback } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import {
  ArrowLeft,
  Clock,
  Ticket,
  CheckCircle2,
  AlertCircle,
  Wifi,
  WifiOff,
  RefreshCw,
  Lock,
  CreditCard,
  ChevronRight,
  ShieldCheck,
  X,
} from 'lucide-react';
import { bookingApi } from '../api/bookingApi';
import { eventApi } from '../api/eventApi';
import { getApiErrorMessage } from '../api/client';
import {
  EventItem,
  EventSeatMatrix,
  SeatItem,
  SeatHoldResult,
  BookingDetail,
  SeatUpdateWsMessage,
} from '../types';
import { useAuth } from '../context/AuthContext';
import { useSeatWebSocket } from '../hooks/useSeatWebSocket';
import { LoadingSpinner } from '../components/common/LoadingSpinner';

export const SeatSelectionPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const eventId = Number(id);
  const navigate = useNavigate();
  const { isAuthenticated, user, loading: authLoading } = useAuth();

  // Core Data States
  const [event, setEvent] = useState<EventItem | null>(null);
  const [seatMatrix, setSeatMatrix] = useState<EventSeatMatrix | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Selection States
  const [selectedSeatIds, setSelectedSeatIds] = useState<number[]>([]);

  // Hold Session States
  const [activeHold, setActiveHold] = useState<SeatHoldResult | null>(null);
  const [isHolding, setIsHolding] = useState<boolean>(false);
  const [holdError, setHoldError] = useState<string | null>(null);
  const [secondsRemaining, setSecondsRemaining] = useState<number>(0);

  // Checkout States
  const [isCheckingOut, setIsCheckingOut] = useState<boolean>(false);
  const [checkoutError, setCheckoutError] = useState<string | null>(null);
  const [confirmedBooking, setConfirmedBooking] = useState<BookingDetail | null>(null);

  // Persistent Idempotency Key per checkout attempt
  const idempotencyKeyRef = useRef<string>(crypto.randomUUID());

  // Ref to hold token for window unload cleanup
  const activeHoldRef = useRef<SeatHoldResult | null>(null);
  activeHoldRef.current = activeHold;

  // 1. Initial Data Fetch
  const loadSeatData = useCallback(async () => {
    if (!eventId || isNaN(eventId)) return;

    try {
      setError(null);
      const [eventData, matrixData] = await Promise.all([
        eventApi.getEventById(eventId),
        bookingApi.getSeatMatrix(eventId),
      ]);
      setEvent(eventData);
      setSeatMatrix(matrixData);
    } catch (err) {
      setError(getApiErrorMessage(err, 'Failed to load event seating layout.'));
    } finally {
      setLoading(false);
    }
  }, [eventId]);

  useEffect(() => {
    loadSeatData();
  }, [loadSeatData]);

  // 2. Real-time WebSocket Updates Handler
  const handleLiveSeatUpdate = useCallback((message: SeatUpdateWsMessage) => {
    if (message.eventId !== eventId) return;

    const affectedIds = new Set<number>(
      message.seatIds && message.seatIds.length > 0
        ? message.seatIds
        : message.seatId
        ? [message.seatId]
        : []
    );

    if (affectedIds.size === 0) return;

    setSeatMatrix((prevMatrix) => {
      if (!prevMatrix) return prevMatrix;

      const updatedSections = prevMatrix.sections.map((section) => ({
        ...section,
        seats: section.seats.map((seat) => {
          if (affectedIds.has(seat.eventSeatId)) {
            return {
              ...seat,
              status: message.status,
            };
          }
          return seat;
        }),
      }));

      // Recompute counters
      let totalAvailable = 0;
      let totalHeld = 0;
      let totalBooked = 0;

      updatedSections.forEach((sec) => {
        sec.seats.forEach((s) => {
          if (s.status === 'AVAILABLE') totalAvailable++;
          else if (s.status === 'HELD') totalHeld++;
          else if (s.status === 'BOOKED') totalBooked++;
        });
      });

      return {
        ...prevMatrix,
        availableSeats: totalAvailable,
        heldSeats: totalHeld,
        bookedSeats: totalBooked,
        sections: updatedSections,
      };
    });

    // If an external update changed a currently selected seat away from available, remove it
    if (message.status !== 'AVAILABLE') {
      setSelectedSeatIds((prevSelected) => {
        const myHeldIds = new Set(activeHoldRef.current?.heldSeatIds || []);
        const stillValid = prevSelected.filter(
          (id) => !affectedIds.has(id) || myHeldIds.has(id)
        );
        if (stillValid.length < prevSelected.length && !activeHoldRef.current) {
          setHoldError('One of your selected seats was just reserved by another user.');
        }
        return stillValid;
      });
    }
  }, [eventId]);

  // 3. WebSocket Integration with Auto-Reconnect Recovery
  const { isConnected, connectionError } = useSeatWebSocket({
    eventId,
    onSeatUpdate: handleLiveSeatUpdate,
    onReconnect: loadSeatData,
  });

  // 4. Countdown Timer for Active Hold
  useEffect(() => {
    if (!activeHold) {
      setSecondsRemaining(0);
      return;
    }

    const targetTime = new Date(activeHold.expiresAt).getTime();

    const updateTimer = () => {
      const diff = Math.max(0, Math.floor((targetTime - Date.now()) / 1000));
      setSecondsRemaining(diff);

      if (diff <= 0) {
        // Hold expired
        setActiveHold(null);
        setSelectedSeatIds([]);
        setHoldError('Your 5-minute hold has expired. The seats have been returned to available inventory.');
        loadSeatData();
      }
    };

    updateTimer();
    const interval = setInterval(updateTimer, 1000);
    return () => clearInterval(interval);
  }, [activeHold, loadSeatData]);

  // 5. Cleanup Hold on Navigation Away / Window Close
  const releaseHoldSafely = useCallback(async (hold: SeatHoldResult) => {
    try {
      await bookingApi.releaseSeats({
        eventId: hold.eventId,
        holdToken: hold.holdToken,
        eventSeatIds: hold.heldSeatIds,
      });
    } catch (err) {
      console.warn('Failed to release hold on exit', err);
    }
  }, []);

  useEffect(() => {
    return () => {
      if (activeHoldRef.current) {
        releaseHoldSafely(activeHoldRef.current);
      }
    };
  }, [releaseHoldSafely]);

  // 6. Seat Click Handler
  const handleSeatClick = (seat: SeatItem) => {
    if (authLoading) return;

    if (!isAuthenticated) {
      navigate('/login', { state: { from: { pathname: `/events/${eventId}/seats` } } });
      return;
    }

    if (activeHold) {
      // Cannot modify selection while an active hold is in place
      return;
    }

    if (seat.status !== 'AVAILABLE') {
      return;
    }

    setHoldError(null);
    setCheckoutError(null);

    setSelectedSeatIds((prev) => {
      if (prev.includes(seat.eventSeatId)) {
        return prev.filter((id) => id !== seat.eventSeatId);
      }
      if (prev.length >= 8) {
        setHoldError('You can select a maximum of 8 seats per booking.');
        return prev;
      }
      return [...prev, seat.eventSeatId];
    });
  };

  // 7. Request Seat Hold
  const handleAcquireHold = async () => {
    if (!isAuthenticated) {
      navigate('/login', { state: { from: { pathname: `/events/${eventId}/seats` } } });
      return;
    }

    if (selectedSeatIds.length === 0) {
      setHoldError('Please select at least one seat to reserve.');
      return;
    }

    try {
      setIsHolding(true);
      setHoldError(null);
      setCheckoutError(null);

      const result = await bookingApi.holdSeats({
        eventId,
        eventSeatIds: selectedSeatIds,
      });

      setActiveHold(result);
      // Reset idempotency key for new checkout attempt
      idempotencyKeyRef.current = crypto.randomUUID();
    } catch (err) {
      setHoldError(getApiErrorMessage(err, 'Failed to reserve selected seats. They may have just been taken.'));
      loadSeatData();
    } finally {
      setIsHolding(false);
    }
  };

  // 8. Manual Release of Current Hold
  const handleCancelHold = async () => {
    if (!activeHold) return;

    try {
      await releaseHoldSafely(activeHold);
    } finally {
      setActiveHold(null);
      setSelectedSeatIds([]);
      setHoldError(null);
      setCheckoutError(null);
      loadSeatData();
    }
  };

  // 9. Confirm Checkout
  const handleCheckout = async () => {
    if (!activeHold) {
      setCheckoutError('Active seat hold session required to checkout.');
      return;
    }

    try {
      setIsCheckingOut(true);
      setCheckoutError(null);

      const booking = await bookingApi.createBooking({
        eventId,
        eventSeatIds: activeHold.heldSeatIds,
        holdToken: activeHold.holdToken,
        idempotencyKey: idempotencyKeyRef.current,
        paymentMethod: 'MOCK_CARD_VALID',
      });

      // Clear hold tracking ref so unmount doesn't release committed seats
      activeHoldRef.current = null;
      setActiveHold(null);
      setConfirmedBooking(booking);
    } catch (err) {
      setCheckoutError(getApiErrorMessage(err, 'Booking checkout failed. Please retry.'));
    } finally {
      setIsCheckingOut(false);
    }
  };

  // Selected Seat Objects & Pricing
  const allSeats = seatMatrix?.sections.flatMap((s) => s.seats) || [];
  // When activeHold exists, display seats using activeHold.heldSeatIds; otherwise use selectedSeatIds
  const activeDisplaySeatIds = activeHold ? activeHold.heldSeatIds : selectedSeatIds;
  const selectedSeatObjects = allSeats.filter((s) => activeDisplaySeatIds.includes(s.eventSeatId));
  const totalPrice = selectedSeatObjects.reduce((sum, s) => sum + Number(s.price), 0);

  // Format timer MM:SS
  const formatTimer = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  // Section Styling Helper
  const getSectionBadgeColor = (sectionCode: string) => {
    switch (sectionCode.toUpperCase()) {
      case 'VIP':
        return 'text-amber-400 bg-amber-500/10 border-amber-500/20';
      case 'PLATINUM':
        return 'text-cyan-400 bg-cyan-500/10 border-cyan-500/20';
      case 'GOLD':
      default:
        return 'text-emerald-400 bg-emerald-500/10 border-emerald-500/20';
    }
  };

  if (loading) {
    return (
      <div className="min-h-[70vh] flex items-center justify-center">
        <LoadingSpinner message="Loading live venue seating layout..." size="lg" />
      </div>
    );
  }

  if (error || !seatMatrix || !event) {
    return (
      <div className="max-w-2xl mx-auto px-4 py-20 text-center">
        <div className="bg-rose-950/40 border border-rose-800/60 rounded-3xl p-8 space-y-4">
          <AlertCircle className="w-12 h-12 text-rose-400 mx-auto" />
          <h2 className="text-xl font-bold text-white">Seating Unavailable</h2>
          <p className="text-sm text-slate-300">{error || 'Could not load seating data.'}</p>
          <button
            onClick={() => loadSeatData()}
            className="inline-flex items-center space-x-2 px-5 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-medium text-sm transition"
          >
            <RefreshCw className="w-4 h-4" />
            <span>Retry</span>
          </button>
        </div>
      </div>
    );
  }

  // Confirmation Success View
  if (confirmedBooking) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-16">
        <div className="bg-slate-900 border border-slate-800 rounded-3xl p-8 sm:p-12 text-center space-y-8 shadow-2xl">
          <div className="w-20 h-20 rounded-full bg-emerald-500/10 border border-emerald-500/30 flex items-center justify-center mx-auto text-emerald-400 animate-bounce">
            <CheckCircle2 className="w-10 h-10" />
          </div>

          <div className="space-y-2">
            <span className="text-xs font-semibold text-emerald-400 uppercase tracking-widest">
              Booking Confirmed
            </span>
            <h1 className="text-3xl font-extrabold text-white">You're Going to the Show!</h1>
            <p className="text-sm text-slate-400">
              Your tickets have been confirmed and reserved in the database.
            </p>
          </div>

          <div className="bg-slate-950/80 border border-slate-800/80 rounded-2xl p-6 text-left space-y-4 max-w-lg mx-auto">
            <div className="flex justify-between items-center pb-4 border-b border-slate-800/80">
              <span className="text-xs text-slate-400 font-medium">Booking Reference</span>
              <span className="text-sm font-mono font-bold text-indigo-400">
                {confirmedBooking.bookingReference}
              </span>
            </div>

            <div className="flex justify-between items-center pb-4 border-b border-slate-800/80">
              <span className="text-xs text-slate-400 font-medium">Event</span>
              <span className="text-sm font-semibold text-white truncate max-w-[240px]">
                {confirmedBooking.eventTitle}
              </span>
            </div>

            <div className="flex justify-between items-center pb-4 border-b border-slate-800/80">
              <span className="text-xs text-slate-400 font-medium">Venue</span>
              <span className="text-sm text-slate-300">{confirmedBooking.venueName}</span>
            </div>

            <div className="flex justify-between items-start pb-4 border-b border-slate-800/80">
              <span className="text-xs text-slate-400 font-medium">Seats Confirmed</span>
              <div className="text-right">
                <div className="flex flex-wrap gap-1 justify-end max-w-[240px]">
                  {confirmedBooking.items.map((item) => (
                    <span
                      key={item.itemId}
                      className="px-2 py-0.5 rounded bg-indigo-500/20 text-indigo-300 text-xs font-mono font-semibold"
                    >
                      {item.sectionName} - Row {item.rowLabel}{item.seatNumber}
                    </span>
                  ))}
                </div>
              </div>
            </div>

            <div className="flex justify-between items-center pt-1">
              <span className="text-sm font-semibold text-white">Total Amount Paid</span>
              <span className="text-xl font-extrabold text-white">
                ₹{Number(confirmedBooking.totalAmount).toLocaleString('en-IN')}
              </span>
            </div>
          </div>

          <div className="flex flex-col sm:flex-row items-center justify-center gap-4 pt-4">
            <Link
              to="/my-bookings"
              className="w-full sm:w-auto px-6 py-3.5 rounded-2xl bg-indigo-600 hover:bg-indigo-500 text-white font-semibold text-sm transition flex items-center justify-center space-x-2 shadow-lg shadow-indigo-600/30"
            >
              <Ticket className="w-4 h-4" />
              <span>View Booking History</span>
            </Link>

            <Link
              to="/events"
              className="w-full sm:w-auto px-6 py-3.5 rounded-2xl bg-slate-800 hover:bg-slate-700 text-slate-300 font-semibold text-sm transition"
            >
              Explore More Events
            </Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
      {/* Top Header Bar */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-slate-800 pb-6">
        <div>
          <Link
            to={`/events/${eventId}`}
            className="inline-flex items-center space-x-2 text-indigo-400 hover:text-indigo-300 text-sm font-medium mb-2 transition"
          >
            <ArrowLeft className="w-4 h-4" />
            <span>Back to Event Details</span>
          </Link>
          <h1 className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight">
            {event.title}
          </h1>
          <p className="text-sm text-slate-400 mt-1">
            {seatMatrix.venueName} • Real-Time Seating Layout
          </p>
        </div>

        {/* Live WebSocket Status Pill */}
        <div className="flex items-center space-x-3">
          <div
            className={`inline-flex items-center space-x-2 px-3 py-1.5 rounded-full text-xs font-semibold border ${
              isConnected
                ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20'
                : 'bg-amber-500/10 text-amber-400 border-amber-500/20'
            }`}
          >
            {isConnected ? (
              <>
                <span className="w-2 h-2 rounded-full bg-emerald-400 animate-ping" />
                <Wifi className="w-3.5 h-3.5" />
                <span>Live Sync Active</span>
              </>
            ) : (
              <>
                <WifiOff className="w-3.5 h-3.5" />
                <span>{connectionError ? 'Syncing...' : 'Connecting Live...'}</span>
              </>
            )}
          </div>

          <button
            onClick={() => loadSeatData()}
            title="Refresh Seating"
            className="p-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-400 hover:text-white transition"
          >
            <RefreshCw className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Main Grid: Layout (Left) and Sticky Checkout Panel (Right) */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
        {/* Left Column: Stage & Seating Map (8 Cols) */}
        <div className="lg:col-span-8 space-y-6">
          {/* Seating Legend Bar */}
          <div className="bg-slate-900/60 border border-slate-800 rounded-2xl p-4 flex flex-wrap items-center justify-center sm:justify-between gap-4 text-xs font-medium text-slate-300">
            <div className="flex items-center space-x-2">
              <div className="w-5 h-5 rounded-lg bg-slate-800 border-2 border-emerald-500/60" />
              <span>Available</span>
            </div>
            <div className="flex items-center space-x-2">
              <div className="w-5 h-5 rounded-lg bg-indigo-600 border-2 border-indigo-400 flex items-center justify-center text-white text-[10px]">
                ✓
              </div>
              <span>Selected</span>
            </div>
            <div className="flex items-center space-x-2">
              <div className="w-5 h-5 rounded-lg bg-amber-500/30 border border-amber-500/60 flex items-center justify-center text-amber-400">
                <Clock className="w-3 h-3" />
              </div>
              <span>Held</span>
            </div>
            <div className="flex items-center space-x-2">
              <div className="w-5 h-5 rounded-lg bg-slate-800/80 border border-slate-700/60 text-slate-600 flex items-center justify-center font-bold">
                ✕
              </div>
              <span>Booked</span>
            </div>
          </div>

          {/* Seating Stage Visual Container */}
          <div className="bg-slate-900 border border-slate-800 rounded-3xl p-6 sm:p-8 space-y-8 overflow-x-auto shadow-xl">
            {/* Stage Arch */}
            <div className="relative text-center py-4">
              <div className="h-2 w-3/4 max-w-md mx-auto rounded-full bg-gradient-to-r from-indigo-500 via-sky-400 to-indigo-500 shadow-[0_0_20px_rgba(99,102,241,0.5)]" />
              <span className="text-[11px] font-bold text-slate-400 tracking-[0.25em] uppercase block mt-2">
                STAGE / PERFORMANCE AREA
              </span>
            </div>

            {/* Sections Loop */}
            <div className="space-y-10">
              {seatMatrix.sections.map((section) => {
                // Group seats by rowLabel
                const rowMap: Record<string, SeatItem[]> = {};
                section.seats.forEach((seat) => {
                  if (!rowMap[seat.rowLabel]) rowMap[seat.rowLabel] = [];
                  rowMap[seat.rowLabel].push(seat);
                });

                const sortedRows = Object.keys(rowMap).sort();

                return (
                  <div key={section.sectionId} className="space-y-4">
                    <div className="flex items-center justify-between border-b border-slate-800 pb-2">
                      <div className="flex items-center space-x-3">
                        <span
                          className={`text-xs font-bold px-3 py-1 rounded-full border ${getSectionBadgeColor(
                            section.sectionCode
                          )}`}
                        >
                          {section.sectionName}
                        </span>
                        <span className="text-xs text-slate-400">
                          {section.availableSeats} of {section.totalSeats} seats available
                        </span>
                      </div>
                      <span className="text-xs font-semibold text-slate-300">
                        ₹{Number(section.seats[0]?.price || event.basePrice).toLocaleString('en-IN')}
                      </span>
                    </div>

                    {/* Rows Container */}
                    <div className="flex flex-col items-center space-y-2.5 overflow-x-auto py-2">
                      {sortedRows.map((rowLabel) => {
                        const seatsInRow = rowMap[rowLabel].sort((a, b) => a.seatNumber - b.seatNumber);

                        return (
                          <div key={rowLabel} className="flex items-center space-x-2">
                            <span className="w-5 text-right text-xs font-bold text-slate-400 select-none">
                              {rowLabel}
                            </span>

                            <div className="flex items-center space-x-1.5 sm:space-x-2">
                              {seatsInRow.map((seat) => {
                                const isSelected = activeDisplaySeatIds.includes(seat.eventSeatId);
                                const isHeld = seat.status === 'HELD';
                                const isBooked = seat.status === 'BOOKED';
                                const isAvailable = seat.status === 'AVAILABLE';

                                let buttonStyles =
                                  'w-7 h-7 sm:w-8 sm:h-8 rounded-lg text-[11px] font-bold transition flex items-center justify-center select-none ';

                                if (isSelected) {
                                  buttonStyles +=
                                    'bg-indigo-600 text-white border-2 border-indigo-300 shadow-md shadow-indigo-600/50 scale-105';
                                } else if (isAvailable) {
                                  buttonStyles +=
                                    'bg-slate-800/90 text-emerald-400 border border-emerald-500/50 hover:bg-emerald-500/20 hover:border-emerald-400 cursor-pointer active:scale-95';
                                } else if (isHeld) {
                                  buttonStyles +=
                                    'bg-amber-950/40 text-amber-400 border border-amber-600/40 cursor-not-allowed opacity-80';
                                } else {
                                  // Booked
                                  buttonStyles +=
                                    'bg-slate-800/40 text-slate-600 border border-slate-800 cursor-not-allowed';
                                }

                                return (
                                  <button
                                    key={seat.eventSeatId}
                                    type="button"
                                    disabled={!isAvailable && !isSelected}
                                    onClick={() => handleSeatClick(seat)}
                                    title={`Seat ${seat.rowLabel}${seat.seatNumber} • ₹${seat.price} (${seat.status})`}
                                    className={buttonStyles}
                                  >
                                    {isSelected ? '✓' : isHeld ? <Clock className="w-3 h-3" /> : isBooked ? '✕' : seat.seatNumber}
                                  </button>
                                );
                              })}
                            </div>

                            <span className="w-5 text-left text-xs font-bold text-slate-400 select-none">
                              {rowLabel}
                            </span>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>

        {/* Right Column: Checkout & Reservation Panel (4 Cols) */}
        <div className="lg:col-span-4 sticky top-24 space-y-6">
          <div className="bg-slate-900 border border-slate-800 rounded-3xl p-6 sm:p-7 space-y-6 shadow-2xl">
            {/* Active Hold Countdown Banner */}
            {activeHold && (
              <div className="bg-gradient-to-r from-amber-950/60 to-orange-950/60 border border-amber-500/40 rounded-2xl p-4 text-center space-y-1 animate-pulse">
                <div className="flex items-center justify-center space-x-2 text-amber-400 text-xs font-bold uppercase tracking-wider">
                  <Clock className="w-4 h-4" />
                  <span>Seats Held Temporarily</span>
                </div>
                <div className="text-3xl font-extrabold text-white tracking-wider font-mono">
                  {formatTimer(secondsRemaining)}
                </div>
                <p className="text-[11px] text-amber-300/80">
                  Complete your booking before the timer expires to secure your seats.
                </p>
              </div>
            )}

            {/* Error Notification */}
            {(holdError || checkoutError) && (
              <div className="bg-rose-950/60 border border-rose-800/80 rounded-2xl p-4 text-xs text-rose-200 space-y-1">
                <div className="flex items-center space-x-1.5 font-bold text-rose-300">
                  <AlertCircle className="w-4 h-4 flex-shrink-0" />
                  <span>Reservation Notice</span>
                </div>
                <p>{holdError || checkoutError}</p>
              </div>
            )}

            {/* Selected Seats Summary */}
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <span className="text-xs text-slate-400 font-semibold uppercase tracking-wider">
                  Selected Seats ({selectedSeatObjects.length})
                </span>
                {selectedSeatObjects.length > 0 && !activeHold && (
                  <button
                    onClick={() => setSelectedSeatIds([])}
                    className="text-xs text-slate-400 hover:text-rose-400 transition"
                  >
                    Clear All
                  </button>
                )}
              </div>

              {selectedSeatObjects.length === 0 ? (
                <div className="py-8 text-center text-slate-400 bg-slate-950/40 border border-slate-800/60 rounded-2xl">
                  <Ticket className="w-8 h-8 text-slate-500 mx-auto mb-2 opacity-50" />
                  <p className="text-xs">Click available seats on the map above to select.</p>
                </div>
              ) : (
                <div className="max-h-48 overflow-y-auto space-y-2 pr-1">
                  {selectedSeatObjects.map((seat) => (
                    <div
                      key={seat.eventSeatId}
                      className="flex items-center justify-between p-2.5 rounded-xl bg-slate-950/80 border border-slate-800 text-xs"
                    >
                      <div>
                        <span className="font-bold text-white block">
                          Row {seat.rowLabel}, Seat {seat.seatNumber}
                        </span>
                        <span className="text-slate-400 text-[10px]">
                          Unit Price: ₹{Number(seat.price).toLocaleString('en-IN')}
                        </span>
                      </div>
                      <div className="flex items-center space-x-2">
                        <span className="font-semibold text-emerald-400">
                          ₹{Number(seat.price).toLocaleString('en-IN')}
                        </span>
                        {!activeHold && (
                          <button
                            onClick={() => handleSeatClick(seat)}
                            className="p-1 rounded text-slate-500 hover:text-rose-400 transition"
                          >
                            <X className="w-3.5 h-3.5" />
                          </button>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Total Summary */}
            <div className="pt-4 border-t border-slate-800 space-y-2">
              <div className="flex justify-between items-baseline">
                <span className="text-sm font-medium text-slate-300">Total Price</span>
                <span className="text-2xl font-extrabold text-white">
                  ₹{totalPrice.toLocaleString('en-IN')}
                </span>
              </div>
              <p className="text-[11px] text-slate-400">
                Inclusive of all applicable taxes and transaction fees.
              </p>
            </div>

            {/* Action Buttons */}
            <div className="space-y-3 pt-2">
              {!activeHold ? (
                <button
                  type="button"
                  disabled={selectedSeatObjects.length === 0 || isHolding}
                  onClick={handleAcquireHold}
                  className="w-full py-4 rounded-2xl bg-indigo-600 hover:bg-indigo-500 disabled:bg-slate-800 disabled:text-slate-500 disabled:cursor-not-allowed text-white font-bold text-sm transition shadow-lg shadow-indigo-600/30 flex items-center justify-center space-x-2"
                >
                  {isHolding ? (
                    <>
                      <RefreshCw className="w-4 h-4 animate-spin" />
                      <span>Securing Seats in Redis...</span>
                    </>
                  ) : (
                    <>
                      <Lock className="w-4 h-4" />
                      <span>Hold Seats (5 Minutes)</span>
                    </>
                  )}
                </button>
              ) : (
                <div className="space-y-2.5">
                  <button
                    type="button"
                    disabled={isCheckingOut}
                    onClick={handleCheckout}
                    className="w-full py-4 rounded-2xl bg-emerald-600 hover:bg-emerald-500 disabled:bg-slate-800 text-white font-bold text-sm transition shadow-lg shadow-emerald-600/30 flex items-center justify-center space-x-2"
                  >
                    {isCheckingOut ? (
                      <>
                        <RefreshCw className="w-4 h-4 animate-spin" />
                        <span>Confirming & Booking...</span>
                      </>
                    ) : (
                      <>
                        <CreditCard className="w-4 h-4" />
                        <span>Confirm & Complete Booking</span>
                      </>
                    )}
                  </button>

                  <button
                    type="button"
                    disabled={isCheckingOut}
                    onClick={handleCancelHold}
                    className="w-full py-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-semibold transition"
                  >
                    Release Seats & Cancel
                  </button>
                </div>
              )}
            </div>

            {/* Security Guarantee Badges */}
            <div className="pt-4 border-t border-slate-800 space-y-2 text-[11px] text-slate-400">
              <div className="flex items-center space-x-2">
                <ShieldCheck className="w-4 h-4 text-emerald-400 flex-shrink-0" />
                <span>Zero Double-Booking Guarantee via MySQL CAS</span>
              </div>
              <div className="flex items-center space-x-2">
                <CheckCircle2 className="w-4 h-4 text-indigo-400 flex-shrink-0" />
                <span>Real-Time Live Seat Status via WebSocket</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

