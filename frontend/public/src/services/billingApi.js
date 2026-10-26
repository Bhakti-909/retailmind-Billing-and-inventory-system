import axios from "axios";
import { API_GATEWAY_URL, attachSessionToken } from "./apiBase";
const billingApi = axios.create({ baseURL: `${API_GATEWAY_URL}/api/billing`, headers: { "Content-Type": "application/json" } });
attachSessionToken(billingApi);
export const getTodaySalesSummary = () => billingApi.get("/summary/today").then((response) => response.data);
export const getSalesDashboard = (range = { days: 7 }) => billingApi.get("/dashboard", { params: range }).then((response) => response.data);
export const createSale = (sale) => billingApi.post("/sales", sale).then((response) => response.data);
export const resendInvoiceEmail = (invoiceId) => billingApi.post(`/invoices/${invoiceId}/email`).then((response) => response.data);
export const getInvoices = (filter) => {
  const params = typeof filter === "string" ? { customerName: filter } : (filter || {});
  return billingApi.get("/invoices", { params }).then((response) => response.data);
};
