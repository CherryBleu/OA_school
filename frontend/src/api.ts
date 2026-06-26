export const API_BASE = (import.meta.env.VITE_API_BASE_URL || "/api/v1").replace(/\/$/, "");

const deviceKey = "oa-school-device-uuid";
const tokenKey = "oa-school-token";

export function getDeviceUuid() {
  let value = localStorage.getItem(deviceKey);
  if (!value) {
    value = crypto.randomUUID();
    localStorage.setItem(deviceKey, value);
  }
  return value;
}

export function getToken() {
  return localStorage.getItem(tokenKey) || "";
}

export function setToken(token: string) {
  if (token) localStorage.setItem(tokenKey, token);
  else localStorage.removeItem(tokenKey);
}

export function wsUrl() {
  const base = API_BASE.replace(/\/api\/v1$/, "");
  const url = new URL(base || window.location.origin, window.location.href);
  url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
  url.pathname = "/ws/hub";
  url.searchParams.set("token", getToken());
  return url.toString();
}

export async function api<T = any>(path: string, options: RequestInit & { body?: any } = {}): Promise<T> {
  const headers = new Headers(options.headers);
  const isFormData = options.body instanceof FormData;
  if (options.body && !isFormData && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
  headers.set("X-Device-Id", getDeviceUuid());
  const token = getToken();
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
    body: options.body && !isFormData && typeof options.body !== "string" ? JSON.stringify(options.body) : options.body
  });

  if (response.status === 204) return null as T;
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(payload.error?.message || "请求失败") as Error & { code?: string; status?: number };
    error.code = payload.error?.code;
    error.status = response.status;
    throw error;
  }
  return payload;
}
