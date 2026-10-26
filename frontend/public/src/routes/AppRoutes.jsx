import {
  Routes,
  Route,
  Navigate,
} from "react-router-dom";

import Login from "../pages/auth/Login";
import ForgotPassword from "../pages/auth/ForgotPassword";
import Register from "../pages/auth/Register";
import VerifyOtp from "../pages/auth/VerifyOtp";

import Dashboard from "../pages/dashboard/Dashboard";
import Profile from "../pages/profile/Profile";
import DashboardLayout from "../layouts/DashboardLayout";
import Inventory from "../pages/inventory/Inventory";
import Customers from "../pages/customers/Customers";
import Suppliers from "../pages/suppliers/Suppliers";
import AIAssistant from "../pages/ai/AIAssistant";
import Billing from "../pages/billing/BillingPage";
import CashPayments from "../pages/billing/CashPayments";
import Reports from "../pages/reports/Reports";

import { useContext } from "react";
import { AuthContext } from "../context/AuthContext";


function ProtectedRoute({ children }) {
  const { isAuthenticated, loading } = useContext(AuthContext);

  if (loading) {
    return (
      <div style={{
        minHeight: "100vh",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        background: "var(--app-canvas, #f3f7f6)",
        gap: "16px",
      }}>
        <div style={{
          width: "48px",
          height: "48px",
          border: "3px solid var(--app-border, #dce6e3)",
          borderTop: "3px solid var(--app-brand, #0f766e)",
          borderRadius: "50%",
          animation: "spin 0.8s linear infinite",
        }} />
        <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
        <div style={{ display: "grid", gap: "5px", textAlign: "center", fontFamily: "Inter, sans-serif" }}>
          <p style={{ color: "var(--app-ink, #172a2a)", fontSize: "15px", fontWeight: 650 }}>Preparing your workspace</p>
          <small style={{ color: "var(--app-muted, #667875)", fontSize: "12px" }}>Checking your session…</small>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return children;
}


export default function AppRoutes() {
  return (
    <Routes>

      <Route
        path="/"
        element={<Navigate to="/login" replace />}
      />

      <Route
        path="/login"
        element={<Login />}
      />

      <Route
        path="/forgot-password"
        element={<ForgotPassword />}
      />

      <Route
        path="/register"
        element={<Register />}
      />

      <Route
        path="/verify-otp"
        element={<VerifyOtp />}
      />

      {/* PROTECTED DASHBOARD */}
      <Route
  element={
    <ProtectedRoute>
      <DashboardLayout />
    </ProtectedRoute>
  }
>
  <Route
    path="/dashboard"
    element={<Dashboard />}
  />

  <Route
    path="/profile"
    element={<Profile />}
  />

  <Route
    path="/inventory"
    element={<Inventory />}
  />

  <Route
    path="/products"
    element={<Inventory />}
  />

  <Route
    path="/customers"
    element={<Customers />}
  />

  <Route
    path="/suppliers"
    element={<Suppliers />}
  />

  <Route
    path="/billing"
    element={<Billing />}
  />

  <Route
    path="/payments"
    element={<CashPayments />}
  />

  <Route
    path="/reports"
    element={<Reports />}
  />

  <Route
    path="/ai-chatbot"
    element={<AIAssistant />}
  />
</Route>

      <Route
        path="*"
        element={<Navigate to="/login" replace />}
      />

    </Routes>
  );
}
