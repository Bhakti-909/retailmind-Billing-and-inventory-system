import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getInvoices } from "../../services/billingApi";
import { addDaysToDateString, businessDateString, formatBusinessDateTime } from "../../utils/businessDate";
import { paymentAmounts, paymentTenders } from "./cashPaymentMath";
import "./cashPayments.css";

const money = (value) => `₹${Number(value || 0).toLocaleString("en-IN", { maximumFractionDigits: 0 })}`;
const dayOf = (value) => String(value || "").slice(0, 10);
const modeLabel = (value) => ({ Cash: "Cash", UPI: "UPI", Online: "UPI", Card: "Card", "Credit Card": "Credit Card", "Debit Card": "Debit Card", Split: "Split payment", Balance: "Balance" }[value] || value || "Cash");
export default function CashPayments() {
  const navigate = useNavigate();
  const today = businessDateString();
  const [from, setFrom] = useState(() => addDaysToDateString(today, -29));
  const [to, setTo] = useState(today);
  const [invoices, setInvoices] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("All statuses");
  const [method, setMethod] = useState("All methods");

  const loadPayments = useCallback(async () => {
    if (!from || !to || from > to) {
      setError("Choose a valid date range.");
      setLoading(false);
      return;
    }
    setLoading(true);
    setError("");
    try {
      setInvoices(await getInvoices());
    } catch {
      setError("Payment records are unavailable right now. Refresh to try again.");
    } finally {
      setLoading(false);
    }
  }, [from, to]);

  useEffect(() => { loadPayments(); }, [loadPayments]);

  const periodInvoices = useMemo(() => invoices.filter((invoice) => {
    const date = dayOf(invoice.soldAt);
    return date >= from && date <= to;
  }), [invoices, from, to]);
  const summary = useMemo(() => {
    const byMethod = new Map();
    let collected = 0;
    let outstanding = 0;
    let dueCount = 0;
    periodInvoices.forEach((invoice) => {
      const { paid, due } = paymentAmounts(invoice);
      collected += paid;
      outstanding += due;
      if (due > 0) dueCount += 1;
      paymentTenders(invoice).forEach(([tender, amount]) => byMethod.set(tender, (byMethod.get(tender) || 0) + amount));
    });
    return { collected, outstanding, dueCount, byMethod: [...byMethod.entries()].sort((a, b) => b[1] - a[1]) };
  }, [periodInvoices]);
  const methods = useMemo(() => ["All methods", ...new Set(periodInvoices.flatMap((invoice) => {
    const tenders = paymentTenders(invoice).map(([tender]) => tender);
    return tenders.length ? tenders : [invoice.paymentMethod || "Cash"];
  }))], [periodInvoices]);
  const filteredInvoices = useMemo(() => periodInvoices.filter((invoice) => {
    const hasDue = paymentAmounts(invoice).due > 0;
    const matchesStatus = status === "All statuses" || (status === "Balance due" ? hasDue : !hasDue);
    const matchesMethod = method === "All methods" || (invoice.paymentMethod || "Cash") === method || paymentTenders(invoice).some(([tender]) => tender === method);
    const term = query.trim().toLocaleLowerCase("en-IN");
    const matchesQuery = !term || `${invoice.invoiceNumber} ${invoice.buyerName} ${invoice.paymentMethod}`.toLocaleLowerCase("en-IN").includes(term);
    return matchesStatus && matchesMethod && matchesQuery;
  }), [periodInvoices, query, status, method]);

  const setPreset = (days) => {
    setFrom(addDaysToDateString(today, -(days - 1)));
    setTo(today);
  };

  return <main className="cash-payments-page">
    <header className="cash-page-header">
      <div><p className="cash-eyebrow">FINANCE</p><h1>Cash &amp; Payments</h1><p>Track collections, payment methods, and customer balances from your bills.</p></div>
      <button className="cash-primary-action" onClick={() => navigate("/billing")}>＋ Create a bill</button>
    </header>

    <section className="cash-toolbar" aria-label="Payment date filters">
      <div className="cash-presets"><button className={from === addDaysToDateString(today, -6) && to === today ? "selected" : ""} onClick={() => setPreset(7)}>7 days</button><button className={from === addDaysToDateString(today, -29) && to === today ? "selected" : ""} onClick={() => setPreset(30)}>30 days</button><button className={from === addDaysToDateString(today, -89) && to === today ? "selected" : ""} onClick={() => setPreset(90)}>90 days</button></div>
      <label>From <input type="date" value={from} max={to} onChange={(event) => setFrom(event.target.value)} /></label>
      <label>To <input type="date" value={to} min={from} max={today} onChange={(event) => setTo(event.target.value)} /></label>
      <button className="cash-refresh" onClick={loadPayments} disabled={loading}>{loading ? "Refreshing…" : "Refresh"}</button>
    </section>

    {error && <div className="cash-error" role="alert">{error}</div>}

    <section className="cash-summary-grid" aria-label="Payment summary">
      <article className="cash-summary-card collected"><span>COLLECTED</span><strong>{loading ? "—" : money(summary.collected)}</strong><small>Payments received in this period</small><i>₹</i></article>
      <article className={`cash-summary-card outstanding ${summary.outstanding > 0 ? "has-balance" : ""}`}><span>OUTSTANDING BALANCE</span><strong>{loading ? "—" : money(summary.outstanding)}</strong><small>{loading ? "Loading bills" : `${summary.dueCount} ${summary.dueCount === 1 ? "bill" : "bills"} with balance due`}</small><i>↗</i></article>
      <article className="cash-summary-card bill-count"><span>BILLS IN PERIOD</span><strong>{loading ? "—" : periodInvoices.length.toLocaleString("en-IN")}</strong><small>{from} to {to}</small><i>▤</i></article>
    </section>

    <section className="cash-content-grid">
      <article className="cash-panel payment-register">
        <div className="cash-panel-heading"><div><span>PAYMENT REGISTER</span><h2>Recent bills &amp; payments</h2></div><b>{filteredInvoices.length} records</b></div>
        <div className="cash-table-filters">
          <input aria-label="Search bills" placeholder="Search invoice or customer" value={query} onChange={(event) => setQuery(event.target.value)} />
          <select aria-label="Filter by payment status" value={status} onChange={(event) => setStatus(event.target.value)}><option>All statuses</option><option>Paid</option><option>Balance due</option></select>
          <select aria-label="Filter by payment method" value={method} onChange={(event) => setMethod(event.target.value)}>{methods.map((value) => <option key={value} value={value}>{value === "All methods" ? value : modeLabel(value)}</option>)}</select>
        </div>
        {loading ? <div className="cash-empty-state">Loading payment records…</div> : filteredInvoices.length ? <div className="cash-table-wrap"><table><thead><tr><th>Bill / Customer</th><th>Date / Due date</th><th>Billing mode</th><th>Status</th><th className="cash-amount">Bill total</th><th className="cash-amount">Paid</th><th className="cash-amount">Balance due</th><th /></tr></thead><tbody>{filteredInvoices.map((invoice) => {
          const { paid, due, status: paymentStatus } = paymentAmounts(invoice);
          const tenders = paymentTenders(invoice);
          const hasDue = paymentStatus === "UNPAID";
          return <tr key={invoice.id || invoice.invoiceNumber}><td><strong>{invoice.invoiceNumber}</strong><small>{invoice.buyerName || "Walk-in customer"}</small></td><td>{formatBusinessDateTime(invoice.soldAt, { month: "short", day: "numeric", year: "numeric", hour: "numeric", minute: "2-digit" })}{invoice.paymentDueAt && <small>Due {formatBusinessDateTime(invoice.paymentDueAt, { month: "short", day: "numeric" })}</small>}</td><td><span className={`cash-method-pill mode-${String(invoice.paymentMethod || "Cash").toLowerCase()}`}>{modeLabel(invoice.paymentMethod)}</span>{invoice.paymentMethod === "Split" && <small className="cash-tender-detail">{tenders.map(([tender, amount]) => `${modeLabel(tender)} ${money(amount)}`).join(" · ") || "Tender details unavailable"}</small>}</td><td><span className={`cash-status ${hasDue ? "due" : "paid"}`}>{hasDue ? "Balance due" : "Paid"}</span></td><td className="cash-amount">{money(invoice.total)}</td><td className="cash-amount">{money(paid)}</td><td className={`cash-amount ${hasDue ? "cash-due-value" : ""}`}>{money(due)}</td><td><button className="cash-view-button" onClick={() => navigate(`/reports?invoice=${encodeURIComponent(invoice.invoiceNumber)}`)}>View bill</button></td></tr>;
        })}</tbody></table></div> : <div className="cash-empty-state"><strong>{periodInvoices.length ? "No matching payment records" : "No bills in this period"}</strong><span>{periodInvoices.length ? "Adjust the search or filters and try again." : "Completed bills will appear here as payments are recorded."}</span></div>}
      </article>

      <aside className="cash-side-column">
        <article className="cash-panel cash-method-panel"><div className="cash-panel-heading"><div><span>COLLECTION MIX</span><h2>By payment method</h2></div></div>
          {loading ? <div className="cash-empty-state compact">Loading…</div> : summary.byMethod.length ? <div className="cash-method-list">{summary.byMethod.map(([name, amount]) => <div className="cash-method-row" key={name}><div className="cash-method-label"><i className={`cash-method-dot ${name.toLowerCase()}`} /><span>{modeLabel(name)}</span></div><strong>{money(amount)}</strong><div className="cash-method-track"><i style={{ width: `${Math.max(3, (amount / summary.collected) * 100)}%` }} /></div></div>)}</div> : <div className="cash-empty-state compact">No payments received in this period.</div>}
        </article>
        <article className="cash-payment-help"><span>↗</span><div><strong>Need to collect a balance?</strong><p>Open the bill to review customer details, or create a new bill with a payment method.</p><button onClick={() => setStatus("Balance due")}>Show balances due →</button></div></article>
      </aside>
    </section>
  </main>;
}
