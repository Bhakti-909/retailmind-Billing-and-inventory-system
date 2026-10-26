import axios from "axios";
import { API_GATEWAY_URL, attachSessionToken, requireSessionToken } from "./apiBase";

const api = axios.create({
  baseURL: `${API_GATEWAY_URL}/api/suppliers`,
  headers: { "Content-Type": "application/json" },
});

attachSessionToken(api);

const data = (request) => request.then((response) => response.data);
const supplierRequest = (send) => {
  let token;
  try {
    token = requireSessionToken();
  } catch (error) {
    error.code = "SUPPLIER_SESSION_TOKEN_MISSING";
    return Promise.reject(error);
  }

  return send({ headers: { Authorization: `Bearer ${token}` } });
};

export const supplierRequestError = (error) => {
  if (error?.code === "SUPPLIER_SESSION_TOKEN_MISSING") return error.message;
  const status = error?.response?.status;
  const body = error?.response?.data;
  const detail = body?.message || body?.error || (typeof body === "string" ? body : "");
  if (status) return `Supplier data request failed (HTTP ${status}): ${detail || (status === 401 ? "The login token was missing or rejected." : "The supplier service returned an error.")}`;
  if (error?.request) return "The supplier service did not respond. Check that the API gateway and supplier service are running.";
  return error?.message || "Unable to load supplier data.";
};

export const getSuppliers = () => data(supplierRequest((config) => api.get("", config)));
export const createSupplier = (supplier) => data(supplierRequest((config) => api.post("", supplier, config)));
export const updateSupplier = (id, supplier) => data(supplierRequest((config) => api.put(`/${id}`, supplier, config)));
export const deleteSupplier = (id) => data(supplierRequest((config) => api.delete(`/${id}`, config)));
