import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Search, MapPin, Calendar, Sparkles, Shield, Zap, ArrowRight, Music, Trophy, Smile, Drama, Cpu, Layers } from 'lucide-react';
import { eventApi } from '../api/eventApi';
import { categoryApi } from '../api/categoryApi';
import { EventCategory, EventItem } from '../types';
import { EventCard } from '../components/common/EventCard';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorMessage } from '../components/common/ErrorMessage';

export const HomePage: React.FC = () => {
  const navigate = useNavigate();
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedCity, setSelectedCity] = useState('');
  const [categories, setCategories] = useState<EventCategory[]>([]);
  const [featuredEvents, setFeaturedEvents] = useState<EventItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);
        setError(null);

        const [cats, eventsPage] = await Promise.all([
          categoryApi.getCategories().catch(() => [] as EventCategory[]),
          eventApi.getEvents({ page: 0, size: 6, sort: 'startTime,asc' }),
        ]);

        setCategories(cats);
        setFeaturedEvents(eventsPage.content || []);
      } catch (err) {
        setError('Failed to load events. Please check your backend connection.');
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, []);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const params = new URLSearchParams();
    if (searchTerm.trim()) params.set('search', searchTerm.trim());
    if (selectedCity.trim()) params.set('city', selectedCity.trim());
    navigate(`/events?${params.toString()}`);
  };

  const getCategoryIcon = (slug: string) => {
    switch (slug) {
      case 'concerts-music':
        return <Music className="w-5 h-5 text-indigo-400" />;
      case 'sports-fitness':
        return <Trophy className="w-5 h-5 text-amber-400" />;
      case 'standup-comedy':
        return <Smile className="w-5 h-5 text-emerald-400" />;
      case 'theatre-arts':
        return <Drama className="w-5 h-5 text-rose-400" />;
      case 'tech-conferences':
        return <Cpu className="w-5 h-5 text-cyan-400" />;
      default:
        return <Layers className="w-5 h-5 text-indigo-400" />;
    }
  };

  return (
    <div className="space-y-20 pb-16">
      {/* Hero Section */}
      <section className="relative pt-12 pb-20 md:pt-20 md:pb-28 overflow-hidden">
        {/* Ambient Glow */}
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[350px] bg-indigo-600/20 rounded-full blur-[120px] pointer-events-none" />

        <div className="relative max-w-5xl mx-auto px-4 text-center space-y-8">
          <div className="inline-flex items-center space-x-2 px-4 py-1.5 rounded-full bg-indigo-500/10 border border-indigo-500/20 text-indigo-300 text-xs font-semibold backdrop-blur-sm">
            <Sparkles className="w-3.5 h-3.5 text-indigo-400" />
            <span>Intelligent Event Discovery & Live Booking</span>
          </div>

          <h1 className="text-4xl sm:text-6xl font-extrabold tracking-tight text-white leading-tight">
            Discover Unforgettable Events. <br />
            <span className="bg-gradient-to-r from-indigo-400 via-violet-300 to-indigo-200 bg-clip-text text-transparent">
              Book Your Perfect Seat Live.
            </span>
          </h1>

          <p className="max-w-2xl mx-auto text-base sm:text-lg text-slate-400 leading-relaxed">
            From sold-out arena concerts and championship sports to intimate comedy nights. Experience ultra-fast discovery powered by real-time inventory management.
          </p>

          {/* Search Box */}
          <form
            onSubmit={handleSearchSubmit}
            className="max-w-3xl mx-auto bg-slate-900/90 border border-slate-800 p-2 sm:p-2.5 rounded-2xl shadow-2xl backdrop-blur-xl flex flex-col sm:flex-row items-center gap-2"
          >
            <div className="flex-1 flex items-center space-x-3 px-3 py-2 w-full">
              <Search className="w-5 h-5 text-indigo-400 flex-shrink-0" />
              <input
                type="text"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                placeholder="Search artists, concerts, festivals, comedy..."
                className="w-full bg-transparent text-sm text-white placeholder-slate-500 focus:outline-none"
              />
            </div>

            <div className="hidden sm:block w-px h-8 bg-slate-800" />

            <div className="flex items-center space-x-2 px-3 py-2 w-full sm:w-auto">
              <MapPin className="w-5 h-5 text-rose-400 flex-shrink-0" />
              <input
                type="text"
                value={selectedCity}
                onChange={(e) => setSelectedCity(e.target.value)}
                placeholder="City (e.g. Mumbai)"
                className="w-full sm:w-36 bg-transparent text-sm text-white placeholder-slate-500 focus:outline-none"
              />
            </div>

            <button
              type="submit"
              className="w-full sm:w-auto px-6 py-3 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-semibold text-sm shadow-lg shadow-indigo-600/30 transition-all flex items-center justify-center space-x-2"
            >
              <span>Explore</span>
              <ArrowRight className="w-4 h-4" />
            </button>
          </form>

          {/* Quick Category Chips */}
          {categories.length > 0 && (
            <div className="flex flex-wrap items-center justify-center gap-2 pt-4">
              {categories.slice(0, 6).map((cat) => (
                <Link
                  key={cat.id}
                  to={`/events?category=${encodeURIComponent(cat.slug)}`}
                  className="flex items-center space-x-2 px-3.5 py-1.5 rounded-xl bg-slate-900/60 hover:bg-slate-800/80 border border-slate-800 hover:border-indigo-500/40 text-xs font-medium text-slate-300 transition-colors"
                >
                  {getCategoryIcon(cat.slug)}
                  <span>{cat.name}</span>
                </Link>
              ))}
            </div>
          )}
        </div>
      </section>

      {/* Featured / Trending Events Section */}
      <section className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex flex-col sm:flex-row sm:items-end justify-between mb-8 gap-4">
          <div>
            <div className="flex items-center space-x-2 text-indigo-400 text-xs font-semibold uppercase tracking-wider mb-1">
              <Calendar className="w-3.5 h-3.5" />
              <span>Trending Now</span>
            </div>
            <h2 className="text-2xl sm:text-3xl font-extrabold text-white">Upcoming Live Events</h2>
          </div>
          <Link
            to="/events"
            className="inline-flex items-center space-x-1.5 text-sm font-semibold text-indigo-400 hover:text-indigo-300 transition-colors"
          >
            <span>View all events</span>
            <ArrowRight className="w-4 h-4" />
          </Link>
        </div>

        {loading ? (
          <LoadingSpinner message="Fetching live events from SeatSync backend..." />
        ) : error ? (
          <ErrorMessage message={error} onRetry={() => window.location.reload()} />
        ) : featuredEvents.length === 0 ? (
          <div className="bg-slate-900/50 border border-slate-800/80 rounded-2xl p-12 text-center max-w-md mx-auto">
            <Calendar className="w-10 h-10 text-slate-500 mx-auto mb-3" />
            <h3 className="text-lg font-bold text-slate-200">No events published yet</h3>
            <p className="text-sm text-slate-400 mt-1">
              Sign in as an organizer to create and publish exciting events!
            </p>
            <Link
              to="/organizer/dashboard"
              className="mt-4 inline-flex items-center px-4 py-2 rounded-xl bg-indigo-600 text-white text-xs font-semibold hover:bg-indigo-500"
            >
              Organizer Hub
            </Link>
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
            {featuredEvents.map((event) => (
              <EventCard key={event.id} event={event} />
            ))}
          </div>
        )}
      </section>

      {/* Categories Feature Grid Section */}
      {categories.length > 0 && (
        <section className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="text-center max-w-2xl mx-auto mb-10 space-y-2">
            <div className="text-xs font-semibold uppercase tracking-wider text-indigo-400">
              Browse by Interest
            </div>
            <h2 className="text-2xl sm:text-3xl font-extrabold text-white">
              Explore by Event Category
            </h2>
            <p className="text-xs sm:text-sm text-slate-400">
              Find live entertainment tailored to your passion
            </p>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-4">
            {categories.map((cat) => (
              <Link
                key={cat.id}
                to={`/events?category=${encodeURIComponent(cat.slug)}`}
                className="group bg-slate-900/70 border border-slate-800 hover:border-indigo-500/50 rounded-2xl p-5 text-center flex flex-col items-center justify-center space-y-3 transition-all duration-300 hover:-translate-y-1 hover:shadow-xl hover:shadow-indigo-500/10"
              >
                <div className="w-12 h-12 rounded-2xl bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center group-hover:scale-110 transition-transform">
                  {getCategoryIcon(cat.slug)}
                </div>
                <div>
                  <h3 className="text-xs font-bold text-white group-hover:text-indigo-400 transition-colors">
                    {cat.name}
                  </h3>
                </div>
              </Link>
            ))}
          </div>
        </section>
      )}

      {/* Platform Features Grid */}
      <section className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-4">
        <div className="bg-gradient-to-b from-slate-900/80 to-slate-950 border border-slate-800/80 rounded-3xl p-8 sm:p-12 relative overflow-hidden">
          <div className="max-w-2xl">
            <h3 className="text-2xl sm:text-3xl font-bold text-white tracking-tight">
              Built for High Concurrency & Zero Overbooking
            </h3>
            <p className="text-slate-400 text-sm sm:text-base mt-2 leading-relaxed">
              SeatSync AI combines Redis temporary coordination with MySQL authoritative ACID guarantees, ensuring seamless ticket booking during high-demand drops.
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mt-10">
            <div className="bg-slate-950/60 border border-slate-800 p-6 rounded-2xl space-y-3">
              <div className="w-10 h-10 rounded-xl bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center text-indigo-400">
                <Zap className="w-5 h-5" />
              </div>
              <h4 className="font-semibold text-slate-100 text-base">Atomic Seat Holds</h4>
              <p className="text-xs text-slate-400 leading-relaxed">
                Temporary holds with strict expiration sweepers prevent contention and ensure no double booking.
              </p>
            </div>

            <div className="bg-slate-950/60 border border-slate-800 p-6 rounded-2xl space-y-3">
              <div className="w-10 h-10 rounded-xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400">
                <Shield className="w-5 h-5" />
              </div>
              <h4 className="font-semibold text-slate-100 text-base">Authoritative Persistence</h4>
              <p className="text-xs text-slate-400 leading-relaxed">
                Transactions validate row locks and user ownership in MySQL before finalizing any ticket.
              </p>
            </div>

            <div className="bg-slate-950/60 border border-slate-800 p-6 rounded-2xl space-y-3">
              <div className="w-10 h-10 rounded-xl bg-cyan-500/10 border border-cyan-500/20 flex items-center justify-center text-cyan-400">
                <Sparkles className="w-5 h-5" />
              </div>
              <h4 className="font-semibold text-slate-100 text-base">Smart Discovery</h4>
              <p className="text-xs text-slate-400 leading-relaxed">
                Dynamic category indexing and venue geographic searches help users find the best experiences fast.
              </p>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
};
