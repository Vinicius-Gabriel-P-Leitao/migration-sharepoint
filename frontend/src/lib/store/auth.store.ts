import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import axios from 'axios';

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
  refreshTimer: number | null;
  setAuth: (data: AuthResponse) => void;
  setToken: (token: string) => void;
  logout: () => void;
  scheduleRefresh: (token: string) => void;
}

const REFRESH_BUFFER = 5 * 60 * 1000; // 5 minutes before expiry

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      token: null,
      isAuthenticated: false,
      passwordResetRequired: false,
      user: null,
      refreshTimer: null,

      setAuth: (data) => {
        const token = data.session.accessToken;
        set({
          token,
          isAuthenticated: true,
          passwordResetRequired: data.session.passwordResetRequired,
          user: data.user,
        });
        get().scheduleRefresh(token);
      },

      setToken: (token) => {
        set({ token });
        get().scheduleRefresh(token);
      },

      logout: () => {
        const timer = get().refreshTimer;
        if (timer) clearTimeout(timer);
        set({ token: null, isAuthenticated: false, passwordResetRequired: false, user: null, refreshTimer: null });
      },

      scheduleRefresh: (token) => {
        const currentTimer = get().refreshTimer;
        if (currentTimer) clearTimeout(currentTimer);

        try {
          // Decode JWT to get expiry
          const payload = JSON.parse(atob(token.split('.')[1]));
          const expiry = payload.exp * 1000; // convert to ms
          const timeout = expiry - Date.now() - REFRESH_BUFFER;

          if (timeout > 0) {
            const timer = window.setTimeout(async () => {
              try {
                const { data } = await axios.post('/v1/auth/refresh', {}, { withCredentials: true });
                get().setToken(data.session.accessToken);
              } catch (error) {
                console.error('Proactive refresh failed', error);
                get().logout();
              }
            }, timeout);
            set({ refreshTimer: timer });
          } else {
            // Token already expired or too close to expiry, refresh immediately
            axios.post('/v1/auth/refresh', {}, { withCredentials: true })
              .then(res => get().setToken(res.data.session.accessToken))
              .catch(() => get().logout());
          }
        } catch (e) {
          console.error('Failed to schedule token refresh', e);
        }
      },
    }),
    {
      name: 'auth-storage',
      partialize: (state) => ({
        token: state.token,
        isAuthenticated: state.isAuthenticated,
        passwordResetRequired: state.passwordResetRequired,
        user: state.user,
      }),
    }
  )
);
