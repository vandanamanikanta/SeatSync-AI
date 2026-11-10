import React, { useEffect, useState, useCallback } from 'react';
import { Link } from 'react-router-dom';
import {
  Ticket,
  Calendar,
  MapPin,
  Clock,
  ArrowRight,
  AlertCircle,
  RefreshCw,
  CheckCircle2,
  XCircle,
  Clock4,
  ExternalLink,
  ShieldCheck,
} from 'lucide-react';
import { bookingApi } from '../api/bookingApi';
import { BookingDetail } from '../types';
import { getApiErrorMessage } from '../api/client';
import { LoadingSpinner } from '../components/common/LoadingSpinner';

export const BookingHistoryPage: React.FC = () => {
  const [bookings, setBookings] = useState<BookingDetail[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const fetchBookings = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await bookingApi.getMyBookings();
      // Sort bookings descending by creation time (most recent first)
      const sorted = [...data].sort(
        (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
      );
      setBookings(sorted);
    } catch (err) {
      setError(getApiErrorMessage(err, 'Failed to retrieve your booking history.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchBookings();
  }, [fetchBookings]);

  const formatDate = (isoString: string) => {
    try {
      const d = new Date(isoString);
      return d.toLocaleDateString('en-IN', {
        day: 'numeric',
        month: 'short',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return isoString;
    }
  };

  const renderStatusBadge = (status: BookingDetail['status']) => {
    switch (status) {
      case 'CONFIRMED':
        return (
          <span className="inline-flex items-center space-x-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
            <CheckCircle2 className="w-3.5 h-3.5" />
            <span>CONFIRMED</span>
          </span>
        );
      case 'PENDING':
        return (
          <span className="inline-flex items-center space-x-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-amber-500/10 text-amber-400 border border-amber-500/20">
            <Clock4 className="w-3.5 h-3.5" />
            <span>PENDING</span>
          </span>
        );
      case 'CANCELLED':
        return (
          <span className="inline-flex items-center space-x-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-rose-500/10 text-rose-400 border border-rose-500/20">
            <XCircle className="w-3.5 h-3.5" />
            <span>CANCELLED</span>
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center px-3 py-1 rounded-full text-xs font-semibold bg-slate-500/10 text-slate-300 border border-slate-500/20">
            {status}
          </span>
        );
    }
  };

  const getSectionBadgeStyle = (sectionName: string) => {
    const s = sectionName.toUpperCase();
    if (s.includes('VIP')) {
      return 'bg-amber-500/10 text-amber-300 border-amber-500/30';
    }
    if (s.includes('PLATINUM')) {
      return 'bg-cyan-500/10 text-cyan-300 border-cyan-500/30';
    }
    return 'bg-emerald-500/10 text-emerald-300 border-emerald-500/30';
  };

  if (loading) {
    return (
      <div className="min-h-[70vh] flex items-center justify-center">
        <LoadingSpinner message="Retrieving your confirmed bookings..." size="lg" />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-950 py-12 px-4 sm:px-6 lg:px-8">
      <div className="max-w-5xl mx-auto space-y-8">
        {/* Page Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-slate-800/80 pb-6">
          <div className="space-y-1">
            <div className="flex items-center space-x-3">
              <div className="w-10 h-10 rounded-xl bg-indigo-600/20 border border-indigo-500/30 flex items-center justify-center text-indigo-400 shadow-md">
                <Ticket className="w-5 h-5 transform -rotate-12" />
              </div>
              <h1 className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight">
                My Bookings
              </h1>
            </div>
            <p className="text-sm text-slate-400 pl-13">
              View your confirmed event tickets, seat reservations, and transaction references.
            </p>
          </div>

          <div className="flex items-center space-x-3">
            <button
              onClick={fetchBookings}
              className="inline-flex items-center space-x-2 px-4 py-2 rounded-xl bg-slate-900 hover:bg-slate-800 text-slate-300 hover:text-white border border-slate-800 text-xs font-semibold transition"
            >
              <RefreshCw className="w-3.5 h-3.5" />
              <span>Refresh</span>
            </button>
            <Link
              to="/events"
              className="inline-flex items-center space-x-2 px-4 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold shadow-md shadow-indigo-600/20 transition"
            >
              <span>Explore More Events</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>
        </div>

        {/* Error State */}
        {error && (
          <div className="bg-rose-950/40 border border-rose-800/60 rounded-2xl p-6 text-center space-y-3">
            <AlertCircle className="w-8 h-8 text-rose-400 mx-auto" />
            <h3 className="text-base font-semibold text-white">Unable to Load Bookings</h3>
            <p className="text-xs text-slate-300 max-w-md mx-auto">{error}</p>
            <button
              onClick={fetchBookings}
              className="inline-flex items-center space-x-2 px-4 py-2 rounded-xl bg-rose-600 hover:bg-rose-500 text-white text-xs font-semibold transition"
            >
              <RefreshCw className="w-3.5 h-3.5" />
              <span>Try Again</span>
            </button>
          </div>
        )}

        {/* Empty State */}
        {!error && bookings.length === 0 && (
          <div className="bg-slate-900/60 border border-slate-800 rounded-3xl p-12 text-center space-y-6 max-w-xl mx-auto shadow-xl">
            <div className="w-20 h-20 rounded-2xl bg-indigo-600/10 border border-indigo-500/20 flex items-center justify-center mx-auto text-indigo-400">
              <Ticket className="w-10 h-10 transform -rotate-12 text-indigo-400/80" />
            </div>
            <div className="space-y-2">
              <h2 className="text-xl font-bold text-white">No Bookings Found Yet</h2>
              <p className="text-sm text-slate-400 leading-relaxed">
                You haven't reserved tickets for any events yet. Check out upcoming concerts,
                sports matches, comedy shows, and conferences with live seat selection.
              </p>
            </div>
            <Link
              to="/events"
              className="inline-flex items-center space-x-2 px-6 py-3 rounded-2xl bg-indigo-600 hover:bg-indigo-500 text-white font-semibold text-sm transition shadow-lg shadow-indigo-600/30"
            >
              <Ticket className="w-4 h-4" />
              <span>Discover Events</span>
              <ArrowRight className="w-4 h-4" />
            </Link>
          </div>
        )}

        {/* Bookings List */}
        {!error && bookings.length > 0 && (
          <div className="space-y-6">
            {bookings.map((booking) => (
              <div
                key={booking.bookingId}
                className="bg-slate-900 border border-slate-800 hover:border-slate-700/80 rounded-3xl p-6 sm:p-8 transition-all shadow-xl space-y-6"
              >
                {/* Header row: Reference & Status */}
                <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 pb-4 border-b border-slate-800/80">
                  <div className="flex items-center space-x-3">
                    <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
                      Reference:
                    </span>
                    <span className="font-mono text-sm font-bold text-indigo-400 bg-indigo-500/10 px-3 py-1 rounded-lg border border-indigo-500/20">
                      {booking.bookingReference}
                    </span>
                  </div>

                  <div className="flex items-center space-x-4">
                    <span className="text-xs text-slate-400 flex items-center space-x-1">
                      <Clock className="w-3.5 h-3.5 text-slate-500" />
                      <span>{formatDate(booking.createdAt)}</span>
                    </span>
                    {renderStatusBadge(booking.status)}
                  </div>
                </div>

                {/* Event & Venue Info */}
                <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                  <div className="md:col-span-2 space-y-2">
                    <Link
                      to={`/events/${booking.eventId}`}
                      className="group inline-flex items-center space-x-2 text-xl font-bold text-white hover:text-indigo-300 transition"
                    >
                      <span>{booking.eventTitle}</span>
                      <ExternalLink className="w-4 h-4 text-slate-500 group-hover:text-indigo-400 transition" />
                    </Link>
                    <div className="flex items-center space-x-2 text-xs text-slate-400">
                      <MapPin className="w-4 h-4 text-rose-400 flex-shrink-0" />
                      <span>{booking.venueName}</span>
                    </div>
                  </div>

                  {/* Payment Summary */}
                  <div className="bg-slate-950/70 border border-slate-800/80 rounded-2xl p-4 flex flex-col justify-between">
                    <span className="text-xs font-medium text-slate-400">Total Paid</span>
                    <div className="flex items-baseline space-x-2 mt-1">
                      <span className="text-2xl font-black text-white">
                        ₹{Number(booking.totalAmount).toLocaleString('en-IN')}
                      </span>
                      <span className="text-xs font-semibold text-slate-400 uppercase">
                        {booking.currency || 'INR'}
                      </span>
                    </div>
                    <div className="mt-2 pt-2 border-t border-slate-800/60 flex items-center space-x-1.5 text-[11px] text-emerald-400">
                      <ShieldCheck className="w-3.5 h-3.5" />
                      <span>Verified Transaction</span>
                    </div>
                  </div>
                </div>

                {/* Reserved Seats List */}
                <div className="space-y-3 pt-2">
                  <div className="flex items-center justify-between text-xs font-semibold text-slate-400 uppercase tracking-wider">
                    <span>Reserved Seats ({booking.items?.length || 0})</span>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3">
                    {booking.items?.map((item) => (
                      <div
                        key={item.itemId}
                        className={`rounded-2xl p-3 border flex flex-col justify-between ${getSectionBadgeStyle(
                          item.sectionName
                        )}`}
                      >
                        <div className="flex items-center justify-between">
                          <span className="text-[11px] font-bold uppercase tracking-wider">
                            {item.sectionName}
                          </span>
                          <span className="text-xs font-mono font-bold">
                            ₹{Number(item.unitPrice).toLocaleString('en-IN')}
                          </span>
                        </div>
                        <div className="mt-2 text-base font-extrabold font-mono tracking-tight text-white">
                          Row {item.rowLabel} &bull; Seat {item.seatNumber}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>

                {/* Action Links */}
                <div className="pt-2 flex justify-end items-center space-x-3">
                  <Link
                    to={`/events/${booking.eventId}`}
                    className="inline-flex items-center space-x-1.5 text-xs font-semibold text-indigo-400 hover:text-indigo-300 transition"
                  >
                    <span>View Event Details</span>
                    <ChevronRightIcon className="w-3.5 h-3.5" />
                  </Link>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

// Mini Chevron Icon helper
const ChevronRightIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg
    xmlns="http://www.w3.org/2000/svg"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
    strokeLinecap="round"
    strokeLinejoin="round"
    className={className}
  >
    <polyline points="9 18 15 12 9 6" />
  </svg>
);

