import React from 'react';
import { Link } from 'react-router-dom';
import { Calendar, MapPin, Tag } from 'lucide-react';
import { EventItem } from '../../types';
import { CategoryBadge, StatusBadge } from './Badge';

interface EventCardProps {
  event: EventItem;
  showStatus?: boolean;
}

export const EventCard: React.FC<EventCardProps> = ({ event, showStatus = false }) => {
  // Format start time
  const startDate = new Date(event.startTime);
  const formattedDate = startDate.toLocaleDateString('en-US', {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });
  const formattedTime = startDate.toLocaleTimeString('en-US', {
    hour: 'numeric',
    minute: '2-digit',
  });

  const getCategoryFallbackImage = (catSlug?: string) => {
    switch (catSlug) {
      case 'sports-fitness':
        return 'https://images.unsplash.com/photo-1508098682722-e99c43a406b2?auto=format&fit=crop&w=800&q=80';
      case 'standup-comedy':
        return 'https://images.unsplash.com/photo-1585699324551-f6c309eedeca?auto=format&fit=crop&w=800&q=80';
      case 'theatre-arts':
        return 'https://images.unsplash.com/photo-1507676184212-d03ab07a01bf?auto=format&fit=crop&w=800&q=80';
      case 'tech-conferences':
        return 'https://images.unsplash.com/photo-1540575467063-178a50c2df87?auto=format&fit=crop&w=800&q=80';
      default:
        return 'https://images.unsplash.com/photo-1501281668745-f7f57925c3b4?auto=format&fit=crop&w=800&q=80';
    }
  };

  const fallbackImage = getCategoryFallbackImage(event.category?.slug);
  const imageUrl =
    event.bannerImageUrl && event.bannerImageUrl.trim().startsWith('http')
      ? event.bannerImageUrl
      : fallbackImage;

  return (
    <Link
      to={`/events/${event.slug || event.id}`}
      className="group relative flex flex-col bg-slate-900/80 rounded-2xl overflow-hidden border border-slate-800 hover:border-indigo-500/50 shadow-md hover:shadow-2xl hover:shadow-indigo-500/10 transition-all duration-300 transform hover:-translate-y-1"
    >
      {/* Event Image & Badges */}
      <div className="relative aspect-[16/9] w-full overflow-hidden bg-slate-800">
        <img
          src={imageUrl}
          alt={event.title}
          onError={(e) => {
            (e.target as HTMLImageElement).src = fallbackImage;
          }}
          className="w-full h-full object-cover transition-transform duration-500 group-hover:scale-105"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-slate-950/90 via-slate-950/30 to-transparent" />

        {/* Top Badges */}
        <div className="absolute top-3 left-3 right-3 flex items-center justify-between">
          {event.category ? (
            <CategoryBadge name={event.category.name} />
          ) : (
            <div />
          )}
          {showStatus && <StatusBadge status={event.status} />}
        </div>

        {/* Price Tag Overlay */}
        <div className="absolute bottom-3 right-3 bg-slate-900/90 backdrop-blur-md px-3 py-1 rounded-lg border border-slate-700/60 shadow-lg">
          <span className="text-[11px] text-slate-400 block -mb-0.5 font-medium">Starts from</span>
          <span className="text-sm font-bold text-indigo-400">
            ₹{Number(event.basePrice).toLocaleString('en-IN')}
          </span>
        </div>
      </div>

      {/* Content */}
      <div className="flex-1 p-5 flex flex-col justify-between">
        <div>
          <h3 className="text-lg font-bold text-slate-100 group-hover:text-indigo-400 transition-colors line-clamp-1">
            {event.title}
          </h3>

          <p className="mt-2 text-xs text-slate-400 line-clamp-2 leading-relaxed">
            {event.shortDescription}
          </p>
        </div>

        {/* Meta Info */}
        <div className="mt-4 pt-4 border-t border-slate-800/80 space-y-2 text-xs text-slate-300">
          <div className="flex items-center space-x-2">
            <Calendar className="w-4 h-4 text-indigo-400 flex-shrink-0" />
            <span>
              {formattedDate} • {formattedTime}
            </span>
          </div>

          <div className="flex items-center space-x-2">
            <MapPin className="w-4 h-4 text-rose-400 flex-shrink-0" />
            <span className="truncate">
              {event.venue ? `${event.venue.name}, ${event.venue.city}` : 'Venue TBA'}
            </span>
          </div>
        </div>
      </div>
    </Link>
  );
};
