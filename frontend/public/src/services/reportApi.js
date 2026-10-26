import axios from "axios";
import { API_GATEWAY_URL, attachSessionToken } from "./apiBase";

const reportsApi = axios.create({
  baseURL: `${API_GATEWAY_URL}/api/reports`,
  headers: { "Content-Type": "application/json" },
});

attachSessionToken(reportsApi);

const get = (path, params) => reportsApi.get(path, { params }).then((response) => response.data);

export const getReportOverview = (range) => get("/overview", range);
export const getSalesReport = (range) => get("/sales", range);
export const getInventoryReport = () => get("/inventory");
export const getTaxReport = (range) => get("/tax", range);
export const getCustomerReport = (range) => get("/customers", range);
export const getInvoiceReport = (params) => get("/invoices", params);
export const getInvoiceItemsReport = (invoiceId, range) => get(`/invoices/${invoiceId}/items`, range);
export const getProductCombinationReport = (range) => get("/products/combinations", range);
export const getReportPdf = (range) => reportsApi.get("/export/pdf", { params: range, responseType: "blob" }).then((response) => response.data);
