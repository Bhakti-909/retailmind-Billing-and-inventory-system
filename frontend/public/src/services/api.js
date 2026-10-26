import axios from "axios";
import { API_GATEWAY_URL } from "./apiBase";

const api = axios.create({
  baseURL: `${API_GATEWAY_URL}/api`,
  headers: {
    "Content-Type": "application/json",
  },
});

api.interceptors.request.use(
  (config) => {

    const token = localStorage.getItem("token");

    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    return config;
  },
  (error) => Promise.reject(error)
);

export default api;
