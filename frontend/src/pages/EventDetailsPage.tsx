import React, { useEffect, useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import {
  Calendar,
  Clock,
  MapPin,
  Users,
  Ticket,
  ArrowLeft,
  Share2,
  CheckCircle2,
  Sparkles,
  ShieldCheck,
  Check,
  ChevronRight,
} from 'lucide-react';
import { eventApi } from '../api/eventApi';
import { EventItem } from '../types';
import { CategoryBadge, StatusBadge } from '../components/common/Badge';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorMessage } from '../components/common/ErrorMessage';

export const EventDetailsPage: React.FC = () => {
  const { identifier } = useParams<{ identifier: string }>();
  const navigate = useNavigate();

  const [event, setEvent] = useState<EventItem | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    const fetchEvent = async () => {
      if (!identifier) return;

      try {
        setLoading(true);
        setError(null);

        let data: EventItem;
        if (/^\d+$/.test(identifier)) {
          data = await eventApi.getEventById(Number(identifier));
        } else {
          data = await eventApi.getEventBySlug(identifier);
        }
        setEvent(data);
      } catch (err) {
        setError('Event not found or failed to load. Please return to explore events.');
      } finally {
        setLoading(false);
      }
    };

    fetchEvent();
  }, [identifier]);

  const handleShare = () => {
    navigator.clipboard.writeText(window.location.href);
    setCopied(true);
    setTimeout(() => setCopied(false), 2500);
  };

  if (loading) {
    return (
      <div className="min-h-[70vh] flex items-center justify-center">
        <LoadingSpinner message="Loading event details..." size="lg" />
      </div>
    );
  }

  if (error || !event) {
    return (
      <div className="max-w-2xl mx-auto px-4 py-20 text-center">
        <ErrorMessage
          title="Event Not Found"
          message={error || 'The requested event could not be retrieved from the database.'}
          onRetry={() => navigate('/events')}
        />
        <Link
          to="/events"
          className="mt-6 inline-flex items-center space-x-2 text-indigo-400 hover:text-indigo-300 text-sm font-semibold"
        >
          <ArrowLeft className="w-4 h-4" />
          <span>Back to Explore Events</span>
        </Link>
      </div>
    );
  }

  // Format dates
  const startDate = new Date(event.startTime);
  const endDate = new Date(event.endTime);
  const formattedStartDate = startDate.toLocaleDateString('en-US', {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
    year: 'numeric',
  });
  const formattedStartTime = startDate.toLocaleTimeString('en-US', {
    hour: 'numeric',
    minute: '2-digit',
  });
  const formattedEndTime = endDate.toLocaleTimeString('en-US', {
    hour: 'numeric',
    minute: '2-digit',
  });

  const getCategoryFallbackImage = (catSlug?: string) => {
    switch (catSlug) {
      case 'sports-fitness':
        return 'https://images.unsplash.com/photo-1508098682722-e99c43a406b2?auto=format&fit=crop&w=1400&q=80';
      case 'standup-comedy':
        return 'https://images.unsplash.com/photo-1585699324551-f6c309eedeca?auto=format&fit=crop&w=1400&q=80';
      case 'theatre-arts':
        return 'https://images.unsplash.com/photo-1507676184212-d03ab07a01bf?auto=format&fit=crop&w=1400&q=80';
      case 'tech-conferences':
        return 'https://images.unsplash.com/photo-1540575467063-178a50c2df87?auto=format&fit=crop&w=1400&q=80';
      default:
        return 'https://images.unsplash.com/photo-1501281668745-f7f57925c3b4?auto=format&fit=crop&w=1400&q=80';
    }
  };

  const fallbackImage = getCategoryFallbackImage(event.category?.slug);
  const imageUrl =
    event.bannerImageUrl && event.bannerImageUrl.trim().startsWith('http')
      ? event.bannerImageUrl
      : fallbackImage;

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
      {/* Breadcrumbs and Actions */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs text-slate-400">
        <nav className="flex items-center space-x-2">
          <Link to="/" className="hover:text-white transition-colors">
            Home
          </Link>
          <ChevronRight className="w-3.5 h-3.5 text-slate-600" />
          <Link to="/events" className="hover:text-white transition-colors">
            Explore Events
          </Link>
          <ChevronRight className="w-3.5 h-3.5 text-slate-600" />
          <span className="text-slate-200 truncate max-w-xs">{event.title}</span>
        </nav>

        <div className="flex items-center space-x-2">
          <button
            onClick={handleShare}
            className="inline-flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-slate-900 border border-slate-800 hover:border-slate-700 text-slate-300 hover:text-white transition-colors"
          >
            {copied ? (
              <>
                <Check className="w-3.5 h-3.5 text-emerald-400" />
                <span className="text-emerald-400 font-medium">Link Copied!</span>
              </>
            ) : (
              <>
                <Share2 className="w-3.5 h-3.5 text-indigo-400" />
                <span>Share</span>
              </>
            )}
          </button>
        </div>
      </div>

      {/* Hero Banner with overlay */}
      <div className="relative aspect-[21/9] sm:aspect-[24/9] w-full rounded-3xl overflow-hidden bg-slate-900 border border-slate-800 shadow-2xl">
        <img
          src={imageUrl}
          alt={event.title}
          onError={(e) => {
            (e.target as HTMLImageElement).src = fallbackImage;
          }}
          className="w-full h-full object-cover"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-slate-950 via-slate-950/60 to-transparent" />

        {/* Floating Badges */}
        <div className="absolute top-4 left-4 right-4 flex items-center justify-between">
          <div className="flex items-center space-x-2">
            {event.category && <CategoryBadge name={event.category.name} />}
            <StatusBadge status={event.status} />
          </div>
        </div>

        {/* Title over banner on bottom */}
        <div className="absolute bottom-6 left-6 right-6">
          <h1 className="text-2xl sm:text-4xl md:text-5xl font-extrabold text-white tracking-tight leading-tight">
            {event.title}
          </h1>
          <p className="mt-2 text-slate-300 text-sm sm:text-base max-w-3xl line-clamp-2">
            {event.shortDescription}
          </p>
        </div>
      </div>

      {/* Content Columns */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 pt-4">
        {/* Left Column (Details, Description, Venue) */}
        <div className="lg:col-span-2 space-y-8">
          {/* Key Quick Facts Grid */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div className="bg-slate-900/60 border border-slate-800/80 rounded-2xl p-5 flex items-start space-x-4">
              <div className="w-10 h-10 rounded-xl bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center text-indigo-400 flex-shrink-0">
                <Calendar className="w-5 h-5" />
              </div>
              <div>
                <span className="text-xs text-slate-400 font-medium block">Date & Schedule</span>
                <span className="text-sm font-semibold text-white mt-0.5 block">
                  {formattedStartDate}
                </span>
                <span className="text-xs text-slate-400 mt-0.5 block">
                  {formattedStartTime} – {formattedEndTime}
                </span>
              </div>
            </div>

            <div className="bg-slate-900/60 border border-slate-800/80 rounded-2xl p-5 flex items-start space-x-4">
              <div className="w-10 h-10 rounded-xl bg-rose-500/10 border border-rose-500/20 flex items-center justify-center text-rose-400 flex-shrink-0">
                <MapPin className="w-5 h-5" />
              </div>
              <div>
                <span className="text-xs text-slate-400 font-medium block">Venue & Location</span>
                <span className="text-sm font-semibold text-white mt-0.5 block">
                  {event.venue ? event.venue.name : 'Venue TBA'}
                </span>
                <span className="text-xs text-slate-400 mt-0.5 block">
                  {event.venue ? `${event.venue.address}, ${event.venue.city}` : ''}
                </span>
              </div>
            </div>
          </div>

          {/* About Event Description */}
          <div className="bg-slate-900/40 border border-slate-800/80 rounded-2xl p-6 sm:p-8 space-y-4">
            <h2 className="text-xl font-bold text-white tracking-wide">About This Event</h2>
            <div className="text-sm text-slate-300 leading-relaxed space-y-4 whitespace-pre-line">
              {event.fullDescription || event.shortDescription}
            </div>
          </div>

          {/* Venue & Facilities */}
          {event.venue && (
            <div className="bg-slate-900/40 border border-slate-800/80 rounded-2xl p-6 sm:p-8 space-y-4">
              <h2 className="text-xl font-bold text-white tracking-wide">Venue Information</h2>
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 pt-2">
                <div className="bg-slate-950/60 border border-slate-800 p-4 rounded-xl">
                  <span className="text-xs text-slate-500 block font-medium">Capacity</span>
                  <span className="text-sm font-semibold text-white mt-1 block">
                    {event.venue.capacity.toLocaleString('en-IN')} Seats
                  </span>
                </div>
                <div className="bg-slate-950/60 border border-slate-800 p-4 rounded-xl">
                  <span className="text-xs text-slate-500 block font-medium">City / State</span>
                  <span className="text-sm font-semibold text-white mt-1 block">
                    {event.venue.city}, {event.venue.state}
                  </span>
                </div>
                <div className="bg-slate-950/60 border border-slate-800 p-4 rounded-xl">
                  <span className="text-xs text-slate-500 block font-medium">Timezone</span>
                  <span className="text-sm font-semibold text-white mt-1 block">
                    {event.venue.timezone}
                  </span>
                </div>
              </div>
            </div>
          )}

          {/* Organizer Info */}
          {event.organizer && (
            <div className="bg-slate-900/40 border border-slate-800/80 rounded-2xl p-6 flex items-center justify-between">
              <div>
                <span className="text-xs text-slate-400 font-medium block">Organized By</span>
                <span className="text-base font-bold text-white mt-0.5 block">
                  {event.organizer.fullName}
                </span>
                <span className="text-xs text-slate-500 block">{event.organizer.email}</span>
              </div>
              <div className="flex items-center space-x-1.5 text-xs text-emerald-400 bg-emerald-500/10 border border-emerald-500/20 px-3 py-1.5 rounded-full">
                <ShieldCheck className="w-4 h-4" />
                <span>Verified Organizer</span>
              </div>
            </div>
          )}
        </div>

        {/* Right Column: Ticket Card & Reservation Notice */}
        <div className="lg:col-span-1">
          <div className="sticky top-24 bg-slate-900 border border-slate-800 rounded-3xl p-6 sm:p-8 space-y-6 shadow-2xl">
            <div>
              <span className="text-xs text-slate-400 font-medium uppercase tracking-wider block">
                Standard Admission
              </span>
              <div className="flex items-baseline space-x-2 mt-2">
                <span className="text-3xl font-extrabold text-white">
                  ₹{Number(event.basePrice).toLocaleString('en-IN')}
                </span>
                <span className="text-xs text-slate-400">/ ticket</span>
              </div>
            </div>

            {/* Live Seating Notice */}
            <div className="bg-indigo-950/40 border border-indigo-800/40 rounded-2xl p-4 text-xs text-indigo-200/90 space-y-2">
              <div className="flex items-center space-x-2 text-indigo-400 font-semibold">
                <Sparkles className="w-4 h-4" />
                <span>Live Interactive Seat Selection</span>
              </div>
              <p className="leading-relaxed text-indigo-300/80">
                Real-time seat matrix with Redis 5-minute hold coordination and live WebSocket updates.
              </p>
            </div>

            {/* Select Seats action button */}
            <Link
              to={`/events/${event.id}/seats`}
              className="w-full py-4 rounded-2xl bg-gradient-to-r from-indigo-600 to-indigo-500 hover:from-indigo-500 hover:to-indigo-400 text-white font-bold text-sm transition shadow-lg shadow-indigo-600/30 flex items-center justify-center space-x-2 border border-indigo-400/30 group"
            >
              <Ticket className="w-4 h-4 transition group-hover:rotate-12" />
              <span>Select Seats</span>
              <ChevronRight className="w-4 h-4 transition group-hover:translate-x-1" />
            </Link>

            <div className="space-y-2 pt-2 border-t border-slate-800 text-xs text-slate-400">
              <div className="flex items-center space-x-2">
                <CheckCircle2 className="w-4 h-4 text-emerald-400 flex-shrink-0" />
                <span>100% Genuine Tickets & Secure Entry</span>
              </div>
              <div className="flex items-center space-x-2">
                <CheckCircle2 className="w-4 h-4 text-emerald-400 flex-shrink-0" />
                <span>Authoritative MySQL Transactional Guarantee</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
