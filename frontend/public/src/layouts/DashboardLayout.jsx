import { useContext, useEffect, useMemo, useState } from "react";
import { Outlet, useLocation, useNavigate } from "react-router-dom";
import Sidebar from "../components/Sidebar";
import { AuthContext } from "../context/AuthContext";
import { getProducts } from "../services/inventoryApi";
import { getCustomers } from "../services/customerApi";
import { getSuppliers } from "../services/supplierApi";
import AIAgentWidget from "../components/AIAgentWidget";

export default function DashboardLayout() {
  const [collapsed, setCollapsed] = useState(() => window.matchMedia("(max-width: 800px)").matches);
  const [profileOpen, setProfileOpen] = useState(false);
  const [search, setSearch] = useState("");
  const [searchResults, setSearchResults] = useState([]);
  const [searching, setSearching] = useState(false);

  const location = useLocation();
  const navigate = useNavigate();
  const { user } = useContext(AuthContext);
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const [notificationProducts, setNotificationProducts] = useState([]);
  const notificationStorageKey = `dismissedInventoryNotifications:${user?.businessId || "anonymous"}`;
  const [dismissedNotifications, setDismissedNotifications] = useState([]);

  useEffect(() => {
    const viewport = window.matchMedia("(max-width: 800px)");
    const updateSidebarForViewport = (event) => setCollapsed(event.matches);
    viewport.addEventListener("change", updateSidebarForViewport);
    return () => viewport.removeEventListener("change", updateSidebarForViewport);
  }, []);

  useEffect(() => {
    try {
      setDismissedNotifications(JSON.parse(localStorage.getItem(notificationStorageKey) || "[]"));
    } catch {
      setDismissedNotifications([]);
    }
  }, [notificationStorageKey]);

  const dismissNotification = (notificationId) => {
    setDismissedNotifications((current) => {
      const updated = [...new Set([...current, notificationId])];
      localStorage.setItem(notificationStorageKey, JSON.stringify(updated));
      return updated;
    });
  };

  const dismissAllNotifications = () => {
    const updated = [...new Set([...dismissedNotifications, ...notifications.map((notification) => notification.id)])];
    localStorage.setItem(notificationStorageKey, JSON.stringify(updated));
    setDismissedNotifications(updated);
  };

  useEffect(() => {
    const loadNotifications = async () => {
      try { setNotificationProducts(await getProducts()); } catch { setNotificationProducts([]); }
    };
    loadNotifications();
    const intervalId = window.setInterval(loadNotifications, 30000);
    return () => window.clearInterval(intervalId);
  }, []);

  useEffect(() => {
    const term = search.trim().toLowerCase();
    if (term.length < 2) {
      setSearchResults([]);
      setSearching(false);
      return undefined;
    }
    const timer = window.setTimeout(async () => {
      setSearching(true);
      const [products, customers, suppliers] = await Promise.allSettled([getProducts(), getCustomers(), getSuppliers()]);
      const results = [];
      if (products.status === "fulfilled") products.value.filter((item) => `${item.name} ${item.sku} ${item.category || ""}`.toLowerCase().includes(term)).slice(0, 4).forEach((item) => results.push({ id: `product-${item.id}`, type: "Product", title: item.name, detail: `${item.sku} · ${item.quantity} ${item.unit} in stock`, path: "/inventory" }));
      if (customers.status === "fulfilled") customers.value.filter((item) => `${item.name} ${item.phone} ${item.email || ""}`.toLowerCase().includes(term)).slice(0, 4).forEach((item) => results.push({ id: `customer-${item.id}`, type: "Customer", title: item.name, detail: item.phone, path: "/customers" }));
      if (suppliers.status === "fulfilled") suppliers.value.filter((item) => `${item.name} ${item.contactPerson || ""} ${item.phone}`.toLowerCase().includes(term)).slice(0, 4).forEach((item) => results.push({ id: `supplier-${item.id}`, type: "Supplier", title: item.name, detail: item.contactPerson || item.phone, path: "/suppliers" }));
      setSearchResults(results);
      setSearching(false);
    }, 250);
    return () => window.clearTimeout(timer);
  }, [search]);

  const openSearchResult = (path) => { setSearch(""); setSearchResults([]); navigate(path); };

  const notifications = useMemo(() => {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const soon = new Date(today);
    soon.setDate(soon.getDate() + 30);
    return notificationProducts.flatMap((product) => {
      const alerts = [];
      if (product.lowStock) alerts.push({ id: `stock-${product.id}`, type: "Low stock", text: `${product.name} has ${product.quantity} ${product.unit} left.` });
      if (product.expiryDate) {
        const expiry = new Date(`${product.expiryDate}T00:00:00`);
        if (expiry < today) alerts.push({ id: `expired-${product.id}`, type: "Expired", text: `${product.name} expired on ${product.expiryDate}.` });
        else if (expiry <= soon) alerts.push({ id: `expiry-${product.id}`, type: "Expiry soon", text: `${product.name} expires on ${product.expiryDate}.` });
      }
      return alerts;
    }).filter((notification) => !dismissedNotifications.includes(notification.id));
  }, [notificationProducts, dismissedNotifications]);




  const isBillingPage = location.pathname.startsWith("/billing");
  const initials = (user?.ownerName || user?.username || "SR").split(" ").map((part) => part[0]).join("").slice(0, 2).toUpperCase();
     
  return (
    <div className={`app-shell ${collapsed ? "sidebar-collapsed" : ""} ${isBillingPage ? "billing-pos-shell" : ""}`}>
      <Sidebar
        collapsed={collapsed}
        setCollapsed={setCollapsed}
      />
      {!collapsed && <button className="sidebar-backdrop" onClick={() => setCollapsed(true)} aria-label="Close navigation menu" />}

      <main className="main-area">

        {!isBillingPage && <header className="topbar">

  <div className="shop-context"><span className="shop-mark">▤</span><span><strong>{user?.shopName || user?.businessName || "Your Store"}</strong><small>{user?.city || "Retail workspace"}</small></span></div>
  <div className="global-search-area"><label className="global-search"><span>⌕</span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search products, customers or suppliers" aria-label="Search your business records" />{search && <button type="button" onClick={() => setSearch("")} aria-label="Clear search">×</button>}</label>{search.trim().length >= 2 && <div className="global-search-results">{searching ? <p>Searching your business records…</p> : searchResults.length ? searchResults.map((result) => <button key={result.id} onClick={() => openSearchResult(result.path)}><span className={`search-result-icon ${result.type.toLowerCase()}`}>{result.type[0]}</span><span><small>{result.type}</small><strong>{result.title}</strong><em>{result.detail}</em></span><i>›</i></button>) : <p>No matching products, customers, or suppliers.</p>}</div>}</div>

  <div className="notification-area">
    <button className="notification-button" onClick={() => setNotificationsOpen((open) => !open)} aria-label="Show notifications" aria-expanded={notificationsOpen}>
      🔔
      {notifications.length > 0 && <span className="notification-count">{notifications.length > 9 ? "9+" : notifications.length}</span>}
    </button>
    {notificationsOpen && <div className="notification-popover"><div className="notification-heading"><strong>Notifications</strong><span>{notifications.length}</span></div>{notifications.length ? <><button className="clear-notifications" onClick={dismissAllNotifications}>Clear all</button><div className="notification-list">{notifications.map((notification) => <div key={notification.id} className="notification-entry"><div><b>{notification.type}</b><span>{notification.text}</span></div><button className="dismiss-notification" onClick={() => dismissNotification(notification.id)} aria-label={`Dismiss ${notification.type} notification`}>×</button></div>)}</div></> : <p className="notification-empty">No stock or expiry alerts.</p>}</div>}
  </div>

  {/* PROFILE */}
  <div className="profile-area"><button className="top-profile" onClick={() => setProfileOpen((open) => !open)} aria-expanded={profileOpen}>
    <div className="avatar">
      {initials}
    </div>

    <div className="top-profile-info">
      <strong>{user?.ownerName || user?.username || "Account"}</strong>
      <span>{user?.role || "Signed in"}</span>
    </div>

    <span className="profile-arrow">
      {profileOpen ? "⌃" : "⌄"}
    </span>
  </button>
  {profileOpen && <div className="profile-menu"><div className="profile-menu-summary"><strong>{user?.shopName || "Your business"}</strong><span>{user?.username || "Signed in"}</span></div><button onClick={() => { setProfileOpen(false); navigate("/profile"); }}>View profile</button><button onClick={() => { setProfileOpen(false); navigate("/inventory"); }}>Manage inventory</button></div>}</div>

</header>}

        <section className="page-content">
        <Outlet />
        </section>

        <AIAgentWidget />

      </main>
    </div>
  );
}
