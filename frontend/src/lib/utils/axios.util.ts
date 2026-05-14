import axios from 'axios';
import { useAuthStore } from '../store/auth.store';

const apiClient = axios.create({
  baseURL: '/v1',
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true,
});

// Request interceptor to add the bearer token
apiClient.interceptors.request.use(
  (config) => {
    const token = useAuthStore.getState().token;
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor to handle token refresh from custom header
apiClient.interceptors.response.use(
  (response) => {
    const newToken = response.headers['x-new-access-token'];
    if (newToken) {
      useAuthStore.getState().setToken(newToken);
    }
    return response;
  },
  (error) => {
    if (error.response?.status === 401) {
      useAuthStore.getState().logout();
      // Only redirect if not already on login page to avoid loops
      if (!window.location.pathname.includes('/login')) {
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

export default apiClient;
