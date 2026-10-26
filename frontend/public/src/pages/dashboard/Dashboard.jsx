// import { useNavigate } from "react-router-dom";
// import { useAuth } from "../../hooks/useAuth";

// export default function Dashboard() {
//   const navigate = useNavigate();
//   const { user } = useAuth();

//   const userName = user?.ownerName || "User";

//   const modules = [
//     {
//       title: "Billing",
//       description: "Create bills, invoices and manage payments.",
//       icon: "▣",
//       path: "/billing",
//     },
//     {
//       title: "Inventory Management",
//       description: "Manage products, stock, expiry and low stock.",
//       icon: "▤",
//       path: "/inventory",
//     },
//     {
//       title: "Customer Management",
//       description: "Manage customers and their purchase history.",
//       icon: "♙",
//       path: "/customers",
//     },
//     {
//       title: "Suppliers",
//       description: "Manage suppliers and purchase records.",
//       icon: "◈",
//       path: "/suppliers",
//     },
//     {
//       title: "Reports",
//       description: "View sales, GST and inventory reports.",
//       icon: "▥",
//       path: "/reports",
//     },
//     {
//       title: "AI Assistant",
//       description: "Get intelligent business insights and assistance.",
//       icon: "✦",
//       path: "/ai-assistant",
//     },
//   ];

//   return (
//     <div className="dashboard">

//       <div className="welcome-section">
//         <div>
//           <p className="eyebrow">SMART RETAIL</p>

//           <h2>
//             Welcome back, {userName}
//           </h2>

//           <p>
//             Manage your retail business from one place.
//           </p>
//           {user?.shopName && (
//             <div className="user-business-info">
//               <strong>{user.shopName}</strong>
//               <span> • Owner</span>
//             </div>
//           )}
//         </div>

//         <button
//           className="profile-button"
//           onClick={() => navigate("/profile")}
//         >
//           View Profile
//         </button>
//       </div>

//       {/* Statistics */}
//       <div className="stats-grid">

//         <div className="stat-card">
//           <span>Total Sales</span>
//           <strong>₹0</strong>
//           <small>Today's sales</small>
//         </div>

//         <div className="stat-card">
//           <span>Total Products</span>
//           <strong>0</strong>
//           <small>Products in inventory</small>
//         </div>

//         <div className="stat-card">
//           <span>Customers</span>
//           <strong>0</strong>
//           <small>Registered customers</small>
//         </div>

//         <div className="stat-card">
//           <span>Low Stock</span>
//           <strong>0</strong>
//           <small>Products need attention</small>
//         </div>

//       </div>

//       {/* Services */}
//       <div className="section-heading">
//         <div>
//           <h3>Business Services</h3>
//           <p>Quick access to your retail modules</p>
//         </div>
//       </div>

//       <div className="module-grid">

//         {modules.map((module) => (
//           <button
//             key={module.path}
//             className="module-card"
//             onClick={() => navigate(module.path)}
//           >
//             <div className="module-icon">
//               {module.icon}
//             </div>

//             <div className="module-content">
//               <h4>{module.title}</h4>

//               <p>
//                 {module.description}
//               </p>

//               <span className="module-link">
//                 Open →
//               </span>
//             </div>
//           </button>
//         ))}

//       </div>

//     </div>
//   );
// }


import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../hooks/useAuth";
import { getProducts } from "../../services/inventoryApi";
import { customerRequestError, getCustomers } from "../../services/customerApi";
import { getSuppliers, supplierRequestError } from "../../services/supplierApi";
import { getSalesDashboard } from "../../services/billingApi";
import { getCustomerReport } from "../../services/reportApi";
import { askBusinessAgent } from "../../services/aiApi";
import { addDaysToDateString, businessDateString, formatBusinessDateTime } from "../../utils/businessDate";
import { calculateDashboardMetrics } from "./dashboardMetrics";
import { buildCustomerCategoryMix, buildDashboardAlerts, calculateBusinessHealthScore, getCustomerInsights } from "./dashboardInsights";
import "./DashboardPage.css";

const emptySalesDashboard = {
  currentRevenue: 0,
  previousRevenue: 0,
  revenueChangePercent: null,
  saleCount: 0,
  profit: 0,
  dailySales: [],
  topProducts: [],
  recentSales: [],
  paymentSummary: { collectedAmount: 0, methods: [], outstandingAmount: 0, outstandingBillCount: 0 },
};

const formatMoney = (amount) => Number(amount || 0).toLocaleString("en-IN", { maximumFractionDigits: 0 });

function MiniSparkline({ values = [] }) {
  const points = values.map((value, index) => ({ value: Number(value) || 0, index }));
  if (points.length < 2) return <span className="metric-sparkline-empty" aria-hidden="true" />;
  const min = Math.min(0, ...points.map((point) => point.value));
  const max = Math.max(0, ...points.map((point) => point.value));
  const spread = max - min || 1;
  const coords = points.map((point) => ({
    x: 2 + (point.index / (points.length - 1)) * 96,
    y: 25 - ((point.value - min) / spread) * 21,
    value: point.value,
  }));
  return <svg className="metric-sparkline" viewBox="0 0 100 28" preserveAspectRatio="none" aria-hidden="true">
    {coords.slice(1).map((point, index) => <line key={index} x1={coords[index].x} y1={coords[index].y} x2={point.x} y2={point.y} className={coords[index].value < 0 || point.value < 0 ? "spark-loss" : "spark-growth"} />)}
  </svg>;
}

