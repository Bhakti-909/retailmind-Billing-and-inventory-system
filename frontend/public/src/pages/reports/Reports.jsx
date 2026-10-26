import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Fragment } from "react";
import { useSearchParams } from "react-router-dom";
import { getProducts } from "../../services/inventoryApi";
import {
  getCustomerReport,
  getInventoryReport,
  getInvoiceItemsReport,
  getInvoiceReport,
  getProductCombinationReport,
  getReportOverview,
  getReportPdf,
  getTaxReport,
} from "../../services/reportApi";
import { addDaysToDateString, businessDateString, formatBusinessDateTime } from "../../utils/businessDate";
import "./reports.css";

const todayString = businessDateString;
const defaultFrom = () => addDaysToDateString(businessDateString(), -29);
const money = (value) => new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  maximumFractionDigits: 2,
}).format(Number(value || 0));
const number = (value) => Number(value || 0).toLocaleString("en-IN");
const reportError = (error) => error?.response?.data?.message || "Could not load reports. Check that the reports service is running.";

function StatCard({ label, value, detail, accent }) {
  return (
    <article className={`report-stat-card ${accent || ""}`}>
      <span>{label}</span>
      <strong>{value}</strong>
      <small>{detail}</small>
    </article>
  );
}

function BarChart({ rows }) {
  const max = Math.max(1, ...rows.map((row) => Number(row.total_sales || 0)));
  if (!rows.length) return <div className="report-empty">No sales in this date range.</div>;

  return (
    <div className="report-chart" role="img" aria-label="Daily sales bar chart">
      {rows.map((row, index) => {
        const height = Math.max(3, (Number(row.total_sales || 0) / max) * 100);
        return (
          <div className="report-chart-column" key={row.date} title={`${row.date}: ${money(row.total_sales)} (${row.bill_count} bills)`}>
            <span className="report-chart-value">{money(row.total_sales)}</span>
            <div className="report-chart-track"><i style={{ height: `${height}%` }} /></div>
            {(index === 0 || index === rows.length - 1 || index % Math.ceil(rows.length / 7) === 0) && (
              <small>{row.date.slice(5)}</small>
            )}
          </div>
        );
      })}
    </div>
  );
}

