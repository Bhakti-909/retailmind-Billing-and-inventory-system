import axios from "axios";

// Keep API calls same-origin. Vite proxies /api to the local gateway in dev,
// while deployments can serve the frontend and gateway behind one host.
export const API_GATEWAY_URL = (import.meta.env.VITE_API_BASE_URL || "").replace(/\/$/, "");

export const requireSessionToken = () => {
  const token = localStorage.getItem("token");
  if (token) return token;

  const error = new Error(
    `Your login token is missing for this browser session (${window.location.origin}). Sign in again in this tab.`,
  );
  error.code = "SESSION_TOKEN_MISSING";
  throw error;
};

export const attachSessionToken = (client) => {
  client.interceptors.request.use((config) => {
    config.headers.Authorization = `Bearer ${requireSessionToken()}`;
    return config;
  });
  return client;
};
