import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../hooks/useAuth";
import { changePassword, updateBusinessGstin } from "../../services/authApi";
import "./Profile.css";

export default function Profile() {
  const navigate = useNavigate();
  const [passwordFormOpen, setPasswordFormOpen] = useState(false);
  const [passwords, setPasswords] = useState({ currentPassword: "", newPassword: "", confirmPassword: "" });
  const [passwordError, setPasswordError] = useState("");
  const [passwordNotice, setPasswordNotice] = useState("");
  const [savingPassword, setSavingPassword] = useState(false);
  const [gstinDraft, setGstinDraft] = useState("");
  const [editingGstin, setEditingGstin] = useState(false);
  const [gstinError, setGstinError] = useState("");
  const [gstinNotice, setGstinNotice] = useState("");
  const [savingGstin, setSavingGstin] = useState(false);

  let auth = {};

  try {
    auth = useAuth();
  } catch {
    auth = {};
  }

  const user = auth?.user || {};
  const initials = (user.ownerName || user.shopName || "SR")
    .split(/\s+/)
    .map((part) => part[0])
    .join("")
    .slice(0, 2)
    .toUpperCase();

  useEffect(() => {
    setGstinDraft(user.gstin || "");
    setEditingGstin(!user.gstin);
  }, [user.gstin]);

  const handleLogout = () => {
    if (auth?.logout) {
      auth.logout();
    }

    localStorage.removeItem("token");
    localStorage.removeItem("user");

    navigate("/login");
  };

  const handlePasswordChange = async (event) => {
    event.preventDefault();
    setPasswordError("");
    setPasswordNotice("");

    if (passwords.newPassword !== passwords.confirmPassword) {
      setPasswordError("The new passwords do not match.");
      return;
    }

    setSavingPassword(true);
    try {
      const result = await changePassword({
        currentPassword: passwords.currentPassword,
        newPassword: passwords.newPassword,
      });
      const notice = result?.message || "Password updated successfully.";
      auth?.logout?.();
      localStorage.removeItem("token");
      localStorage.removeItem("user");
      navigate("/login", {
        replace: true,
        state: {
          email: user.username || user.email || "",
          notice: `${notice} Please sign in again with your new password.`,
        },
      });
    } catch (requestError) {
      setPasswordError(requestError?.response?.data?.message || "Could not update your password. Please try again.");
    } finally {
      setSavingPassword(false);
    }
  };

  const handleGstinChange = async (event) => {
    event.preventDefault();
    setGstinError("");
    setGstinNotice("");
    setSavingGstin(true);
    try {
      const result = await updateBusinessGstin({ gstin: gstinDraft.trim().toUpperCase() });
      auth?.updateUser?.({ gstin: result.gstin });
      setGstinDraft(result.gstin);
      setEditingGstin(false);
      setGstinNotice("GSTIN saved. New bills will include this number.");
    } catch (requestError) {
      setGstinError(requestError?.response?.data?.message || "Could not save the GSTIN. Check the number and try again.");
    } finally {
      setSavingGstin(false);
    }
  };

  return (
    <div className="account-page">
      <header className="account-heading">
        <div>
          <p className="account-eyebrow">ACCOUNT</p>
          <h1>Profile & security</h1>
          <p>Manage your sign-in details and registered shop information.</p>
        </div>
        <span className="account-status"><i /> Account active</span>
      </header>

      <section className="account-identity">
        <div className="account-avatar">{initials}</div>
        <div className="account-identity-copy">
          <h2>{user.ownerName || user.shopName || "Shop owner"}</h2>
          <p>{user.shopName || "Your business"}<span>·</span>{user.role || "Owner"}</p>
        </div>
        <div className="account-id"><span>USER ID</span><strong>{user.username || "Not available"}</strong></div>
      </section>

      <div className="account-grid">
        <section className="account-section">
          <header className="account-section-heading">
            <div><p className="account-eyebrow">PERSONAL</p><h2>Account details</h2></div>
          </header>
          <dl className="account-details">
            <div><dt>Owner name</dt><dd>{user.ownerName || "Not provided"}</dd></div>
            <div><dt>Email address</dt><dd>{user.email || "Not provided"}</dd></div>
            <div><dt>Mobile number</dt><dd>{user.ownerContact || user.contactNumber || "Not provided"}</dd></div>
          </dl>
        </section>

        <section className="account-section">
          <header className="account-section-heading">
            <div><p className="account-eyebrow">BUSINESS</p><h2>Registered shop</h2></div>
          </header>
          <dl className="account-details">
            <div><dt>Shop name</dt><dd>{user.shopName || "Not provided"}</dd></div>
            <div><dt>Location</dt><dd>{[user.city, user.state, user.postalCode].filter(Boolean).join(", ") || "Not provided"}</dd></div>
            <div><dt>Street address</dt><dd>{user.shopAddress || "Not added"}</dd></div>
            <div><dt>Map point</dt><dd>{user.locationLatitude != null && user.locationLongitude != null ? `${Number(user.locationLatitude).toFixed(5)}, ${Number(user.locationLongitude).toFixed(5)}` : "Not shared"}</dd></div>
            <div><dt>Business GSTIN</dt><dd>{user.gstin || "Not set"}</dd></div>
            <div><dt>Account role</dt><dd>{user.role || "Owner"}</dd></div>
          </dl>
        </section>

        <section className="account-section tax-section">
          <header className="account-section-heading">
            <div><p className="account-eyebrow">TAX REGISTRATION</p><h2>Business GSTIN</h2><p>Used on every new bill created for this shop.</p></div>
            {!editingGstin && <button className="account-secondary-button" type="button" onClick={() => { setGstinDraft(user.gstin || ""); setEditingGstin(true); setGstinError(""); setGstinNotice(""); }}>Edit GSTIN</button>}
          </header>
          {gstinNotice && <p className="account-feedback success" role="status">{gstinNotice}</p>}
          {gstinError && <p className="account-feedback error" role="alert">{gstinError}</p>}
          {editingGstin ? <form className="gstin-form" onSubmit={handleGstinChange}>
            <label htmlFor="profile-gstin">GSTIN</label>
            <div><input id="profile-gstin" type="text" value={gstinDraft} onChange={(event) => setGstinDraft(event.target.value.replace(/\s/g, "").toUpperCase().slice(0, 15))} maxLength={15} pattern="[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]" title="Enter a valid 15-character GSTIN." required /><button className="account-primary-button" type="submit" disabled={savingGstin}>{savingGstin ? "Saving…" : "Save GSTIN"}</button></div>
            <small>Format: 15 characters, for example 22AAAAA0000A1Z5.</small>
          </form> : <p className="gstin-current">{user.gstin || "No GSTIN saved"}</p>}
        </section>

        <section className="account-section security-section">
          <header className="account-section-heading security-heading">
            <div><p className="account-eyebrow">SECURITY</p><h2>Password</h2><p>Change your password to keep your account secure.</p></div>
            <button className="account-secondary-button" type="button" onClick={() => { setPasswordFormOpen((open) => !open); setPasswordError(""); setPasswordNotice(""); }} aria-expanded={passwordFormOpen} aria-controls="password-change-form">
              {passwordFormOpen ? "Cancel" : "Change password"}
            </button>
          </header>

          {passwordNotice && <p className="account-feedback success" role="status">{passwordNotice}</p>}
          {passwordError && <p className="account-feedback error" role="alert">{passwordError}</p>}

          {passwordFormOpen && <form id="password-change-form" className="password-form" onSubmit={handlePasswordChange}>
            <label>Current password<input type="password" autoComplete="current-password" value={passwords.currentPassword} onChange={(event) => setPasswords((current) => ({ ...current, currentPassword: event.target.value }))} required /></label>
            <label>New password<input type="password" autoComplete="new-password" minLength={8} value={passwords.newPassword} onChange={(event) => setPasswords((current) => ({ ...current, newPassword: event.target.value }))} required /></label>
            <label>Confirm new password<input type="password" autoComplete="new-password" minLength={8} value={passwords.confirmPassword} onChange={(event) => setPasswords((current) => ({ ...current, confirmPassword: event.target.value }))} required /></label>
            <p className="password-hint">Use at least 8 characters. Your current password is required to save the change.</p>
            <button className="account-primary-button" type="submit" disabled={savingPassword}>{savingPassword ? "Saving password…" : "Update password"}</button>
          </form>}
        </section>
      </div>

      <footer className="account-actions">
        <button className="account-secondary-button" type="button" onClick={() => navigate("/dashboard")}>Back to dashboard</button>
        <button className="account-logout-button" type="button" onClick={handleLogout}>Sign out</button>
      </footer>
    </div>
  );
}
