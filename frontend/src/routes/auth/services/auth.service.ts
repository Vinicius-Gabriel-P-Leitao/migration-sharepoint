import apiClient from "@lib/utils/axios.util";
import { useAuthStore } from "@lib/store/auth.store";
import type { LoginFormData } from "../auth.schema";

export async function loginAttempt(data: LoginFormData) {
  const response = await apiClient.post("/auth/login", {
    email: data.email,
    password: data.password,
  });
  
  const authData = response.data;
  useAuthStore.getState().setAuth(authData);
  
  return authData;
}

export async function firstChangePasswordAttempt(newPassword: string) {
  const response = await apiClient.post("/auth/first-reset", {
    newPassword,
  });
  
  useAuthStore.getState().logout();
  
  return response.data;
}

export async function logoutAttempt() {
  try {
    await apiClient.post("/auth/logout");
  } catch (error) {
    console.error("Erro ao realizar logout no servidor:", error);
    throw error;
  }
}