export default function Dashboard() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [products, setProducts] = useState([]);
  const [customers, setCustomers] = useState([]);
  const [suppliers, setSuppliers] = useState([]);
  const [salesDashboard, setSalesDashboard] = useState(emptySalesDashboard);
  const [customerReport, setCustomerReport] = useState(null);
  const [aiInsights, setAiInsights] = useState([]);
  const [aiInsightLoading, setAiInsightLoading] = useState(true);
  const [aiInsightError, setAiInsightError] = useState(false);
  const [aiInsightDuration, setAiInsightDuration] = useState(null);
  const [hoveredChartPoint, setHoveredChartPoint] = useState(null);
  const [periodPreset, setPeriodPreset] = useState("30");
  const [customFrom, setCustomFrom] = useState(() => addDaysToDateString(businessDateString(), -29));
  const [customTo, setCustomTo] = useState(businessDateString);
  const today = businessDateString();
  const performanceRange = useMemo(() => {
    if (periodPreset === "today") return { from: today, to: today };
    if (periodPreset === "7") return { from: addDaysToDateString(today, -6), to: today };
    if (periodPreset === "30") return { from: addDaysToDateString(today, -29), to: today };
    return { from: customFrom, to: customTo };
  }, [periodPreset, today, customFrom, customTo]);
  const rangeStartMs = Date.parse(`${performanceRange.from}T00:00:00Z`);
  const rangeEndMs = Date.parse(`${performanceRange.to}T00:00:00Z`);
  const periodDays = Number.isFinite(rangeStartMs) && Number.isFinite(rangeEndMs)
    ? Math.max(1, Math.round((rangeEndMs - rangeStartMs) / 86400000) + 1)
    : 0;
  const periodLabel = periodPreset === "today" ? "Today"
    : periodPreset === "7" ? "Last 7 days"
      : periodPreset === "30" ? "Last 30 days"
        : `${formatBusinessDateTime(`${performanceRange.from}T12:00:00`, { month: "short", day: "numeric" })} – ${formatBusinessDateTime(`${performanceRange.to}T12:00:00`, { month: "short", day: "numeric", year: "numeric" })}`;
  const [inventoryError, setInventoryError] = useState("");
  const [customersError, setCustomersError] = useState("");
  const [suppliersError, setSuppliersError] = useState("");
  const [salesError, setSalesError] = useState("");
  const [inventoryLoading, setInventoryLoading] = useState(true);
  const [customersLoading, setCustomersLoading] = useState(true);
  const [suppliersLoading, setSuppliersLoading] = useState(true);
  const [salesLoading, setSalesLoading] = useState(true);
  const [loading, setLoading] = useState(true);
  const [lastUpdated, setLastUpdated] = useState(null);
  const requestSequence = useRef(0);
  const hasLoadedRef = useRef(false);
  const initialAiInsightRequestedRef = useRef(false);

  const loadAiInsights = useCallback(async () => {
    const startedAt = performance.now();
    setAiInsightLoading(true);
    setAiInsightError(false);
    setAiInsightDuration(null);
    try {
      const response = await askBusinessAgent("Review my recent sales, gross profit, gross margin, and inventory. Give me up to three short, actionable business insights based only on my recorded data. Include useful numbers and the comparison period when available. Do not invent missing data.");
      const answer = String(response?.answer || response?.message || "").trim();
      const lines = answer.split(/\n+/).map((line) => line.replace(/^\s*(?:[-*•]|\d+[.)])\s*/, "").trim()).filter(Boolean).slice(0, 3);
      setAiInsights(lines.length ? lines : [answer || "Nova did not return any insights."]);
      setAiInsightDuration(Math.max(1, Math.round(performance.now() - startedAt)));
    } catch {
      setAiInsights([]);
      setAiInsightError(true);
      setAiInsightDuration(Math.max(1, Math.round(performance.now() - startedAt)));
    } finally {
      setAiInsightLoading(false);
    }
  }, []);

  useEffect(() => {
    if (initialAiInsightRequestedRef.current) return;
    initialAiInsightRequestedRef.current = true;
    loadAiInsights();
  }, [loadAiInsights]);

  const loadDashboard = useCallback(async () => {
    const requestId = ++requestSequence.current;
    const validSalesRange = Boolean(performanceRange.from && performanceRange.to && performanceRange.from <= performanceRange.to && periodDays <= 366);
    setLoading(true);
    if (!hasLoadedRef.current) {
      setInventoryLoading(true);
      setCustomersLoading(true);
      setSuppliersLoading(true);
      setSalesLoading(true);
    }
    setInventoryError("");
    setCustomersError("");
    setSuppliersError("");
    setSalesError(validSalesRange ? "" : periodDays > 366 ? "Custom date ranges are limited to 366 days." : "Choose a valid custom date range.");
    if (!validSalesRange) setSalesLoading(false);

    let receivedData = false;
    const updateIfCurrent = (callback, didSucceed = false) => {
      if (requestId === requestSequence.current) {
        if (didSucceed) receivedData = true;
        callback();
      }
    };
    const requests = [
      getProducts().then(
        (result) => updateIfCurrent(() => { setProducts(result); setInventoryLoading(false); }, true),
        () => updateIfCurrent(() => { setInventoryError("Inventory is temporarily unavailable. Refresh to try again."); setInventoryLoading(false); }),
      ),
      getCustomers().then(
        (result) => updateIfCurrent(() => { setCustomers(result); setCustomersLoading(false); }, true),
        (error) => updateIfCurrent(() => { setCustomersError(customerRequestError(error)); setCustomersLoading(false); }),
      ),
      getSuppliers().then(
        (result) => updateIfCurrent(() => { setSuppliers(result); setSuppliersLoading(false); }, true),
        (error) => updateIfCurrent(() => { setSuppliersError(supplierRequestError(error)); setSuppliersLoading(false); }),
      ),
      (validSalesRange ? getSalesDashboard(performanceRange) : Promise.resolve(null)).then(
        (result) => { if (result) updateIfCurrent(() => { setSalesDashboard(result); setSalesLoading(false); }, true); },
        () => updateIfCurrent(() => { setSalesDashboard(emptySalesDashboard); setSalesError("Sales performance is temporarily unavailable."); setSalesLoading(false); }),
      ),
      (validSalesRange ? getCustomerReport(performanceRange) : Promise.resolve(null)).then(
        (result) => { if (result) updateIfCurrent(() => setCustomerReport(result), true); },
        () => updateIfCurrent(() => setCustomerReport(null)),
      ),
    ];
    await Promise.all(requests);

    if (requestId !== requestSequence.current) return;
    hasLoadedRef.current = true;
    if (receivedData) setLastUpdated(new Date());
    setLoading(false);
  }, [performanceRange, periodDays]);

  useEffect(() => {
    loadDashboard();
    const intervalId = window.setInterval(loadDashboard, 30000);
    const refreshVisibleDashboard = () => {
      if (document.visibilityState === "visible") loadDashboard();
    };
    window.addEventListener("focus", refreshVisibleDashboard);
    document.addEventListener("visibilitychange", refreshVisibleDashboard);
    return () => {
      window.clearInterval(intervalId);
      window.removeEventListener("focus", refreshVisibleDashboard);
      document.removeEventListener("visibilitychange", refreshVisibleDashboard);
      requestSequence.current += 1;
    };
  }, [loadDashboard]);

  const liveStock = useMemo(() => products.reduce((total, product) => total + (Number(product.quantity) || 0), 0), [products]);
  const lowStock = useMemo(() => products.filter((product) => product.lowStock), [products]);
  const activeProductNames = useMemo(() => new Set(products.map((product) => String(product.name || "").trim().toLocaleLowerCase()).filter(Boolean)), [products]);
  const activeTopProducts = useMemo(() => salesDashboard.topProducts.filter((product) => activeProductNames.has(String(product.name || "").trim().toLocaleLowerCase())), [salesDashboard.topProducts, activeProductNames]);
  const customerCategoryMix = useMemo(() => buildCustomerCategoryMix(activeTopProducts, products), [activeTopProducts, products]);
  const customerCategoryGradient = useMemo(() => {
    const colors = ["#1688e5", "#12b76a", "#8b5cf6", "#f79009"];
    let cursor = 0;
    const stops = customerCategoryMix.map((item, index) => {
      const start = cursor;
      cursor += item.share;
      return `${colors[index % colors.length]} ${start}% ${cursor}%`;
    });
    return stops.length ? `conic-gradient(${stops.join(", ")})` : "#e7edf5";
  }, [customerCategoryMix]);
  const expiredProducts = useMemo(() => products.filter((product) => product.expiryDate && product.expiryDate < today), [products, today]);
  const expiringSoonProducts = useMemo(() => {
    const soon = addDaysToDateString(today, 30);
    return products.filter((product) => product.expiryDate && product.expiryDate >= today && product.expiryDate <= soon);
  }, [products, today]);
  const expiryAlerts = useMemo(() => {
    const today = businessDateString();
    const soon = addDaysToDateString(today, 30);
    return products.filter((product) => product.expiryDate && product.expiryDate <= soon)
      .sort((first, second) => first.expiryDate.localeCompare(second.expiryDate));
  }, [products]);
  const attentionItems = useMemo(() => {
    const items = new Map();
    lowStock.forEach((product) => items.set(product.id, { ...product, hasLowStock: true }));
    expiryAlerts.forEach((product) => {
      items.set(product.id, { ...(items.get(product.id) || product), hasExpiryAlert: true });
    });
    return [...items.values()].sort((first, second) => {
      return Number(second.hasLowStock) - Number(first.hasLowStock)
        || (first.expiryDate || "9999-12-31").localeCompare(second.expiryDate || "9999-12-31");
    });
  }, [lowStock, expiryAlerts]);

  const supplierStats = useMemo(() => {
    const bySupplier = new Map();
    products.forEach((product) => {
      const name = product.supplierName?.trim() || "Unassigned";
      const key = name.toLocaleLowerCase("en-IN");
      const entry = bySupplier.get(key) || { name, products: 0, units: 0, costValue: 0 };
      entry.products += 1;
      entry.units += Number(product.quantity) || 0;
      entry.costValue += (Number(product.quantity) || 0) * (Number(product.costPrice) || 0);
      bySupplier.set(key, entry);
    });
    const rows = [...bySupplier.values()].sort((first, second) => second.products - first.products || first.name.localeCompare(second.name));
    return { rows };
  }, [products]);
  const inventoryCostValue = useMemo(() => products.reduce((total, product) => total + (Number(product.quantity) || 0) * (Number(product.costPrice) || 0), 0), [products]);
  const assignedSupplierStockValue = useMemo(() => supplierStats.rows.filter((supplier) => supplier.name !== "Unassigned").reduce((total, supplier) => total + supplier.costValue, 0), [supplierStats]);
  const chartPoints = useMemo(() => {
    const dailySales = salesDashboard.dailySales || [];
    const maximum = Math.max(1, ...dailySales.map((day) => Number(day.sales) || 0));
    return dailySales.map((day, index) => ({
      ...day,
      x: dailySales.length === 1 ? 360 : 18 + (index * 684) / (dailySales.length - 1),
      y: 184 - ((Number(day.sales) || 0) / maximum) * 148,
    }));
  }, [salesDashboard.dailySales]);
  const linePath = chartPoints.map((point, index) => `${index ? "L" : "M"} ${point.x} ${point.y}`).join(" ");
  const chartLabelIndexes = new Set([0, Math.floor((chartPoints.length - 1) / 3), Math.floor(2 * (chartPoints.length - 1) / 3), chartPoints.length - 1]);
  const attentionCount = attentionItems.length;
  const changePercent = salesDashboard.salesChangePercent ?? salesDashboard.revenueChangePercent;
  const dashboardMetrics = useMemo(() => calculateDashboardMetrics(salesDashboard, changePercent), [salesDashboard, changePercent]);
  const healthScore = calculateBusinessHealthScore({
    productCount: products.length,
    attentionCount,
    inventoryLoading,
    inventoryError: Boolean(inventoryError),
    grossMargin: dashboardMetrics.grossMargin,
    salesChangePercent: changePercent,
    outstandingAmount: salesDashboard.paymentSummary?.outstandingAmount,
  });
  const outOfStockCount = products.filter((product) => Number(product.quantity) <= 0).length;
  const ownerName = user?.ownerName?.trim();
  const shopName = user?.shopName?.trim() || "your shop";
  const hour = Number(new Intl.DateTimeFormat("en-GB", { timeZone: "Asia/Kolkata", hour: "numeric", hourCycle: "h23" }).format(new Date()));
  const greeting = hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";
  const isExpired = (expiryDate) => expiryDate < today;
  const dailyProfitValues = (salesDashboard.dailySales || []).map((day) => Number(day.profit) || 0);
  const dailyMarginValues = (salesDashboard.dailySales || []).map((day) => Number(day.revenue) > 0 ? (Number(day.profit) / Number(day.revenue)) * 100 : 0);
  const changeText = changePercent === null || changePercent === undefined
    ? Number(salesDashboard.currentRevenue) > 0 ? "Sales recorded; no prior sales to compare" : "No sales in either period"
    : `${Number(changePercent) > 0 ? "+" : ""}${Number(changePercent).toLocaleString(undefined, { maximumFractionDigits: 1 })}% vs previous ${periodPreset === "today" ? "day" : `${periodDays} days`}`;
  const customerInsights = getCustomerInsights(customerReport, customers.length);
  const { totalCustomers, repeatCustomers: repeatCustomerCount, averageBill: customerAverageBill, topCustomers } = customerInsights;
  const alertRows = buildDashboardAlerts({
    attentionItems,
    outstandingAmount: salesDashboard.paymentSummary?.outstandingAmount,
    outstandingBillCount: salesDashboard.paymentSummary?.outstandingBillCount,
    salesChangePercent: changePercent,
    changeText,
  });

  return (
    <div className="dashboard professional-dashboard">
      <main className="dashboard-content">
        <section className="overview-hero">
          <div>
            <h1>{greeting}{ownerName ? `, ${ownerName}` : ""} <span aria-hidden="true">👋</span></h1>
            <p>Here's what's happening with your business today.</p>
          </div>
          <div className="overview-change">↗ <strong>{changePercent == null ? "Sales overview" : `Sales ${Number(changePercent) >= 0 ? "up" : "down"} ${Math.abs(Number(changePercent)).toFixed(1)}%`}</strong><small>compared to previous period</small></div>
        </section>

        <aside className="dashboard-rail">
          <section className="rail-card insights-card">
            <div className="rail-heading"><span className="robot-avatar" aria-hidden="true"><svg viewBox="0 0 32 32" focusable="false"><path d="M16 3v4" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/><circle cx="16" cy="2.5" r="2" fill="#19a5ff"/><rect x="5" y="9" width="22" height="18" rx="6" fill="url(#novaRobot)"/><path d="M5 15H3v6h2m22-6h2v6h-2" fill="none" stroke="#1678d2" strokeWidth="2" strokeLinecap="round"/><circle cx="12" cy="17" r="2" fill="white"/><circle cx="20" cy="17" r="2" fill="white"/><path d="M12 22h8" stroke="white" strokeWidth="2" strokeLinecap="round"/><defs><linearGradient id="novaRobot" x1="5" y1="9" x2="27" y2="27" gradientUnits="userSpaceOnUse"><stop stopColor="#38bdf8"/><stop offset="1" stopColor="#087ee5"/></linearGradient></defs></svg></span><div><strong>AI Business Insights</strong><small>Nova</small></div><button className="insight-refresh" onClick={loadAiInsights} disabled={aiInsightLoading} aria-label="Refresh AI business insights">↻</button></div>
            <div className="rail-intro">{aiInsightLoading ? "Nova is reviewing your business data…" : aiInsightError ? "AI insights are temporarily unavailable. Live stock alerts are shown below." : "Your business insights today:"}</div>
            {aiInsightDuration !== null && <div className="insight-duration">Response time: {aiInsightDuration.toLocaleString()} ms</div>}
            {aiInsights.length ? aiInsights.map((insight, index) => <div className="insight-row ai-generated-insight" key={`${index}-${insight}`}><span>{index + 1}.</span><div><strong>{insight}</strong></div></div>) : attentionCount ? attentionItems.slice(0, 4).map((item, index) => <div className="insight-row" key={item.id}><span>{index + 1}.</span><div><strong>{item.hasLowStock ? `${item.name} is low in stock` : `${item.name} is nearing expiry`}</strong><small>{item.hasLowStock ? `${item.quantity} ${item.unit} remaining` : `Expires ${item.expiryDate}`}</small></div><button onClick={() => navigate("/inventory")}>View</button></div>) : <div className="insight-row"><span>✓</span><div><strong>No urgent stock alerts</strong><small>Inventory looks healthy right now.</small></div><button onClick={() => navigate("/inventory")}>View</button></div>}
            <button className="rail-link" onClick={() => window.dispatchEvent(new Event("open-ai-agent"))}>Ask Nova a question <span>→</span></button>
          </section>
          <section className="rail-card help-card"><div className="help-avatar" aria-hidden="true"><svg viewBox="0 0 64 64" focusable="false"><circle cx="32" cy="32" r="31" fill="#d9ebff"/><path d="M9 58c2-10 10-16 23-16s21 6 23 16" fill="#f7fbff"/><path d="M15 30c0-15 7-23 18-23 10 0 17 8 17 22v8H15z" fill="#263c63"/><path d="M20 27c0-9 5-15 13-15s13 6 13 15v10c0 9-6 15-13 15s-13-6-13-15z" fill="#f4bd9e"/><path d="M19 29c1-13 7-20 16-20 8 0 13 6 14 15-5-1-9-4-12-8-3 5-9 8-18 9z" fill="#263c63"/><path d="M17 27v11m30-11v11" fill="none" stroke="#1688e5" strokeWidth="3" strokeLinecap="round"/><rect x="12" y="29" width="7" height="12" rx="3.5" fill="#55b8ff"/><rect x="45" y="29" width="7" height="12" rx="3.5" fill="#55b8ff"/><circle cx="27" cy="34" r="1.4" fill="#263c63"/><circle cx="38" cy="34" r="1.4" fill="#263c63"/><path d="M28 42c2 2 6 2 8 0" fill="none" stroke="#be6c64" strokeWidth="1.5" strokeLinecap="round"/><path d="M21 53c3-3 7-4 11-4s8 1 11 4l4 7H17z" fill="#fff"/><path d="M25 52l7 7 7-7" fill="#68b5ec"/></svg></div><div><strong>Need Help?</strong><small>Ask your business assistant</small><button onClick={() => window.dispatchEvent(new Event("open-ai-agent"))}>Open assistant →</button></div></section>
          <section className="rail-card quick-card"><h2>Quick Actions</h2><div className="quick-grid">
            <button onClick={() => navigate("/billing")}><span>▤</span>Create Bill</button><button onClick={() => navigate("/inventory")}><span>＋</span>Add Product</button><button onClick={() => navigate("/customers")}><span>♙</span>Add Customer</button><button onClick={() => navigate("/suppliers")}><span>▣</span>New Purchase</button><button onClick={() => navigate("/reports")}><span>▥</span>View Reports</button><button onClick={() => navigate("/inventory")}><span>⚑</span>Stock Alert</button>
          </div></section>
        </aside>

        <section className={`business-pulse ${attentionCount ? "needs-review" : "is-clear"}`}>
          <div className="pulse-mark" aria-hidden="true">{attentionCount ? "!" : "✓"}</div>
          <div className="pulse-copy">
            <p className="section-label">BUSINESS PULSE</p>
            <strong>{inventoryError ? "Inventory status unavailable" : attentionCount ? `${attentionCount} products need attention` : "No urgent stock issues"}</strong>
            <span>{salesError ? salesError : `${periodLabel}: ${changeText}`}</span>
          </div>
          <button onClick={() => navigate("/inventory")}>{inventoryError || attentionCount ? "Review inventory" : "Open inventory"} <span>→</span></button>
        </section>

        <section className="professional-stats" aria-label={`${periodLabel} business summary`}>
          <article className={`metric-card ${dashboardMetrics.salesDeclining ? "metric-declining" : ""}`}><span className={`metric-icon ${dashboardMetrics.salesDeclining ? "red" : "green"}`}>₹</span><p>Net Sales</p><strong>{salesLoading || salesError ? "—" : `₹ ${formatMoney(dashboardMetrics.netSales)}`}</strong><small>{periodLabel} · excluding GST</small><div className={`metric-trend ${dashboardMetrics.salesDeclining ? "trend-loss" : "trend-growth"}`}><span>{changePercent == null ? "No previous period" : `${Number(changePercent) > 0 ? "↑" : dashboardMetrics.salesDeclining ? "↓" : "→"} ${Math.abs(Number(changePercent)).toFixed(1)}% vs previous period`}</span><MiniSparkline values={(salesDashboard.dailySales || []).map((day) => day.revenue)} /></div></article>
          <article className={`metric-card ${dashboardMetrics.grossProfitLoss ? "metric-loss" : ""}`}><span className={`metric-icon ${dashboardMetrics.grossProfitLoss ? "red" : "violet"}`}>◈</span><p>Gross Profit</p><strong>{salesLoading || salesError ? "—" : `₹ ${formatMoney(dashboardMetrics.grossProfit)}`}</strong><small>{periodLabel}</small><div className={`metric-trend ${dashboardMetrics.grossProfitLoss ? "trend-loss" : "trend-growth"}`}><span>{dashboardMetrics.grossProfitLoss ? "↓ Loss this period" : "Daily profit"}</span><MiniSparkline values={dailyProfitValues} /></div></article>
          <article className={`metric-card ${dashboardMetrics.grossMarginLoss ? "metric-loss" : ""}`}><span className={`metric-icon ${dashboardMetrics.grossMarginLoss ? "red" : "blue"}`}>%</span><p>Gross Margin</p><strong>{salesLoading || salesError || dashboardMetrics.grossMargin === null ? "—" : `${dashboardMetrics.grossMargin.toFixed(1)}%`}</strong><small>Profit ÷ net revenue</small><div className={`metric-trend ${dashboardMetrics.grossMarginLoss ? "trend-loss" : "trend-growth"}`}><span>{dashboardMetrics.grossMarginLoss ? "↓ Negative margin" : "Daily margin"}</span><MiniSparkline values={dailyMarginValues} /></div></article>
          <article className="metric-card"><span className="metric-icon orange">▤</span><p>Total Orders / Bills</p><strong>{salesLoading || salesError ? "—" : dashboardMetrics.totalOrders.toLocaleString()}</strong><small>{periodLabel}</small></article>
          <article className="metric-card"><span className="metric-icon blue">▣</span><p>Avg. Bill Value</p><strong>{salesLoading || salesError || dashboardMetrics.averageBillValue === null ? "—" : `₹ ${formatMoney(dashboardMetrics.averageBillValue)}`}</strong><small>{periodLabel}</small></article>
          <article className="metric-card"><span className="metric-icon red">↩</span><p>Returns</p><strong>—</strong><small>Not available in this data</small></article>
        </section>

        <div className="chart-score-grid">
        <section className="sales-performance">
          <div className="sales-performance-heading">
            <div><p className="section-label">SALES PERFORMANCE</p><h2>Sales over time</h2><p>{salesError || `${periodLabel} · compared with the previous ${periodPreset === "today" ? "day" : `${periodDays} days`}`}</p></div>
            <div className="period-controls">
              <label className="period-select">Period<select value={periodPreset} onChange={(event) => setPeriodPreset(event.target.value)}><option value="today">Today</option><option value="7">Last 7 days</option><option value="30">Last 30 days</option><option value="custom">Custom range</option></select></label>
              {periodPreset === "custom" && <div className="custom-date-range">
                <label>From<input type="date" value={customFrom} max={customTo || today} onChange={(event) => setCustomFrom(event.target.value)} /></label>
                <label>To<input type="date" value={customTo} min={customFrom} max={today} onChange={(event) => setCustomTo(event.target.value)} /></label>
              </div>}
            </div>
          </div>
          <div className="sales-chart-summary">
            <div><span>Total sales</span><strong>{salesLoading || salesError ? "—" : `₹${formatMoney(salesDashboard.currentSales)}`}</strong><small className={Number(changePercent) < 0 ? "trend-down" : "trend-up"}>{salesLoading ? "Loading sales…" : changeText}</small></div>
            <div><span>Net revenue</span><strong>{salesLoading || salesError ? "—" : `₹${formatMoney(salesDashboard.currentRevenue)}`}</strong><small>Excludes GST</small></div>
            <div><span>Completed bills</span><strong>{salesLoading || salesError ? "—" : Number(salesDashboard.saleCount || 0).toLocaleString()}</strong><small>In selected period</small></div>
            <div><span>Gross profit</span><strong className={!salesLoading && !salesError && Number(salesDashboard.profit) < 0 ? "profit-loss-value" : ""}>{salesLoading || salesError ? "—" : `₹${formatMoney(salesDashboard.profit)}`}</strong><small>After product cost</small></div>
          </div>
          <div className="sales-chart-wrap">
            {salesError ? <p className="chart-message">{salesError}</p> : salesLoading ? <p className="chart-message">Loading sales…</p> : salesDashboard.saleCount === 0 ? <p className="chart-message">No completed sales during this period.</p> : null}
            <svg className="sales-chart" viewBox="0 0 720 205" role="img" aria-label={`Sales amounts from ${performanceRange.from} through ${performanceRange.to}`}>
              <defs><linearGradient id="salesArea" x1="0" x2="0" y1="0" y2="1"><stop offset="0%" stopColor="#78a984" stopOpacity="0.27" /><stop offset="100%" stopColor="#78a984" stopOpacity="0" /></linearGradient></defs>
              {[36, 85, 134, 184].map((y) => <line key={y} x1="12" x2="708" y1={y} y2={y} className="chart-gridline" />)}
              {chartPoints.map((point) => <rect key={`bar-${point.date}`} x={point.x - 4} y={point.y} width="8" height={184 - point.y} rx="2" className="chart-bar" />)}
              {linePath && <path d={linePath} className="chart-line" />}
              {chartPoints.map((point) => <g key={point.date} className="chart-point-target" onMouseEnter={() => setHoveredChartPoint(point)} onMouseLeave={() => setHoveredChartPoint(null)} onFocus={() => setHoveredChartPoint(point)} onBlur={() => setHoveredChartPoint(null)}>
                <circle cx={point.x} cy={point.y} r="11" className="chart-point-hit" tabIndex="0" role="button" aria-label={`${point.label || point.date}: ₹${formatMoney(point.sales)} from ${point.saleCount} bills`} />
                <circle cx={point.x} cy={point.y} r="4" className="chart-point" aria-hidden="true" />
              </g>)}
            </svg>
            {hoveredChartPoint && <div className={`chart-tooltip ${hoveredChartPoint.y < 62 ? "below" : ""}`} style={{ left: `${Math.min(88, Math.max(12, (hoveredChartPoint.x / 720) * 100))}%`, top: `${(hoveredChartPoint.y / 205) * 100}%` }} role="status">
              <strong>{hoveredChartPoint.label || formatBusinessDateTime(`${hoveredChartPoint.date}T12:00:00`, { month: "short", day: "numeric" })}</strong>
              <span>Sales <b>₹{formatMoney(hoveredChartPoint.sales)}</b></span>
              <small>{Number(hoveredChartPoint.saleCount || 0).toLocaleString()} {Number(hoveredChartPoint.saleCount) === 1 ? "bill" : "bills"}</small>
            </div>}
            <div className="chart-labels">{chartPoints.filter((_, index) => chartLabelIndexes.has(index)).map((point) => <span key={point.date}>{point.label || formatBusinessDateTime(`${point.date}T12:00:00`, { month: "short", day: "numeric" })}</span>)}</div>
          </div>
        </section>
        <section className="dashboard-panel score-panel">
          <div className="panel-heading"><div><p className="section-label">BUSINESS HEALTH SCORE</p><h2>Business Health</h2></div></div>
          <div className="score-content"><div className="score-ring" style={{"--score": `${healthScore ?? 0}%`}}><strong>{healthScore ?? "—"}</strong><small>{healthScore === null ? "" : "/100"}</small><b>{healthScore === null ? "Data needed" : healthScore >= 75 ? "Good" : healthScore >= 45 ? "Fair" : "Needs attention"}</b></div><div className="score-checks"><p><i className={salesError || Number(changePercent) < 0 ? "warn" : "good"}>{salesError || Number(changePercent) < 0 ? "!" : "✓"}</i>Sales growth <b>{salesError ? "Unavailable" : changePercent == null ? "New" : Number(changePercent) >= 0 ? "Good" : "Review"}</b></p><p><i className={dashboardMetrics.grossMargin != null && dashboardMetrics.grossMargin < 15 ? "warn" : "good"}>{dashboardMetrics.grossMargin != null && dashboardMetrics.grossMargin < 15 ? "!" : "✓"}</i>Profit margin <b>{dashboardMetrics.grossMargin == null ? "—" : `${dashboardMetrics.grossMargin.toFixed(1)}%`}</b></p><p><i className={inventoryLoading || inventoryError || lowStock.length ? "warn" : "good"}>{inventoryLoading || inventoryError || lowStock.length ? "!" : "✓"}</i>Inventory health <b>{inventoryError ? "Unavailable" : inventoryLoading ? "Loading" : lowStock.length ? "Review" : "Good"}</b></p><p><i className={Number(salesDashboard.paymentSummary?.outstandingAmount) > 0 ? "warn" : "good"}>{Number(salesDashboard.paymentSummary?.outstandingAmount) > 0 ? "!" : "✓"}</i>Cash flow <b>{Number(salesDashboard.paymentSummary?.outstandingAmount) > 0 ? "Watch" : "Good"}</b></p><p><i className="good">✓</i>Customer growth <b>{customers.length.toLocaleString()}</b></p></div></div>
          <button className="rail-link" onClick={() => navigate("/reports")}>View business reports <span>→</span></button>
        </section>
        </div>

        <section className="dashboard-grid analytics-grid">
          <article className="dashboard-panel product-performance">
            <div className="panel-heading"><div><p className="section-label">PRODUCT PERFORMANCE</p><h2>Top Selling Products</h2><p>Units and sales value · {periodLabel}</p></div><button onClick={() => navigate("/reports")}>View reports</button></div>
            {salesError ? <p className="panel-message error-message">{salesError}</p> : inventoryError ? <p className="panel-message error-message">Current inventory is unavailable, so active best sellers cannot be verified.</p> : salesLoading || inventoryLoading ? <p className="panel-message">Loading products…</p> : activeTopProducts.length ? <div className="product-list product-table-list"><div className="product-table-head"><span>#</span><span>Product</span><span>Qty sold</span><span>Sales value</span><span>Trend</span></div>{activeTopProducts.slice(0, 5).map((product, index) => <div className="product-row" key={product.name}><span className="product-rank">{index + 1}</span><strong>{product.name}</strong><span>{product.units}</span><b>₹{formatMoney(product.sales)}</b><em>—</em></div>)}<button className="rail-link" onClick={() => navigate("/reports")}>View all products <span>→</span></button></div> : <div className="empty-state"><span>—</span><div><strong>No active products sold in this period</strong><p>Deleted products remain in past bills but are omitted from this ranking.</p></div></div>}
          </article>
          <article className="dashboard-panel inventory-health">
            <div className="panel-heading"><div><p className="section-label">INVENTORY HEALTH</p><h2>Stock overview</h2></div><button onClick={() => navigate("/inventory")}>Manage stock</button></div>
            {inventoryError ? <p className="panel-message error-message">{inventoryError}</p> : inventoryLoading ? <p className="panel-message">Loading inventory health…</p> : <div className="inventory-metric-grid"><div><span>Total stock value</span><strong>₹{formatMoney(inventoryCostValue)}</strong><small>At current cost</small></div><div className="inventory-warn"><span>Low stock items</span><strong>{lowStock.length.toLocaleString()}</strong><small>Below reorder level</small></div><div className="inventory-danger"><span>Out of stock</span><strong>{outOfStockCount.toLocaleString()}</strong><small>Needs replenishment</small></div><div><span>Units on hand</span><strong>{liveStock.toLocaleString()}</strong><small>{products.length.toLocaleString()} products</small></div><div className={expiringSoonProducts.length ? "inventory-warn" : ""}><span>Expiring soon</span><strong>{expiringSoonProducts.length.toLocaleString()}</strong><small>Next 30 days</small></div><div className={expiredProducts.length ? "inventory-danger" : ""}><span>Expired stock</span><strong>{expiredProducts.length.toLocaleString()}</strong><small>Review inventory</small></div></div>}
          </article>
          <article className="dashboard-panel cash-pulse">
            <div className="panel-heading"><div><p className="section-label">CASH &amp; PAYMENTS</p><h2>Money collected</h2></div><button onClick={() => navigate("/payments")}>Payments</button></div>
            {salesError ? <p className="panel-message error-message">{salesError}</p> : salesLoading ? <p className="panel-message">Loading payment activity…</p> : <>
              <div className="cash-total"><strong>₹{formatMoney(salesDashboard.paymentSummary?.collectedAmount)}</strong><span>{periodLabel}</span></div>
              <div className="payment-method-list">
                {(salesDashboard.paymentSummary?.methods || []).length ? salesDashboard.paymentSummary.methods.map((method) => { const share = Number(salesDashboard.paymentSummary?.collectedAmount) > 0 ? Math.min(100, (Number(method.amount) / Number(salesDashboard.paymentSummary.collectedAmount)) * 100) : 0; return <div className="payment-method-row" key={method.method}><span><i className={`payment-method-dot ${String(method.method).toLowerCase()}`} />{method.method === "Online" ? "UPI / Digital" : method.method}<small>₹{formatMoney(method.amount)}</small></span><b>{share.toFixed(0)}%</b><i className="payment-method-track"><i className={`payment-method-fill ${String(method.method).toLowerCase()}`} style={{ width: `${share}%` }} /></i></div>; }) : <p className="panel-message">No payments recorded in this period.</p>}
              </div>
              <div className={`cash-outstanding ${(Number(salesDashboard.paymentSummary?.outstandingAmount) > 0) ? "has-due" : ""}`}><span>Outstanding balance <small>{Number(salesDashboard.paymentSummary?.outstandingBillCount || 0)} bills</small></span><strong>₹{formatMoney(salesDashboard.paymentSummary?.outstandingAmount)}</strong></div>
              <div className="supplier-summary"><span>Supplier network <strong>{suppliersLoading || suppliersError ? "—" : suppliers.length}</strong></span><small>{inventoryError ? "Supplier stock value unavailable" : `Assigned stock at cost: ₹${formatMoney(assignedSupplierStockValue)}`}</small><button onClick={() => navigate("/suppliers")}>View suppliers →</button></div>
            </>}
          </article>
        </section>

        <section className="dashboard-grid health-grid">
          <article className="dashboard-panel customer-pulse">
            <div className="panel-heading"><div><p className="section-label">CUSTOMER INSIGHTS</p><h2>Customer activity</h2></div><button onClick={() => navigate("/customers")}>View customers</button></div>
            {customersError ? <p className="panel-message error-message">{customersError}</p> : customersLoading || salesLoading ? <p className="panel-message">Loading customer insights…</p> : <>
              <div className="customer-insight-metrics">
                <div><span>Customers</span><strong>{customers.length.toLocaleString()}</strong><small>{totalCustomers.toLocaleString()} purchased this period</small></div>
                <div><span>Repeat customers</span><strong>{repeatCustomerCount.toLocaleString()}</strong><small>Among top customers</small></div>
                <div><span>Avg. bill value</span><strong>₹{formatMoney(customerAverageBill ?? dashboardMetrics.averageBillValue)}</strong><small>Selected period</small></div>
              </div>
              <div className="customer-insight-breakdown">
                <div className="customer-top-list">
                  <div className="customer-breakdown-heading"><b>Top customers</b><small>By spend</small></div>
                  {topCustomers.slice(0, 4).map((customer, index) => {
                    const name = String(customer.customer_name || "Customer");
                    const initials = name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]).join("").toLocaleUpperCase();
                    return <div className="customer-row" key={`${customer.customer_id || name}-${index}`}><span className="customer-avatar">{initials || "C"}</span><span className="customer-name">{name}<small>{Number(customer.bill_count) || 0} bills</small></span><strong>₹{formatMoney(customer.total_spend)}</strong></div>;
                  })}
                  {!topCustomers.length && <small className="customer-empty">No named customer purchases in this period.</small>}
                </div>
                <div className="customer-category">
                  <div className="customer-breakdown-heading"><b>Sales by category</b><small>Top 5 products</small></div>
                  {customerCategoryMix.length ? <div className="customer-category-content">
                    <div className="customer-donut" role="img" aria-label={`Category sales mix: ${customerCategoryMix.map((item) => `${item.category} ${item.share.toFixed(0)}%`).join(", ")}`} style={{ background: customerCategoryGradient }}><div><strong>₹{formatMoney(customerCategoryMix.reduce((sum, item) => sum + item.sales, 0))}</strong><small>Sales</small></div></div>
                    <ul className="customer-category-legend">{customerCategoryMix.map((item, index) => <li key={item.category}><i className={`category-dot category-${index}`} /><span>{item.category}</span><strong>{item.share.toFixed(0)}%</strong></li>)}</ul>
                  </div> : <p className="customer-empty">Category sales appear when product sales are recorded.</p>}
                </div>
              </div>
            </>}
          </article>
          <article className="dashboard-panel latest-alerts">
            <div className="panel-heading"><div><p className="section-label">LATEST ALERTS &amp; ACTIONS</p><h2>What needs attention</h2></div><button onClick={() => navigate("/inventory")}>View all</button></div>
            {inventoryLoading || salesLoading ? <p className="panel-message">Checking latest activity…</p> : alertRows.length ? <div className="latest-alert-list">{alertRows.map((alert, index) => <div className={`latest-alert-row ${alert.tone}`} key={`${alert.title}-${index}`}><span className="alert-symbol">{alert.tone === "danger" ? "!" : alert.tone === "warn" ? "◷" : "↗"}</span><div><b>{alert.title}</b><small>{alert.detail}</small><button onClick={() => navigate(alert.path)}>Take action →</button></div><time>{index === 0 ? "Now" : `${index + 1}h ago`}</time></div>)}</div> : <div className="empty-state"><span>✓</span><div><strong>No open alerts</strong><p>Stock, payment, and sales alerts will appear here.</p></div></div>}
          </article>
          <article className="dashboard-panel business-impact">
            <div className="panel-heading"><div><p className="section-label">BUSINESS IMPACT</p><h2>Changes in this period</h2></div><span className="impact-range">{periodLabel}</span></div>
            <div className="impact-list">
              <div><i className="impact-icon green">▣</i><span>Stock-out risks<small>{lowStock.length} low stock · {outOfStockCount} out of stock</small></span><b className={lowStock.length ? "impact-negative" : "impact-positive"}>{lowStock.length ? `${lowStock.length} items` : "Clear"}</b></div>
              <div><i className="impact-icon blue">◷</i><span>Inventory ageing<small>{expiringSoonProducts.length} expiring within 30 days</small></span><b>{expiringSoonProducts.length} items</b></div>
              <div><i className="impact-icon orange">%</i><span>Gross margin<small>Current period</small></span><b className={Number(dashboardMetrics.grossMargin) < 15 ? "impact-negative" : "impact-positive"}>{dashboardMetrics.grossMargin == null ? "—" : `${dashboardMetrics.grossMargin.toFixed(1)}%`}</b></div>
              <div><i className="impact-icon violet">↻</i><span>Repeat customers<small>Customers with multiple bills</small></span><b>{repeatCustomerCount}</b></div>
              <div><i className="impact-icon red">₹</i><span>Overdue receivables<small>{salesDashboard.paymentSummary?.outstandingBillCount || 0} bills · balance due</small></span><b className={Number(salesDashboard.paymentSummary?.outstandingAmount) ? "impact-negative" : "impact-positive"}>₹{formatMoney(salesDashboard.paymentSummary?.outstandingAmount)}</b></div>
            </div>
            <div className="impact-note"><strong>{attentionCount || Number(salesDashboard.paymentSummary?.outstandingAmount) ? "A few actions can improve business health" : "Your business is in good shape"}</strong><span>{attentionCount ? `Review ${attentionCount} stock or expiry alert${attentionCount === 1 ? "" : "s"}` : Number(salesDashboard.paymentSummary?.outstandingAmount) ? "Follow up on outstanding customer balances." : "Keep tracking stock, collections, and repeat sales."}</span></div>
          </article>
        </section>

        <button className="dashboard-ai-link" onClick={() => window.dispatchEvent(new Event("open-ai-agent"))} aria-label="Open Nova retail assistant"><span className="ai-mark">✦</span><span><strong>Nova retail assistant</strong><small>Ask about your inventory, sales, customers, or forecasts.</small></span><b>Ask Nova <i>→</i></b></button>
        <p className="dashboard-updated">{lastUpdated ? `Last updated ${formatBusinessDateTime(lastUpdated.toISOString(), { hour: "2-digit", minute: "2-digit" })}` : "Waiting for shop data"}<button onClick={loadDashboard} disabled={loading}>{loading ? "Refreshing…" : "Refresh"}</button></p>
      </main>
    </div>
  );
}
