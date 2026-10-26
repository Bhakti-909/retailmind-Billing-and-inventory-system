import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../hooks/useAuth";
import { getCurrentUser } from "../../services/authApi";
import { createSale, resendInvoiceEmail } from "../../services/billingApi";
import { getProducts } from "../../services/inventoryApi";
import { getCustomers, createCustomer } from "../../services/customerApi";
import { getProductCombinationReport, getSalesReport } from "../../services/reportApi";
import BarcodeScanner from "../../components/BarcodeScanner";
import { businessDateString, formatBusinessDateTime } from "../../utils/businessDate";
import "./BillingPage.css";

const gstRates = [0, 5, 12, 18, 28];
const billingModeStorageKey = "smartRetail:lastBillingMode";
const readLastBillingMode = (fallback) => {
  try {
    const savedMode = window.localStorage.getItem(billingModeStorageKey);
    return savedMode === "gst" || savedMode === "quick" ? savedMode : fallback;
  } catch {
    return fallback;
  }
};

const roundMoney = (value) => Math.round((Number(value) + Number.EPSILON) * 100) / 100;
const localDateString = businessDateString;
const isExpired = (product) => Boolean(product.expiryDate && product.expiryDate < localDateString());
const formatMoney = (value) =>
  Number(value || 0).toLocaleString("en-IN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
const displayCategory = (category) =>
  /^miscellaneous$/i.test(String(category || "")) ? "Other" : category;
const normalizeProductName = (name) => String(name || "").trim().toLocaleLowerCase();
const isLowStock = (product) => {
  const quantity = Number(product?.quantity) || 0;
  const reorderLevel = Number(product?.reorderLevel ?? 5);
  return quantity > 0 && quantity <= reorderLevel;
};
const isStockLimitError = (message) =>
  /^(?:Only \d+ .* available for |Only \d+ available in stock\.|Max stock reached for )/.test(message || "");
const normalizeScanCode = (value) => String(value || "").trim().toUpperCase();
const businessDateTimeValue = (date = new Date()) => {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Kolkata", year: "numeric", month: "2-digit", day: "2-digit",
    hour: "2-digit", minute: "2-digit", hourCycle: "h23",
  }).formatToParts(date).reduce((result, part) => ({ ...result, [part.type]: part.value }), {});
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}`;
};
const businessDateTimeInstant = (value) => new Date(`${value}:00+05:30`);
const barcodeCodeMatches = (left, right) => {
  const first = normalizeScanCode(left);
  const second = normalizeScanCode(right);
  if (!first || !second) return false;
  if (first === second) return true;
  // Some scanners report UPC-A as 12 digits while the catalogue stores its EAN-13 form.
  return first.replace(/^0(?=\d{12}$)/, "") === second.replace(/^0(?=\d{12}$)/, "");
};

export default function BillingPage() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [shop, setShop] = useState(user || {});

  // Data states
  const [products, setProducts] = useState([]);
  const [customers, setCustomers] = useState([]);
  const [productInsights, setProductInsights] = useState({ combinations: [], topProducts: [] });
  const [productsLoading, setProductsLoading] = useState(true);
  const [customersLoading, setCustomersLoading] = useState(false);

  // Billing mode & search
  const [mode, setMode] = useState(() => readLastBillingMode(user?.gstin ? "gst" : "quick")); // 'quick' or 'gst'
  const [productSearch, setProductSearch] = useState("");
  const [showProductSearchResults, setShowProductSearchResults] = useState(false);
  const [categoryBrowseActive, setCategoryBrowseActive] = useState(false);
  const [selectedCategory, setSelectedCategory] = useState("all");
  const [showLowStockOnly, setShowLowStockOnly] = useState(false);
  const [barcodeInput, setBarcodeInput] = useState("");
  const [unknownBarcode, setUnknownBarcode] = useState("");
  const barcodeInputRef = useRef(null);
  const productSearchContainerRef = useRef(null);
  const [cameraScanOpen, setCameraScanOpen] = useState(false);

  useEffect(() => {
    if (!showProductSearchResults) return undefined;

    const closeSearchOnOutsideClick = (event) => {
      if (productSearchContainerRef.current?.contains(event.target)) return;
      setShowProductSearchResults(false);
      setCategoryBrowseActive(false);
      setBarcodeInput("");
      setProductSearch("");
    };

    document.addEventListener("pointerdown", closeSearchOnOutsideClick, true);
    return () => document.removeEventListener("pointerdown", closeSearchOnOutsideClick, true);
  }, [showProductSearchResults]);

  // Cart & calculations
  const [items, setItems] = useState([]);
  const [discountInput, setDiscountInput] = useState("0");
  const [gstRate, setGstRate] = useState(18);
  const [intraState, setIntraState] = useState(true);
  const [paymentMethod, setPaymentMethod] = useState("Cash");
  const [paymentDueAt, setPaymentDueAt] = useState("");

  // Split payment breakdown fields
  const [splitCash, setSplitCash] = useState("");
  const [splitOnline, setSplitOnline] = useState("");
  const [splitCreditCard, setSplitCreditCard] = useState("");
  const [splitDebitCard, setSplitDebitCard] = useState("");

  // Customer selection / quick create
  const [customerSearch, setCustomerSearch] = useState("");
  const [selectedCustomer, setSelectedCustomer] = useState(null);
  const [showCustomerDropdown, setShowCustomerDropdown] = useState(false);
  const customerSearchContainerRef = useRef(null);
  const [quickCustomerModal, setQuickCustomerModal] = useState(false);
  const [newCustPhone, setNewCustPhone] = useState("");
  const [newCustName, setNewCustName] = useState("");
  const [newCustEmail, setNewCustEmail] = useState("");
  const [creatingCustomer, setCreatingCustomer] = useState(false);

  useEffect(() => {
    if (!showCustomerDropdown) return undefined;

    const closeCustomerDropdownOnOutsideClick = (event) => {
      if (customerSearchContainerRef.current?.contains(event.target)) return;
      setShowCustomerDropdown(false);
    };

    document.addEventListener("pointerdown", closeCustomerDropdownOnOutsideClick, true);
    return () => document.removeEventListener("pointerdown", closeCustomerDropdownOnOutsideClick, true);
  }, [showCustomerDropdown]);

  // GST specifics
  const [buyerName, setBuyerName] = useState("");
  const [buyerGstin, setBuyerGstin] = useState("");

  // Sale result modal / state
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [completedInvoice, setCompletedInvoice] = useState(null);
  const [emailRetrying, setEmailRetrying] = useState(false);

  // Both billing paths use the GST rates stored on inventory products.
  const gstEnabled = mode === "quick" || mode === "gst";
  const usesIntraState = intraState;
  const selectBillingMode = (nextMode) => {
    setMode(nextMode);
    if (nextMode === "quick") {
      setSelectedCustomer(null);
      setCustomerSearch("");
      setBuyerName("");
      setBuyerGstin("");
      setShowCustomerDropdown(false);
      setQuickCustomerModal(false);
    }
    try {
      window.localStorage.setItem(billingModeStorageKey, nextMode);
    } catch {
      // Keep the current selection if browser storage is unavailable.
    }
  };

  // Load Inventory & Customers
  const loadData = async () => {
    setProductsLoading(true);
    setCustomersLoading(true);
    setError("");
    try {
      const [prods, custs] = await Promise.all([
        getProducts().catch(() => []),
        getCustomers().catch(() => []),
      ]);
      setProducts(prods || []);
      setCustomers(custs || []);
      Promise.all([
        getProductCombinationReport().catch(() => ({ combinations: [] })),
        getSalesReport().catch(() => ({ topProducts: [] })),
      ]).then(([combinationReport, salesReport]) => {
        setProductInsights({
          combinations: combinationReport?.combinations || [],
          topProducts: salesReport?.topProducts || [],
        });
      });
    } catch (err) {
      setError("Failed to load initial billing records.");
    } finally {
      setProductsLoading(false);
      setCustomersLoading(false);
    }
  };

  useEffect(() => {
    loadData();
    getCurrentUser().then((profile) => {
      setShop(profile);
      setMode(readLastBillingMode(profile?.gstin ? "gst" : "quick"));
    }).catch(() => setShop(user || {}));
  }, []);

  useEffect(() => {
    let refreshTimer;
    const refreshVisibleBilling = () => {
      if (document.visibilityState !== "visible") return;
      window.clearTimeout(refreshTimer);
      refreshTimer = window.setTimeout(loadData, 120);
    };
    window.addEventListener("focus", refreshVisibleBilling);
    document.addEventListener("visibilitychange", refreshVisibleBilling);
    return () => {
      window.clearTimeout(refreshTimer);
      window.removeEventListener("focus", refreshVisibleBilling);
      document.removeEventListener("visibilitychange", refreshVisibleBilling);
    };
  }, []);

  const categories = useMemo(() => {
    const values = new Set(products.map((product) => product.category).filter(Boolean));
    return ["all", ...values];
  }, [products]);

  const searchResults = useMemo(() => {
    const query = productSearch.trim().toLocaleLowerCase();
    return products.filter((product) =>
      !isExpired(product) && Number(product.quantity) > 0 &&
      (selectedCategory === "all" || product.category === selectedCategory) &&
      (!showLowStockOnly || isLowStock(product)) &&
      (!query || [product.name, product.sku, product.barcode, product.category]
        .some((value) => String(value || "").toLocaleLowerCase().includes(query)))
    );
  }, [products, productSearch, selectedCategory, showLowStockOnly]);
  const visibleSearchProducts = productSearch.trim()
    ? searchResults.slice(0, 8)
    : categoryBrowseActive ? searchResults : [];

  // Customer search suggestions
  const filteredCustomers = useMemo(() => {
    if (!customerSearch.trim()) return customers.slice(0, 6);
    const q = customerSearch.toLowerCase().trim();
    return customers.filter(
      (c) =>
        c.name.toLowerCase().includes(q) ||
        (c.phone && c.phone.includes(q)) ||
        (c.email && c.email.toLowerCase().includes(q))
    ).slice(0, 8);
  }, [customers, customerSearch]);

  const bestCombinations = useMemo(() => {
    const available = products.filter((product) => Number(product.quantity) > 0 && !isExpired(product));
    const productsByName = new Map(available.map((product) => [normalizeProductName(product.name), product]));
    const currentProducts = items.map((item) => products.find((product) => product.id === item.productId)).filter(Boolean)
      .map((product) => ({ product, score: Number.POSITIVE_INFINITY, label: "In this bill", inBill: true }));
    const cartIds = new Set(items.map((item) => item.productId));
    const cartNames = new Set(items.map((item) => normalizeProductName(item.productName)));
    const paired = [];
    const popular = [];

    if (items.length) {
      productInsights.combinations.forEach((pair) => {
        const nameA = normalizeProductName(pair.product_a);
        const nameB = normalizeProductName(pair.product_b);
        const candidateName = cartNames.has(nameA) && !cartNames.has(nameB)
          ? nameB
          : cartNames.has(nameB) && !cartNames.has(nameA) ? nameA : "";
        const product = productsByName.get(candidateName);
        if (product && !cartIds.has(product.id)) paired.push({ product, score: Number(pair.bills_together) || 0, label: "Often bought together" });
      });
    }

    productInsights.topProducts.forEach((entry) => {
      const product = productsByName.get(normalizeProductName(entry.product_name));
      if (product && !cartIds.has(product.id)) popular.push({ product, score: Number(entry.units_sold) || 0, label: "Popular" });
    });
    const bySales = (left, right) => right.score - left.score || Number(right.product.quantity) - Number(left.product.quantity);
    const pairedIds = new Set(paired.map((entry) => entry.product.id));
    const suggestions = items.length
      ? [...paired.sort(bySales), ...popular.filter((entry) => !pairedIds.has(entry.product.id)).sort(bySales)]
      : popular.sort(bySales);
    const ranked = [...currentProducts, ...suggestions];
    const seen = new Set();
    return ranked.filter(({ product }) => {
      if (seen.has(product.id)) return false;
      seen.add(product.id);
      return true;
    }).slice(0, 4).map((entry) => ({ ...entry.product, suggestionLabel: entry.label, inBill: Boolean(entry.inBill || cartIds.has(entry.product.id)) }));
  }, [items, products, productInsights]);

  // Subtotals & Tax math
  const subtotal = useMemo(
    () => roundMoney(items.reduce((sum, item) => sum + item.quantity * item.unitPrice, 0)),
    [items]
  );
  const totalQuantity = items.reduce((sum, item) => sum + item.quantity, 0);
  const mrpSubtotal = useMemo(
    () => roundMoney(items.reduce((sum, item) => sum + item.quantity * item.mrpPrice, 0)),
    [items]
  );
  const productSavings = Math.max(0, roundMoney(mrpSubtotal - subtotal));
  const discount = roundMoney(Number(discountInput) || 0);
  const discountInvalid = discount < 0 || discount > subtotal;
  const totalSavings = roundMoney(productSavings + (discountInvalid ? 0 : discount));
  const grossAfterDiscount = Math.max(0, roundMoney(subtotal - discount));

  const taxBreakdown = useMemo(() => {
    if (!gstEnabled || subtotal <= 0) return [];
    const byRate = new Map();
    let discountAllocated = 0;
    for (const [index, item] of items.entries()) {
      const rate = Number(item.gstRate ?? gstRate);
      const lineSubtotal = roundMoney(item.quantity * item.unitPrice);
      const lineDiscount =
        index === items.length - 1
          ? roundMoney(discount - discountAllocated)
          : roundMoney((discount * lineSubtotal) / subtotal);
      discountAllocated = roundMoney(discountAllocated + lineDiscount);
      const lineGross = Math.max(0, roundMoney(lineSubtotal - lineDiscount));
      const lineTax = rate === 0 ? 0 : roundMoney((lineGross * rate) / (100 + rate));
      const entry = byRate.get(rate) || { rate, taxable: 0, tax: 0 };
      entry.taxable = roundMoney(entry.taxable + lineGross - lineTax);
      entry.tax = roundMoney(entry.tax + lineTax);
      byRate.set(rate, entry);
    }
    return [...byRate.values()]
      .sort((a, b) => a.rate - b.rate)
      .map((entry) => {
        const cgstAmount = usesIntraState ? roundMoney(Math.floor(entry.tax * 50) / 100) : 0;
        const sgstAmount = usesIntraState ? roundMoney(entry.tax - cgstAmount) : 0;
        return {
          ...entry,
          cgst: cgstAmount,
          sgst: sgstAmount,
          igst: usesIntraState ? 0 : entry.tax,
        };
      });
  }, [items, subtotal, discount, gstEnabled, gstRate, usesIntraState]);

  const taxAmount = taxBreakdown.reduce((sum, entry) => sum + entry.tax, 0);
  const cgst = taxBreakdown.reduce((sum, entry) => sum + entry.cgst, 0);
  const sgst = taxBreakdown.reduce((sum, entry) => sum + entry.sgst, 0);
  const igst = taxBreakdown.reduce((sum, entry) => sum + entry.igst, 0);
  const taxableAmount = gstEnabled
    ? Math.max(0, roundMoney(grossAfterDiscount - taxAmount))
    : grossAfterDiscount;
  const total = grossAfterDiscount;
  const sellerGstin = shop?.gstin?.replace(/\s/g, "").toUpperCase() || "";
  const sellerGstinValid = /^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$/.test(sellerGstin);
  const missingGstin = gstEnabled && !sellerGstinValid;
  const buyerGstinInvalid = gstEnabled && Boolean(buyerGstin) && !/^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$/.test(buyerGstin);

  // Split calculation validation
  const splitCashNum = roundMoney(Number(splitCash) || 0);
  const splitOnlineNum = roundMoney(Number(splitOnline) || 0);
  const splitCreditCardNum = roundMoney(Number(splitCreditCard) || 0);
  const splitDebitCardNum = roundMoney(Number(splitDebitCard) || 0);
  const splitSum = roundMoney(splitCashNum + splitOnlineNum + splitCreditCardNum + splitDebitCardNum);
  const splitDifference = roundMoney(total - splitSum);
  // The billing service validates split tenders against the exact rounded bill total.
  const splitInvalid = paymentMethod === "Split" && splitDifference !== 0;

  // Auto-fill first split field when toggling to split
  const handleSelectPaymentMethod = (pm) => {
    setPaymentMethod(pm);
    if (pm !== "Balance") setPaymentDueAt("");
    if (pm === "Split" && !splitCash && !splitOnline && !splitCreditCard && !splitDebitCard) {
      setSplitCash(String(total));
      setSplitOnline("0");
      setSplitCreditCard("0");
      setSplitDebitCard("0");
    }
  };

  // Add product to cart with strict stock and expiry guards
  const addProductToCart = (product, requested = 1) => {
    setError("");
    setUnknownBarcode("");
    if (!product || !Number.isInteger(requested) || requested < 1) return false;
    if (Number(product.quantity) <= 0) {
      setError(`"${product.name}" is completely out of stock.`);
      return false;
    }
    if (isExpired(product)) {
      setError(`"${product.name}" is expired and cannot be sold.`);
      return false;
    }

    const existingInCart = items.find((item) => item.productId === product.id);
    const currentQtyInCart = existingInCart ? existingInCart.quantity : 0;

    if (currentQtyInCart + requested > Number(product.quantity)) {
      setError(
        `Only ${product.quantity} ${product.unit} available for ${product.name}. Already added ${currentQtyInCart}.`
      );
      return false;
    }

    setItems((current) => {
      const existingIndex = current.findIndex((item) => item.productId === product.id);
      if (existingIndex >= 0) {
        const updated = { ...current[existingIndex], quantity: current[existingIndex].quantity + requested };
        return [updated, ...current.filter((_, index) => index !== existingIndex)];
      }
      return [
        {
          productId: product.id,
          productName: product.name,
          category: product.category || "General",
          unit: product.unit,
          available: Number(product.quantity),
          quantity: requested,
          unitPrice: Number(product.sellingPrice),
          unitCost: Number(product.costPrice || 0),
          mrpPrice: Number(product.mrpPrice ?? product.sellingPrice),
          gstRate: Number(product.gstRate ?? gstRate),
        },
        ...current,
      ];
    });
    return true;
  };

  const updateQuantity = (index, delta) => {
    const item = items[index];
    if (!item) return;
    const nextQty = item.quantity + delta;
    if (nextQty > item.available) {
      setError(`Max stock reached for ${item.productName} (${item.available} max)`);
      return;
    }
    setError((current) => (isStockLimitError(current) ? "" : current));
    setItems((current) => current
      .map((currentItem, itemIndex) => {
        if (itemIndex !== index) return currentItem;
        return nextQty <= 0 ? null : { ...currentItem, quantity: nextQty };
      })
      .filter(Boolean));
  };

  const setDirectQuantity = (index, val) => {
    const qty = parseInt(val, 10);
    if (isNaN(qty) || qty <= 0) return;
    const item = items[index];
    if (!item) return;
    if (qty > item.available) {
      setError(`Only ${item.available} available in stock.`);
      setItems((current) => current.map((currentItem, itemIndex) =>
        itemIndex === index ? { ...currentItem, quantity: currentItem.available } : currentItem));
      return;
    }
    setError((current) => (isStockLimitError(current) ? "" : current));
    setItems((current) => current.map((currentItem, itemIndex) =>
      itemIndex === index ? { ...currentItem, quantity: qty } : currentItem));
  };

  // Barcode search handling
  const handleBarcodeKeyDown = (event) => {
    if (event.key !== "Enter") return;
    event.preventDefault();
    const barcode = barcodeInput.trim();
    if (!barcode) return;
    const match = products.find(
      (item) => barcodeCodeMatches(item.barcode, barcode) || normalizeScanCode(item.sku) === normalizeScanCode(barcode)
    );
    if (match) {
      addProductToCart(match, 1);
    } else {
      const matches = products.filter((item) =>
        !isExpired(item) && Number(item.quantity) > 0 &&
        [item.name, item.sku, item.barcode, item.category].some((value) => String(value || "").toLowerCase().includes(barcode.toLowerCase()))
      );
      setProductSearch(barcode);
      setShowProductSearchResults(true);
      setBarcodeInput("");
      if (matches.length === 1) {
        addProductToCart(matches[0], 1);
      } else if (matches.length > 1) {
        setUnknownBarcode("");
        setError(`${matches.length} products match “${barcode}”. Choose the right item from the list.`);
      } else {
        setUnknownBarcode(barcode);
        setError(`No product found for “${barcode}”. Check the code or add the product to inventory.`);
      }
    }
    requestAnimationFrame(() => barcodeInputRef.current?.focus());
  };

  const handleScannedBarcode = (barcode) => {
    const normalized = barcode.trim();
    const match = products.find(
      (item) => barcodeCodeMatches(item.barcode, normalized) || normalizeScanCode(item.sku) === normalizeScanCode(normalized)
    );
    if (!match) {
      setUnknownBarcode(normalized);
      setProductSearch(normalized);
      setShowProductSearchResults(true);
      setError(`No product is linked to “${normalized}”. Close the scanner to review search results or add the product to inventory.`);
      setBarcodeInput("");
      if (!cameraScanOpen) requestAnimationFrame(() => barcodeInputRef.current?.focus());
      return {
        type: "warning",
        title: `No product is linked to “${normalized}”.`,
        detail: "Close the scanner to review search results or add the product to inventory.",
      };
    }
    const previousQuantity = items.find((item) => item.productId === match.id)?.quantity || 0;
    const added = addProductToCart(match, 1);
    setBarcodeInput("");
    setProductSearch("");
    if (!cameraScanOpen) requestAnimationFrame(() => barcodeInputRef.current?.focus());
    if (!added) {
      return { type: "warning", title: `${match.name} not added`, detail: "Check stock and expiry details." };
    }
    return {
      type: "success",
      title: `${match.name} added to bill`,
      detail: `Quantity in bill: ${previousQuantity + 1} · ${Math.max(0, Number(match.quantity) - previousQuantity - 1)} ${match.unit} remaining`,
    };
  };

  // Customer selection logic
  const handleSelectCustomer = (customer) => {
    setSelectedCustomer(customer);
    setCustomerSearch(customer.name);
    setBuyerName(customer.name);
    setShowCustomerDropdown(false);
  };

  const handleClearCustomer = () => {
    setSelectedCustomer(null);
    setCustomerSearch("");
    setBuyerName("");
  };

  // Quick Customer Creation modal handler
  const handleCreateNewCustomer = async (e) => {
    e.preventDefault();
    if (!newCustPhone.trim() || !newCustName.trim()) {
      setError("Customer name and phone number are required.");
      return;
    }
    setCreatingCustomer(true);
    setError("");
    try {
      const created = await createCustomer({
        name: newCustName.trim(),
        phone: newCustPhone.trim(),
        email: newCustEmail.trim() || null,
        address: "",
        notes: "Created from Billing POS",
      });
      // Add to local state & select directly
      setCustomers((prev) => [created, ...prev]);
      handleSelectCustomer(created);
      setQuickCustomerModal(false);
      setNewCustName("");
      setNewCustPhone("");
      setNewCustEmail("");
      setNotice(`Customer "${created.name}" created and attached to bill.`);
    } catch (err) {
      setError(err?.response?.data?.message || "Failed to create customer. Phone may already exist.");
    } finally {
      setCreatingCustomer(false);
    }
  };

  // Checkout submission - Strict Single Payment Processing (No duplicate / multiple pays)
  const retryInvoiceEmail = async () => {
    if (!completedInvoice?.id || emailRetrying) return;
    setEmailRetrying(true);
    try {
      const result = await resendInvoiceEmail(completedInvoice.id);
      setCompletedInvoice((current) => current ? { ...current, ...result } : current);
    } catch (err) {
      const message = err?.response?.data?.emailMessage || err?.response?.data?.message || "Email could not be sent. Check the customer's email and Gmail settings.";
      setCompletedInvoice((current) => current ? { ...current, emailSent: false, emailMessage: message } : current);
    } finally {
      setEmailRetrying(false);
    }
  };

  const completeSale = async (event) => {
    if (event) event.preventDefault();
    if (saving) return; // Prevent double submit
    setError("");
    setNotice("");
    if (!items.length) {
      setError("Please add at least one product to the bill.");
      return;
    }
    if (discountInvalid) {
      setError("Discount cannot be greater than subtotal.");
      return;
    }
    if (missingGstin) {
      return;
    }
    if (buyerGstinInvalid) {
      setError("Enter a valid 15-character buyer GSTIN, or leave the field blank.");
      return;
    }
    if (paymentMethod === "Split" && splitInvalid) {
      setError(
        `Split payments must equal the net payable total ${"\u20B9"}${formatMoney(total)}. Currently ${"\u20B9"}${formatMoney(splitSum)} (Difference: ${"\u20B9"}${formatMoney(splitDifference)}).`
      );
      return;
    }
    if (paymentMethod === "Balance" && !selectedCustomer) {
      setError("Select a saved customer before recording a balance bill.");
      return;
    }
    if (paymentMethod === "Balance" && !selectedCustomer.email) {
      setError("Add an email address to this customer's profile to schedule the payment reminder.");
      return;
    }
    if (paymentMethod === "Balance" && (!paymentDueAt || businessDateTimeInstant(paymentDueAt).getTime() <= Date.now())) {
      setError("Choose a future date and time for the payment reminder.");
      return;
    }

    setSaving(true);
    try {
      // Quick billing is a walk-in GST sale; customer lookup is optional in invoice mode.
      const resolvedCustomerName = selectedCustomer
        ? selectedCustomer.name
        : mode === "quick"
        ? "Walk-in Customer"
        : customerSearch.trim() || buyerName.trim() || "Walk-in GST Customer";

      const result = await createSale({
        items: items.map(
          ({ productId, productName, quantity: itemQty, unitPrice, unitCost, mrpPrice, gstRate: itemGstRate }) => ({
            productId,
            productName,
            quantity: itemQty,
            unitPrice,
            unitCost,
            mrpPrice,
            gstRate: itemGstRate,
          })
        ),
        gstInvoice: gstEnabled,
        discountAmount: discount,
        gstRate: gstEnabled ? Number(gstRate) : 0,
        intraState: usesIntraState,
        paymentMethod,
        paymentDueAt: paymentMethod === "Balance" ? businessDateTimeInstant(paymentDueAt).toISOString() : null,
        splitPayment: paymentMethod === "Split" ? {
          cash: splitCashNum,
          upi: splitOnlineNum,
          creditCard: splitCreditCardNum,
          debitCard: splitDebitCardNum,
        } : null,
        buyerName: resolvedCustomerName,
        customerId: selectedCustomer?.id || null,
        buyerGstin: mode === "gst" ? buyerGstin.trim().toUpperCase() || null : null,
      });
      // Prepare detailed invoice data with split details
      const invoiceData = {
        ...result,
        customer: selectedCustomer,
        buyerName: resolvedCustomerName,
        buyerGstin: mode === "gst" ? buyerGstin.trim().toUpperCase() : null,
        paymentMethod,
        splitDetails:
          paymentMethod === "Split"
            ? {
                cash: splitCashNum,
                online: splitOnlineNum,
                creditCard: splitCreditCardNum,
                debitCard: splitDebitCardNum,
              }
            : null,
        itemsPurchased: [...items],
      };


      setCompletedInvoice(invoiceData);

      // Reset cart and fields immediately so only ONE bill is processed
      setItems([]);
      setDiscountInput("0");
      handleClearCustomer();
      setBuyerGstin("");
      setSplitCash("");
      setSplitOnline("");
      setSplitCreditCard("");
      setSplitDebitCard("");
      if (paymentMethod === "Balance") setPaymentMethod("Cash");
      setPaymentDueAt("");

      // Keep checkout, receipt preview, print and email on this page.
      setTimeout(() => window.print(), 350);

      // Refresh inventory stock
      try {
        const freshProducts = await getProducts();
        setProducts(freshProducts || []);
      } catch {
        setError("The bill was completed, but inventory could not refresh. Reload the product list before the next sale.");
      }
    } catch (requestError) {
      setError(
        requestError?.response?.data?.message ||
          "Unable to complete transaction. Please check stock and try again."
      );
    } finally {
      setSaving(false);
    }
  };


  const currentBillItemsPanel = (
    <div className="pos-cart-panel pos-current-bill-panel">
            <div className="pos-cart-header">
              <div>
                <h3>Current Bill Items ({items.length})</h3>
                <small>Recently added products appear first</small>
              </div>
              {items.length > 0 && (
                <button
                  type="button"
                  className="cart-clear-all"
                  onClick={() => setItems([])}
                >
                  Clear Cart
                </button>
              )}
            </div>

            {items.length === 0 ? (
              <div className="pos-cart-empty">
                <div className="empty-cart-icon" aria-hidden="true">&#128722;</div>
                <p>Your bill is empty</p>
                <small>Scan a barcode or select a product from the list to add it.</small>
              </div>
            ) : (
              <>
                <div className="pos-cart-columns" aria-hidden="true">
                  <span>Product</span><span>Qty</span><span>Line total</span><span />
                </div>
                <div className="pos-cart-items-list">
                  {items.map((item, index) => (
                    <div key={`${item.productId}-${index}`} className="pos-cart-row">
                    <div className="item-info">
                      <strong>{item.productName}</strong>
                      <div className="item-sub">
                        <span>{"\u20B9"}{formatMoney(item.unitPrice)} / {item.unit}</span>
                        <span className="item-gst-tag">{item.gstRate}% GST included</span>
                        {item.mrpPrice > item.unitPrice && <small className="item-save">Save {"\u20B9"}{formatMoney((item.mrpPrice - item.unitPrice) * item.quantity)}</small>}
                      </div>
                      <small className="item-line-detail">
                        Line {"\u20B9"}{formatMoney(item.quantity * item.unitPrice)}
                        {gstEnabled && Number(item.gstRate) > 0 && ` · includes ${"\u20B9"}${formatMoney((item.quantity * item.unitPrice * item.gstRate) / (100 + item.gstRate))} GST`}
                      </small>
                    </div>

                    {/* Quantity Stepper */}
                    <div className="item-quantity-controls">
                      <button
                        type="button"
                        onClick={() => updateQuantity(index, -1)}
                      >
                        -
                      </button>
                      <input
                        type="number"
                        min="1"
                        max={item.available}
                        value={item.quantity}
                        onChange={(e) => setDirectQuantity(index, e.target.value)}
                      />
                      <button
                        type="button"
                        onClick={() => updateQuantity(index, 1)}
                      >
                        +
                      </button>
                    </div>

                    {/* Line Total */}
                    <div className="item-row-total">
                      <strong>{"\u20B9"}{formatMoney(item.quantity * item.unitPrice)}</strong>
                    </div>

                    <button
                      type="button"
                      className="item-remove-cross"
                      title="Remove from bill"
                      aria-label={`Remove ${item.productName} from bill`}
                      onClick={() =>
                        setItems((curr) => curr.filter((_, i) => i !== index))
                      }
                    >
                      ×
                    </button>
                    </div>
                  ))}
                </div>
              </>
            )}

          </div>
  );

  return (
    <div className={`pos-billing-container mode-${mode}`}>
      {/* Top Header Bar */}
      <header className="pos-top-header">
        <div className="pos-header-left">
          <div className="pos-brand-badge" aria-hidden="true">
            <svg viewBox="0 0 24 24" focusable="false"><path d="M3 4h2l2.1 10.1a2 2 0 0 0 2 1.6h8.8a2 2 0 0 0 1.9-1.4L22 8H7M10 20h.01M18 20h.01" /></svg>
          </div>
          <div className="pos-brand-copy">
            <strong>Point of Sale</strong>
            <span>{shop?.shopName || shop?.businessName || "Your business"}</span>
          </div>
        </div>

        <div ref={productSearchContainerRef} className={`pos-header-search ${showProductSearchResults ? "is-searching" : ""}`}>
          <div className="pos-input-group barcode-group">
            <span className="pos-input-icon" aria-hidden="true">&#128269;</span>
            <input
              ref={barcodeInputRef}
              type="text"
              className={`pos-input ${barcodeInput ? "has-search-clear" : ""}`}
              placeholder="Search product name, SKU, category or barcode"
              aria-label="Search products by name, SKU, category or barcode"
              value={barcodeInput}
              onFocus={() => setShowProductSearchResults(true)}
              onClick={() => setShowProductSearchResults(true)}
              onBlur={(event) => {
                if (!event.relatedTarget?.closest?.(".pos-header-search")) {
                  setShowProductSearchResults(false);
                }
              }}
              onChange={(e) => {
                setBarcodeInput(e.target.value);
                setProductSearch(e.target.value);
                setShowProductSearchResults(true);
              }}
              onKeyDown={handleBarcodeKeyDown}
            />
            {barcodeInput && <button type="button" className="pos-product-search-clear" aria-label="Clear product search" onClick={() => { setBarcodeInput(""); setProductSearch(""); setError(""); setUnknownBarcode(""); setShowProductSearchResults(true); }}>×</button>}
            <button type="button" className="pos-cam-scan-btn" title="Open barcode scanner" onClick={() => { setError(""); setUnknownBarcode(""); setCameraScanOpen(true); }}>Barcode Scanner</button>
          </div>
          {showProductSearchResults && (
            <div className="pos-product-search-results" role="region" aria-label="Product search and results">
              <div className="pos-search-actions">
                <span>Search or scan to add products to the bill</span>
                <div>
                  <button type="button" className="pos-add-inventory-link" onClick={() => navigate("/inventory")}>+ New product</button>
                  <button type="button" className="pos-refresh-link" onClick={loadData} disabled={productsLoading}>
                    {productsLoading ? "Refreshing…" : "↻ Refresh stock"}
                  </button>
                </div>
              </div>
              <div className="pos-category-pills pos-search-category-pills" role="group" aria-label="Browse products by category">
                <button type="button" className={`pos-pill ${selectedCategory === "all" && !showLowStockOnly ? "active" : ""}`} onClick={() => { setSelectedCategory("all"); setShowLowStockOnly(false); setCategoryBrowseActive(true); }}>
                  All ({products.filter((product) => !isExpired(product) && Number(product.quantity) > 0).length})
                </button>
                {categories.filter((category) => category !== "all").map((category) => (
                  <button key={category} type="button" className={`pos-pill ${selectedCategory === category && !showLowStockOnly ? "active" : ""}`} onClick={() => { setSelectedCategory(category); setShowLowStockOnly(false); setCategoryBrowseActive(true); }}>
                    {displayCategory(category)} ({products.filter((product) => product.category === category && !isExpired(product) && Number(product.quantity) > 0).length})
                  </button>
                ))}
                <button type="button" className={`pos-pill ${showLowStockOnly ? "active" : ""}`} onClick={() => { setSelectedCategory("all"); setShowLowStockOnly(true); setCategoryBrowseActive(true); }}>
                  Low stock ({products.filter((product) => isLowStock(product) && !isExpired(product)).length})
                </button>
              </div>
              {(productSearch.trim() || categoryBrowseActive) && (
                <>
                  <div className="product-search-results-heading">
                    <strong>{productsLoading ? "Loading products..." : productSearch.trim() ? `Search Suggestions (${searchResults.length})` : `${showLowStockOnly ? "Low stock" : selectedCategory === "all" ? "All active products" : displayCategory(selectedCategory)} (${searchResults.length})`}</strong>
                    <span>{productSearch.trim() ? "Matching products" : "Category products"} ? select Add to Cart</span>
                  </div>
                  {productsLoading ? <div className="product-search-empty"><span>Loading active stock…</span></div> : visibleSearchProducts.length ? visibleSearchProducts.map((product) => {
                    const inCartQty = items.find((item) => item.productId === product.id)?.quantity || 0;
                    const available = Math.max(0, Number(product.quantity) - inCartQty);
                    const selling = Number(product.sellingPrice) || 0;
                    return (
                      <div className="product-search-result" key={product.id}>
                        <span className="product-search-result-mark" aria-hidden="true">{String(product.name || "P").charAt(0).toUpperCase()}</span>
                        <div className="product-search-result-info">
                          <strong>{product.name}</strong>
                          <small>{product.sku || "No SKU"} · {displayCategory(product.category || "General")}</small>
                        </div>
                        <strong className="product-search-result-price">{"\u20B9"}{formatMoney(selling)}</strong>
                        <span className="product-search-result-stock">{available} {product.unit}</span>
                        <button type="button" className="catalog-add-btn" disabled={available <= 0} onClick={() => addProductToCart(product, 1)}>
                          {available > 0 ? "+ Add to Cart" : "In bill"}
                        </button>
                      </div>
                    );
                  }) : (
                    <div className="product-search-empty">
                      <span>No active stock matches this search.</span>
                      <button type="button" onClick={() => navigate("/inventory")}>Add product to inventory</button>
                    </div>
                  )}
                  <button type="button" className="product-search-close" onClick={() => { setShowProductSearchResults(false); setCategoryBrowseActive(false); }}>Close list</button>
                </>
              )}
            </div>
          )}
        </div>

        <div className="pos-header-right">
          {/* Mode Switcher */}
          <div className="pos-mode-selector">
            <button
              type="button"
              className={`pos-mode-btn ${mode === "quick" ? "active" : ""}`}
              onClick={() => selectBillingMode("quick")}
            >
              <span className="mode-icon" aria-hidden="true">⚡</span>
              <div>
                <strong>Quick Retail Receipt</strong>
                <small>Fast GST checkout · no customer details</small>
              </div>
            </button>
            <button
              type="button"
              className={`pos-mode-btn ${mode === "gst" ? "active" : ""}`}
              onClick={() => selectBillingMode("gst")}
            >
              <span className="mode-icon mode-icon-text" aria-hidden="true">GST</span>
              <div>
                <strong>GST Tax Invoice</strong>
                <small>Customer details and valid seller GSTIN</small>
              </div>
            </button>
          </div>
        </div>

        <div className="pos-cashier" aria-label={`Cashier ${shop?.username || user?.username || "Cashier"}`}>
          <span className="pos-cashier-avatar">{String(shop?.username || user?.username || "DS").split(/[ ._-]+/).filter(Boolean).slice(0, 2).map((part) => part[0]).join("").toUpperCase()}</span>
          <span><strong>{shop?.username || user?.username || "Cashier"}</strong><small>Cashier</small></span>
        </div>
      </header>

      {/* Alert Banners */}
      {error && !cameraScanOpen && (
        <div className="pos-alert error" role="alert">
          <span className="pos-alert-message">{error}</span>
          {unknownBarcode && <button type="button" className="pos-alert-action" onClick={() => navigate(`/inventory?barcode=${encodeURIComponent(unknownBarcode)}`)}>Add product to inventory</button>}
          <button type="button" className="pos-alert-dismiss" aria-label="Dismiss error" onClick={() => { setError(""); setUnknownBarcode(""); }}>×</button>
        </div>
      )}
      {notice && (
        <div className="pos-alert success" role="status">
          <span className="pos-alert-message">{notice}</span>
          <button type="button" className="pos-alert-dismiss" aria-label="Dismiss message" onClick={() => setNotice("")}>×</button>
        </div>
      )}

      {missingGstin && (
        <div className="pos-alert error pos-gstin-alert" role="alert">
          <span className="pos-gstin-alert-label">GST setup</span>
          <span className="pos-alert-message">{sellerGstin ? "The GSTIN saved in your business profile is invalid." : "A seller GSTIN is required to create a GST bill."} Update it in Profile to continue.</span>
          <button type="button" className="pos-alert-action" onClick={() => navigate("/profile")}>Open Profile</button>
        </div>
      )}

      {/* Main Billing Grid */}
      <div className="pos-main-layout">
        {/* LEFT COLUMN: Product search, search results and bill rows */}
        <section className="pos-products-column">
          {currentBillItemsPanel}
          <div className="pos-combination-section">
              <div className="combination-header"><div className="comb-title"><span className="comb-sparkle" aria-hidden="true">✦</span><div><h4>{items.length ? "Frequently Bought Together" : "Popular products"}</h4><small>{items.length ? "Suggested from recent bills" : "Popular items with active stock"}</small></div></div></div>
              <div className="combination-grid">
                {bestCombinations.map((product) => <div key={product.id} className={`combination-chip ${product.inBill ? "in-bill" : ""}`}><div className="comb-info"><strong>{product.name}</strong><span>{"\u20B9"}{formatMoney(product.sellingPrice)} · {product.quantity} {product.unit} available</span></div><button type="button" className={`comb-add-btn ${product.inBill ? "in-bill" : ""}`} disabled={product.inBill} onClick={() => addProductToCart(product, 1)}>{product.inBill ? "Added" : "+ Add"}</button></div>)}
              </div>
              {!bestCombinations.length && <div className="product-search-empty"><span>Popular products will appear here when sales history is available.</span></div>}
          </div>

        </section>


        {/* RIGHT COLUMN: Optional customer details, totals, GST and checkout */}
        <aside className="pos-billing-column">
          {/* CUSTOMER PROFILE CARD: Auto-Detect, Choose, or Instant Create */}
          <div className="pos-customer-card">
            <div className="customer-card-header">
              <span className="cust-tag">{mode === "quick" ? "CUSTOMER (OPTIONAL)" : "CUSTOMER DETAILS"}</span>
              {selectedCustomer ? (
                <button
                  type="button"
                  className="cust-change-btn"
                  onClick={handleClearCustomer}
                >
                  Change
                </button>
              ) : (
                <button
                  type="button"
                  className="cust-create-link"
                  onClick={() => {
                    setNewCustPhone(customerSearch.replace(/\D/g, ""));
                    setNewCustName(customerSearch.replace(/[0-9]/g, "").trim());
                    setQuickCustomerModal(true);
                  }}
                >
                  + New Customer
                </button>
              )}
            </div>

            {selectedCustomer ? (
              <div className="selected-customer-details">
                <div className="cust-avatar">
                  {selectedCustomer.name.charAt(0).toUpperCase()}
                </div>
                <div className="cust-meta">
                  <h3>{selectedCustomer.name}</h3>
                  <p>Phone: {selectedCustomer.phone || "Not provided"}</p>
                  {selectedCustomer.email && <small>Email: {selectedCustomer.email}</small>}
                  {selectedCustomer.notes && (
                    <span className="cust-notes">Note: {selectedCustomer.notes}</span>
                  )}
                </div>
              </div>
            ) : (
              <div ref={customerSearchContainerRef} className="customer-search-wrapper">
                <div className="pos-input-group">
                  <input
                    type="text"
                    className="pos-input"
                    placeholder="Search by Mobile No or Customer Name..."
                    value={customerSearch}
                    aria-label="Search customer by name or mobile number"
                    aria-expanded={showCustomerDropdown}
                    aria-controls="customer-search-options"
                    aria-autocomplete="list"
                    onChange={(e) => {
                      setCustomerSearch(e.target.value);
                      setShowCustomerDropdown(true);
                    }}
                    onFocus={() => setShowCustomerDropdown(true)}
                    onKeyDown={(event) => {
                      if (event.key === "Escape") setShowCustomerDropdown(false);
                    }}
                  />
                  {customerSearch && (
                    <button
                      type="button"
                      className="input-clear-btn"
                      onClick={() => {
                        setCustomerSearch("");
                        setShowCustomerDropdown(false);
                      }}
                    >
                      ×
                    </button>
                  )}
                </div>

                {/* Dropdown with Match list + Quick Create Action */}
                {showCustomerDropdown && (
                  <div className="customer-dropdown-menu" id="customer-search-options" role="listbox" aria-label="Customer matches">
                    {filteredCustomers.length > 0 ? (
                      filteredCustomers.map((cust) => (
                        <button
                          type="button"
                          role="option"
                          aria-selected="false"
                          key={cust.id}
                          className="customer-item"
                          onClick={() => handleSelectCustomer(cust)}
                        >
                          <div className="cust-dropdown-name">
                            <strong>{cust.name}</strong>
                            <span>{cust.phone}</span>
                          </div>
                          {cust.email && <small>{cust.email}</small>}
                        </button>
                      ))
                    ) : (
                      <div className="customer-not-found">
                        <span>No registered customer found for "{customerSearch}"</span>
                      </div>
                    )}
                    <button
                      type="button"
                      className="customer-dropdown-footer"
                      onClick={() => {
                        setNewCustPhone(customerSearch.replace(/\D/g, ""));
                        setNewCustName(customerSearch.replace(/[0-9]/g, "").trim());
                        setQuickCustomerModal(true);
                        setShowCustomerDropdown(false);
                      }}
                    >
                      + Create customer “{customerSearch || "New Customer"}”
                    </button>
                  </div>
                )}
              </div>
            )}
            {!selectedCustomer && (
              <p className="customer-helper-note">
                Customer details are optional. Add a customer when you want their details on the bill.
              </p>
            )}

            {/* If GST Mode is active, show GSTIN and Intra/Inter-state options */}
            {mode === "gst" && (
              <div className="pos-gst-extra-fields">
                <div className="pos-field-row">
                  <label>Buyer GSTIN (Optional for B2C):</label>
                  <input
                    type="text"
                    maxLength={15}
                    placeholder="e.g. 27AAAAA0000A1Z5"
                    value={buyerGstin}
                    aria-invalid={buyerGstinInvalid}
                    onChange={(e) =>
                      setBuyerGstin(e.target.value.replace(/\s/g, "").toUpperCase())
                    }
                  />
                </div>
                <div className="pos-field-row radio-group">
                  <label>
                    <input
                      type="radio"
                      checked={intraState}
                      onChange={() => setIntraState(true)}
                    />
                    Intra-State (CGST + SGST)
                  </label>
                  <label>
                    <input
                      type="radio"
                      checked={!intraState}
                      onChange={() => setIntraState(false)}
                    />
                    Inter-State (IGST)
                  </label>
                </div>
              </div>
            )}
          </div>

          <div className="pos-cart-panel pos-checkout-summary">
            {/* BILL TOTALS SUMMARY */}
            <div className="pos-totals-block">
              <section className="pos-summary-section" aria-label="Bill overview">
                <h4 className="pos-summary-section-title">Bill overview</h4>
                <div className="pos-summary-line">
                  <span>Products in bill</span>
                  <span>{items.length}</span>
                </div>
                <div className="pos-summary-line">
                  <span>Total units</span>
                  <span>{totalQuantity}</span>
                </div>
                <div className="pos-summary-line">
                  <span>Items subtotal <small>(prices include GST)</small></span>
                  <span>{"\u20B9"}{formatMoney(subtotal)}</span>
                </div>
              </section>

              <section className="pos-summary-section pos-savings-section" aria-label="Savings and discount">
                <h4 className="pos-summary-section-title">Savings &amp; discount</h4>
                {productSavings > 0 && <div className="pos-summary-line discount-line"><span>Product savings vs MRP</span><span>- {"\u20B9"}{formatMoney(productSavings)}</span></div>}
                <div className="pos-discount-row">
                  <label htmlFor="bill-discount">Bill discount</label>
                  <div className="discount-input-wrap"><span>{"\u20B9"}</span><input id="bill-discount" type="number" min="0" max={subtotal} step="0.01" value={discountInput} onChange={(e) => setDiscountInput(e.target.value)} /></div>
                </div>
                {discount > 0 && <div className="pos-summary-line discount-line"><span>Bill discount applied</span><span>- {"\u20B9"}{formatMoney(discount)}</span></div>}
                <div className="pos-total-savings"><span>Total customer savings</span><strong>{"\u20B9"}{formatMoney(totalSavings)}</strong></div>
              </section>

              <section className="pos-summary-section pos-tax-summary-section" aria-label="Tax calculation">
                <h4 className="pos-summary-section-title">Tax calculation</h4>
                <div className="pos-summary-line tax-label"><span>{gstEnabled ? "Taxable value" : "Subtotal after discount"}</span><span>{"\u20B9"}{formatMoney(taxableAmount)}</span></div>
                {gstEnabled && <>
                  {usesIntraState ? <>
                    <div className="pos-summary-line tax-subline"><span>CGST</span><span>{"\u20B9"}{formatMoney(cgst)}</span></div>
                    <div className="pos-summary-line tax-subline"><span>SGST</span><span>{"\u20B9"}{formatMoney(sgst)}</span></div>
                  </> : <div className="pos-summary-line tax-subline"><span>IGST</span><span>{"\u20B9"}{formatMoney(igst)}</span></div>}
                  <div className="pos-summary-line gst-total-line">
                    <span>GST included in total</span>
                    <strong>{"\u20B9"}{formatMoney(taxAmount)}</strong>
                  </div>
                </>}
                {gstEnabled && taxBreakdown.length > 0 && (
                  <details className="tax-breakdown-details">
                    <summary>View rate-wise GST breakdown · {taxBreakdown.length} {taxBreakdown.length === 1 ? "rate" : "rates"}</summary>
                    <div className="tax-breakdown-content">
                      {taxBreakdown.map((t) => <div key={t.rate} className="tax-rate-group">
                        <div className="tax-rate-heading"><span>GST {t.rate}% on {"\u20B9"}{formatMoney(t.taxable)}</span><strong>Tax {"\u20B9"}{formatMoney(t.tax)}</strong></div>
                        {usesIntraState ? <div className="tax-component-grid">
                          {t.cgst > 0 && <div className="pos-summary-line tax-subline"><span>CGST {Number(t.rate / 2).toFixed(2)}%</span><span>{"\u20B9"}{formatMoney(t.cgst)}</span></div>}
                          {t.sgst > 0 && <div className="pos-summary-line tax-subline"><span>SGST {Number(t.rate / 2).toFixed(2)}%</span><span>{"\u20B9"}{formatMoney(t.sgst)}</span></div>}
                        </div> : t.igst > 0 && <div className="pos-summary-line tax-subline"><span>IGST {t.rate}%</span><span>{"\u20B9"}{formatMoney(t.igst)}</span></div>}
                      </div>)}
                    </div>
                  </details>
                )}
              </section>

              <div className="pos-grand-total-row">
                <div>
                  <span className="net-payable-label">Total payable</span>
                  <small>{gstEnabled ? "GST included" : "After discount"}</small>
                </div>
                <strong className="grand-total-amount">{"\u20B9"}{formatMoney(total)}</strong>
              </div>
              {/* Payment Method Selector */}
              <h4 className="pos-summary-section-title payment-section-title">Payment method</h4>
              <div className="payment-method-selector">
                {["Cash", "UPI", "Credit Card", "Debit Card", "Split", "Balance"].map((pm) => (
                  <button
                    key={pm}
                    type="button"
                    className={`pay-tab ${paymentMethod === pm ? "active" : ""} ${pm === "Balance" ? "balance-option" : ""}`}
                    onClick={() => handleSelectPaymentMethod(pm)}
                  >
                    {pm}
                  </button>
                ))}
              </div>
              {paymentMethod === "Balance" && (
                <div className={`pos-balance-payment-note ${selectedCustomer?.email ? "" : "needs-customer"}`} role="status">
                  <label className="balance-due-field">
                    Payment due date and time (IST)
                    <input
                      type="datetime-local"
                      min={businessDateTimeValue()}
                      value={paymentDueAt}
                      onChange={(event) => setPaymentDueAt(event.target.value)}
                      required
                    />
                  </label>
                  {selectedCustomer?.email
                    ? <>Balance of <strong>{"\u20B9"}{formatMoney(total)}</strong> will be linked to <strong>{selectedCustomer.name}</strong>. A reminder email will be sent at the selected time.</>
                    : "Select a saved customer with an email address to schedule a payment reminder."}
                </div>
              )}

              {/* Split payment amounts are saved with their tender modes. */}
              {paymentMethod === "Split" && (
                <div className="pos-split-payment-card">
                  <div className="split-header">
                    <strong>Split Payment Details</strong>
                    <span className={splitDifference === 0 ? "split-balanced" : "split-unbalanced"}>
                      {splitDifference === 0
                        ? "Balanced"
                        : `Remaining: ${"\u20B9"}${formatMoney(splitDifference)}`}
                    </span>
                  </div>
                  <div className="split-inputs-grid">
                    <label>
                      Cash ({"\u20B9"})
                      <input
                        type="number"
                        min="0"
                        step="0.01"
                        placeholder="0.00"
                        value={splitCash}
                        onChange={(e) => setSplitCash(e.target.value)}
                      />
                    </label>
                    <label>
                      UPI ({"\u20B9"})
                      <input
                        type="number"
                        min="0"
                        step="0.01"
                        placeholder="0.00"
                        value={splitOnline}
                        onChange={(e) => setSplitOnline(e.target.value)}
                      />
                    </label>
                    <label>
                      Credit Card ({"\u20B9"})
                      <input
                        type="number"
                        min="0"
                        step="0.01"
                        placeholder="0.00"
                        value={splitCreditCard}
                        onChange={(e) => setSplitCreditCard(e.target.value)}
                      />
                    </label>
                    <label>
                      Debit Card ({"\u20B9"})
                      <input
                        type="number"
                        min="0"
                        step="0.01"
                        placeholder="0.00"
                        value={splitDebitCard}
                        onChange={(e) => setSplitDebitCard(e.target.value)}
                      />
                    </label>
                  </div>
                  <div className="split-summary-bar">
                    <span>Entered: <strong>{"\u20B9"}{formatMoney(splitSum)}</strong></span>
                    <span>Bill total: <strong>{"\u20B9"}{formatMoney(total)}</strong></span>
                  </div>
                </div>
              )}

              
            </div>
            {items.length === 0 && (
              <div className="pos-cashier-ready-guide" role="note">
                <span className="pos-cashier-guide-mark" aria-hidden="true">✦</span>
                <div className="pos-cashier-guide-content">
                  <strong>Ready for your next sale</strong>
                  <p>Add products by search or barcode. Quantity, savings, tax, and payable total update as you build the bill.</p>
                  <div className="pos-cashier-guide-steps">
                    <span>1&nbsp; Add products</span>
                    <span>2&nbsp; Review bill</span>
                    <span>3&nbsp; Complete sale</span>
                  </div>
                </div>
              </div>
            )}
          </div>
        
          <div className="pos-checkout-action-card">
            <button type="button" className="pos-checkout-btn" disabled={items.length === 0 || saving || discountInvalid || missingGstin || buyerGstinInvalid || (paymentMethod === "Split" && splitInvalid) || (paymentMethod === "Balance" && (!selectedCustomer?.email || !paymentDueAt || businessDateTimeInstant(paymentDueAt).getTime() <= Date.now()))} onClick={completeSale}>
              {saving ? <span>Processing transaction...</span> : <><span>{mode === "quick" ? <>Complete Sale &middot; Print Bill</> : selectedCustomer?.email ? <>Complete Sale &middot; Email &amp; Print Bill</> : <>Complete Sale &middot; Print Bill</>}</span><strong><span aria-hidden="true">&#128438;</span><span className="checkout-action-arrow" aria-hidden="true">&rsaquo;</span></strong></>}
            </button>
          </div>
        </aside>
      </div>

      <div className="pos-billing-footer-summary" aria-label="Live bill summary">
        <div><span>Items</span><strong>{items.length}</strong></div>
        <div><span>Total quantity</span><strong>{totalQuantity}</strong></div>
        <div><span>Subtotal</span><strong>{"\u20B9"}{formatMoney(subtotal)}</strong></div>
        <div><span>GST</span><strong>{"\u20B9"}{formatMoney(taxAmount)}</strong></div>
        <div className="footer-grand-total"><span>Grand total</span><strong>{"\u20B9"}{formatMoney(total)}</strong></div>
      </div>

      {/* QUICK CUSTOMER CREATE MODAL */}
      {quickCustomerModal && (
        <div className="pos-modal-overlay">
          <div className="pos-modal-card">
            <div className="modal-header">
              <h3>Create New Customer</h3>
              <button
                type="button"
                className="modal-close-btn"
                onClick={() => setQuickCustomerModal(false)}
              >
                ×
              </button>
            </div>
            <form onSubmit={handleCreateNewCustomer} className="modal-form">
              <p className="modal-tip">
                Enter details to register customer immediately without leaving POS billing.
              </p>
              <label>
                Full Name *
                <input
                  type="text"
                  required
                  placeholder="e.g. Ramesh Patel"
                  value={newCustName}
                  onChange={(e) => setNewCustName(e.target.value)}
                />
              </label>
              <label>
                Mobile Number *
                <input
                  type="tel"
                  required
                  placeholder="10-digit phone number"
                  value={newCustPhone}
                  onChange={(e) => setNewCustPhone(e.target.value)}
                />
              </label>
              <label>
                Email Address (Optional)
                <input
                  type="email"
                  placeholder="customer@email.com"
                  value={newCustEmail}
                  onChange={(e) => setNewCustEmail(e.target.value)}
                />
              </label>
              <div className="modal-actions">
                <button
                  type="button"
                  className="modal-cancel-btn"
                  onClick={() => setQuickCustomerModal(false)}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="modal-submit-btn"
                  disabled={creatingCustomer}
                >
                  {creatingCustomer ? "Registering..." : "Save & Attach"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* COMPLETED INVOICE SLIP (SINGLE PRINT) */}
      {completedInvoice && (
        <div className="pos-modal-overlay">
          <div className="pos-modal-card invoice-slip-card">
            <div className="slip-header">
              <span className="slip-brand-mark" aria-hidden="true">{(shop?.shopName || "SR").trim().split(/\s+/).slice(0, 2).map((part) => part[0]).join("").toUpperCase()}</span>
              <h2>{shop?.shopName || "RETAIL STORE"}</h2>
              <p>{shop?.address || "Point of Sale Invoice"}</p>
              {completedInvoice.gstInvoice && completedInvoice.sellerGstin && (
                <p>GSTIN: {completedInvoice.sellerGstin}</p>
              )}
              <hr />
            </div>

            <div className="slip-meta">
              <div>
                <strong>Invoice:</strong> {completedInvoice.invoiceNumber}
              </div>
              <div>
                <strong>Date:</strong> {formatBusinessDateTime(completedInvoice.soldAt || new Date().toISOString(), { dateStyle: "medium", timeStyle: "short" })}
              </div>
              <div>
                <strong>Customer:</strong> {completedInvoice.buyerName}
              </div>
              {completedInvoice.gstInvoice && completedInvoice.buyerGstin && (
                <div><strong>Buyer GSTIN:</strong> {completedInvoice.buyerGstin}</div>
              )}
              {completedInvoice.customer?.phone && (
                <div>
                  <strong>Phone:</strong> {completedInvoice.customer.phone}
                </div>
              )}
              {(completedInvoice.customer?.email || completedInvoice.customerEmail) && (
                <div>
                  <strong>Email:</strong> {completedInvoice.customer?.email || completedInvoice.customerEmail}
                </div>
              )}
              {(completedInvoice.customer?.address || completedInvoice.customerAddress) && (
                <div>
                  <strong>Address:</strong> {completedInvoice.customer?.address || completedInvoice.customerAddress}
                </div>
              )}
              <div>
                <strong>Payment Mode:</strong> {completedInvoice.paymentMethod}
              </div>
              <div>
                <strong>Payment Status:</strong> {completedInvoice.paymentStatus === "UNPAID" ? "Balance due" : "Paid"}
              </div>
              {Number(completedInvoice.balanceDue) > 0 && (
                <div>
                  <strong>Balance Due:</strong> {"\u20B9"}{formatMoney(completedInvoice.balanceDue)}
                </div>
              )}
              {completedInvoice.paymentDueAt && (
                <div><strong>Reminder scheduled (IST):</strong> {formatBusinessDateTime(completedInvoice.paymentDueAt)}</div>
              )}
              {completedInvoice.splitDetails && (
                <div className="slip-split-breakdown">
                  <span>Cash: {"\u20B9"}{formatMoney(completedInvoice.splitDetails.cash)}</span>
                  <span>UPI: {"\u20B9"}{formatMoney(completedInvoice.splitDetails.online)}</span>
                  <span>Credit Card: {"\u20B9"}{formatMoney(completedInvoice.splitDetails.creditCard)}</span>
                  <span>Debit Card: {"\u20B9"}{formatMoney(completedInvoice.splitDetails.debitCard)}</span>
                </div>
              )}
            </div>

            <table className="slip-table">
              <thead>
                <tr>
                  <th>Item</th>
                  <th>Qty</th>
                  <th>Rate</th>
                  {completedInvoice.gstInvoice && <th>GST</th>}
                  <th style={{ textAlign: "right" }}>Amount</th>
                </tr>
              </thead>
              <tbody>
                {completedInvoice.itemsPurchased.map((it, i) => (
                  <tr key={i}>
                    <td>{it.productName}</td>
                    <td>{it.quantity}</td>
                    <td>{"\u20B9"}{formatMoney(it.unitPrice)}</td>
                    {completedInvoice.gstInvoice && <td>{Number(it.gstRate || 0)}%</td>}
                    <td style={{ textAlign: "right" }}>
                      {"\u20B9"}{formatMoney(it.quantity * it.unitPrice)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            <div className="slip-totals">
              <div>
                <span>Subtotal:</span>
                <span>{"\u20B9"}{formatMoney(completedInvoice.subtotal)}</span>
              </div>
              {Number(completedInvoice.discountAmount) > 0 && (
                <div>
                  <span>Discount:</span>
                  <span>-{"\u20B9"}{formatMoney(completedInvoice.discountAmount)}</span>
                </div>
              )}
              {Number(completedInvoice.cgst) > 0 && (
                <div>
                  <span>CGST:</span>
                  <span>{"\u20B9"}{formatMoney(completedInvoice.cgst)}</span>
                </div>
              )}
              {Number(completedInvoice.sgst) > 0 && (
                <div>
                  <span>SGST:</span>
                  <span>{"\u20B9"}{formatMoney(completedInvoice.sgst)}</span>
                </div>
              )}
              {Number(completedInvoice.igst) > 0 && (
                <div>
                  <span>IGST:</span>
                  <span>{"\u20B9"}{formatMoney(completedInvoice.igst)}</span>
                </div>
              )}
              <div className="slip-grand">
                <strong>GRAND TOTAL:</strong>
                <strong>{"\u20B9"}{formatMoney(completedInvoice.total)}</strong>
              </div>
            </div>

            <div className="slip-footer">
              <p className={`invoice-email-status ${completedInvoice.emailSent ? "sent" : "not-sent"}`} role="status">{completedInvoice.emailMessage}</p>
              {!completedInvoice.emailSent && completedInvoice.customerId && (
                <button type="button" className="slip-email-retry-btn" onClick={retryInvoiceEmail} disabled={emailRetrying}>
                  {emailRetrying ? "Sending invoice..." : "Send invoice email"}
                </button>
              )}
              <p>Thank you for shopping with us!</p>
              <div className="slip-actions">
                <button
                  type="button"
                  className="slip-print-btn"
                  onClick={() => window.print()}
                >
                  Print bill
                </button>
                <button
                  type="button"
                  className="slip-done-btn"
                  onClick={() => setCompletedInvoice(null)}
                >
                  Close receipt
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Barcode Camera Scanner */}
      {cameraScanOpen && (
        <BarcodeScanner
          onClose={() => { setCameraScanOpen(false); setError(""); setUnknownBarcode(""); }}
          onDetected={handleScannedBarcode}
          continuous
        />
      )}
    </div>
  );
}

