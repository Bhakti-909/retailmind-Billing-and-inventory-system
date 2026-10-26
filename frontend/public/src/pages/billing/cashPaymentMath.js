export function paymentAmounts(invoice = {}) {
  const total = Math.max(0, Number(invoice.total) || 0);
  const mode = String(invoice.paymentMethod || "Cash").trim().toLowerCase();
  const isBalanceMode = mode === "balance";
  // A Balance bill is not collected at checkout. Treat its saved mode as the
  // source of truth if older API/database rows contain stale paid fields.
  if (isBalanceMode) {
    return { paid: 0, due: total, status: total > 0 ? "UNPAID" : "PAID" };
  }
  const hasPaidAmount = invoice.paidAmount !== null && invoice.paidAmount !== undefined && Number.isFinite(Number(invoice.paidAmount));
  const hasBalanceDue = invoice.balanceDue !== null && invoice.balanceDue !== undefined && Number.isFinite(Number(invoice.balanceDue));
  const paid = hasPaidAmount
    ? Math.min(total, Math.max(0, Number(invoice.paidAmount)))
    : isBalanceMode ? 0 : total;
  let due = hasBalanceDue
    ? Math.min(total, Math.max(0, Number(invoice.balanceDue)))
    : isBalanceMode ? total : 0;
  if (due === 0 && paid < total) due = total - paid;
  return { paid, due, status: due > 0 ? "UNPAID" : "PAID" };
}

export function paymentTenders(invoice = {}) {
  const saved = Object.entries(invoice.paymentBreakdown || {})
    .map(([method, amount]) => [method, Math.max(0, Number(amount) || 0)])
    .filter(([, amount]) => amount > 0);
  if (saved.length) return saved;
  const { paid } = paymentAmounts(invoice);
  return paid > 0 ? [[invoice.paymentMethod || "Cash", paid]] : [];
}