export default function Reports() {
  const [searchParams] = useSearchParams();
  const targetInvoice = searchParams.get("invoice") || "";
  const [from, setFrom] = useState(() => searchParams.get("from") || defaultFrom());
  const [to, setTo] = useState(() => searchParams.get("to") || todayString);
  const [overview, setOverview] = useState(null);
  const [catalogueProducts, setCatalogueProducts] = useState(null);
  const [catalogueError, setCatalogueError] = useState("");
  const [inventory, setInventory] = useState(null);
  const [tax, setTax] = useState(null);
  const [customers, setCustomers] = useState(null);
  const [combinations, setCombinations] = useState(null);
  const [invoiceReport, setInvoiceReport] = useState(null);
  const [invoicePage, setInvoicePage] = useState(0);
  const [invoicePageSize, setInvoicePageSize] = useState(50);
  const [allBills, setAllBills] = useState(Boolean(targetInvoice));
  const [invoiceSearch, setInvoiceSearch] = useState(targetInvoice);
  const [invoiceLoading, setInvoiceLoading] = useState(true);
  const [invoiceError, setInvoiceError] = useState("");
  const [expandedInvoiceId, setExpandedInvoiceId] = useState(null);
  const [invoiceItems, setInvoiceItems] = useState({});
  const [itemsLoadingId, setItemsLoadingId] = useState(null);
  const [itemsError, setItemsError] = useState("");
  const [exporting, setExporting] = useState(false);
  const [pdfExporting, setPdfExporting] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const invoiceRequestSequence = useRef(0);
  const autoOpenedInvoice = useRef(false);

  const range = useMemo(() => ({ from, to }), [from, to]);
  const activeTopProducts = useMemo(() => {
    if (!catalogueProducts) return [];
    const activeNames = new Set(catalogueProducts.map((product) => String(product.name || "").trim().toLocaleLowerCase()).filter(Boolean));
    return (overview?.topProducts || []).filter((product) => activeNames.has(String(product.product_name || "").trim().toLocaleLowerCase()));
  }, [catalogueProducts, overview]);

  useEffect(() => {
    let current = true;
    getProducts().then((products) => {
      if (!current) return;
      setCatalogueProducts(Array.isArray(products) ? products : []);
      setCatalogueError("");
    }).catch(() => {
      if (!current) return;
      setCatalogueProducts([]);
      setCatalogueError("Current inventory is unavailable, so active best sellers cannot be verified.");
    });
    return () => { current = false; };
  }, []);

  const loadReports = useCallback(async () => {
    if (!allBills && (!from || !to || from > to)) {
      setError("Choose a valid date range. Start date must be on or before end date.");
      setOverview(null);
      setInventory(null);
      setTax(null);
      setCustomers(null);
      setCombinations(null);
      setLoading(false);
      return;
    }
    setLoading(true);
    setError("");
    setOverview(null);
    setInventory(null);
    setTax(null);
    setCustomers(null);
    setCombinations(null);
    try {
      const results = await Promise.allSettled([
        getReportOverview(range),
        getInventoryReport(),
        getTaxReport(range),
        getCustomerReport(range),
        getProductCombinationReport(range),
      ]);
      const setters = [setOverview, setInventory, setTax, setCustomers, setCombinations];
      results.forEach((result, index) => setters[index](result.status === "fulfilled" ? result.value : null));
      const labels = ["Sales", "inventory", "GST", "customer", "product combination"];
      const unavailable = results.flatMap((result, index) => result.status === "rejected" ? [labels[index]] : []);
      if (unavailable.length) setError(`${unavailable.join(", ")} report data could not be loaded. Check the reports service and database, then refresh.`);
    } catch (requestError) {
      setError(reportError(requestError));
    } finally {
      setLoading(false);
    }
  }, [from, range, to]);

  useEffect(() => { loadReports(); }, [loadReports]);

  const loadInvoices = useCallback(async () => {
    const requestId = ++invoiceRequestSequence.current;
    if (!from || !to || from > to) {
      setInvoiceError("Choose a valid date range before loading bills.");
      setInvoiceReport(null);
      setInvoiceLoading(false);
      return;
    }
    setInvoiceLoading(true);
    setInvoiceError("");
    try {
      const result = await getInvoiceReport({
        ...(allBills ? {} : range),
        all: allBills || undefined,
        page: invoicePage,
        size: invoicePageSize,
        search: invoiceSearch.trim() || undefined,
      });
      if (requestId === invoiceRequestSequence.current) setInvoiceReport(result);
    } catch (requestError) {
      if (requestId === invoiceRequestSequence.current) {
        setInvoiceReport(null);
        setInvoiceError(reportError(requestError));
      }
    } finally {
      if (requestId === invoiceRequestSequence.current) setInvoiceLoading(false);
    }
  }, [from, to, range, allBills, invoicePage, invoicePageSize, invoiceSearch]);

  useEffect(() => {
    loadInvoices();
    return () => { invoiceRequestSequence.current += 1; };
  }, [loadInvoices]);

  useEffect(() => {
    const refreshVisibleRegister = () => {
      if (document.visibilityState === "visible") loadInvoices();
    };
    window.addEventListener("focus", refreshVisibleRegister);
    document.addEventListener("visibilitychange", refreshVisibleRegister);
    return () => {
      window.removeEventListener("focus", refreshVisibleRegister);
      document.removeEventListener("visibilitychange", refreshVisibleRegister);
    };
  }, [loadInvoices]);

  const toggleInvoiceDetails = async (invoiceId) => {
    if (expandedInvoiceId === invoiceId) {
      setExpandedInvoiceId(null);
      return;
    }
    setExpandedInvoiceId(invoiceId);
    setItemsError("");
    if (invoiceItems[invoiceId]) return;
    setItemsLoadingId(invoiceId);
    try {
      const rows = await getInvoiceItemsReport(invoiceId, { ...(allBills ? {} : range), all: allBills || undefined });
      setInvoiceItems((current) => ({ ...current, [invoiceId]: rows }));
    } catch (requestError) {
      setItemsError(reportError(requestError));
    } finally {
      setItemsLoadingId(null);
    }
  };

  useEffect(() => {
    if (!targetInvoice || autoOpenedInvoice.current || invoiceLoading || !invoiceReport) return;
    const bill = invoiceReport.invoices?.find((item) => item.invoice_number === targetInvoice);
    if (!bill) return;
    autoOpenedInvoice.current = true;
    setExpandedInvoiceId(bill.invoice_id);
    setItemsError("");
    if (!invoiceItems[bill.invoice_id]) {
      setItemsLoadingId(bill.invoice_id);
      getInvoiceItemsReport(bill.invoice_id, { all: true })
        .then((rows) => setInvoiceItems((current) => ({ ...current, [bill.invoice_id]: rows })))
        .catch((requestError) => setItemsError(reportError(requestError)))
        .finally(() => setItemsLoadingId(null));
    }
    requestAnimationFrame(() => document.getElementById(`invoice-row-${bill.invoice_id}`)?.scrollIntoView({ behavior: "smooth", block: "center" }));
  }, [targetInvoice, invoiceLoading, invoiceReport, invoiceItems]);

  const downloadCsv = async () => {
    if (!overview) return;
    setExporting(true);
    setInvoiceError("");
    let allInvoices = [];
    try {
      const search = invoiceSearch.trim() || undefined;
      const firstPage = await getInvoiceReport({ ...(allBills ? {} : range), all: allBills || undefined, page: 0, size: 100, search });
      allInvoices = firstPage.invoices || [];
      for (let page = 1; page < firstPage.totalPages; page += 1) {
        const nextPage = await getInvoiceReport({ ...(allBills ? {} : range), all: allBills || undefined, page, size: 100, search });
        allInvoices = allInvoices.concat(nextPage.invoices || []);
      }
    } catch (requestError) {
      setInvoiceError(`Could not export the full bill register. ${reportError(requestError)}`);
      setExporting(false);
      return;
    }
    const lines = [
      ["Smart Retail Report", `${from} to ${to}`],
      ["Bill register", `${allBills ? "All bills" : `Date range: ${from} to ${to}`}${invoiceSearch.trim() ? ` · Search: ${invoiceSearch.trim()}` : ""}`],
      ["Invoice", "Date", "Customer", "Phone", "Items", "GST invoice", "Discount", "Taxable", "CGST", "SGST", "IGST", "Invoice total", "Net revenue", "Cost", "Gross profit"],
      ...allInvoices.map((bill) => [bill.invoice_number, bill.sold_at, bill.customer_name, bill.customer_phone, bill.item_count, bill.gst_invoice ? "Yes" : "No", bill.discount_amount, bill.taxable_amount, bill.cgst_amount, bill.sgst_amount, bill.igst_amount, bill.total_amount, bill.net_revenue, bill.cost_of_goods, bill.gross_profit]),
      [],
      ["Sales summary", "Value"],
      ["Bills", overview.summary.bill_count],
      ["Total sales", overview.summary.total_sales],
      ["Net revenue", overview.summary.net_revenue],
      ["Cost of goods", overview.summary.cost_of_goods],
      ["Gross profit", overview.summary.gross_profit],
      ["Tax collected", overview.summary.tax_collected],
      [],
      ["Daily sales"],
      ["Date", "Bills", "Sales", "Gross profit"],
      ...(overview.dailySales || []).map((row) => [row.date, row.bill_count, row.total_sales, row.gross_profit]),
      [],
      ["Top products"],
      ["Product", "Units sold", "Sales", "Gross profit"],
      ...(overview.topProducts || []).map((row) => [row.product_name, row.units_sold, row.sales, row.gross_profit]),
      [],
      ["Stock by category"],
      ["Category", "Products", "Units on hand", "Cost value"],
      ...(inventory?.categories || []).map((row) => [row.category, row.product_count, row.units_on_hand, row.cost_value]),
      [],
      ["Stock by supplier"],
      ["Supplier", "Products", "Units on hand", "Cost value", "Low stock products"],
      ...(inventory?.suppliers || []).map((row) => [row.supplier_name, row.product_count, row.units_on_hand, row.cost_value, row.low_stock_count]),
      [],
      ["GST summary"],
      ["Taxable sales", gst.taxable_sales],
      ["CGST", gst.cgst],
      ["SGST", gst.sgst],
      ["IGST", gst.igst],
      ["Tax collected", gst.tax_collected],
      [],
      ["Top customers"],
      ["Customer", "Phone", "Bills", "Total spend", "Gross profit", "Last purchase"],
      ...(customers?.topCustomers || []).map((row) => [row.customer_name, row.phone, row.bill_count, row.total_spend, row.gross_profit, row.last_purchase]),
      [],
      ["Products to review"],
      ["Product", "SKU", "Category", "On hand", "Reorder level", "Cost", "Expiry"],
      ...(inventory?.attentionItems || []).map((row) => [row.name, row.sku, row.category, row.quantity, row.reorder_level, row.cost_price, row.expiry_date]),
      [],
      ["Frequently bought together"],
      ["Product A", "Product B", "Bills together", "Combined units", "Combined sales"],
      ...(combinations?.combinations || []).map((row) => [row.product_a, row.product_b, row.bills_together, row.combined_units, row.combined_sales]),
    ];
    const csv = lines.map((row) => row.map((value) => `"${String(value ?? "").replaceAll('"', '""')}"`).join(",")).join("\r\n");
    const url = URL.createObjectURL(new Blob([csv], { type: "text/csv;charset=utf-8" }));
    const link = document.createElement("a");
    link.href = url;
    link.download = `smart-retail-report-${from}-to-${to}.csv`;
    link.click();
    URL.revokeObjectURL(url);
    setExporting(false);
  };

  const downloadPdf = async () => {
    if (!overview) return;
    setPdfExporting(true);
    setError("");
    try {
      const pdf = await getReportPdf(range);
      const url = URL.createObjectURL(new Blob([pdf], { type: "application/pdf" }));
      const link = document.createElement("a");
      link.href = url;
      link.download = `smart-retail-report-${from}-to-${to}.pdf`;
      link.click();
      URL.revokeObjectURL(url);
    } catch (requestError) {
      setError(reportError(requestError));
    } finally {
      setPdfExporting(false);
    }
  };

  const sales = overview?.summary || {};
  const stock = inventory?.summary || {};
  const gst = tax?.summary || {};
  const customerSummary = customers?.summary || {};
  const topProductSales = activeTopProducts.reduce((sum, product) => sum + Number(product.sales || 0), 0);

  return (
    <main className="reports-page">
      <header className="reports-header">
        <div>
          <p className="reports-eyebrow">SMART RETAIL BUSINESS INTELLIGENCE</p>
          <h1>Reports</h1>
          <p>Track sales, product performance, stock, tax and customer activity for your business.</p>
        </div>
        <div className="reports-controls">
          <label>From<input type="date" value={from} max={to} onChange={(event) => { setFrom(event.target.value); setInvoicePage(0); }} /></label>
          <label>To<input type="date" value={to} min={from} max={todayString()} onChange={(event) => { setTo(event.target.value); setInvoicePage(0); }} /></label>
          <button type="button" onClick={loadReports} disabled={loading}>{loading ? "Loading…" : "Refresh reports"}</button>
          <button type="button" className="reports-export" onClick={downloadCsv} disabled={!overview || exporting}>{exporting ? "Preparing CSV…" : "Export CSV"}</button>
          <button type="button" className="reports-export" onClick={downloadPdf} disabled={!overview || pdfExporting}>{pdfExporting ? "Preparing PDF…" : "Export PDF"}</button>
        </div>
      </header>

      {error && <div className="reports-error" role="alert">{error}</div>}

      <section className="reports-section">
        <div className="reports-section-heading"><div><span>SALES PERFORMANCE</span><h2>At a glance</h2></div><small>{from} — {to}</small></div>
        <div className="report-stats-grid">
          <StatCard label="TOTAL SALES" value={overview ? money(sales.total_sales) : loading ? "…" : error ? "Unavailable" : money(0)} detail={overview ? `${number(sales.bill_count)} bills` : "Selected period"} />
          <StatCard label="GROSS PROFIT" value={overview ? money(sales.gross_profit) : loading ? "…" : error ? "Unavailable" : money(0)} detail={overview ? `Cost of goods ${money(sales.cost_of_goods)}` : "Completed bills less product cost"} accent={overview && Number(sales.gross_profit) < 0 ? "loss" : "profit"} />
          <StatCard label="AVERAGE BILL" value={overview ? money(sales.average_bill) : loading ? "…" : error ? "Unavailable" : money(0)} detail="Average amount per bill" />
          <StatCard label="UNITS IN STOCK" value={inventory ? number(stock.units_on_hand) : loading ? "…" : error ? "Unavailable" : number(0)} detail={inventory ? `${number(stock.product_count)} products` : "Current inventory"} />
          <StatCard label="LOW STOCK" value={inventory ? number(stock.low_stock_count) : loading ? "…" : error ? "Unavailable" : number(0)} detail={`${number(stock.out_of_stock_count)} out of stock · ${number(stock.expired_count)} expired`} accent="warning" />
          <StatCard label="GST COLLECTED" value={tax ? money(gst.tax_collected) : loading ? "…" : error ? "Unavailable" : money(0)} detail={tax ? `${number(gst.invoice_count)} GST invoices` : "Selected period"} />
        </div>
      </section>

      <section className="reports-grid-two">
        <article className="report-panel">
          <div className="report-panel-heading"><div><span>DAILY TOTALS</span><h2>Sales trend</h2></div><small>{number(sales.bill_count)} bills</small></div>
          {loading && !overview ? <div className="report-empty">Loading sales…</div> : !overview ? <div className="report-empty">Sales report is unavailable.</div> : <BarChart rows={overview.dailySales || []} />}
        </article>
        <article className="report-panel">
          <div className="report-panel-heading"><div><span>PRODUCT PERFORMANCE</span><h2>Best sellers</h2></div><small>Active products ranked by units sold</small></div>
          {catalogueProducts === null ? <div className="report-empty">Loading current inventory…</div> : catalogueError ? <div className="report-empty">{catalogueError}</div> : activeTopProducts.length ? (
            <div className="report-ranked-list">
              {activeTopProducts.map((product, index) => (
                <div className="report-rank-row" key={product.product_name}>
                  <span className="report-rank">{String(index + 1).padStart(2, "0")}</span>
                  <div className="report-rank-main"><strong>{product.product_name}</strong><small>{number(product.units_sold)} units</small></div>
                  <div className="report-rank-total"><strong>{money(product.sales)}</strong><small className={Number(product.gross_profit) < 0 ? "profit-loss-value" : ""}>Profit {money(product.gross_profit)}</small></div>
                  <span className="report-product-share" title={`${topProductSales ? Math.round((Number(product.sales || 0) / topProductSales) * 100) : 0}% of sales from top products`}>
                    <i style={{ width: `${topProductSales ? Math.max(2, (Number(product.sales || 0) / topProductSales) * 100) : 0}%` }} />
                  </span>
                </div>
              ))}
            </div>
          ) : <div className="report-empty">{loading && !overview ? "Loading product sales…" : !overview ? "Product sales report is unavailable." : "No active products sold in this period. Deleted products remain in past bills but are omitted from this ranking."}</div>}
        </article>
      </section>

      <section className="report-panel report-invoices-panel">
        <div className="report-panel-heading">
          <div><span>BILL REGISTER</span><h2>{allBills ? "All generated bills" : "Bills for selected dates"}</h2></div>
          <div className="invoice-register-heading-actions"><small>{number(invoiceReport?.totalElements)} bills · {allBills ? "All dates" : `${from} — ${to}`}</small><button type="button" className="invoice-details-button" onClick={loadInvoices} disabled={invoiceLoading}>{invoiceLoading ? "Refreshing…" : "Refresh bills"}</button></div>
        </div>
        <div className="invoice-register-controls">
          <div className="invoice-mode-control" role="group" aria-label="Bill date filter"><button type="button" className={!allBills ? "selected" : ""} aria-pressed={!allBills} onClick={() => { setAllBills(false); setInvoicePage(0); }}>Selected dates</button><button type="button" className={allBills ? "selected" : ""} aria-pressed={allBills} onClick={() => { setAllBills(true); setInvoicePage(0); }}>All bills</button></div>
          <label className="invoice-search">Search bills<input type="search" value={invoiceSearch} placeholder="Invoice, customer or phone" onChange={(event) => { setInvoiceSearch(event.target.value); setInvoicePage(0); }} /></label>
          <label>Rows per page<select value={invoicePageSize} onChange={(event) => { setInvoicePageSize(Number(event.target.value)); setInvoicePage(0); }}><option value={25}>25</option><option value={50}>50</option><option value={100}>100</option></select></label>
        </div>
        {invoiceError && <div className="reports-error" role="alert">{invoiceError}</div>}
        {invoiceLoading ? <div className="report-empty">Loading generated bills…</div> : !invoiceReport ? <div className="report-empty">Bill register is unavailable.</div> : invoiceReport.invoices?.length ? <>
          <div className="report-table-wrap">
            <table className="report-table invoice-register-table">
              <thead><tr><th>Date</th><th>Invoice</th><th>Customer</th><th>Phone</th><th>Items</th><th>Discount</th><th>Taxable</th><th>GST</th><th>Total</th><th>Gross profit</th><th>Details</th></tr></thead>
              <tbody>{invoiceReport.invoices.map((bill) => <Fragment key={bill.invoice_id}>
                  <tr id={`invoice-row-${bill.invoice_id}`}>
                  <td>{formatBusinessDateTime(bill.sold_at, { dateStyle: "medium", timeStyle: "short" })}</td>
                  <td><strong>{bill.invoice_number || `#${bill.invoice_id}`}</strong></td>
                  <td>{bill.customer_name || bill.buyer_name || "Walk-in customer"}</td>
                  <td>{bill.customer_phone || "—"}</td>
                  <td>{number(bill.item_count)}</td>
                  <td>{money(bill.discount_amount)}</td>
                  <td>{money(bill.taxable_amount)}</td>
                  <td><span className="report-status" title={`CGST ${money(bill.cgst_amount)} · SGST ${money(bill.sgst_amount)} · IGST ${money(bill.igst_amount)}`}>{bill.gst_invoice ? `GST ${money(Number(bill.cgst_amount || 0) + Number(bill.sgst_amount || 0) + Number(bill.igst_amount || 0))}` : "Non-GST"}</span></td>
                  <td><strong>{money(bill.total_amount)}</strong></td>
                  <td className={Number(bill.gross_profit) < 0 ? "profit-loss-value" : ""}>{money(bill.gross_profit)}</td>
                  <td><button type="button" className="invoice-details-button" onClick={() => toggleInvoiceDetails(bill.invoice_id)}>{expandedInvoiceId === bill.invoice_id ? "Close bill" : "View bill"}</button></td>
                </tr>
                {expandedInvoiceId === bill.invoice_id && <tr className="invoice-detail-row"><td colSpan="11">
                  <div className="invoice-full-detail">
                    <div className="invoice-full-detail-heading"><div><span>INVOICE</span><strong>{bill.invoice_number || `#${bill.invoice_id}`}</strong><small>{formatBusinessDateTime(bill.sold_at, { dateStyle: "long", timeStyle: "short" })}</small></div><div><span>SELLER GSTIN</span><strong>{bill.seller_gstin || "Not recorded"}</strong></div><div><span>BUYER</span><strong>{bill.customer_name || "Walk-in customer"}</strong><small>{bill.customer_phone || "Phone not recorded"}</small></div><div><span>BUYER GSTIN</span><strong>{bill.buyer_gstin || "Not recorded"}</strong></div></div>
                    {itemsLoadingId === bill.invoice_id ? <div className="report-empty compact">Loading bill items…</div> : itemsError ? <div className="reports-error" role="alert">{itemsError}</div> : (invoiceItems[bill.invoice_id] || []).length ? <div className="report-table-wrap"><table className="report-table invoice-items-table">
                      <thead><tr><th>Product</th><th>Qty</th><th>Unit price</th><th>Product discount</th><th>Bill discount</th><th>Taxable</th><th>GST rate</th><th>GST amount</th><th>Sales</th><th>Profit</th></tr></thead>
                      <tbody>{invoiceItems[bill.invoice_id].map((item) => <tr key={item.line_id}><td><strong>{item.product_name}</strong></td><td>{number(item.quantity)}</td><td>{money(item.unit_price)}</td><td>{money(item.product_discount)}</td><td>{money(item.bill_discount)}</td><td>{money(item.taxable_amount)}</td><td>{number(item.gst_rate)}%</td><td>{money(item.gst_amount)}</td><td>{money(item.line_sales)}</td><td className={Number(item.line_profit) < 0 ? "profit-loss-value" : ""}>{money(item.line_profit)}</td></tr>)}</tbody>
                    </table></div> : <div className="report-empty compact">No line items found for this bill.</div>}
                    <div className="invoice-full-detail-totals"><span>Items <strong>{number(bill.item_count)}</strong></span><span>Discount <strong>{money(bill.discount_amount)}</strong></span><span>Taxable amount <strong>{money(bill.taxable_amount)}</strong></span><span>CGST <strong>{money(bill.cgst_amount)}</strong></span><span>SGST <strong>{money(bill.sgst_amount)}</strong></span><span>IGST <strong>{money(bill.igst_amount)}</strong></span><span>Cost <strong>{money(bill.cost_of_goods)}</strong></span><span>Gross profit <strong className={Number(bill.gross_profit) < 0 ? "profit-loss-value" : ""}>{money(bill.gross_profit)}</strong></span><span className="invoice-full-grand-total">Bill total <strong>{money(bill.total_amount)}</strong></span></div>
                  </div>
                </td></tr>}
              </Fragment>)}</tbody>
            </table>
          </div>
          <div className="invoice-register-pager"><span>Showing {invoiceReport.totalElements ? invoicePage * invoicePageSize + 1 : 0}–{Math.min((invoicePage + 1) * invoicePageSize, invoiceReport.totalElements || 0)} of {number(invoiceReport.totalElements)} bills</span><div><button type="button" onClick={() => setInvoicePage((page) => Math.max(0, page - 1))} disabled={invoicePage === 0}>Previous</button><span>Page {invoicePage + 1} of {Math.max(1, invoiceReport.totalPages || 0)}</span><button type="button" onClick={() => setInvoicePage((page) => page + 1)} disabled={invoicePage + 1 >= (invoiceReport.totalPages || 0)}>Next</button></div></div>
        </> : <div className="report-empty">No generated bills match this date range and search.</div>}
      </section>

      <section className="report-panel report-combinations-panel">
        <div className="report-panel-heading"><div><span>PRODUCT COMBINATIONS</span><h2>Frequently bought together</h2></div><small>Products sold on the same bill</small></div>
        {(combinations?.combinations || []).length ? (
          <div className="report-table-wrap">
            <table className="report-table">
              <thead><tr><th>Product pair</th><th>Bills together</th><th>Combined units</th><th>Combined sales</th></tr></thead>
              <tbody>{combinations.combinations.map((pair) => (
                <tr key={`${pair.product_a}-${pair.product_b}`}>
                  <td><strong>{pair.product_a}</strong><span className="report-pair-join">+</span><strong>{pair.product_b}</strong></td>
                  <td>{number(pair.bills_together)}</td>
                  <td>{number(pair.combined_units)}</td>
                  <td>{money(pair.combined_sales)}</td>
                </tr>
              ))}</tbody>
            </table>
          </div>
        ) : <div className="report-empty">{loading && !combinations ? "Loading product combinations…" : !combinations ? "Product combination report is unavailable." : "No multi-product bills in this date range yet. Product pairs appear after customers buy more than one product on a bill."}</div>}
      </section>

      <section className="reports-grid-two">
        <article className="report-panel">
          <div className="report-panel-heading"><div><span>INVENTORY POSITION</span><h2>Stock by category</h2></div><small>On hand value at cost</small></div>
          {(inventory?.categories || []).length ? (
            <div className="report-ranked-list">
              {inventory.categories.map((category) => (
                <div className="report-rank-row" key={category.category}>
                  <span className="report-category-dot" />
                  <div className="report-rank-main"><strong>{category.category}</strong><small>{number(category.product_count)} products · {number(category.units_on_hand)} units</small></div>
                  <div className="report-rank-total"><strong>{money(category.cost_value)}</strong><small>Cost value</small></div>
                </div>
              ))}
            </div>
          ) : <div className="report-empty">{loading && !inventory ? "Loading inventory…" : !inventory ? "Inventory report is unavailable." : "No products in inventory."}</div>}
          <h3 className="report-subheading">Stock assigned by supplier</h3>
          {(inventory?.suppliers || []).length ? (
            <div className="report-ranked-list report-supplier-list">
              {inventory.suppliers.slice(0, 6).map((supplier) => (
                <div className="report-rank-row" key={supplier.supplier_name}>
                  <span className="report-category-dot supplier-dot" />
                  <div className="report-rank-main"><strong>{supplier.supplier_name}</strong><small>{number(supplier.product_count)} products · {number(supplier.units_on_hand)} units</small></div>
                  <div className="report-rank-total"><strong>{money(supplier.cost_value)}</strong><small>{number(supplier.low_stock_count)} low stock</small></div>
                </div>
              ))}
            </div>
          ) : <div className="report-empty compact">{!inventory ? "Supplier stock data is unavailable." : "No supplier assignments in products."}</div>}
          <div className="report-stock-footnote">Inventory cost value {money(stock.cost_value)} · Retail value {money(stock.retail_value)} · Expiring soon {number(stock.expiring_soon_count)} · Expired {number(stock.expired_count)}</div>
        </article>

        <article className="report-panel">
          <div className="report-panel-heading"><div><span>GST SUMMARY</span><h2>Tax collected</h2></div><small>{from} — {to}</small></div>
          <div className="report-tax-total"><span>Taxable sales</span><strong>{money(gst.taxable_sales)}</strong></div>
          <div className="report-tax-split">
            <div><span>CGST</span><strong>{money(gst.cgst)}</strong></div>
            <div><span>SGST</span><strong>{money(gst.sgst)}</strong></div>
            <div><span>IGST</span><strong>{money(gst.igst)}</strong></div>
          </div>
          <div className="report-rate-list">
            {(tax?.rates || []).map((rate) => (
              <div key={rate.gst_rate}><span>{Number(rate.gst_rate)}% rate · {number(rate.units_sold)} units</span><strong>{money(rate.tax_collected)}</strong></div>
            ))}
            {!tax?.rates?.length && <div className="report-empty">{loading && !tax ? "Loading tax report…" : !tax ? "Tax report is unavailable." : "No GST invoices in this date range."}</div>}
          </div>
        </article>
      </section>

      <section className="report-panel report-attention-panel">
        <div className="report-panel-heading"><div><span>STOCK ACTIONS</span><h2>Products to review</h2></div><small>Low stock and expired or soon-to-expire products</small></div>
        {(inventory?.attentionItems || []).length ? (
          <div className="report-table-wrap">
            <table className="report-table">
              <thead><tr><th>Product</th><th>SKU</th><th>Category</th><th>On hand</th><th>Reorder level</th><th>Cost</th><th>Expiry</th><th>Status</th></tr></thead>
              <tbody>{inventory.attentionItems.map((product) => {
                const expired = product.expiry_date && product.expiry_date < todayString();
                const status = expired ? "Expired" : Number(product.quantity) <= 0 ? "Out of stock" : Number(product.quantity) <= Number(product.reorder_level) ? "Low stock" : "Expiring soon";
                return (
                  <tr key={product.id}>
                    <td><strong>{product.name}</strong></td><td>{product.sku}</td><td>{product.category}</td>
                    <td>{number(product.quantity)}</td><td>{number(product.reorder_level)}</td><td>{money(product.cost_price)}</td>
                    <td>{product.expiry_date || "—"}</td><td><span className={`report-status ${status.toLowerCase().replaceAll(" ", "-")}`}>{status}</span></td>
                  </tr>
                );
              })}</tbody>
            </table>
          </div>
        ) : <div className="report-empty">{loading && !inventory ? "Loading inventory attention items…" : !inventory ? "Inventory attention data is unavailable." : "No products need stock or expiry attention."}</div>}
      </section>

      <section className="report-panel report-customers-panel">
        <div className="report-panel-heading"><div><span>CUSTOMER ACTIVITY</span><h2>Top customers</h2></div><small>{number(customerSummary.customer_count)} customers · {money(customerSummary.customer_sales)} sales</small></div>
        {(customers?.topCustomers || []).length ? (
          <div className="report-table-wrap">
            <table className="report-table">
              <thead><tr><th>Customer</th><th>Phone</th><th>Bills</th><th>Total spend</th><th>Gross profit</th><th>Last purchase</th></tr></thead>
              <tbody>{customers.topCustomers.map((customer, index) => (
                <tr key={customer.customer_id || `${customer.customer_name}-${index}`}>
                  <td><strong>{customer.customer_name}</strong></td>
                  <td>{customer.phone || "—"}</td>
                  <td>{number(customer.bill_count)}</td>
                  <td>{money(customer.total_spend)}</td>
                  <td className={Number(customer.gross_profit) < 0 ? "profit-loss-value" : ""}>{money(customer.gross_profit)}</td>
                  <td>{formatBusinessDateTime(customer.last_purchase, { dateStyle: "medium" })}</td>
                </tr>
              ))}</tbody>
            </table>
          </div>
        ) : <div className="report-empty">{loading && !customers ? "Loading customer report…" : !customers ? "Customer report is unavailable." : "No named customer sales in this date range."}</div>}
      </section>

      <p className="reports-footnote">Sales and profit figures come from completed bills. Inventory values use current on-hand quantity and product prices.</p>
    </main>
  );
}
