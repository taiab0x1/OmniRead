import axios, { AxiosError, type AxiosInstance, type InternalAxiosRequestConfig } from "axios";
import { useAuthStore } from "@/lib/auth-store";

const baseURL = import.meta.env.VITE_API_URL || "";

export const api: AxiosInstance = axios.create({
  baseURL: `${baseURL}/v1`,
  headers: { "Content-Type": "application/json" },
});

api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = useAuthStore.getState().accessToken;
  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (r) => r,
  (error: AxiosError<{ error?: { code?: string; message?: string } }>) => {
    if (error.response?.status === 401) {
      const code = error.response.data?.error?.code;
      if (code === "token_expired" || code === "invalid_token" || code === "unauthorized") {
        useAuthStore.getState().logout();
        if (window.location.pathname !== "/login") {
          window.location.href = "/login";
        }
      }
    }
    return Promise.reject(error);
  }
);

export type ApiEnvelope<T> = {
  success: boolean;
  data: T;
  meta?: { page?: number; per_page?: number; total?: number; next_cursor?: string | null };
  error: { code: string; message: string; extra?: unknown } | null;
};

export async function apiGet<T>(path: string, params?: Record<string, unknown>): Promise<T> {
  const r = await api.get<ApiEnvelope<T>>(path, { params });
  return r.data.data;
}

export async function apiGetWithMeta<T>(path: string, params?: Record<string, unknown>) {
  const r = await api.get<ApiEnvelope<T>>(path, { params });
  return { data: r.data.data, meta: r.data.meta };
}

export async function apiPost<T>(path: string, body?: unknown): Promise<T> {
  const r = await api.post<ApiEnvelope<T>>(path, body);
  return r.data.data;
}

export async function apiPut<T>(path: string, body?: unknown): Promise<T> {
  const r = await api.put<ApiEnvelope<T>>(path, body);
  return r.data.data;
}

export async function apiDelete<T>(path: string): Promise<T> {
  const r = await api.delete<ApiEnvelope<T>>(path);
  return r.data.data;
}

export function extractApiError(err: unknown): string {
  if (axios.isAxiosError(err)) {
    const e = err.response?.data?.error;
    if (e?.message) return e.message;
    return err.message;
  }
  return String(err);
}
