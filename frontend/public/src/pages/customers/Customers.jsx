import { useEffect, useMemo, useState, useCallback } from "react";
import {
  createCustomer,
  customerRequestError,
  deleteCustomer,
  getCustomers,
  updateCustomer,
} from "../../services/customerApi";
import { formatBusinessDateTime } from "../../utils/businessDate";
import { getInvoices } from "../../services/billingApi";
import "../inventory/inventory.css";
import "./customers.css";

const emptyCustomer = { name: "", phone: "", email: "", address: "", notes: "" };
const message = (error) => error?.response?.data?.message || "Unable to save customer.";
const loadCustomerMessage = customerRequestError;
const billingMessage = (error) =>
  error?.response?.data?.message || "Unable to load bills. Check that the billing service is running and try again.";
const formatMoney = (val) =>
  Number(val || 0).toLocaleString("en-IN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });

export default function Customers() {
  const [customers, setCustomers] = useState([]);
  const [allInvoices, setAllInvoices] = useState([]);
  const [form, setForm] = useState(emptyCustomer);
  const [editing, setEditing] = useState(null);
  const [formOpen, setFormOpen] = useState(false);

  // Selected customer for full purchase history
  const [selectedCustomer, setSelectedCustomer] = useState(null);
  const [customerInvoices, setCustomerInvoices] = useState([]);
  const [invoicesLoading, setInvoicesLoading] = useState(false);
  const [invoiceHistoryError, setInvoiceHistoryError] = useState("");

  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  // Load customers + all invoices summary for bill count badges
  const load = async () => {
    setLoading(true);
    try {
      setError("");
      const [custList, invList] = await Promise.all([
        getCustomers(),
        getInvoices()
          .then((list) => {
            setInvoiceHistoryError("");
            return list;
          })
          .catch((invoiceError) => {
            setInvoiceHistoryError(billingMessage(invoiceError));
            return [];
          }),
      ]);
      setCustomers(custList || []);
      setAllInvoices(invList || []);
    } catch (requestError) {
      setError(loadCustomerMessage(requestError));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  // Auto-clear notices
  useEffect(() => {
    if (!notice) return;
    const t = setTimeout(() => setNotice(""), 3500);
    return () => clearTimeout(t);
  }, [notice]);

  // Fetch linked bills plus exact-name legacy bills created before customer IDs were saved.
  const fetchCustomerInvoices = useCallback(async (customer) => {
    setInvoicesLoading(true);
    setInvoiceHistoryError("");
    try {
      const bills = await getInvoices({ customerId: customer.id, customerName: customer.name });
      setCustomerInvoices(bills || []);
    } catch (requestError) {
      setCustomerInvoices([]);
      setInvoiceHistoryError(billingMessage(requestError));
    } finally {
      setInvoicesLoading(false);
    }
  }, []);

  const handleSelectCustomer = (customer) => {
    setSelectedCustomer(customer);
    fetchCustomerInvoices(customer);
  };

  const shown = useMemo(
    () =>
      customers.filter((customer) =>
        `${customer.name} ${customer.phone} ${customer.email || ""}`
          .toLowerCase()
          .includes(query.toLowerCase())
      ),
    [customers, query]
  );

  // Bill count per customer row (using allInvoices for badges)
  const getBillCount = useCallback((customer) => {
    const custName = (customer.name || "").trim().toLocaleLowerCase();
    return allInvoices.filter((inv) => {
      return String(inv.customerId || "") === String(customer.id) ||
        (!inv.customerId && (inv.buyerName || "").trim().toLocaleLowerCase() === custName);
    }).length;
  }, [allInvoices]);

  // Aggregate customer metrics
  const customerTotalSpend = useMemo(() => {
    return customerInvoices.reduce((sum, inv) => sum + Number(inv.total || 0), 0);
  }, [customerInvoices]);

  const customerTotalItems = useMemo(() => {
    return customerInvoices.reduce((sum, inv) => {
      const itemsCount = (inv.items || []).reduce(
        (sub, it) => sub + Number(it.quantity || 1),
        0
      );
      return sum + itemsCount;
    }, 0);
  }, [customerInvoices]);

  const reset = () => {
    setForm(emptyCustomer);
    setEditing(null);
    setFormOpen(false);
  };

  const save = async (event) => {
    event.preventDefault();
    setSaving(true);
    setError("");
    try {
      if (editing) {
        await updateCustomer(editing, form);
        setNotice("Customer updated successfully.");
      } else {
        await createCustomer(form);
        setNotice("Customer added successfully.");
      }
      reset();
      await load();
    } catch (requestError) {
      setError(message(requestError));
    } finally {
      setSaving(false);
    }
  };

  const edit = (e, customer) => {
    e.stopPropagation();
    setEditing(customer.id);
    setFormOpen(true);
    setForm({
      name: customer.name,
      phone: customer.phone,
      email: customer.email || "",
      address: customer.address || "",
      notes: customer.notes || "",
    });
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const remove = async (e, customer) => {
    e.stopPropagation();
    if (!window.confirm(`Delete customer "${customer.name}"? This cannot be undone.`)) return;
    try {
      await deleteCustomer(customer.id);
      if (editing === customer.id) reset();
      if (selectedCustomer?.id === customer.id) setSelectedCustomer(null);
      setNotice("Customer deleted.");
      await load();
    } catch (requestError) {
      setError(message(requestError));
    }
  };

  return (
    <div className="inventory-page customer-page">
      <div className="page-heading">
        <div>
          <p className="eyebrow">CUSTOMERS & BILLING INTELLIGENCE</p>
          <h1>Customer Management</h1>
          <p>
            Click any customer row to view full billing history, purchased products, and transaction records.
          </p>
        </div>
        <button
          className="inventory-add-button"
          onClick={() => {
            reset();
            setFormOpen(true);
          }}
        >
          + Add customer
        </button>
      </div>

      {error && <div className="inventory-alert error">{error}</div>}
      {notice && <div className="inventory-alert success">{notice}</div>}

      <div className="inventory-stats">
        <div>
          <span className="stat-kicker">CUSTOMERS</span>
          <strong>{customers.length}</strong>
          <small>People in your directory</small>
        </div>
        <div>
          <span className="stat-kicker">CONTACTABLE</span>
          <strong>{customers.filter((c) => c.phone).length}</strong>
          <small>With phone number</small>
        </div>
        <div>
          <span className="stat-kicker">TOTAL BILLS ISSUED</span>
          <strong>{allInvoices.length}</strong>
          <small>Store transaction receipts</small>
        </div>
        <div>
          <span className="stat-kicker">SEARCH RESULTS</span>
          <strong>{shown.length}</strong>
          <small>Matching your filter</small>
        </div>
      </div>

      {formOpen && (
        <section className="inventory-panel product-form-panel">
          <div className="panel-title">
            <div>
              <span className="panel-eyebrow">CUSTOMER DETAILS</span>
              <h2>{editing ? "Update customer" : "Add a customer"}</h2>
              <p>Name and phone number are enough to get started.</p>
            </div>
            <button className="text-button" onClick={reset}>
              Close
            </button>
          </div>
          <form className="product-form" onSubmit={save}>
            <label>
              Customer name *
              <input
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                required
                placeholder="Full name"
              />
            </label>
            <label>
              Phone number *
              <input
                value={form.phone}
                onChange={(e) => setForm({ ...form, phone: e.target.value })}
                required
                placeholder="10-digit mobile"
              />
            </label>
            <label>
              Email
              <input
                type="email"
                value={form.email}
                onChange={(e) => setForm({ ...form, email: e.target.value })}
                placeholder="optional@email.com"
              />
            </label>
            <label className="form-wide">
              Address
              <textarea
                rows="2"
                value={form.address}
                onChange={(e) => setForm({ ...form, address: e.target.value })}
                placeholder="Shop / home address"
              />
            </label>
            <label className="form-wide">
              Notes
              <textarea
                rows="2"
                value={form.notes}
                onChange={(e) => setForm({ ...form, notes: e.target.value })}
                placeholder="Preferences, favorite products or loyalty notes"
              />
            </label>
            <div className="form-wide form-actions">
              <button className="primary-button customer-save-button" disabled={saving}>
                {saving
                  ? "Saving…"
                  : editing
                  ? "Update customer"
                  : "Save customer"}
              </button>
            </div>
          </form>
        </section>
      )}

      {/* CUSTOMER DIRECTORY */}
      <section className="inventory-panel">
        <div className="panel-title inventory-toolbar">
          <div>
            <span className="panel-eyebrow">CUSTOMER DIRECTORY</span>
            <h2>All customers (Click to inspect purchases)</h2>
            <p>
              {shown.length} of {customers.length} customers shown
            </p>
          </div>
          <div className="table-controls">
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search name, phone or email..."
            />
          </div>
        </div>

        {loading ? (
          <p className="inventory-empty">Loading customers and billing ledger…</p>
        ) : shown.length === 0 ? (
          <div className="inventory-empty-state">
            <strong>
              {customers.length
                ? "No matching customers"
                : "Start your customer directory"}
            </strong>
            <span>
              {customers.length
                ? "Try another search term."
                : "Add your first customer to keep their billing history ready."}
            </span>
            {!customers.length && (
              <button
                className="primary-button"
                onClick={() => setFormOpen(true)}
              >
                Add first customer
              </button>
            )}
          </div>
        ) : (
          <div className="table-wrap">
            <table className="inventory-table customer-interactive-table">
              <thead>
                <tr>
                  <th>Customer</th>
                  <th>Contact Info</th>
                  <th>Address</th>
                  <th>Bills Recorded</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {shown.map((customer) => {
                  const billCount = getBillCount(customer);
                  const isSelected = selectedCustomer?.id === customer.id;

                  return (
                    <tr
                      key={customer.id}
                      className={`customer-row-clickable ${isSelected ? "selected-customer-row" : ""}`}
                      onClick={() => handleSelectCustomer(customer)}
                      title="Click to view purchase & billing history"
                    >
                      <td>
                        <div className="customer-cell-main">
                          <span className="customer-avatar-badge">
                            {customer.name.charAt(0).toUpperCase()}
                          </span>
                          <div>
                            <strong>{customer.name}</strong>
                            <small>{customer.notes || "No notes"}</small>
                          </div>
                        </div>
                      </td>
                      <td>
                        <strong className="customer-contact-line"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.8 19.8 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.12 4.2 2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.12.9.33 1.78.64 2.62a2 2 0 0 1-.45 2.11L8 9.75a16 16 0 0 0 6.25 6.25l1.3-1.3a2 2 0 0 1 2.11-.45c.84.31 1.72.52 2.62.64a2 2 0 0 1 1.72 2.03Z" /></svg>{customer.phone}</strong>
                        {customer.email && <small className="customer-contact-line"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><rect x="3" y="5" width="18" height="14" rx="2" /><path d="m3 7 9 6 9-6" /></svg>{customer.email}</small>}
                      </td>
                      <td>{customer.address || "Not provided"}</td>
                      <td>
                        <span className="badge-bill-count">
                          {billCount} {billCount === 1 ? "bill" : "bills"}
                        </span>
                      </td>
                      <td className="row-actions">
                        <button
                          type="button"
                          className="view-bills-btn"
                          onClick={(e) => {
                            e.stopPropagation();
                            handleSelectCustomer(customer);
                          }}
                        >
                          View Bills
                        </button>
                        <button
                          type="button"
                          onClick={(e) => edit(e, customer)}
                        >
                          Edit
                        </button>
                        <button
                          type="button"
                          className="delete-action"
                          onClick={(e) => remove(e, customer)}
                        >
                          Delete
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* CUSTOMER PURCHASE HISTORY MODAL */}
      {selectedCustomer && (
        <div className="customer-details-modal-overlay" onClick={(e) => {
          if (e.target === e.currentTarget) {
            setSelectedCustomer(null);
            setCustomerInvoices([]);
          }
        }}>
          <div className="customer-details-modal">
            <div className="modal-top">
              <div className="modal-title-left">
                <span className="modal-avatar">
                  {selectedCustomer.name.charAt(0).toUpperCase()}
                </span>
                <div>
                  <h2>{selectedCustomer.name}</h2>
                  <p>
                    📞 {selectedCustomer.phone} • {selectedCustomer.email || "No email"}
                  </p>
                  {selectedCustomer.address && (
                    <small>📍 {selectedCustomer.address}</small>
                  )}
                </div>
              </div>
              <button
                type="button"
                className="close-modal-x"
                onClick={() => {
                  setSelectedCustomer(null);
                  setCustomerInvoices([]);
                }}
              >
                ✕
              </button>
            </div>

            {/* Quick customer spending summary */}
            <div className="customer-spend-summary-cards">
              <div className="spend-stat">
                <span>Total Bills</span>
                <strong>{invoicesLoading ? "…" : customerInvoices.length}</strong>
              </div>
              <div className="spend-stat">
                <span>Total Lifetime Spend</span>
                <strong className="spend-highlight">₹{formatMoney(customerTotalSpend)}</strong>
              </div>
              <div className="spend-stat">
                <span>Items Purchased</span>
                <strong>{customerTotalItems} units</strong>
              </div>
            </div>

            {/* Invoices List with Item Breakdown */}
            <div className="customer-invoices-section">
              <h3>Purchase Bills & Line Items</h3>
              {invoicesLoading ? (
                <div style={{ padding: "32px", textAlign: "center", color: "rgba(255,255,255,0.5)" }}>
                  <div style={{
                    width: "32px", height: "32px",
                    border: "3px solid rgba(255,255,255,0.1)",
                    borderTop: "3px solid #6366f1",
                    borderRadius: "50%",
                    animation: "spin 0.8s linear infinite",
                    margin: "0 auto 12px",
                  }} />
                  <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
                  <p style={{ margin: 0, fontSize: "14px" }}>Loading billing history…</p>
                </div>
              ) : invoiceHistoryError ? (
                <div className="no-invoices-found">
                  <p>{invoiceHistoryError}</p>
                </div>
              ) : customerInvoices.length === 0 ? (
                <div className="no-invoices-found">
                  <p>No purchase records found for <strong>{selectedCustomer.name}</strong>.</p>
                  <small>Bills created with this customer name will automatically appear here once billed.</small>
                </div>
              ) : (
                <div className="customer-invoices-list">
                  {customerInvoices.map((inv) => (
                    <div key={inv.id} className="invoice-history-card">
                      <div className="inv-card-header">
                        <div>
                          <span className="inv-badge">{inv.invoiceNumber}</span>
                          <span className="inv-time">
                            📅 {formatBusinessDateTime(inv.soldAt, { dateStyle: "medium", timeStyle: "short" })}
                          </span>
                        </div>
                        <div className="inv-total-pill">
                          Total: ₹{formatMoney(inv.total)}
                          <small className={`inv-payment-status ${inv.paymentStatus === "UNPAID" ? "unpaid" : "paid"}`}>
                            {inv.paymentStatus === "UNPAID" ? `Balance due · ₹${formatMoney(inv.balanceDue)}` : "Paid"}
                          </small>
                          <span className={`inv-payment-method ${inv.paymentMethod === "Balance" ? "balance" : ""}`}>
                            {inv.paymentMethod || "Cash"}
                          </span>
                          {inv.paymentMethod === "Balance" && (
                            <small className="inv-payment-due-date">
                              Payment due: {inv.paymentDueAt ? formatBusinessDateTime(inv.paymentDueAt, { dateStyle: "medium", timeStyle: "short" }) : "Date not set"}
                            </small>
                          )}
                        </div>
                      </div>

                      {/* Items Purchased in this Bill */}
                      <div className="inv-items-table-wrap">
                        <table className="inv-items-table">
                          <thead>
                            <tr>
                              <th>Product</th>
                              <th>Qty</th>
                              <th>Unit Price</th>
                              <th>GST</th>
                              <th style={{ textAlign: "right" }}>Amount</th>
                            </tr>
                          </thead>
                          <tbody>
                            {(inv.items || []).map((it, idx) => (
                              <tr key={idx}>
                                <td>
                                  <strong>{it.productName}</strong>
                                </td>
                                <td>{it.quantity}</td>
                                <td>₹{formatMoney(it.unitPrice)}</td>
                                <td>{Number(it.gstRate || 0)}%</td>
                                <td style={{ textAlign: "right" }}>
                                  ₹{formatMoney(it.lineTotal || it.quantity * it.unitPrice)}
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>

                      <div className="inv-card-footer">
                        <span>Taxable: ₹{formatMoney(inv.taxableAmount)}</span>
                        {Number(inv.discountAmount) > 0 && (
                          <span>Discount: -₹{formatMoney(inv.discountAmount)}</span>
                        )}
                        <span>
                          Tax (CGST+SGST/IGST): ₹
                          {formatMoney(Number(inv.cgst || 0) + Number(inv.sgst || 0) + Number(inv.igst || 0))}
                        </span>
                        <strong>Net Paid: ₹{formatMoney(inv.total)}</strong>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="modal-bottom-actions">
              <button
                type="button"
                className="close-drawer-btn"
                onClick={() => {
                  setSelectedCustomer(null);
                  setCustomerInvoices([]);
                }}
              >
                Close Customer Details
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
