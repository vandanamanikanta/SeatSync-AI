import React, { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Search, Filter, MapPin, SlidersHorizontal, X, ChevronLeft, ChevronRight, Calendar, Sparkles } from 'lucide-react';
import { eventApi } from '../api/eventApi';
import { categoryApi } from '../api/categoryApi';
import { EventCategory, EventItem, PageResponse } from '../types';
import { EventCard } from '../components/common/EventCard';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorMessage } from '../components/common/ErrorMessage';

export const ExploreEventsPage: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();

  // Search filter states from URL query
  const search = searchParams.get('search') || '';
  const category = searchParams.get('category') || '';
  const city = searchParams.get('city') || '';
  const minPriceStr = searchParams.get('minPrice') || '';
  const maxPriceStr = searchParams.get('maxPrice') || '';
  const sort = searchParams.get('sort') || 'startTime,asc';
  const pageStr = searchParams.get('page') || '0';

  const [categories, setCategories] = useState<EventCategory[]>([]);
  const [eventsData, setEventsData] = useState<PageResponse<EventItem> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Local filter form state
  const [searchInput, setSearchInput] = useState(search);
  const [cityInput, setCityInput] = useState(city);
  const [selectedCategory, setSelectedCategory] = useState(category);
  const [minPrice, setMinPrice] = useState(minPriceStr);
  const [maxPrice, setMaxPrice] = useState(maxPriceStr);
  const [selectedSort, setSelectedSort] = useState(sort);
  const [showMobileFilters, setShowMobileFilters] = useState(false);

  // Sync inputs when searchParams change externally
  useEffect(() => {
    setSearchInput(search);
    setCityInput(city);
    setSelectedCategory(category);
    setMinPrice(minPriceStr);
    setMaxPrice(maxPriceStr);
    setSelectedSort(sort);
  }, [search, category, city, minPriceStr, maxPriceStr, sort]);

  // Load categories once
  useEffect(() => {
    categoryApi.getCategories().then(setCategories).catch(() => {});
  }, []);

  // Fetch events whenever URL query changes
  useEffect(() => {
    const fetchEvents = async () => {
      try {
        setLoading(true);
        setError(null);

        const page = parseInt(pageStr, 10) || 0;
        const res = await eventApi.getEvents({
          search: search || undefined,
          category: category || undefined,
          city: city || undefined,
          minPrice: minPriceStr ? Number(minPriceStr) : undefined,
          maxPrice: maxPriceStr ? Number(maxPriceStr) : undefined,
          sort,
          page,
          size: 9,
        });

        setEventsData(res);
      } catch (err) {
        setError('Failed to fetch events from backend. Please verify your connection.');
      } finally {
        setLoading(false);
      }
    };

    fetchEvents();
  }, [search, category, city, minPriceStr, maxPriceStr, sort, pageStr]);

  const applyFilters = (newPage = 0) => {
    const params = new URLSearchParams();
    if (searchInput.trim()) params.set('search', searchInput.trim());
    if (selectedCategory.trim()) params.set('category', selectedCategory.trim());
    if (cityInput.trim()) params.set('city', cityInput.trim());
    if (minPrice.trim()) params.set('minPrice', minPrice.trim());
    if (maxPrice.trim()) params.set('maxPrice', maxPrice.trim());
    if (selectedSort) params.set('sort', selectedSort);
    params.set('page', newPage.toString());

    setSearchParams(params);
    setShowMobileFilters(false);
  };

  const handleCategoryQuickFilter = (catSlug: string) => {
    const newCat = selectedCategory === catSlug ? '' : catSlug;
    setSelectedCategory(newCat);
    const params = new URLSearchParams(searchParams);
    if (newCat) {
      params.set('category', newCat);
    } else {
      params.delete('category');
    }
    params.set('page', '0');
    setSearchParams(params);
  };

  const handleResetFilters = () => {
    setSearchInput('');
    setCityInput('');
    setSelectedCategory('');
    setMinPrice('');
    setMaxPrice('');
    setSelectedSort('startTime,asc');
    setSearchParams(new URLSearchParams());
  };

  const hasActiveFilters = !!(search || category || city || minPriceStr || maxPriceStr);

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-800 pb-6">
        <div>
          <h1 className="text-3xl font-extrabold text-white">Explore Events</h1>
          <p className="text-sm text-slate-400 mt-1">
            Discover upcoming concerts, sports games, theatre, and conferences
          </p>
        </div>

        {/* Mobile filter toggle */}
        <button
          onClick={() => setShowMobileFilters(!showMobileFilters)}
          className="lg:hidden flex items-center justify-center space-x-2 px-4 py-2.5 bg-slate-900 border border-slate-800 rounded-xl text-slate-200 text-sm font-semibold hover:border-slate-700 transition-colors"
        >
          <Filter className="w-4 h-4 text-indigo-400" />
          <span>Filters & Sort</span>
        </button>
      </div>

      {/* Category Quick Chips Bar */}
      {categories.length > 0 && (
        <div className="flex items-center space-x-2 overflow-x-auto pb-2 scrollbar-none">
          <button
            onClick={() => handleCategoryQuickFilter('')}
            className={`px-3.5 py-1.5 rounded-full text-xs font-semibold whitespace-nowrap transition-colors border ${
              !category
                ? 'bg-indigo-600 text-white border-indigo-500 shadow-md shadow-indigo-600/20'
                : 'bg-slate-900 text-slate-400 border-slate-800 hover:text-white hover:border-slate-700'
            }`}
          >
            All Events
          </button>
          {categories.map((cat) => (
            <button
              key={cat.id}
              onClick={() => handleCategoryQuickFilter(cat.slug)}
              className={`px-3.5 py-1.5 rounded-full text-xs font-semibold whitespace-nowrap transition-colors border ${
                category === cat.slug
                  ? 'bg-indigo-600 text-white border-indigo-500 shadow-md shadow-indigo-600/20'
                  : 'bg-slate-900 text-slate-400 border-slate-800 hover:text-white hover:border-slate-700'
              }`}
            >
              {cat.name}
            </button>
          ))}
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-4 gap-8">
        {/* Filters Sidebar */}
        <div
          className={`lg:block ${
            showMobileFilters ? 'block' : 'hidden'
          } lg:col-span-1 bg-slate-900/70 border border-slate-800 rounded-2xl p-6 h-fit space-y-6 shadow-xl`}
        >
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-2 text-white font-bold text-base">
              <SlidersHorizontal className="w-4 h-4 text-indigo-400" />
              <span>Filters</span>
            </div>
            {hasActiveFilters && (
              <button
                onClick={handleResetFilters}
                className="text-xs font-semibold text-rose-400 hover:text-rose-300 transition-colors"
              >
                Reset All
              </button>
            )}
          </div>

          {/* Keyword Search */}
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-300">Keyword</label>
            <div className="relative">
              <Search className="w-4 h-4 text-slate-500 absolute left-3 top-3" />
              <input
                type="text"
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                placeholder="Event title or keyword..."
                className="w-full bg-slate-950 border border-slate-800 rounded-xl pl-9 pr-3 py-2 text-sm text-white placeholder-slate-500 focus:outline-none focus:border-indigo-500"
              />
            </div>
          </div>

          {/* Category Filter */}
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-300">Category</label>
            <select
              value={selectedCategory}
              onChange={(e) => setSelectedCategory(e.target.value)}
              className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500"
            >
              <option value="">All Categories</option>
              {categories.map((cat) => (
                <option key={cat.id} value={cat.slug}>
                  {cat.name}
                </option>
              ))}
            </select>
          </div>

          {/* City Filter */}
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-300">City</label>
            <div className="relative">
              <MapPin className="w-4 h-4 text-slate-500 absolute left-3 top-3" />
              <input
                type="text"
                value={cityInput}
                onChange={(e) => setCityInput(e.target.value)}
                placeholder="e.g. Mumbai, Delhi..."
                className="w-full bg-slate-950 border border-slate-800 rounded-xl pl-9 pr-3 py-2 text-sm text-white placeholder-slate-500 focus:outline-none focus:border-indigo-500"
              />
            </div>
          </div>

          {/* Price Range */}
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-300">Price Range (₹)</label>
            <div className="grid grid-cols-2 gap-2">
              <input
                type="number"
                value={minPrice}
                onChange={(e) => setMinPrice(e.target.value)}
                placeholder="Min ₹"
                className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-sm text-white placeholder-slate-500 focus:outline-none focus:border-indigo-500"
              />
              <input
                type="number"
                value={maxPrice}
                onChange={(e) => setMaxPrice(e.target.value)}
                placeholder="Max ₹"
                className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-sm text-white placeholder-slate-500 focus:outline-none focus:border-indigo-500"
              />
            </div>
          </div>

          {/* Sort Order */}
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-300">Sort By</label>
            <select
              value={selectedSort}
              onChange={(e) => setSelectedSort(e.target.value)}
              className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500"
            >
              <option value="startTime,asc">Date: Earliest First</option>
              <option value="startTime,desc">Date: Latest First</option>
              <option value="basePrice,asc">Price: Low to High</option>
              <option value="basePrice,desc">Price: High to Low</option>
              <option value="title,asc">Title: A-Z</option>
            </select>
          </div>

          <button
            onClick={() => applyFilters(0)}
            className="w-full py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-semibold text-sm transition-all shadow-md shadow-indigo-600/30"
          >
            Apply Filters
          </button>
        </div>

        {/* Event Grid & Pagination */}
        <div className="lg:col-span-3 space-y-6">
          {/* Active filter badges & result count */}
          <div className="flex items-center justify-between text-xs text-slate-400">
            <span>
              {eventsData ? (
                <>
                  Found <strong className="text-white">{eventsData.totalElements}</strong> live event
                  {eventsData.totalElements === 1 ? '' : 's'}
                </>
              ) : (
                'Loading...'
              )}
            </span>

            {hasActiveFilters && (
              <div className="flex items-center space-x-1.5">
                <span className="text-indigo-400 font-medium">Filtered</span>
                <button
                  onClick={handleResetFilters}
                  className="p-1 hover:text-white rounded"
                  title="Clear filters"
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              </div>
            )}
          </div>

          {loading ? (
            <LoadingSpinner message="Searching events..." />
          ) : error ? (
            <ErrorMessage message={error} onRetry={() => applyFilters(0)} />
          ) : !eventsData || eventsData.content.length === 0 ? (
            <div className="bg-slate-900/40 border border-slate-800 rounded-2xl p-16 text-center space-y-3">
              <Calendar className="w-12 h-12 text-slate-600 mx-auto" />
              <h3 className="text-lg font-bold text-white">No events match your criteria</h3>
              <p className="text-sm text-slate-400 max-w-sm mx-auto">
                Try loosening your search terms, adjusting price filters, or clearing category selections.
              </p>
              {hasActiveFilters && (
                <button
                  onClick={handleResetFilters}
                  className="mt-4 px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-xs font-semibold text-indigo-300"
                >
                  Clear All Filters
                </button>
              )}
            </div>
          ) : (
            <>
              {/* Event Cards Grid */}
              <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-6">
                {eventsData.content.map((event) => (
                  <EventCard key={event.id} event={event} />
                ))}
              </div>

              {/* Pagination Controls */}
              {eventsData.totalPages > 1 && (
                <div className="flex items-center justify-center space-x-2 pt-8">
                  <button
                    disabled={eventsData.pageNumber === 0}
                    onClick={() => applyFilters(eventsData.pageNumber - 1)}
                    className="p-2 rounded-xl border border-slate-800 bg-slate-900 text-slate-300 hover:text-white disabled:opacity-40 disabled:cursor-not-allowed"
                  >
                    <ChevronLeft className="w-5 h-5" />
                  </button>

                  <span className="px-4 py-2 text-xs font-semibold text-slate-400">
                    Page {eventsData.pageNumber + 1} of {eventsData.totalPages}
                  </span>

                  <button
                    disabled={eventsData.last}
                    onClick={() => applyFilters(eventsData.pageNumber + 1)}
                    className="p-2 rounded-xl border border-slate-800 bg-slate-900 text-slate-300 hover:text-white disabled:opacity-40 disabled:cursor-not-allowed"
                  >
                    <ChevronRight className="w-5 h-5" />
                  </button>
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
};
