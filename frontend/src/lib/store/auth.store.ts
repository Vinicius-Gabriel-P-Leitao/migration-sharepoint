import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface AuthResponse {
  session: {
    accessToken: string;
    tokenVersion: number;
    passwordResetRequired: boolean;
  };
  user: {
    id: string;
    email: string;
    active: boolean;
    roles: string[];
    profile: {
      username: string;
      registration: string;
      position: string;
    };
  };
}

interface AuthState {
  token: string | null;
  isAuthenticated: boolean;
  passwordResetRequired: boolean;
  user: AuthResponse['user'] | null;
  setAuth: (data: AuthResponse) => void;
  setToken: (token: string) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      isAuthenticated: false,
      passwordResetRequired: false,
      user: null,
      setAuth: (data) => set({
        token: data.session.accessToken,
        isAuthenticated: true,
        passwordResetRequired: data.session.passwordResetRequired,
        user: data.user,
      }),
      setToken: (token) => set({ token }),
      logout: () => set({ token: null, isAuthenticated: false, passwordResetRequired: false, user: null }),
    }),
    {
      name: 'auth-storage',
    }
  )
);
