import React from 'react';
import { EventStatus } from '../../types';

interface StatusBadgeProps {
  status: EventStatus;
}

export const StatusBadge: React.FC<StatusBadgeProps> = ({ status }) => {
  const styles: Record<EventStatus, string> = {
    PUBLISHED: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
    DRAFT: 'bg-amber-500/10 text-amber-400 border-amber-500/20',
    POSTPONED: 'bg-orange-500/10 text-orange-400 border-orange-500/20',
    CANCELLED: 'bg-rose-500/10 text-rose-400 border-rose-500/20',
    COMPLETED: 'bg-slate-500/10 text-slate-400 border-slate-500/20',
  };

  return (
    <span
      className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold border ${
        styles[status] || styles.DRAFT
      }`}
    >
      {status}
    </span>
  );
};

interface CategoryBadgeProps {
  name: string;
}

export const CategoryBadge: React.FC<CategoryBadgeProps> = ({ name }) => {
  return (
    <span className="inline-flex items-center px-2.5 py-1 rounded-md text-xs font-medium bg-indigo-500/15 text-indigo-300 border border-indigo-500/20 backdrop-blur-sm">
      {name}
    </span>
  );
};

