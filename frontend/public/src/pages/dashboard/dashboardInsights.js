export function calculateBusinessHealthScore({
  productCount,
  attentionCount,
  inventoryLoading = false,
  inventoryError = false,
  grossMargin,
  salesChangePercent,
  outstandingAmount,
}) {
  if (inventoryLoading || inventoryError || productCount <= 0) return null;
  const score = 100
    - (attentionCount / productCount) * 55
    - (grossMargin != null && grossMargin < 15 ? 10 : 0)
    - (Number(salesChangePercent) < 0 ? 10 : 0)
    - (Number(outstandingAmount) > 0 ? 10 : 0);
  return Math.max(0, Math.round(score));
}

export function getCustomerInsights(report, savedCustomerCount = 0) {
  const topCustomers = report?.topCustomers || [];
  return {
    totalCustomers: Number(report?.summary?.customer_count ?? savedCustomerCount),
    repeatCustomers: topCustomers.filter((customer) => Number(customer.bill_count) > 1).length,
    averageBill: Number(report?.summary?.average_bill) || null,
    topCustomers: topCustomers.slice(0, 4),
  };
}

export function buildCustomerCategoryMix(topProducts = [], inventoryProducts = [], limit = 4) {
  const categoryByProduct = new Map(inventoryProducts.map((product) => [
    String(product.name || "").trim().toLocaleLowerCase(),
    String(product.category || "Uncategorized").trim() || "Uncategorized",
  ]));
  const totals = new Map();
  topProducts.slice(0, 5).forEach((product) => {
    const name = String(product.name || "").trim().toLocaleLowerCase();
    const category = categoryByProduct.get(name) || "Uncategorized";
    totals.set(category, (totals.get(category) || 0) + (Number(product.sales) || 0));
  });
  const totalSales = [...totals.values()].reduce((sum, sales) => sum + sales, 0);
  return [...totals.entries()]
    .map(([category, sales]) => ({ category, sales, share: totalSales > 0 ? (sales / totalSales) * 100 : 0 }))
    .sort((first, second) => second.sales - first.sales || first.category.localeCompare(second.category))
    .slice(0, limit);
}

export function buildDashboardAlerts({ attentionItems = [], outstandingAmount = 0, outstandingBillCount = 0, salesChangePercent, changeText = "" }) {
  return [
    ...attentionItems.slice(0, 3).map((product) => ({
      tone: product.hasLowStock ? "danger" : "warn",
      title: product.hasLowStock ? `${product.name} is low in stock` : `${product.name} is nearing expiry`,
      detail: product.hasLowStock ? `${product.quantity} ${product.unit} remaining` : `Expiry ${product.expiryDate}`,
      path: "/inventory",
    })),
    ...(Number(outstandingAmount) > 0 ? [{
      tone: "warn", title: "Customer balances are outstanding",
      detail: `${outstandingBillCount || 0} bills · ₹${Number(outstandingAmount).toLocaleString("en-IN", { maximumFractionDigits: 0 })} due`, path: "/payments",
    }] : []),
    ...(Number(salesChangePercent) < 0 ? [{ tone: "info", title: "Sales declined in this period", detail: changeText, path: "/reports" }] : []),
  ].slice(0, 5);
}
