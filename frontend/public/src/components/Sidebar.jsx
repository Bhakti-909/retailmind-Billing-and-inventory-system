import { useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../hooks/useAuth";
import "../styles/sidebar.css";

const iconContent = {
  dashboard: <><rect x="3" y="3" width="8" height="8" rx="1.5" /><rect x="13" y="3" width="8" height="5" rx="1.5" /><rect x="13" y="10" width="8" height="11" rx="1.5" /><rect x="3" y="13" width="8" height="8" rx="1.5" /></>,
  billing: <><path d="M3 4h2l2.1 10.1a2 2 0 0 0 2 1.6h8.8a2 2 0 0 0 1.9-1.4L22 8H7" /><circle cx="10" cy="20" r="1" /><circle cx="18" cy="20" r="1" /></>,
  payments: <><rect x="3" y="5" width="18" height="14" rx="2" /><path d="M3 10h18M7 15h4" /><path d="m16 14 2 2 3-4" /></>,
  products: <><path d="m12 3 8 4.5-8 4.5-8-4.5L12 3Z" /><path d="m4 7.5v9L12 21l8-4.5v-9M12 12v9" /><path d="m8 5.2 8 4.6" /></>,
  inventory: <><path d="m12 3 8 4.5v9L12 21l-8-4.5v-9L12 3Z" /><path d="m4.5 7.8 7.5 4.3 7.5-4.3M12 12.2V21" /></>,
  customers: <><circle cx="9" cy="8" r="3.5" /><path d="M2.5 20v-1a6.5 6.5 0 0 1 13 0v1M16 5a3.5 3.5 0 0 1 0 6.8M18 14a5 5 0 0 1 3.5 4.8V20" /></>,
  suppliers: <><path d="M3 20V8l9-5 9 5v12H3Z" /><path d="M8 20v-6h8v6M8 9h.01M12 9h.01M16 9h.01" /></>,
  reports: <><path d="M4 20V10M10 20V4M16 20v-7M22 20H2" /><path d="m3 7 6-3 6 5 6-4" /></>,
  settings: <><circle cx="12" cy="12" r="3" /><path d="m19.4 15 .1.1 1.4 1.1-1.4 2.4-1.7-.6a8 8 0 0 1-1.6.9l-.3 1.8h-2.8l-.3-1.8a8 8 0 0 1-1.6-.9l-1.7.6-1.4-2.4 1.4-1.1a7 7 0 0 1 0-1.9l-1.4-1.1 1.4-2.4 1.7.6a8 8 0 0 1 1.6-.9l.3-1.8h2.8l.3 1.8a8 8 0 0 1 1.6.9l1.7-.6 1.4 2.4-1.4 1.1a7 7 0 0 1-.1 1.8Z" transform="translate(-1 -1)" /></>,
  nova: <><path d="m12 2 1.8 7.2L21 12l-7.2 2.8L12 22l-2.8-7.2L2 12l7.2-2.8L12 2Z" /><path d="m19 14 1.2 3.8L24 19l-3.8 1.2L19 24l-1.2-3.8L14 19l3.8-1.2L19 14Z" transform="translate(-1 -1) scale(.9)" /></>,
  profile: <><circle cx="12" cy="8" r="3.5" /><path d="M5 21v-1a7 7 0 0 1 14 0v1" /></>,
  logout: <><path d="M10 17l5-5-5-5M15 12H3" /><path d="M12 3h7a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-7" /></>,
};

function SidebarIcon({ name }) {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{iconContent[name]}</svg>;
}

const menuItems = [
  { name: "Dashboard", path: "/dashboard", icon: "dashboard" },
  { name: "Billing / POS", path: "/billing", icon: "billing" },
  { name: "Inventory", path: "/inventory", icon: "inventory" },
  { name: "Customers", path: "/customers", icon: "customers" },
  { name: "Suppliers", path: "/suppliers", icon: "suppliers" },
  { name: "Purchases", path: "/suppliers", icon: "billing" },
  { name: "Sales & Reports", path: "/reports", icon: "reports" },
  { name: "Profit & Loss", path: "/reports", icon: "reports" },
  { name: "Cash & Payments", path: "/payments", icon: "payments" },
  { name: "Expenses", path: "/reports", icon: "reports" },
  { name: "AI Insights", path: "/ai-chatbot", icon: "nova" },
  { name: "Settings", path: "/profile", icon: "settings" },
];

export default function Sidebar({ collapsed, setCollapsed }) {
  const navigate = useNavigate();
  const location = useLocation();
  const { logout, user } = useAuth();
  const isActive = (path) => path === "/dashboard"
    ? location.pathname === path
    : location.pathname.startsWith(path);
  const activeItem = menuItems.find((item) => isActive(item.path));

  const openItem = (item) => {
    if (item.path === "/ai-chatbot") {
      window.dispatchEvent(new Event("open-ai-agent"));
      return;
    }
    navigate(item.path);
  };

  const handleLogout = () => {
    logout();
    localStorage.removeItem("token");
    localStorage.removeItem("user");
    navigate("/login");
  };

  const renderItem = (item) => (
    <button
      key={item.name}
      type="button"
      className={`sidebar-item ${activeItem === item ? "active" : ""}`}
      onClick={() => openItem(item)}
      title={collapsed ? item.name : undefined}
      aria-current={activeItem === item ? "page" : undefined}
    >
      <span className="sidebar-icon"><SidebarIcon name={item.icon} /></span>
      {!collapsed && <span className="sidebar-name">{item.name}</span>}
      {!collapsed && activeItem === item && <span className="sidebar-active-mark" aria-hidden="true" />}
    </button>
  );

  return (
    <aside id="primary-navigation" className={`sidebar ${collapsed ? "collapsed" : ""}`}>
      {collapsed ? (
        <button type="button" className="sidebar-logo sidebar-logo-button" onClick={() => setCollapsed(false)} aria-label="Open navigation menu" title="Open navigation menu">
          <span className="logo-box" aria-hidden="true"><SidebarIcon name="billing" /></span>
        </button>
      ) : (
        <div className="sidebar-logo">
          <div className="logo-box" aria-hidden="true"><SidebarIcon name="billing" /></div>
          <div className="logo-text"><strong>RetailPro</strong><span>Manage · Sell · Grow</span></div>
          <button type="button" className="sidebar-collapse-toggle" onClick={() => setCollapsed(true)} aria-label="Collapse navigation to icons" title="Collapse navigation">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m15 18-6-6 6-6" /></svg>
          </button>
        </div>
      )}

      <nav className="sidebar-menu" aria-label="Main navigation">
        {menuItems.map(renderItem)}
      </nav>

      <div className="sidebar-bottom">
        {!collapsed && <div className="sidebar-stock-card">
          <span className="sidebar-stock-icon" aria-hidden="true"><SidebarIcon name="inventory" /></span>
          <strong>Smart Stock Management</strong>
          <p>Track, manage &amp; grow your business</p>
          <button type="button" onClick={() => navigate("/reports")}>View Reports <span aria-hidden="true">→</span></button>
        </div>}
        <button type="button" className="sidebar-item logout-item" onClick={handleLogout} title={collapsed ? "Sign out" : undefined}>
          <span className="sidebar-icon"><SidebarIcon name="logout" /></span>
          {!collapsed && <span className="sidebar-name">Sign out</span>}
        </button>
      </div>
    </aside>
  );
}
