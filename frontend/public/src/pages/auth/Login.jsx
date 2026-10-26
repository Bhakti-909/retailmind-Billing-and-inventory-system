import { useContext, useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { AuthContext } from "../../context/AuthContext";
import "../../styles/auth.css";
import "./LoginPage.css";

export default function Login() {
  const navigate = useNavigate();
  const location = useLocation();
  const { login, isAuthenticated, loading: authLoading } = useContext(AuthContext);

  const [username, setUsername] = useState(location.state?.email || "");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState(location.state?.notice || "");

  // Track whether a login was just completed so we can redirect after state settles
  const loginSucceeded = useRef(false);

  // ─── FIX 1 & 3: Redirect inside useEffect, NOT during render ────────────
  // This runs AFTER React has committed all state updates (including setUser
  // inside AuthContext.login), so isAuthenticated is always up-to-date here.
  useEffect(() => {
    if (isAuthenticated && !authLoading) {
      navigate("/dashboard", { replace: true });
    }
  }, [isAuthenticated, authLoading, navigate]);

  // ─── FIX 2: While auth check runs (existing token being validated), ──────
  // show a minimal loading state so the page does not blink/flash
  if (authLoading) {
    return (
      <div
        style={{
          minHeight: "100vh",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          background: "#fff",
          flexDirection: "column",
          gap: "14px",
        }}
      >
        <div
          style={{
            width: "36px",
            height: "36px",
            border: "3px solid #e7eeec",
            borderTop: "3px solid #0f766e",
            borderRadius: "50%",
            animation: "ls 0.75s linear infinite",
          }}
        />
        <style>{`@keyframes ls{to{transform:rotate(360deg)}}`}</style>
        <p
          style={{
            margin: 0,
            color: "#667875",
            fontSize: "13px",
            fontFamily: "Inter, sans-serif",
          }}
        >
          Checking your session…
        </p>
      </div>
    );
  }

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (submitting) return;

    setError("");
    setSubmitting(true);
    loginSucceeded.current = false;

    try {
      // AuthContext.login() sets localStorage + calls setUser(userData)
      // After this await, React will batch-flush the state update on next render
      await login(username.trim(), password);
      loginSucceeded.current = true;
      // Do NOT call navigate() here — the useEffect above handles it
      // after isAuthenticated becomes true in the next render cycle
    } catch (err) {
      setError(
        err?.response?.data?.message ||
          err?.message ||
          "Login failed. Please check your User ID and password."
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="login-page">
      <section className="login-story" aria-label="Smart Retail overview">
        <div className="login-category-collage" role="img" aria-label="Retail categories: grocery, pharmacy, apparel, and general merchandise">
          <img src="https://images.unsplash.com/photo-1698466632366-09fa1d925de6?auto=format&fit=crop&w=1200&q=85" alt="" />
          <img src="https://images.unsplash.com/photo-1696861286643-341a8d7a79e9?auto=format&fit=crop&w=1200&q=85" alt="" />
          <img src="https://images.unsplash.com/photo-1441984904996-e0b6ba687e04?auto=format&fit=crop&w=1200&q=85" alt="" />
          <img src="https://images.unsplash.com/photo-1681625731358-54ccba21a786?auto=format&fit=crop&w=1200&q=85" alt="" />
        </div>
        <div className="login-brand">
          <div className="login-brand-mark" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round"><path d="M3 4h2l2.1 10.1a2 2 0 0 0 2 1.6h8.8a2 2 0 0 0 1.9-1.4L22 8H7" /><circle cx="10" cy="20" r="1" /><circle cx="18" cy="20" r="1" /></svg></div>
          <div>
            <strong>Smart Retail</strong>
            <span>Retail management system</span>
          </div>
          <span className="login-secure">
            <i />
            Secure workspace
          </span>
        </div>

        <div className="login-story-content">
          <p className="login-story-eyebrow">
            MADE FOR THE EVERYDAY WORK OF RETAIL
          </p>
          <h1>
            Keep your shop
            <br />
            <span>moving forward.</span>
          </h1>
          <p className="login-story-intro">
            The essentials for running your business, together in one place.
          </p>
          <div className="login-category-tags" role="group" aria-label="Supported retail categories">
            <span>Grocery</span><span>Pharmacy</span><span>Apparel</span><span>General retail</span>
          </div>
          <div className="login-capabilities">
            <div>
              <span>01</span>
              <p>
                <strong>Inventory</strong>
                <small>Stock levels and expiry dates</small>
              </p>
            </div>
            <div>
              <span>02</span>
              <p>
                <strong>Billing</strong>
                <small>Sales and daily totals</small>
              </p>
            </div>
            <div>
              <span>03</span>
              <p>
                <strong>Customers</strong>
                <small>Contact details in one place</small>
              </p>
            </div>
            <div>
              <span>04</span>
              <p>
                <strong>Reports</strong>
                <small>A clearer view of your shop</small>
              </p>
            </div>
          </div>
        </div>

        <p className="login-story-footer">
          A BETTER VIEW OF YOUR DAY-TO-DAY BUSINESS
        </p>
      </section>

      <section className="login-panel" aria-labelledby="login-heading">
        <div className="login-form-wrap">
          <p className="login-form-eyebrow"></p>
          <div className="login-form-heading">
            <h2 id="login-heading">Welcome back</h2>
            <p>Sign in to manage your shop.</p>
          </div>

          {notice && (
            <div className="auth-notice" role="status">
              {notice}
            </div>
          )}

          {error && (
            <div className="auth-error" role="alert">
              {error}
            </div>
          )}

          <form className="login-form" onSubmit={handleSubmit} noValidate>
            <div className="form-group">
              <label htmlFor="username">User ID or email</label>
              <input
                id="username"
                type="text"
                placeholder="Enter your User ID or registered email"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                autoComplete="username"
                required
                disabled={submitting}
              />
            </div>

            <div className="form-group">
              <div className="login-password-label-row">
                <label htmlFor="password">Password</label>
                <button type="button" className="forgot-password-link" onClick={() => navigate("/forgot-password")}>Forgot password?</button>
              </div>
              <input
                id="password"
                type="password"
                placeholder="Enter your password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
                required
                disabled={submitting}
              />
            </div>

            <button
              type="submit"
              disabled={submitting}
              className="primary-button"
            >
              {submitting ? "Signing in…" : "Sign in to your account"}
            </button>
          </form>

          <div className="login-register-prompt">
            <span>New to Smart Retail?</span>
            <button
              type="button"
              className="link-button"
              onClick={() => navigate("/register")}
            >
              Create an account <span aria-hidden="true">→</span>
            </button>
          </div>
        </div>

        <p className="login-panel-footer">
          SMART RETAIL <span>·</span> BUSINESS MANAGEMENT
        </p>
      </section>
    </div>
  );
}
