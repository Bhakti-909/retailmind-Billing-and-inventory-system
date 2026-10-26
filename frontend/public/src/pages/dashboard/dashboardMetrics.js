const finiteNumber = (value) => {
  if (value === null || value === undefined || value === "") return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
};

export function calculateDashboardMetrics(sales = {}, changePercent = null) {
  const netSales = finiteNumber(sales.currentRevenue) ?? 0;
  const grossProfit = finiteNumber(sales.profit) ?? 0;
  const invoiceTotal = finiteNumber(sales.currentSales) ?? 0;
  const totalOrders = Math.max(0, finiteNumber(sales.saleCount) ?? 0);
  const grossMargin = netSales > 0 ? (grossProfit / netSales) * 100 : null;
  const trend = finiteNumber(changePercent);

  return {
    netSales,
    grossProfit,
    grossMargin,
    totalOrders,
    averageBillValue: totalOrders > 0 ? invoiceTotal / totalOrders : null,
    salesDeclining: trend !== null && trend < 0,
    grossProfitLoss: grossProfit < 0,
    grossMarginLoss: grossMargin !== null && grossMargin < 0,
  };
}
