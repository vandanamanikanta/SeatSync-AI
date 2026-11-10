import React from 'react';
import { Ticket, ShieldCheck, Zap, Sparkles, Heart } from 'lucide-react';
import { Link } from 'react-router-dom';

export const Footer: React.FC = () => {
  return (
    <footer className="bg-slate-950 border-t border-slate-900 mt-20">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-8">
          {/* Brand info */}
          <div className="md:col-span-2 space-y-4">
            <div className="flex items-center space-x-2.5">
              <div className="w-8 h-8 rounded-lg bg-indigo-600 flex items-center justify-center">
                <Ticket className="w-4 h-4 text-white transform -rotate-12" />
              </div>
              <span className="font-extrabold text-lg text-white">
                SeatSync<span className="text-indigo-400">AI</span>
              </span>
            </div>
            <p className="text-sm text-slate-400 max-w-sm leading-relaxed">
              High-concurrency, intelligent event discovery and ticket booking engine. Experience zero-friction seat holds with atomic inventory coordination.
            </p>
            <div className="flex items-center space-x-4 text-xs text-slate-500 pt-2">
              <span className="flex items-center space-x-1.5">
                <Zap className="w-3.5 h-3.5 text-amber-400" />
                <span>Real-Time Engine</span>
              </span>
              <span className="flex items-center space-x-1.5">
                <ShieldCheck className="w-3.5 h-3.5 text-emerald-400" />
                <span>Atomic Concurrency</span>
              </span>
              <span className="flex items-center space-x-1.5">
                <Sparkles className="w-3.5 h-3.5 text-indigo-400" />
                <span>AI Recommendations</span>
              </span>
            </div>
          </div>

          {/* Quick Links */}
          <div>
            <h4 className="text-sm font-semibold text-slate-200 mb-3 tracking-wide">Explore</h4>
            <ul className="space-y-2 text-sm text-slate-400">
              <li>
                <Link to="/" className="hover:text-indigo-400 transition-colors">
                  Home Discovery
                </Link>
              </li>
              <li>
                <Link to="/events" className="hover:text-indigo-400 transition-colors">
                  Browse All Events
                </Link>
              </li>
              <li>
                <Link to="/events?category=concerts-music" className="hover:text-indigo-400 transition-colors">
                  Concerts & Music
                </Link>
              </li>
              <li>
                <Link to="/events?category=sports-fitness" className="hover:text-indigo-400 transition-colors">
                  Sports & Arena
                </Link>
              </li>
            </ul>
          </div>

          {/* Account & Organizer */}
          <div>
            <h4 className="text-sm font-semibold text-slate-200 mb-3 tracking-wide">Platform</h4>
            <ul className="space-y-2 text-sm text-slate-400">
              <li>
                <Link to="/login" className="hover:text-indigo-400 transition-colors">
                  Sign In
                </Link>
              </li>
              <li>
                <Link to="/register" className="hover:text-indigo-400 transition-colors">
                  Create Account
                </Link>
              </li>
              <li>
                <Link to="/organizer/dashboard" className="hover:text-indigo-400 transition-colors">
                  Organizer Hub
                </Link>
              </li>
              <li>
                <span className="text-xs text-slate-600 block mt-2">
                  Spring Boot 3.4 • MySQL 8.0 • React 18
                </span>
              </li>
            </ul>
          </div>
        </div>

        <div className="mt-12 pt-8 border-t border-slate-900 flex flex-col sm:flex-row items-center justify-between text-xs text-slate-500">
          <p>© {new Date().getFullYear()} SeatSync AI. All rights reserved.</p>
          <p className="flex items-center space-x-1 mt-2 sm:mt-0">
            <span>Built for high-scale event discovery</span>
          </p>
        </div>
      </div>
    </footer>
  );
};

