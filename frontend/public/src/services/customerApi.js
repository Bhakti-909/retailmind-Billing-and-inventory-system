import api from "./api";
import { requireSessionToken } from "./apiBase";

const data = (request) => request.then((response) => response.data);
const customerRequest = (send) => {
  let token;
  try {
    token = requireSessionToken();
  } catch (error) {
    error.code = "CUSTOMER_SESSION_TOKEN_MISSING";
    return Promise.reject(error);
  }

  // Set the customer Authorization header directly as well as through the
  // shared API interceptor, so customer calls cannot silently omit a token.
  return send({ headers: { Authorization: `Bearer ${token}` } });
};

export const customerRequestError = (error) => {
  if (error?.code === "CUSTOMER_SESSION_TOKEN_MISSING") return error.message;
  const status = error?.response?.status;
  const body = error?.response?.data;
  const detail = body?.message || body?.error || (typeof body === "string" ? body : "");
  if (status) {
    const fallback = status === 401
      ? "The login token was missing or rejected."
      : status === 403
        ? "The customer service denied access."
        : "The customer service returned an error.";
    return `Customer data request failed (HTTP ${status}): ${detail || fallback}`;
  }
  if (error?.request) return "The customer service did not respond. Check that the API gateway and customer service are running.";
  return error?.message || "Unable to load customer data.";
};

export const getCustomers = () => data(customerRequest((config) => api.get("/customers", config)));
export const createCustomer = (customer) => data(customerRequest((config) => api.post("/customers", customer, config)));
export const updateCustomer = (id, customer) => data(customerRequest((config) => api.put(`/customers/${id}`, customer, config)));
export const deleteCustomer = (id) => data(customerRequest((config) => api.delete(`/customers/${id}`, config)));
