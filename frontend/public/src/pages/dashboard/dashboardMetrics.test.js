import test from "node:test";
import assert from "node:assert/strict";
import { calculateDashboardMetrics } from "./dashboardMetrics.js";

test("calculates net sales, gross margin, orders, and average bill from billing totals", () => {
  const metrics = calculateDashboardMetrics({
    currentRevenue: 1000,
    currentSales: 1180,
    profit: 250,
    saleCount: 4,
  }, 12.5);

  assert.equal(metrics.netSales, 1000);
  assert.equal(metrics.grossProfit, 250);
  assert.equal(metrics.grossMargin, 25);
  assert.equal(metrics.totalOrders, 4);
  assert.equal(metrics.averageBillValue, 295);
  assert.equal(metrics.salesDeclining, false);
  assert.equal(metrics.grossProfitLoss, false);
  assert.equal(metrics.grossMarginLoss, false);
});

test("marks negative sales trend, gross loss, and negative margin", () => {
  const metrics = calculateDashboardMetrics({
    currentRevenue: 500,
    currentSales: 590,
    profit: -75,
    saleCount: 2,
  }, -8.2);

  assert.equal(metrics.grossMargin, -15);
  assert.equal(metrics.averageBillValue, 295);
  assert.equal(metrics.salesDeclining, true);
  assert.equal(metrics.grossProfitLoss, true);
  assert.equal(metrics.grossMarginLoss, true);
});

test("handles empty, invalid, and zero order data without NaN or division by zero", () => {
  const metrics = calculateDashboardMetrics({
    currentRevenue: 0,
    currentSales: "not a number",
    profit: null,
    saleCount: 0,
  }, null);

  assert.equal(metrics.netSales, 0);
  assert.equal(metrics.grossProfit, 0);
  assert.equal(metrics.grossMargin, null);
  assert.equal(metrics.totalOrders, 0);
  assert.equal(metrics.averageBillValue, null);
  assert.equal(metrics.salesDeclining, false);
});
