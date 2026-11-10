import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { Navbar } from './components/common/Navbar';
import { Footer } from './components/common/Footer';
import { ProtectedRoute } from './components/common/ProtectedRoute';

import { HomePage } from './pages/HomePage';
import { ExploreEventsPage } from './pages/ExploreEventsPage';
import { EventDetailsPage } from './pages/EventDetailsPage';
import { SeatSelectionPage } from './pages/SeatSelectionPage';
import { BookingHistoryPage } from './pages/BookingHistoryPage';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { OrganizerDashboardPage } from './pages/OrganizerDashboardPage';
import { AiAssistantPage } from './pages/AiAssistantPage';

export const App: React.FC = () => {
  return (
    <AuthProvider>
      <BrowserRouter>
        <div className="flex flex-col min-h-screen bg-slate-950 text-slate-100">
          <Navbar />
          <main className="flex-1">
            <Routes>
              <Route path="/" element={<HomePage />} />
              <Route path="/events" element={<ExploreEventsPage />} />
              <Route path="/ai-assistant" element={<AiAssistantPage />} />
              <Route path="/events/:id/seats" element={<SeatSelectionPage />} />
              <Route path="/events/:identifier" element={<EventDetailsPage />} />
              <Route
                path="/my-bookings"
                element={
                  <ProtectedRoute>
                    <BookingHistoryPage />
                  </ProtectedRoute>
                }
              />
              <Route path="/login" element={<LoginPage />} />
              <Route path="/register" element={<RegisterPage />} />
              <Route
                path="/organizer/dashboard"
                element={
                  <ProtectedRoute requiredRole="ROLE_ORGANIZER">
                    <OrganizerDashboardPage />
                  </ProtectedRoute>
                }
              />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </main>
          <Footer />
        </div>
      </BrowserRouter>
    </AuthProvider>
  );
};

export default App;

