import React, { useState, useRef, useEffect } from 'react';
import { Link } from 'react-router-dom';
import {
  Sparkles,
  Send,
  Bot,
  User as UserIcon,
  MapPin,
  Calendar,
  Tag,
  ArrowRight,
  Ticket,
  AlertCircle,
  RefreshCw,
  Search,
  CheckCircle2,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { aiApi } from '../api/aiApi';
import { AiChatResponseData, AiEventCard } from '../types';
import { getApiErrorMessage } from '../api/client';

interface ChatMessage {
  id: string;
  sender: 'user' | 'assistant';
  text: string;
  timestamp: Date;
  data?: AiChatResponseData;
}

const QUICK_PROMPTS = [
  'Find music concerts in Mumbai',
  'Show events under ₹2,000',
  'Find comedy events this weekend',
  'Recommend events similar to a rock concert',
  'Which upcoming events are available?',
];

export const AiAssistantPage: React.FC = () => {
  const { isAuthenticated } = useAuth();
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      id: 'welcome',
      sender: 'assistant',
      text: "Hello! I am your SeatSync AI Event Concierge powered by Google Gemini. Ask me about live concerts, shows, cities, dates, or ticket prices, and I'll find matching events directly from our live catalog!",
      timestamp: new Date(),
    },
  ]);
  const [input, setInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isLiveActive, setIsLiveActive] = useState<boolean | null>(null);

  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    aiApi.getStatus().then(setIsLiveActive).catch(() => setIsLiveActive(false));
  }, []);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages, isLoading]);

  const handleSend = async (messageToSend?: string) => {
    const text = (messageToSend || input).trim();
    if (!text || isLoading) return;

    if (!isAuthenticated) {
      setError('Please log in or register to chat with the SeatSync AI Assistant.');
      return;
    }

    setError(null);
    setInput('');

    const userMessage: ChatMessage = {
      id: Date.now().toString(),
      sender: 'user',
      text,
      timestamp: new Date(),
    };

    setMessages((prev) => [...prev, userMessage]);
    setIsLoading(true);

    try {
      const data = await aiApi.chat({ message: text });
      const assistantMessage: ChatMessage = {
        id: (Date.now() + 1).toString(),
        sender: 'assistant',
        text: data.response,
        timestamp: new Date(),
        data,
      };
      setMessages((prev) => [...prev, assistantMessage]);
    } catch (err: unknown) {
      const errMsg = getApiErrorMessage(err, 'Failed to get a response from the AI assistant. Please try again.');
      setError(errMsg);
    } finally {
      setIsLoading(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className="min-h-[calc(100vh-4rem)] flex flex-col bg-slate-950 text-slate-100">
      {/* Header Banner */}
      <div className="bg-slate-900/60 border-b border-slate-800/80 backdrop-blur-sm px-4 sm:px-6 py-4">
        <div className="max-w-5xl mx-auto flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
          <div className="flex items-center space-x-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-indigo-500 via-purple-500 to-pink-500 flex items-center justify-center shadow-lg shadow-indigo-500/20">
              <Sparkles className="w-5 h-5 text-white" />
            </div>
            <div>
              <h1 className="text-lg font-bold text-white flex items-center gap-2">
                SeatSync AI Concierge
                <span className="text-[11px] font-semibold px-2 py-0.5 rounded-full bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
                  Gemini Grounded RAG
                </span>
              </h1>
              <p className="text-xs text-slate-400">
                Natural-language event discovery powered by live catalog grounding
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <span
              className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium border ${
                isLiveActive
                  ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20'
                  : 'bg-indigo-500/10 text-indigo-300 border-indigo-500/20'
              }`}
            >
              <CheckCircle2 className="w-3.5 h-3.5 mr-1" />
              {isLiveActive ? 'Gemini 2.5 Flash Online' : 'SeatSync Grounded Concierge Active'}
            </span>
          </div>
        </div>
      </div>

      {/* Main Chat Container */}
      <div className="flex-1 max-w-5xl w-full mx-auto px-4 sm:px-6 py-6 flex flex-col">
        {/* Messages List */}
        <div className="flex-1 space-y-6 overflow-y-auto pb-6">
          {messages.map((msg) => (
            <div
              key={msg.id}
              className={`flex gap-3.5 ${msg.sender === 'user' ? 'justify-end' : 'justify-start'}`}
            >
              {msg.sender === 'assistant' && (
                <div className="w-8 h-8 rounded-lg bg-indigo-600/30 border border-indigo-500/30 flex items-center justify-center flex-shrink-0 mt-0.5">
                  <Bot className="w-4 h-4 text-indigo-400" />
                </div>
              )}

              <div
                className={`max-w-2xl rounded-2xl px-4 py-3 shadow-md ${
                  msg.sender === 'user'
                    ? 'bg-gradient-to-r from-indigo-600 to-indigo-700 text-white rounded-tr-none'
                    : 'bg-slate-900 border border-slate-800 text-slate-100 rounded-tl-none'
                }`}
              >
                <div className="text-sm leading-relaxed whitespace-pre-line">{msg.text}</div>

                {msg.data?.provider && (
                  <div className="mt-2 text-[10px] text-slate-400 font-medium flex items-center gap-1">
                    <span className="w-1.5 h-1.5 rounded-full bg-indigo-400"></span>
                    <span>Provider: {msg.data.provider}</span>
                  </div>
                )}

                {/* Extracted Query Filter Tags */}
                {msg.data?.extractedFilters && (
                  <div className="mt-3 pt-3 border-t border-slate-800/80 flex flex-wrap items-center gap-1.5 text-[11px]">
                    <span className="text-slate-400 font-semibold mr-1">Parsed Filters:</span>
                    {msg.data.extractedFilters.category && (
                      <span className="px-2 py-0.5 rounded-md bg-indigo-950/60 text-indigo-300 border border-indigo-800/60">
                        {msg.data.extractedFilters.category}
                      </span>
                    )}
                    {msg.data.extractedFilters.city && (
                      <span className="px-2 py-0.5 rounded-md bg-emerald-950/60 text-emerald-300 border border-emerald-800/60">
                        {msg.data.extractedFilters.city}
                      </span>
                    )}
                    {msg.data.extractedFilters.maxBudget && (
                      <span className="px-2 py-0.5 rounded-md bg-amber-950/60 text-amber-300 border border-amber-800/60">
                        Budget: ≤ ₹{Number(msg.data.extractedFilters.maxBudget).toLocaleString('en-IN')}
                      </span>
                    )}
                    {msg.data.extractedFilters.timeframe && (
                      <span className="px-2 py-0.5 rounded-md bg-purple-950/60 text-purple-300 border border-purple-800/60">
                        {msg.data.extractedFilters.timeframe}
                      </span>
                    )}
                  </div>
                )}

                {/* Matched Event Cards */}
                {msg.data?.events && msg.data.events.length > 0 && (
                  <div className="mt-4 space-y-2.5">
                    <p className="text-xs font-semibold text-slate-300 flex items-center gap-1.5">
                      <Ticket className="w-3.5 h-3.5 text-indigo-400" />
                      Matching Events ({msg.data.events.length})
                    </p>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                      {msg.data.events.map((event: AiEventCard) => (
                        <div
                          key={event.id}
                          className="p-3 rounded-xl bg-slate-950/70 border border-slate-800 hover:border-indigo-500/50 transition-colors flex flex-col justify-between"
                        >
                          <div>
                            <div className="flex items-center justify-between gap-1 mb-1">
                              <span className="text-[10px] font-semibold uppercase tracking-wider text-indigo-400 bg-indigo-500/10 px-1.5 py-0.5 rounded">
                                {event.categoryName || 'Event'}
                              </span>
                              <span className="text-xs font-bold text-emerald-400">
                                From ₹{Number(event.basePrice).toLocaleString('en-IN')}
                              </span>
                            </div>
                            <h4 className="text-sm font-bold text-white line-clamp-1">{event.title}</h4>
                            <p className="text-[11px] text-slate-400 line-clamp-2 mt-1">
                              {event.shortDescription}
                            </p>
                            <div className="mt-2 space-y-1 text-[11px] text-slate-400">
                              <div className="flex items-center gap-1">
                                <MapPin className="w-3 h-3 text-slate-500" />
                                <span>
                                  {event.venueName}, {event.city}
                                </span>
                              </div>
                              <div className="flex items-center gap-1">
                                <Calendar className="w-3 h-3 text-slate-500" />
                                <span>{new Date(event.startTime).toLocaleDateString()}</span>
                              </div>
                            </div>
                          </div>

                          <div className="mt-3 pt-2.5 border-t border-slate-800/80 flex items-center justify-between">
                            <Link
                              to={`/events/${event.id}`}
                              className="text-xs text-slate-400 hover:text-white transition-colors"
                            >
                              Details
                            </Link>
                            <Link
                              to={`/events/${event.id}/seats`}
                              className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold shadow-sm transition-colors"
                            >
                              <span>Select Seats</span>
                              <ArrowRight className="w-3 h-3" />
                            </Link>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>

              {msg.sender === 'user' && (
                <div className="w-8 h-8 rounded-lg bg-indigo-500/20 border border-indigo-500/30 flex items-center justify-center flex-shrink-0 mt-0.5">
                  <UserIcon className="w-4 h-4 text-indigo-300" />
                </div>
              )}
            </div>
          ))}

          {/* Loading Indicator */}
          {isLoading && (
            <div className="flex gap-3.5 justify-start">
              <div className="w-8 h-8 rounded-lg bg-indigo-600/30 border border-indigo-500/30 flex items-center justify-center flex-shrink-0 animate-pulse">
                <Sparkles className="w-4 h-4 text-indigo-400 animate-spin" />
              </div>
              <div className="bg-slate-900 border border-slate-800 rounded-2xl rounded-tl-none px-4 py-3 text-sm text-slate-300 flex items-center space-x-2">
                <RefreshCw className="w-4 h-4 text-indigo-400 animate-spin" />
                <span>SeatSync AI is querying MySQL database & reasoning with Gemini...</span>
              </div>
            </div>
          )}

          <div ref={messagesEndRef} />
        </div>

        {/* Error Alert Banner */}
        {error && (
          <div className="mb-4 p-3 rounded-xl bg-rose-500/10 border border-rose-500/20 text-rose-300 text-xs flex items-center justify-between">
            <div className="flex items-center gap-2">
              <AlertCircle className="w-4 h-4 flex-shrink-0" />
              <span>{error}</span>
            </div>
            {!isAuthenticated && (
              <Link
                to="/login"
                className="ml-3 px-2.5 py-1 rounded-lg bg-rose-500/20 hover:bg-rose-500/30 text-rose-200 font-semibold"
              >
                Log In
              </Link>
            )}
          </div>
        )}

        {/* Quick Suggestion Pills */}
        <div className="mb-3">
          <p className="text-[11px] text-slate-400 font-semibold mb-1.5 flex items-center gap-1">
            <Search className="w-3 h-3 text-indigo-400" /> Suggested Questions:
          </p>
          <div className="flex flex-wrap gap-1.5">
            {QUICK_PROMPTS.map((prompt, idx) => (
              <button
                key={idx}
                type="button"
                onClick={() => handleSend(prompt)}
                disabled={isLoading}
                className="px-2.5 py-1 rounded-lg bg-slate-900 hover:bg-slate-850 hover:border-indigo-500/40 border border-slate-800 text-xs text-slate-300 hover:text-white transition-all text-left"
              >
                {prompt}
              </button>
            ))}
          </div>
        </div>

        {/* Input Bar */}
        <div className="relative">
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            disabled={isLoading}
            placeholder={
              isAuthenticated
                ? 'Ask me anything (e.g., "Concerts in Mumbai under ₹2,000" or "Upcoming events")...'
                : 'Please log in to chat with the AI Event Assistant'
            }
            className="w-full bg-slate-900 border border-slate-800 focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 rounded-xl pl-4 pr-12 py-3 text-sm text-white placeholder-slate-500 disabled:opacity-60 transition-all outline-none"
          />
          <button
            type="button"
            onClick={() => handleSend()}
            disabled={isLoading || !input.trim() || !isAuthenticated}
            className="absolute right-2 top-2 p-2 rounded-lg bg-indigo-600 hover:bg-indigo-500 disabled:bg-slate-800 text-white disabled:text-slate-500 transition-colors shadow-sm"
            title="Send Message"
          >
            <Send className="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  );
};

