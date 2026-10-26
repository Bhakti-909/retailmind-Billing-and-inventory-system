import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { buildCustomerCategoryMix, buildDashboardAlerts, calculateBusinessHealthScore, getCustomerInsights } from "./dashboardInsights.js";

test("business health score is unavailable until inventory data is ready", () => {
  assert.equal(calculateBusinessHealthScore({ productCount: 4, attentionCount: 1, inventoryLoading: true }), null);
  assert.equal(calculateBusinessHealthScore({ productCount: 0, attentionCount: 0 }), null);
});

test("business health score decreases for stock, margin, sales, and receivable risks", () => {
  const healthy = calculateBusinessHealthScore({ productCount: 10, attentionCount: 0, grossMargin: 25, salesChangePercent: 8, outstandingAmount: 0 });
  const risk = calculateBusinessHealthScore({ productCount: 10, attentionCount: 2, grossMargin: 8, salesChangePercent: -5, outstandingAmount: 100 });
  assert.equal(healthy, 100);
  assert.equal(risk, 59);
});

test("customer insights use report totals and count repeat buyers from bills", () => {
  const insights = getCustomerInsights({
    summary: { customer_count: 12, average_bill: 450 },
    topCustomers: [{ customer_name: "A", bill_count: 3 }, { customer_name: "B", bill_count: 1 }],
  }, 15);
  assert.equal(insights.totalCustomers, 12);
  assert.equal(insights.repeatCustomers, 1);
  assert.equal(insights.averageBill, 450);
  assert.equal(insights.topCustomers.length, 2);
});

test("customer insights fall back to saved customer count when report is unavailable", () => {
  assert.deepEqual(getCustomerInsights(null, 7), { totalCustomers: 7, repeatCustomers: 0, averageBill: null, topCustomers: [] });
});

test("customer category mix groups top product sales by inventory category", () => {
  const mix = buildCustomerCategoryMix([
    { name: "Rice", sales: 600 }, { name: "Soap", sales: 300 }, { name: "Tea", sales: 100 },
    { name: "Oil", sales: 50 }, { name: "Snacks", sales: 25 }, { name: "Ignored", sales: 999 },
  ], [
    { name: "rice", category: "Grocery" }, { name: "Soap", category: "Personal Care" },
    { name: "Tea", category: "Grocery" }, { name: "Oil", category: "Grocery" },
    { name: "Snacks", category: "Snacks" },
  ]);
  assert.deepEqual(mix.map(({ category, sales }) => ({ category, sales })), [
    { category: "Grocery", sales: 750 }, { category: "Personal Care", sales: 300 }, { category: "Snacks", sales: 25 },
  ]);
  assert.equal(mix.reduce((sum, item) => sum + item.share, 0), 100);
});

test("customer category mix handles missing categories and empty sales", () => {
  assert.deepEqual(buildCustomerCategoryMix([{ name: "Unmapped", sales: 0 }], []), [
    { category: "Uncategorized", sales: 0, share: 0 },
  ]);
  assert.deepEqual(buildCustomerCategoryMix([], []), []);
});

test("alerts include actionable stock, balance, and sales issues and cap the list", () => {
  const alerts = buildDashboardAlerts({
    attentionItems: [
      { name: "Rice", hasLowStock: true, quantity: 2, unit: "kg" },
      { name: "Soap", hasExpiryAlert: true, expiryDate: "2026-10-30" },
    ],
    outstandingAmount: 1250,
    outstandingBillCount: 2,
    salesChangePercent: -4,
    changeText: "-4% vs prior period",
  });
  assert.equal(alerts.length, 4);
  assert.equal(alerts[0].path, "/inventory");
  assert.match(alerts[2].detail, /1,250/);
  assert.equal(alerts[3].path, "/reports");
  assert.equal(buildDashboardAlerts({ attentionItems: Array.from({ length: 8 }, (_, i) => ({ name: `Item ${i}`, hasLowStock: true, quantity: i, unit: "ea" })) }).length, 3);
});

test("dashboard derives score dependencies before rendering the score", async () => {
  const dashboardSource = await readFile(new URL("./Dashboard.jsx", import.meta.url), "utf8");
  assert.ok(dashboardSource.indexOf("const changePercent =") < dashboardSource.indexOf("const healthScore ="));
  assert.ok(dashboardSource.indexOf("const dashboardMetrics =") < dashboardSource.indexOf("const healthScore ="));
});

test("dashboard rail includes a virtual help portrait and robot insight profile", async () => {
  const dashboardSource = await readFile(new URL("./Dashboard.jsx", import.meta.url), "utf8");
  assert.match(dashboardSource, /className="robot-avatar"/);
  assert.match(dashboardSource, /className="help-avatar" aria-hidden="true"><svg/);
});
