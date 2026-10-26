import axios from "axios";
import { API_GATEWAY_URL, attachSessionToken } from "./apiBase";

// Inventory is its own service. Requests still carry the same JWT used at login.
const inventoryApi = axios.create({
  baseURL: `${API_GATEWAY_URL}/api/inventory`,
  timeout: 15000,
  headers: { "Content-Type": "application/json" },
});

attachSessionToken(inventoryApi);

const unwrap = (request) => request.then((response) => response.data);

export const getProducts = () => unwrap(inventoryApi.get("/products", {
  headers: { "Cache-Control": "no-cache", Pragma: "no-cache" },
}));
export const getLowStockProducts = () => unwrap(inventoryApi.get("/products/low-stock"));
export const createProduct = (product) => unwrap(inventoryApi.post("/products", product));
export const updateProduct = (id, product) => unwrap(inventoryApi.put(`/products/${id}`, product));
export const deleteProduct = (id) => unwrap(inventoryApi.delete(`/products/${id}`));

export default inventoryApi;
