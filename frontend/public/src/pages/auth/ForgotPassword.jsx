import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useEffect } from "react";
import { useAuth } from "../../hooks/useAuth";
import { requestPasswordReset, resetPassword, verifyPasswordResetOtp } from "../../services/authApi";
import "../../styles/auth.css";
import "./LoginPage.css";

function getRecoveryError(error, fallback) {
  const serverMessage = error?.response?.data?.message;
  if (serverMessage) return serverMessage;

  if (!error?.response) {
    return "Cannot reach the authentication service. Make sure it is running on port 8081, then try again.";
  }
  if (error.response.status === 404) {
    return "No active account exists with this email. Check the address and try again.";
  }
  if (error.response.status === 403) {
    return "The authentication service is blocking password recovery. Rebuild and restart the auth service, then try again.";
  }
  if (error.response.status === 503) {
    return "The email service could not send the code. Check the mail sender settings and try again.";
  }

  return fallback;
}

export default function ForgotPassword() {
  const navigate = useNavigate();
  const { logout } = useAuth();
  const [step, setStep] = useState("email");
  const [email, setEmail] = useState("");
  const [otp, setOtp] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [secondsRemaining, setSecondsRemaining] = useState(0);
  const [attemptsRemaining, setAttemptsRemaining] = useState(5);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [complete, setComplete] = useState(false);

  useEffect(() => {
    if (complete || secondsRemaining <= 0) return undefined;
    const timer = window.setTimeout(() => setSecondsRemaining((value) => Math.max(0, value - 1)), 1000);
    return () => window.clearTimeout(timer);
  }, [complete, secondsRemaining]);

  const expiryTime = `${String(Math.floor(secondsRemaining / 60)).padStart(2, "0")}:${String(secondsRemaining % 60).padStart(2, "0")}`;

  const sendCode = async (event) => {
    event.preventDefault();
    if (submitting) return;
    setError("");
    setNotice("");
    setSubmitting(true);
    try {
      const result = await requestPasswordReset({ email: email.trim() });
      setStep("otp");
      setOtp("");
      setAttemptsRemaining(5);
      setSecondsRemaining(5 * 60);
      setNotice(result?.message || "A six-digit code was sent to your email. It expires in 5 minutes.");
    } catch (err) {
      setError(getRecoveryError(err, "We could not send the code. Check the email address and try again."));
    } finally {
      setSubmitting(false);
    }
  };

  const verifyCode = async (event) => {
    event.preventDefault();
    if (submitting) return;
    setError("");
    setNotice("");
    if (otp.length !== 6) {
      setError("Enter the six-digit code from your email.");
      return;
    }
    if (secondsRemaining <= 0) {
      setError("This code has expired. Request a new code to continue.");
      return;
    }
    if (attemptsRemaining <= 0) {
      setError("The verification attempt limit was reached. Request a new code to continue.");
      return;
    }
    setSubmitting(true);
    try {
      const result = await verifyPasswordResetOtp({ email: email.trim(), otp });
      setStep("password");
      setNotice(result?.message || "Email code verified. Choose a new password.");
    } catch (err) {
      if (Number.isInteger(err?.response?.data?.attemptsRemaining)) {
        setAttemptsRemaining(err.response.data.attemptsRemaining);
      }
      setError(getRecoveryError(err, "The code is invalid or expired. Request a new code and try again."));
    } finally {
      setSubmitting(false);
    }
  };

  const submitNewPassword = async (event) => {
    event.preventDefault();
    if (submitting) return;
    setError("");
    setNotice("");
    if (newPassword.length < 8) {
      setError("Use at least 8 characters for your new password.");
      return;
    }
    if (newPassword !== confirmPassword) {
      setError("The passwords do not match.");
      return;
    }

    setSubmitting(true);
    try {
      const result = await resetPassword({ email: email.trim(), otp, newPassword });
      // A password reset ends this browser's authenticated session too.
      // The user must authenticate again with the new password.
      logout();
      setNotice(result?.message || "Your password has been updated. You can now sign in.");
      setComplete(true);
    } catch (err) {
      setError(getRecoveryError(err, "The code is invalid or expired. Request a new code and try again."));
    } finally {
      setSubmitting(false);
    }
  };

  const resendCode = async () => {
    if (submitting) return;
    setError("");
    setNotice("");
    setSubmitting(true);
    try {
      const result = await requestPasswordReset({ email: email.trim() });
      setStep("otp");
      setNotice(result?.message || "A new code was sent to your email. It expires in 5 minutes.");
      setOtp("");
      setAttemptsRemaining(5);
      setSecondsRemaining(5 * 60);
    } catch (err) {
      setError(getRecoveryError(err, "We could not send a new code. Try again shortly."));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="login-page forgot-password-page">
      <section className="login-story" aria-label="Smart Retail account security">
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
          <span className="login-secure"><i />Secure workspace</span>
        </div>
        <div className="login-story-content">
          <p className="login-story-eyebrow">ACCOUNT ACCESS</p>
          <h1>Back to running<br /><span>your shop.</span></h1>
          <p className="login-story-intro">Verify your email, choose a new password, and return to your retail workspace.</p>
          <div className="login-category-tags" role="group" aria-label="Supported retail categories">
            <span>Grocery</span><span>Pharmacy</span><span>Apparel</span><span>General retail</span>
          </div>
          <div className="login-capabilities">
            <div><span>01</span><p><strong>Email code</strong><small>Sent to your registered address</small></p></div>
            <div><span>02</span><p><strong>One time use</strong><small>Expires after 5 minutes</small></p></div>
          </div>
        </div>
        <p className="login-story-footer">SECURE ACCESS TO YOUR BUSINESS</p>
      </section>

      <section className="login-panel" aria-labelledby="recovery-heading">
        <div className="login-form-wrap">
          <p className="login-form-eyebrow"></p>
          <div className="login-form-heading">
            <h2 id="recovery-heading">{complete ? "Password updated" : step === "email" ? "Forgot your password?" : step === "otp" ? "Verify your email" : "Set a new password"}</h2>
            <p>{complete ? "Your account is ready. Sign in with your new password." : step === "email" ? "Enter your registered email address. We will check your account before sending a code." : step === "otp" ? `Enter the six-digit code sent to ${email.trim()}.` : "Your email is verified. Choose a new password for your account."}</p>
          </div>

          {error && <div className="auth-error" role="alert">{error}</div>}
          {notice && <div className="recovery-notice" role="status">{notice}</div>}

          {complete ? (
            <button type="button" className="primary-button recovery-primary" onClick={() => navigate("/login", { replace: true, state: { email } })}>Return to sign in</button>
          ) : step === "email" ? (
            <form className="login-form" onSubmit={sendCode}>
              <div className="form-group">
                <label htmlFor="recovery-email">Registered email address</label>
                <input id="recovery-email" type="email" placeholder="you@example.com" value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="email" required disabled={submitting} />
              </div>
              <button type="submit" className="primary-button" disabled={submitting}>{submitting ? "Sending code..." : "Send verification code"}</button>
            </form>
          ) : step === "otp" ? (
            <form className="login-form" onSubmit={verifyCode}>
              <div className="form-group">
                <label htmlFor="recovery-otp">Six-digit email code</label>
                <input id="recovery-otp" type="text" inputMode="numeric" pattern="[0-9]{6}" maxLength={6} placeholder="000000" value={otp} onChange={(event) => setOtp(event.target.value.replace(/\D/g, "").slice(0, 6))} autoComplete="one-time-code" required disabled={submitting} />
              </div>
              <div className="otp-verification-status" aria-live="polite">
                <span>{secondsRemaining > 0 ? `Code expires in ${expiryTime}` : "Code expired. Request a new one."}</span>
                <span>{attemptsRemaining} {attemptsRemaining === 1 ? "attempt" : "attempts"} remaining</span>
              </div>
              <button type="submit" className="primary-button" disabled={submitting || secondsRemaining <= 0 || attemptsRemaining <= 0}>{submitting ? "Checking code..." : "Verify code"}</button>
              <div className="recovery-secondary-actions">
                <button type="button" className="link-button" onClick={resendCode} disabled={submitting}>Send a new code</button>
                <button type="button" className="link-button" onClick={() => { setStep("email"); setOtp(""); setSecondsRemaining(0); setError(""); setNotice(""); }}>Change email</button>
              </div>
            </form>
          ) : (
            <form className="login-form" onSubmit={submitNewPassword}>
              <div className="form-group">
                <label htmlFor="new-password">New password</label>
                <input id="new-password" type="password" minLength={8} maxLength={128} placeholder="At least 8 characters" value={newPassword} onChange={(event) => setNewPassword(event.target.value)} autoComplete="new-password" required disabled={submitting} />
              </div>
              <div className="form-group">
                <label htmlFor="confirm-new-password">Confirm new password</label>
                <input id="confirm-new-password" type="password" minLength={8} maxLength={128} placeholder="Enter the new password again" value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} autoComplete="new-password" required disabled={submitting} />
              </div>
              <button type="submit" className="primary-button" disabled={submitting}>{submitting ? "Updating password..." : "Set new password"}</button>
              <div className="recovery-secondary-actions">
                <button type="button" className="link-button" onClick={() => { setStep("otp"); setError(""); setNotice(""); }} disabled={submitting}>Back to code</button>
              </div>
            </form>
          )}

          {!complete && <div className="recovery-back-row"><button type="button" className="link-button" onClick={() => navigate("/login")}>Back to sign in</button></div>}
        </div>
        <p className="login-panel-footer">SMART RETAIL <span>&middot;</span> BUSINESS MANAGEMENT</p>
      </section>
    </div>
  );
}
