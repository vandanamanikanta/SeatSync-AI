import React, { createContext, useContext, useState, useEffect } from 'react';
import { UserSummary, LoginPayload, RegisterPayload } from '../types';
import { authApi } from '../api/authApi';

interface AuthContextType {
  user: UserSummary | null;
  token: string | null;
  isAuthenticated: boolean;
  isOrganizer: boolean;
  isAdmin: boolean;
  loading: boolean;
  login: (payload: LoginPayload) => Promise<void>;
  register: (payload: RegisterPayload) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem('seatsync_access_token'));
  const [user, setUser] = useState<UserSummary | null>(() => {
    const cached = localStorage.getItem('seatsync_user');
    if (cached) {
      try {
        return JSON.parse(cached);
      } catch {
        return null;
      }
    }
    return null;
  });
  const [loading, setLoading] = useState<boolean>(true);

  // Validate or refresh session on mount
  useEffect(() => {
    const initializeAuth = async () => {
      const storedToken = localStorage.getItem('seatsync_access_token');
      if (storedToken) {
        try {
          const profile = await authApi.getCurrentUser();
          const userSummary: UserSummary = {
            id: profile.id,
            email: profile.email,
            fullName: profile.fullName,
            roles: profile.roles,
          };
          setUser(userSummary);
          localStorage.setItem('seatsync_user', JSON.stringify(userSummary));
        } catch {
          // Token is invalid/expired
          localStorage.removeItem('seatsync_access_token');
          localStorage.removeItem('seatsync_user');
          setToken(null);
          setUser(null);
        }
      }
      setLoading(false);
    };

    initializeAuth();
  }, []);

  const login = async (payload: LoginPayload) => {
    const res = await authApi.login(payload);
    localStorage.setItem('seatsync_access_token', res.accessToken);
    localStorage.setItem('seatsync_user', JSON.stringify(res.user));
    if (res.refreshToken) {
      localStorage.setItem('seatsync_refresh_token', res.refreshToken);
    }
    setToken(res.accessToken);
    setUser(res.user);
  };

  const register = async (payload: RegisterPayload) => {
    const res = await authApi.register(payload);
    localStorage.setItem('seatsync_access_token', res.accessToken);
    localStorage.setItem('seatsync_user', JSON.stringify(res.user));
    if (res.refreshToken) {
      localStorage.setItem('seatsync_refresh_token', res.refreshToken);
    }
    setToken(res.accessToken);
    setUser(res.user);
  };

  const logout = () => {
    localStorage.removeItem('seatsync_access_token');
    localStorage.removeItem('seatsync_refresh_token');
    localStorage.removeItem('seatsync_user');
    setToken(null);
    setUser(null);
  };

  const isAuthenticated = !!token && !!user;
  const isOrganizer = !!user?.roles?.includes('ROLE_ORGANIZER') || !!user?.roles?.includes('ROLE_ADMIN');
  const isAdmin = !!user?.roles?.includes('ROLE_ADMIN');

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isAuthenticated,
        isOrganizer,
        isAdmin,
        loading,
        login,
        register,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

