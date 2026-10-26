import {
  Routes,
  Route,
  Navigate,
} from "react-router-dom";

import Login from "../pages/auth/Login";
import Register from "../pages/auth/Register";
import VerifyOtp from "../pages/auth/VerifyOtp";

import Dashboard from "../pages/dashboard/Dashboard";
import Profile from "../pages/profile/Profile";

import DashboardLayout from "../layouts/DashboardLayout";
import ModulePage from "../pages/ModulePage";

export default function AppRoutes() {
  return (
    <Routes>

      {/* Auth */}
      <Route
        path="/"
        element={
          <Navigate
            to="/login"
            replace
          />
        }
      />

      <Route
        path="/login"
        element={<Login />}
      />

      <Route
        path="/register"
        element={<Register />}
      />

      <Route
        path="/verify-otp"
        element={<VerifyOtp />}
      />

      {/* Dashboard Application */}
      <Route
        element={<DashboardLayout />}
      >

        <Route
          path="/dashboard"
          element={<Dashboard />}
        />

        <Route
          path="/billing"
          element={
            <ModulePage
              title="Billing"
              description="Create bills, invoices and manage customer payments."
            />
          }
        />

        <Route
          path="/inventory"
          element={
            <ModulePage
              title="Inventory Management"
              description="Manage products, stock, expiry and low-stock items."
            />
          }
        />

        <Route
          path="/customers"
          element={
            <ModulePage
              title="Customer Management"
              description="Manage customers and their purchase history."
            />
          }
        />

        <Route
          path="/suppliers"
          element={
            <ModulePage
              title="Suppliers"
              description="Manage suppliers and purchase records."
            />
          }
        />

        <Route
          path="/reports"
          element={
            <ModulePage
              title="Reports"
              description="View sales, GST, customer and inventory reports."
            />
          }
        />

        <Route
          path="/ai-assistant"
          element={
            <ModulePage
              title="AI Assistant"
              description="Get intelligent insights and assistance for your business."
            />
          }
        />

        <Route
          path="/profile"
          element={<Profile />}
        />

      </Route>

      {/* Unknown */}
      <Route
        path="*"
        element={
          <Navigate
            to="/dashboard"
            replace
          />
        }
      />

    </Routes>
  );
}