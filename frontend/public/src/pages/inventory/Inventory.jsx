import { useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import {
  createProduct,
  deleteProduct,
  getProducts,
  updateProduct,
} from "../../services/inventoryApi";
import BarcodeScanner from "../../components/BarcodeScanner";
import "./inventory.css";

const blankProduct = {
  name: "", sku: "", barcode: "", category: "", description: "", unit: "pcs",
  quantity: 0, reorderLevel: 5, costPrice: "", sellingPrice: "", mrpPrice: "", gstRate: "0",
  supplierName: "", expiryDate: "",
};

const normalizeScanCode = (value) => String(value || "").trim().toUpperCase();
const scanCodeMatches = (left, right) => {
  const first = normalizeScanCode(left);
  const second = normalizeScanCode(right);
  if (!first || !second) return false;
  if (first === second) return true;
  // A UPC-A scan can omit the leading zero stored in its EAN-13 form.
  return first.replace(/^0(?=\d{12}$)/, "") === second.replace(/^0(?=\d{12}$)/, "");
};

const apiMessage = (error) => {
  if (error.code === "ECONNABORTED") return "Inventory service timed out. Check your connection, refresh the product list, then try again.";
  const data = error?.response?.data;
  const validationErrors = data?.errors && Object.values(data.errors).filter(Boolean);
  if (validationErrors?.length) return validationErrors.join(" ");
  return data?.message || (error.response
    ? `Product request failed (${error.response.status}). Please check the product details and try again.`
    : "Inventory service is unreachable. Check that it is running and try again.");
};
const isExpired = (product) => {
  if (!product.expiryDate) return false;
  const today = new Date();
  const todayString = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}-${String(today.getDate()).padStart(2, "0")}`;
  return product.expiryDate < todayString;
};

export default function Inventory() {
  const [products, setProducts] = useState([]);
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState("all");
  const [form, setForm] = useState(blankProduct);
  const [editingId, setEditingId] = useState(null);
  const [formOpen, setFormOpen] = useState(false);
  const [cameraScanOpen, setCameraScanOpen] = useState(false);
  const [barcodeLookup, setBarcodeLookup] = useState("");
  const [searchParams, setSearchParams] = useSearchParams();
  const handledQueryBarcode = useRef("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const loadProducts = async () => {
    setLoading(true);
    setError("");
    try {
      setProducts(await getProducts());
    } catch (requestError) {
      setError(apiMessage(requestError));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadProducts(); }, []);

  useEffect(() => {
    let refreshTimer;
    const refreshVisibleInventory = () => {
      if (document.visibilityState !== "visible") return;
      window.clearTimeout(refreshTimer);
      refreshTimer = window.setTimeout(loadProducts, 120);
    };
    window.addEventListener("focus", refreshVisibleInventory);
    document.addEventListener("visibilitychange", refreshVisibleInventory);
    return () => {
      window.clearTimeout(refreshTimer);
      window.removeEventListener("focus", refreshVisibleInventory);
      document.removeEventListener("visibilitychange", refreshVisibleInventory);
    };
  }, []);

  const handleInventoryBarcode = (rawBarcode) => {
    const barcode = String(rawBarcode || "").trim();
    if (!barcode) return;
    setBarcodeLookup("");
    setFilter("all");
    setError("");
    const existing = products.find((product) =>
      scanCodeMatches(product.barcode, barcode) || normalizeScanCode(product.sku) === normalizeScanCode(barcode)
    );
    if (existing) {
      setQuery(barcode);
      editProduct(existing);
      setNotice(`Barcode matched ${existing.name}. Update its stock or product details.`);
      return;
    }
    setQuery("");
    setEditingId(null);
    setForm({ ...blankProduct, barcode, sku: barcode });
    setFormOpen(true);
    setError("");
    setNotice(`Barcode ${barcode} is new. Add the product details and save it to inventory.`);
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  useEffect(() => {
    const barcode = searchParams.get("barcode");
    if (!loading && barcode && handledQueryBarcode.current !== barcode) {
      handledQueryBarcode.current = barcode;
      handleInventoryBarcode(barcode);
      setSearchParams({}, { replace: true });
    }
  }, [loading, products, searchParams, setSearchParams]);

  const handleInventoryBarcodeKeyDown = (event) => {
    if (event.key !== "Enter") return;
    event.preventDefault();
    handleInventoryBarcode(barcodeLookup);
  };

  const lowStock = products.filter((product) => product.lowStock);
  const totalUnits = products.reduce((sum, product) => sum + (product.quantity || 0), 0);
  const totalStock = products.reduce((sum, product) => sum + (product.totalStock ?? product.quantity ?? 0), 0);
  const soldStock = products.reduce((sum, product) => sum + (product.soldStock || 0), 0);
  const inventoryValue = products.reduce((sum, product) => sum + (Number(product.quantity) || 0) * (Number(product.costPrice) || 0), 0);
  const salesValue = products.reduce((sum, product) => sum + (Number(product.quantity) || 0) * (Number(product.sellingPrice) || 0), 0);
  const expectedProfit = salesValue - inventoryValue;
  const grossMargin = salesValue > 0 ? (expectedProfit / salesValue) * 100 : 0;
  const belowCostProducts = products.filter((product) => Number(product.sellingPrice) < Number(product.costPrice));
  const displayedProducts = useMemo(() => products.filter((product) => {
    const text = `${product.name} ${product.sku} ${product.barcode || ""} ${product.category || ""}`.toLowerCase();
    return (filter !== "low" || product.lowStock) && text.includes(query.toLowerCase());
  }), [products, query, filter]);

  const handleChange = (event) => setForm((current) => ({ ...current, [event.target.name]: event.target.value }));

  const resetForm = () => {
    setForm(blankProduct);
    setEditingId(null);
    setFormOpen(false);
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setSaving(true);
    setError("");
    setNotice("");
    const payload = {
      ...form,
      quantity: Number(form.quantity),
      reorderLevel: Number(form.reorderLevel),
      costPrice: Number(form.costPrice),
      sellingPrice: Number(form.sellingPrice),
      mrpPrice: Number(form.mrpPrice),
      gstRate: Number(form.gstRate),
      barcode: form.barcode.trim() || null,
      expiryDate: form.expiryDate || null,
    };
    try {
      let savedProduct;
      if (editingId) {
        savedProduct = await updateProduct(editingId, payload);
        setProducts((current) => current.map((product) => product.id === savedProduct.id ? savedProduct : product));
        setNotice("Product updated.");
      } else {
        savedProduct = await createProduct(payload);
        setProducts((current) => [...current, savedProduct].sort((left, right) => left.name.localeCompare(right.name)));
        setNotice("Product added to inventory.");
      }
      resetForm();
    } catch (requestError) {
      setError(apiMessage(requestError));
    } finally {
      setSaving(false);
    }
  };

  const editProduct = (product) => {
    setEditingId(product.id);
    setFormOpen(true);
    setForm({ ...blankProduct, ...product, mrpPrice: product.mrpPrice ?? product.sellingPrice, expiryDate: product.expiryDate || "" });
    setNotice("");
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const removeProduct = async (product) => {
    if (!window.confirm(`Remove ${product.name} from inventory?`)) return;
    setError("");
    try {
      await deleteProduct(product.id);
      setNotice("Product removed.");
      if (editingId === product.id) resetForm();
      await loadProducts();
    } catch (requestError) {
      setError(apiMessage(requestError));
    }
  };

  return (
    <div className="inventory-page">
      <div className="page-heading">
        <div><p className="eyebrow">INVENTORY</p><h1>Stock at a glance</h1><p>Keep products, quantities, prices and reorder levels in one organised place.</p></div>
        <button className="inventory-add-button" onClick={() => { resetForm(); setFormOpen(true); }}>+ Add product</button>
      </div>

      {error && <div className="inventory-alert error" role="alert">{error}</div>}
      {notice && <div className="inventory-alert success" role="status">{notice}</div>}

      <div className="inventory-stats">
        <div><span className="stat-kicker">CATALOGUE</span><strong>{products.length}</strong><small>Products tracked</small></div>
        <div><span className="stat-kicker">TOTAL STOCK</span><strong>{totalStock}</strong><small>Units received</small></div>
        <div><span className="stat-kicker">REMAINING STOCK</span><strong>{totalUnits}</strong><small>Units available now</small></div>
        <div><span className="stat-kicker">SOLD STOCK</span><strong>{soldStock}</strong><small>Units sold</small></div>
      </div>

      <section className="inventory-profit-panel">
        <div className="inventory-profit-heading"><div><span className="panel-eyebrow">BUSINESS INSIGHT</span><h2>Current stock profit outlook</h2><p>Projected from the cost and selling price of stock you currently hold.</p></div><span className={expectedProfit < 0 ? "profit-status risk" : "profit-status"}>{expectedProfit < 0 ? "Pricing needs review" : "Stock pricing is positive"}</span></div>
        <div className="inventory-profit-grid"><div><span>Potential sales value</span><strong>₹{salesValue.toLocaleString("en-IN", { maximumFractionDigits: 2 })}</strong><small>If all current stock sells</small></div><div><span>{expectedProfit < 0 ? "Estimated loss" : "Expected gross profit"}</span><strong className={expectedProfit < 0 ? "danger-text" : "profit-text"}>{expectedProfit < 0 ? "−" : "+"}₹{Math.abs(expectedProfit).toLocaleString("en-IN", { maximumFractionDigits: 2 })}</strong><small>Before tax and operating expenses</small></div><div><span>Gross margin</span><strong>{grossMargin.toFixed(1)}%</strong><small>Across current inventory</small></div><div><span>Products below cost</span><strong className={belowCostProducts.length ? "danger-text" : "profit-text"}>{belowCostProducts.length}</strong><small>{belowCostProducts.length ? "Check selling prices" : "No loss-making prices"}</small></div></div>
        {belowCostProducts.length > 0 && <div className="below-cost-warning"><strong>Review these prices:</strong>{belowCostProducts.slice(0, 4).map((product) => <span key={product.id}>{product.name} — cost ₹{product.costPrice}, sell ₹{product.sellingPrice}</span>)}</div>}
      </section>

      {formOpen && <section className="inventory-panel product-form-panel">
        <div className="panel-title"><div><span className="panel-eyebrow">PRODUCT DETAILS</span><h2>{editingId ? "Update product" : "Add a product"}</h2><p>Start with the basics—more details can be added when you need them.</p></div><button className="text-button" onClick={resetForm}>Close</button></div>
        <form className="product-form" onSubmit={handleSubmit}>
          <label>Product name *<input name="name" value={form.name} onChange={handleChange} required autoFocus /></label>
          <label>SKU *<input name="sku" value={form.sku} onChange={handleChange} required /></label>
          <label>Barcode<input name="barcode" value={form.barcode || ""} onChange={handleChange} maxLength={64} autoComplete="off" /></label>
          <label>Category<input name="category" value={form.category || ""} onChange={handleChange} /></label>
          <label>Unit<input name="unit" value={form.unit} onChange={handleChange} placeholder="pcs" /></label>
          <label>Remaining stock *<input name="quantity" type="number" min="0" value={form.quantity} onChange={handleChange} required /><small>Total stock includes sold units plus this remaining quantity.</small></label>
          <label>Reorder level<input name="reorderLevel" type="number" min="0" value={form.reorderLevel} onChange={handleChange} /></label>
          <label>Purchase price *<input name="costPrice" type="number" min="0" step="0.01" value={form.costPrice} onChange={handleChange} required /></label>
          <label>Selling price (GST included) *<input name="sellingPrice" type="number" min="0" step="0.01" value={form.sellingPrice} onChange={handleChange} required /></label>
          <label>MRP *<input name="mrpPrice" type="number" min="0" step="0.01" value={form.mrpPrice} onChange={handleChange} required /></label>
          <label>GST rate<select name="gstRate" value={form.gstRate} onChange={handleChange}>{[0, 5, 12, 18, 28].map((rate) => <option key={rate} value={rate}>{rate}%</option>)}</select></label>
          <label>Supplier<input name="supplierName" value={form.supplierName || ""} onChange={handleChange} /></label>
          <label>Expiry date<input name="expiryDate" type="date" value={form.expiryDate} onChange={handleChange} /></label>
          <label className="form-wide">Description<textarea name="description" value={form.description || ""} onChange={handleChange} rows="2" /></label>
          <div className="form-wide form-actions"><button className="primary-button" disabled={saving}>{saving ? "Saving…" : editingId ? "Update product" : "Add product"}</button></div>
        </form>
      </section>}

      <section className="inventory-panel">
        <div className="panel-title inventory-toolbar"><div><span className="panel-eyebrow">PRODUCT DIRECTORY</span><h2>All products</h2><p>{displayedProducts.length} of {products.length} products shown</p></div><div className="table-controls"><input aria-label="Search products" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search name, SKU, or barcode" /><input aria-label="Scan or enter barcode" value={barcodeLookup} onChange={(event) => setBarcodeLookup(event.target.value)} onKeyDown={handleInventoryBarcodeKeyDown} placeholder="Scan barcode, then press Enter" autoFocus /><button type="button" onClick={() => setCameraScanOpen(true)}>Scan with camera</button><button className={filter === "low" ? "filter-active" : ""} onClick={() => setFilter(filter === "low" ? "all" : "low")}>{filter === "low" ? "Showing low stock" : "Low stock"}</button></div></div>
        {loading ? <p className="inventory-empty">Loading your inventory…</p> : displayedProducts.length === 0 ? <div className="inventory-empty-state"><strong>{products.length ? "No matching products" : "Your inventory is ready for its first product"}</strong><span>{products.length ? "Try a different search or clear the low-stock filter." : "Add products to see their stock status and value here."}</span>{!products.length && <button className="primary-button" onClick={() => setFormOpen(true)}>Add first product</button>}</div> : <div className="table-wrap"><table className="inventory-table"><thead><tr><th>Product</th><th>SKU / Barcode</th><th>Stock</th><th>Price & tax per unit</th><th>Status</th><th aria-label="Actions" /></tr></thead><tbody>{displayedProducts.map((product) => { const expired = isExpired(product); return <tr key={product.id}><td><strong>{product.name}</strong><small>{product.category || "Uncategorised"}</small></td><td><strong>{product.sku}</strong><small>{product.barcode || "Barcode not set"}</small></td><td><strong>{product.quantity} {product.unit} remaining</strong><small>Total {product.totalStock ?? product.quantity} · Sold {product.soldStock || 0} · Reorder at {product.reorderLevel}</small></td><td><strong>Sell (GST incl.) ₹{Number(product.sellingPrice).toLocaleString("en-IN", { minimumFractionDigits: 2 })}</strong><small>Taxable ₹{Number(product.taxablePrice ?? product.sellingPrice).toLocaleString("en-IN", { minimumFractionDigits: 2 })} · GST ₹{Number(product.gstAmount || 0).toLocaleString("en-IN", { minimumFractionDigits: 2 })} ({Number(product.gstRate ?? 0)}%)</small><small>MRP ₹{Number(product.mrpPrice ?? product.sellingPrice).toLocaleString("en-IN", { minimumFractionDigits: 2 })} · Purchase ₹{Number(product.costPrice).toLocaleString("en-IN", { minimumFractionDigits: 2 })}</small></td><td><span className={`stock-badge ${expired || product.lowStock ? "low" : "healthy"}`}>{expired ? "Expired" : product.lowStock ? "Low stock" : "In stock"}</span></td><td className="row-actions"><button onClick={() => editProduct(product)}>Edit</button><button className="delete-action" onClick={() => removeProduct(product)}>Delete</button></td></tr>; })}</tbody></table></div>}
      </section>
      {cameraScanOpen && <BarcodeScanner onClose={() => setCameraScanOpen(false)} onDetected={(barcode) => { setCameraScanOpen(false); handleInventoryBarcode(barcode); }} />}
    </div>
  );
}
