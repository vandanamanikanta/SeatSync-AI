import React, { useEffect, useState } from 'react';
import {
  PlusCircle,
  Calendar,
  MapPin,
  Tag,
  Clock,
  Sparkles,
  CheckCircle,
  AlertCircle,
  X,
  Eye,
  Edit3,
  Trash2,
  TrendingUp,
} from 'lucide-react';
import { eventApi } from '../api/eventApi';
import { categoryApi } from '../api/categoryApi';
import { venueApi } from '../api/venueApi';
import { EventCategory, EventItem, EventStatus, Venue } from '../types';
import { StatusBadge, CategoryBadge } from '../components/common/Badge';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { getApiErrorMessage } from '../api/client';
import { Link } from 'react-router-dom';

export const OrganizerDashboardPage: React.FC = () => {
  const [events, setEvents] = useState<EventItem[]>([]);
  const [categories, setCategories] = useState<EventCategory[]>([]);
  const [venues, setVenues] = useState<Venue[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Modal states: Create or Edit
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingEvent, setEditingEvent] = useState<EventItem | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  // Form Fields
  const [title, setTitle] = useState('');
  const [categoryId, setCategoryId] = useState<number | ''>('');
  const [venueId, setVenueId] = useState<number | ''>('');
  const [shortDescription, setShortDescription] = useState('');
  const [fullDescription, setFullDescription] = useState('');
  const [bannerImageUrl, setBannerImageUrl] = useState('');
  const [startDateTime, setStartDateTime] = useState('');
  const [endDateTime, setEndDateTime] = useState('');
  const [basePrice, setBasePrice] = useState<string>('999');
  const [initialStatus, setInitialStatus] = useState<EventStatus>('DRAFT');

  const fetchOrganizerData = async () => {
    try {
      setLoading(true);
      setError(null);

      const [myEventsPage, cats, venuesPage] = await Promise.all([
        eventApi.getMyEvents(0, 50),
        categoryApi.getCategories().catch(() => [] as EventCategory[]),
        venueApi.getVenues(undefined, 0, 50).catch(() => ({ content: [] as Venue[] })),
      ]);

      setEvents(myEventsPage.content || []);
      setCategories(cats);
      const vList = venuesPage.content || [];
      setVenues(vList);

      if (cats.length > 0 && categoryId === '') setCategoryId(cats[0].id);
      if (vList.length > 0 && venueId === '') setVenueId(vList[0].id);
    } catch (err) {
      setError(getApiErrorMessage(err, 'Failed to fetch organizer dashboard data.'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchOrganizerData();
  }, []);

  const openCreateModal = () => {
    setEditingEvent(null);
    setTitle('');
    setShortDescription('');
    setFullDescription('');
    setBannerImageUrl('');
    setStartDateTime('');
    setEndDateTime('');
    setBasePrice('999');
    setInitialStatus('DRAFT');
    if (categories.length > 0) setCategoryId(categories[0].id);
    if (venues.length > 0) setVenueId(venues[0].id);
    setFormError(null);
    setIsModalOpen(true);
  };

  const openEditModal = (event: EventItem) => {
    setEditingEvent(event);
    setTitle(event.title);
    setCategoryId(event.category?.id || (categories[0]?.id ?? ''));
    setVenueId(event.venue?.id || (venues[0]?.id ?? ''));
    setShortDescription(event.shortDescription || '');
    setFullDescription(event.fullDescription || '');
    setBannerImageUrl(event.bannerImageUrl || '');

    // Format ISO string for <input type="datetime-local"> (YYYY-MM-DDThh:mm)
    try {
      const sDate = new Date(event.startTime);
      const eDate = new Date(event.endTime);
      setStartDateTime(new Date(sDate.getTime() - sDate.getTimezoneOffset() * 60000).toISOString().slice(0, 16));
      setEndDateTime(new Date(eDate.getTime() - eDate.getTimezoneOffset() * 60000).toISOString().slice(0, 16));
    } catch {
      setStartDateTime('');
      setEndDateTime('');
    }

    setBasePrice(event.basePrice.toString());
    setInitialStatus(event.status);
    setFormError(null);
    setIsModalOpen(true);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);

    if (!title.trim() || !categoryId || !venueId || !shortDescription.trim() || !startDateTime || !endDateTime || !basePrice) {
      setFormError('Please fill in all mandatory fields.');
      return;
    }

    try {
      setSubmitting(true);
      const startTimeIso = new Date(startDateTime).toISOString();
      const endTimeIso = new Date(endDateTime).toISOString();

      if (editingEvent) {
        // Edit existing event via PUT
        await eventApi.updateEvent(editingEvent.id, {
          title: title.trim(),
          categoryId: Number(categoryId),
          venueId: Number(venueId),
          shortDescription: shortDescription.trim(),
          fullDescription: fullDescription.trim() || undefined,
          bannerImageUrl: bannerImageUrl.trim() || undefined,
          startTime: startTimeIso,
          endTime: endTimeIso,
          basePrice: Number(basePrice),
          status: initialStatus,
        });
        setSuccessMessage(`Event "${title}" updated successfully!`);
      } else {
        // Create new event via POST
        await eventApi.createEvent({
          title: title.trim(),
          categoryId: Number(categoryId),
          venueId: Number(venueId),
          shortDescription: shortDescription.trim(),
          fullDescription: fullDescription.trim() || undefined,
          bannerImageUrl: bannerImageUrl.trim() || undefined,
          startTime: startTimeIso,
          endTime: endTimeIso,
          basePrice: Number(basePrice),
          status: initialStatus,
        });
        setSuccessMessage(`Event "${title}" created successfully!`);
      }

      setIsModalOpen(false);
      fetchOrganizerData();
    } catch (err) {
      setFormError(getApiErrorMessage(err, 'Failed to save event.'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleStatusChange = async (event: EventItem, newStatus: EventStatus) => {
    try {
      await eventApi.updateEventStatus(event.id, newStatus);
      setSuccessMessage(`Event "${event.title}" is now ${newStatus}.`);
      fetchOrganizerData();
    } catch (err) {
      setError(getApiErrorMessage(err, `Failed to change status to ${newStatus}.`));
    }
  };

  const handleDeleteEvent = async (event: EventItem) => {
    if (!window.confirm(`Are you sure you want to delete "${event.title}"?`)) return;

    try {
      await eventApi.deleteEvent(event.id);
      setSuccessMessage(`Event "${event.title}" deleted.`);
      fetchOrganizerData();
    } catch (err) {
      setError(getApiErrorMessage(err, 'Failed to delete event.'));
    }
  };

  const totalEvents = events.length;
  const publishedEvents = events.filter((e) => e.status === 'PUBLISHED').length;
  const draftEvents = events.filter((e) => e.status === 'DRAFT').length;

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
      {/* Top Banner */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-800 pb-6">
        <div>
          <h1 className="text-3xl font-extrabold text-white">Organizer Hub</h1>
          <p className="text-sm text-slate-400 mt-1">
            Publish events, manage venues, and track live ticket inventory
          </p>
        </div>

        <button
          onClick={openCreateModal}
          className="inline-flex items-center space-x-2 px-5 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-semibold text-sm shadow-lg shadow-indigo-600/30 transition-all"
        >
          <PlusCircle className="w-4 h-4" />
          <span>Create New Event</span>
        </button>
      </div>

      {successMessage && (
        <div className="bg-emerald-950/40 border border-emerald-800/60 rounded-xl p-4 text-emerald-200 text-sm flex items-center justify-between">
          <span>{successMessage}</span>
          <button onClick={() => setSuccessMessage(null)} className="p-1 hover:text-white">
            <X className="w-4 h-4" />
          </button>
        </div>
      )}

      {error && <ErrorMessage message={error} onRetry={fetchOrganizerData} />}

      {/* Stats Overview Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-6">
        <div className="bg-slate-900/60 border border-slate-800 p-6 rounded-2xl flex items-center space-x-4">
          <div className="w-12 h-12 rounded-xl bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center text-indigo-400">
            <TrendingUp className="w-6 h-6" />
          </div>
          <div>
            <span className="text-xs text-slate-400 block font-medium">Total Events Hosted</span>
            <span className="text-2xl font-extrabold text-white">{totalEvents}</span>
          </div>
        </div>

        <div className="bg-slate-900/60 border border-slate-800 p-6 rounded-2xl flex items-center space-x-4">
          <div className="w-12 h-12 rounded-xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400">
            <CheckCircle className="w-6 h-6" />
          </div>
          <div>
            <span className="text-xs text-slate-400 block font-medium">Published & Live</span>
            <span className="text-2xl font-extrabold text-white">{publishedEvents}</span>
          </div>
        </div>

        <div className="bg-slate-900/60 border border-slate-800 p-6 rounded-2xl flex items-center space-x-4">
          <div className="w-12 h-12 rounded-xl bg-amber-500/10 border border-amber-500/20 flex items-center justify-center text-amber-400">
            <Clock className="w-6 h-6" />
          </div>
          <div>
            <span className="text-xs text-slate-400 block font-medium">Draft & Pending</span>
            <span className="text-2xl font-extrabold text-white">{draftEvents}</span>
          </div>
        </div>
      </div>

      {/* Events Table / List */}
      <div className="bg-slate-900/60 border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
        <div className="p-6 border-b border-slate-800 flex items-center justify-between">
          <h2 className="text-lg font-bold text-white">Your Managed Events</h2>
          <span className="text-xs text-slate-400 font-medium">{events.length} total events</span>
        </div>

        {loading ? (
          <div className="py-12">
            <LoadingSpinner message="Loading your events..." />
          </div>
        ) : events.length === 0 ? (
          <div className="p-16 text-center space-y-3">
            <Calendar className="w-12 h-12 text-slate-600 mx-auto" />
            <h3 className="text-base font-bold text-white">You haven't created any events yet</h3>
            <p className="text-xs text-slate-400 max-w-sm mx-auto">
              Create your first live event to start managing ticket pricing and seat allocation.
            </p>
            <button
              onClick={openCreateModal}
              className="mt-3 inline-flex items-center space-x-2 px-4 py-2 rounded-xl bg-indigo-600 text-white text-xs font-semibold hover:bg-indigo-500"
            >
              <PlusCircle className="w-4 h-4" />
              <span>Create Event</span>
            </button>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="border-b border-slate-800 text-xs font-semibold text-slate-400 uppercase tracking-wider bg-slate-950/40">
                  <th className="py-3.5 px-6">Event</th>
                  <th className="py-3.5 px-4">Date & Time</th>
                  <th className="py-3.5 px-4">Venue & City</th>
                  <th className="py-3.5 px-4">Base Price</th>
                  <th className="py-3.5 px-4">Status</th>
                  <th className="py-3.5 px-6 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800 text-sm">
                {events.map((event) => {
                  const sDate = new Date(event.startTime);
                  return (
                    <tr key={event.id} className="hover:bg-slate-800/30 transition-colors">
                      <td className="py-4 px-6">
                        <div className="font-semibold text-white hover:text-indigo-400 transition-colors">
                          <Link to={`/events/${event.slug || event.id}`}>{event.title}</Link>
                        </div>
                        <div className="text-xs text-slate-400 flex items-center space-x-1.5 mt-0.5">
                          {event.category && <span>{event.category.name}</span>}
                          <span>•</span>
                          <span>ID: {event.id}</span>
                        </div>
                      </td>

                      <td className="py-4 px-4 text-xs text-slate-300">
                        {sDate.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}
                        <div className="text-slate-500">{sDate.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' })}</div>
                      </td>

                      <td className="py-4 px-4 text-xs text-slate-300">
                        {event.venue ? `${event.venue.name}, ${event.venue.city}` : 'TBA'}
                      </td>

                      <td className="py-4 px-4 text-xs font-semibold text-indigo-400">
                        ₹{Number(event.basePrice).toLocaleString('en-IN')}
                      </td>

                      <td className="py-4 px-4">
                        <StatusBadge status={event.status} />
                      </td>

                      <td className="py-4 px-6 text-right">
                        <div className="flex items-center justify-end space-x-1.5">
                          <Link
                            to={`/events/${event.slug || event.id}`}
                            className="p-2 text-slate-400 hover:text-indigo-400 hover:bg-slate-800 rounded-lg transition-colors"
                            title="View Public Page"
                          >
                            <Eye className="w-4 h-4" />
                          </Link>

                          <button
                            onClick={() => openEditModal(event)}
                            className="p-2 text-slate-400 hover:text-amber-400 hover:bg-slate-800 rounded-lg transition-colors"
                            title="Edit Event"
                          >
                            <Edit3 className="w-4 h-4" />
                          </button>

                          {event.status === 'DRAFT' ? (
                            <button
                              onClick={() => handleStatusChange(event, 'PUBLISHED')}
                              className="px-2.5 py-1 text-xs font-semibold rounded-lg bg-emerald-500/10 text-emerald-400 hover:bg-emerald-500/20 border border-emerald-500/30 transition-colors"
                            >
                              Publish
                            </button>
                          ) : event.status === 'PUBLISHED' ? (
                            <button
                              onClick={() => handleStatusChange(event, 'DRAFT')}
                              className="px-2.5 py-1 text-xs font-semibold rounded-lg bg-amber-500/10 text-amber-400 hover:bg-amber-500/20 border border-amber-500/30 transition-colors"
                            >
                              Unpublish
                            </button>
                          ) : null}

                          <button
                            onClick={() => handleDeleteEvent(event)}
                            className="p-2 text-slate-500 hover:text-rose-400 hover:bg-rose-500/10 rounded-lg transition-colors"
                            title="Delete Event"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Create / Edit Event Modal */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-md overflow-y-auto">
          <div className="w-full max-w-2xl bg-slate-900 border border-slate-800 rounded-3xl p-6 sm:p-8 shadow-2xl space-y-6 my-8">
            <div className="flex items-center justify-between border-b border-slate-800 pb-4">
              <div>
                <h3 className="text-xl font-bold text-white">
                  {editingEvent ? `Edit Event: ${editingEvent.title}` : 'Create New Event'}
                </h3>
                <p className="text-xs text-slate-400 mt-0.5">
                  {editingEvent ? 'Update event parameters and ticket base pricing' : 'Publish to SeatSync live discovery catalog'}
                </p>
              </div>
              <button
                onClick={() => setIsModalOpen(false)}
                className="p-1.5 text-slate-400 hover:text-white rounded-lg"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {formError && <ErrorMessage title="Validation Error" message={formError} />}

            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-300">Event Title *</label>
                <input
                  type="text"
                  required
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  placeholder="e.g. Electric Daisy Carnival 2026"
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3.5 py-2.5 text-sm text-white focus:outline-none focus:border-indigo-500"
                />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-300">Category *</label>
                  <select
                    required
                    value={categoryId}
                    onChange={(e) => setCategoryId(Number(e.target.value))}
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2.5 text-sm text-white focus:outline-none focus:border-indigo-500"
                  >
                    {categories.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-300">Venue *</label>
                  <select
                    required
                    value={venueId}
                    onChange={(e) => setVenueId(Number(e.target.value))}
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2.5 text-sm text-white focus:outline-none focus:border-indigo-500"
                  >
                    {venues.map((v) => (
                      <option key={v.id} value={v.id}>
                        {v.name} ({v.city})
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-300">Short Summary * (max 500 chars)</label>
                <input
                  type="text"
                  required
                  maxLength={500}
                  value={shortDescription}
                  onChange={(e) => setShortDescription(e.target.value)}
                  placeholder="Catchy highlight shown on event cards"
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3.5 py-2.5 text-sm text-white focus:outline-none focus:border-indigo-500"
                />
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-300">Full Description</label>
                <textarea
                  rows={3}
                  value={fullDescription}
                  onChange={(e) => setFullDescription(e.target.value)}
                  placeholder="Comprehensive event details, artist line-up, schedule..."
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3.5 py-2 text-sm text-white focus:outline-none focus:border-indigo-500"
                />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-300">Start Date & Time *</label>
                  <input
                    type="datetime-local"
                    required
                    value={startDateTime}
                    onChange={(e) => setStartDateTime(e.target.value)}
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2.5 text-sm text-white focus:outline-none focus:border-indigo-500"
                  />
                </div>

                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-300">End Date & Time *</label>
                  <input
                    type="datetime-local"
                    required
                    value={endDateTime}
                    onChange={(e) => setEndDateTime(e.target.value)}
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2.5 text-sm text-white focus:outline-none focus:border-indigo-500"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-300">Base Ticket Price (₹) *</label>
                  <input
                    type="number"
                    required
                    min={0}
                    value={basePrice}
                    onChange={(e) => setBasePrice(e.target.value)}
                    placeholder="999"
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2.5 text-sm text-white focus:outline-none focus:border-indigo-500"
                  />
                </div>

                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-300">Status</label>
                  <select
                    value={initialStatus}
                    onChange={(e) => setInitialStatus(e.target.value as EventStatus)}
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2.5 text-sm text-white focus:outline-none focus:border-indigo-500"
                  >
                    <option value="DRAFT">DRAFT (Hidden from public)</option>
                    <option value="PUBLISHED">PUBLISHED (Live on explore)</option>
                  </select>
                </div>
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-300">Banner Image URL (Unsplash / Web)</label>
                <input
                  type="url"
                  value={bannerImageUrl}
                  onChange={(e) => setBannerImageUrl(e.target.value)}
                  placeholder="https://images.unsplash.com/..."
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3.5 py-2.5 text-sm text-white focus:outline-none focus:border-indigo-500"
                />
              </div>

              <div className="flex items-center justify-end space-x-3 pt-4 border-t border-slate-800">
                <button
                  type="button"
                  onClick={() => setIsModalOpen(false)}
                  className="px-4 py-2.5 rounded-xl border border-slate-800 text-slate-300 hover:bg-slate-800 text-xs font-semibold"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={submitting}
                  className="px-6 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold shadow-lg shadow-indigo-600/30 disabled:opacity-50"
                >
                  {submitting
                    ? editingEvent
                      ? 'Updating Event...'
                      : 'Saving Event...'
                    : editingEvent
                    ? 'Save Changes'
                    : 'Save & Publish Event'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
