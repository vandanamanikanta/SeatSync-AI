import React, { useState, useEffect } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import {
  Ticket,
  Menu,
  X,
  User,
  LogOut,
  LayoutDashboard,
  ChevronDown,
  Music,
  Trophy,
  Smile,
  Drama,
  Cpu,
  Layers,
  Sparkles,
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { categoryApi } from '../../api/categoryApi';
import { EventCategory } from '../../types';

export const Navbar: React.FC = () => {
  const { user, isAuthenticated, isOrganizer, logout } = useAuth();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [categoriesOpen, setCategoriesOpen] = useState(false);
  const [categories, setCategories] = useState<EventCategory[]>([]);
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    categoryApi.getCategories().then(setCategories).catch(() => {});
  }, []);

  const handleLogout = () => {
    logout();
    navigate('/');
    setMobileMenuOpen(false);
    setCategoriesOpen(false);
  };

  const isActive = (path: string) => {
    if (path === '/' && location.pathname === '/') return true;
    if (path !== '/' && location.pathname.startsWith(path)) return true;
    return false;
  };

  const getCategoryIcon = (slug: string) => {
    switch (slug) {
      case 'concerts-music':
        return <Music className="w-4 h-4 text-indigo-400" />;
      case 'sports-fitness':
        return <Trophy className="w-4 h-4 text-amber-400" />;
      case 'standup-comedy':
        return <Smile className="w-4 h-4 text-emerald-400" />;
      case 'theatre-arts':
        return <Drama className="w-4 h-4 text-rose-400" />;
      case 'tech-conferences':
        return <Cpu className="w-4 h-4 text-cyan-400" />;
      default:
        return <Layers className="w-4 h-4 text-indigo-400" />;
    }
  };

  return (
    <nav className="sticky top-0 z-50 bg-slate-950/90 backdrop-blur-md border-b border-slate-800/80">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16">
          {/* Logo & Brand */}
          <div className="flex items-center space-x-3">
            <Link to="/" className="flex items-center space-x-2.5 group">
              <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-indigo-600 via-indigo-500 to-violet-500 flex items-center justify-center shadow-lg shadow-indigo-500/20 group-hover:shadow-indigo-500/40 transition-shadow">
                <Ticket className="w-5 h-5 text-white transform -rotate-12" />
              </div>
              <div className="flex flex-col">
                <span className="font-extrabold text-xl tracking-tight bg-gradient-to-r from-white via-slate-100 to-indigo-300 bg-clip-text text-transparent">
                  SeatSync<span className="text-indigo-400">AI</span>
                </span>
                <span className="text-[10px] text-slate-400 tracking-wider uppercase font-semibold -mt-1">
                  Live Event Discovery
                </span>
              </div>
            </Link>

            {/* Desktop Navigation Links */}
            <div className="hidden md:flex items-center space-x-1 ml-8">
              <Link
                to="/"
                className={`px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                  isActive('/')
                    ? 'text-indigo-400 bg-indigo-500/10'
                    : 'text-slate-300 hover:text-white hover:bg-slate-900'
                }`}
              >
                Home
              </Link>

              <Link
                to="/events"
                className={`px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                  isActive('/events') && !location.search.includes('category=')
                    ? 'text-indigo-400 bg-indigo-500/10'
                    : 'text-slate-300 hover:text-white hover:bg-slate-900'
                }`}
              >
                Explore Events
              </Link>

              <Link
                to="/ai-assistant"
                className={`flex items-center space-x-1.5 px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                  isActive('/ai-assistant')
                    ? 'text-indigo-400 bg-indigo-500/10'
                    : 'text-indigo-300 hover:text-white hover:bg-slate-900'
                }`}
              >
                <Sparkles className="w-4 h-4 text-indigo-400" />
                <span>AI Assistant</span>
              </Link>

              {isAuthenticated && (
                <Link
                  to="/my-bookings"
                  className={`px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                    isActive('/my-bookings')
                      ? 'text-indigo-400 bg-indigo-500/10'
                      : 'text-slate-300 hover:text-white hover:bg-slate-900'
                  }`}
                >
                  My Bookings
                </Link>
              )}

              {/* Categories Dropdown */}
              <div className="relative">
                <button
                  onClick={() => setCategoriesOpen(!categoriesOpen)}
                  onBlur={() => setTimeout(() => setCategoriesOpen(false), 200)}
                  className={`flex items-center space-x-1.5 px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                    location.search.includes('category=')
                      ? 'text-indigo-400 bg-indigo-500/10'
                      : 'text-slate-300 hover:text-white hover:bg-slate-900'
                  }`}
                >
                  <span>Categories</span>
                  <ChevronDown className="w-4 h-4 text-slate-400" />
                </button>

                {categoriesOpen && (
                  <div className="absolute left-0 mt-2 w-64 bg-slate-900 border border-slate-800 rounded-2xl shadow-2xl py-2 z-50 backdrop-blur-xl">
                    <div className="px-3 py-1.5 text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
                      Event Categories
                    </div>
                    {categories.map((cat) => (
                      <Link
                        key={cat.id}
                        to={`/events?category=${encodeURIComponent(cat.slug)}`}
                        onClick={() => setCategoriesOpen(false)}
                        className="flex items-center space-x-2.5 px-3 py-2 text-sm text-slate-200 hover:text-white hover:bg-slate-800/60 transition-colors"
                      >
                        {getCategoryIcon(cat.slug)}
                        <span className="truncate">{cat.name}</span>
                      </Link>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>

          {/* Desktop Right Actions */}
          <div className="hidden md:flex items-center space-x-3">
            {isAuthenticated ? (
              <>
                {isOrganizer && (
                  <Link
                    to="/organizer/dashboard"
                    className="flex items-center space-x-2 px-3.5 py-1.5 rounded-lg text-xs font-semibold bg-indigo-600/20 text-indigo-300 hover:bg-indigo-600/30 border border-indigo-500/30 transition-colors"
                  >
                    <LayoutDashboard className="w-4 h-4 text-indigo-400" />
                    <span>Organizer Hub</span>
                  </Link>
                )}

                <div className="flex items-center space-x-2.5 bg-slate-900 border border-slate-800 px-3 py-1.5 rounded-xl">
                  <div className="w-7 h-7 rounded-lg bg-indigo-600/20 flex items-center justify-center text-indigo-400">
                    <User className="w-4 h-4" />
                  </div>
                  <div className="text-left">
                    <div className="text-xs font-semibold text-slate-200 truncate max-w-[120px]">
                      {user?.fullName}
                    </div>
                    <div className="text-[10px] text-slate-400 uppercase tracking-wider font-mono">
                      {user?.roles?.[0]?.replace('ROLE_', '') || 'User'}
                    </div>
                  </div>
                </div>

                <button
                  onClick={handleLogout}
                  className="p-2 text-slate-400 hover:text-rose-400 hover:bg-rose-500/10 rounded-lg transition-colors"
                  title="Sign Out"
                >
                  <LogOut className="w-4 h-4" />
                </button>
              </>
            ) : (
              <>
                <Link
                  to="/login"
                  className="px-4 py-2 text-sm font-semibold text-slate-300 hover:text-white transition-colors"
                >
                  Sign In
                </Link>
                <Link
                  to="/register"
                  className="px-4 py-2 rounded-xl text-sm font-semibold bg-indigo-600 hover:bg-indigo-500 text-white shadow-md shadow-indigo-500/20 transition-all hover:shadow-indigo-500/40"
                >
                  Get Started
                </Link>
              </>
            )}
          </div>

          {/* Mobile menu button */}
          <div className="flex md:hidden items-center">
            <button
              onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
              className="p-2 text-slate-400 hover:text-white rounded-lg"
            >
              {mobileMenuOpen ? <X className="w-6 h-6" /> : <Menu className="w-6 h-6" />}
            </button>
          </div>
        </div>
      </div>

      {/* Mobile menu dropdown */}
      {mobileMenuOpen && (
        <div className="md:hidden bg-slate-950 border-b border-slate-800 px-4 pt-2 pb-6 space-y-2">
          <Link
            to="/"
            onClick={() => setMobileMenuOpen(false)}
            className="block px-3 py-2 rounded-lg text-base font-medium text-slate-200 hover:bg-slate-900"
          >
            Home
          </Link>
          <Link
            to="/events"
            onClick={() => setMobileMenuOpen(false)}
            className="block px-3 py-2 rounded-lg text-base font-medium text-slate-200 hover:bg-slate-900"
          >
            Explore Events
          </Link>
          <Link
            to="/ai-assistant"
            onClick={() => setMobileMenuOpen(false)}
            className="flex items-center space-x-2 px-3 py-2 rounded-lg text-base font-medium text-indigo-300 hover:bg-slate-900"
          >
            <Sparkles className="w-5 h-5 text-indigo-400" />
            <span>AI Assistant</span>
          </Link>
          {isAuthenticated && (
            <Link
              to="/my-bookings"
              onClick={() => setMobileMenuOpen(false)}
              className="block px-3 py-2 rounded-lg text-base font-medium text-slate-200 hover:bg-slate-900"
            >
              My Bookings
            </Link>
          )}

          {/* Categories Submenu */}
          <div className="py-2 border-t border-slate-800">
            <div className="px-3 text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">
              Browse Categories
            </div>
            <div className="grid grid-cols-2 gap-1.5 px-1">
              {categories.map((cat) => (
                <Link
                  key={cat.id}
                  to={`/events?category=${encodeURIComponent(cat.slug)}`}
                  onClick={() => setMobileMenuOpen(false)}
                  className="flex items-center space-x-2 px-2.5 py-1.5 rounded-lg text-xs font-medium text-slate-300 hover:text-white hover:bg-slate-900"
                >
                  {getCategoryIcon(cat.slug)}
                  <span className="truncate">{cat.name}</span>
                </Link>
              ))}
            </div>
          </div>

          {isAuthenticated ? (
            <div className="pt-4 border-t border-slate-800 space-y-2">
              <div className="px-3 py-2 bg-slate-900 rounded-lg text-sm text-slate-300">
                Logged in as <span className="font-semibold text-white">{user?.fullName}</span>
              </div>
              {isOrganizer && (
                <Link
                  to="/organizer/dashboard"
                  onClick={() => setMobileMenuOpen(false)}
                  className="flex items-center space-x-2 px-3 py-2 rounded-lg text-base font-medium text-indigo-400 hover:bg-slate-900"
                >
                  <LayoutDashboard className="w-5 h-5" />
                  <span>Organizer Dashboard</span>
                </Link>
              )}
              <button
                onClick={handleLogout}
                className="w-full text-left flex items-center space-x-2 px-3 py-2 rounded-lg text-base font-medium text-rose-400 hover:bg-rose-500/10"
              >
                <LogOut className="w-5 h-5" />
                <span>Sign Out</span>
              </button>
            </div>
          ) : (
            <div className="pt-4 border-t border-slate-800 grid grid-cols-2 gap-2">
              <Link
                to="/login"
                onClick={() => setMobileMenuOpen(false)}
                className="text-center px-4 py-2.5 rounded-xl text-sm font-semibold border border-slate-700 text-slate-200 hover:bg-slate-900"
              >
                Sign In
              </Link>
              <Link
                to="/register"
                onClick={() => setMobileMenuOpen(false)}
                className="text-center px-4 py-2.5 rounded-xl text-sm font-semibold bg-indigo-600 text-white hover:bg-indigo-500"
              >
                Register
              </Link>
            </div>
          )}
        </div>
      )}
    </nav>
  );
};
