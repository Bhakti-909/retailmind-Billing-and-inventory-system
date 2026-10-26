import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { registerUser } from "../../services/authApi";
import "../../styles/auth.css";
import "./LoginPage.css";

const indianStates = [
  "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh",
  "Goa", "Gujarat", "Haryana", "Himachal Pradesh", "Jharkhand", "Karnataka",
  "Kerala", "Madhya Pradesh", "Maharashtra", "Manipur", "Meghalaya", "Mizoram",
  "Nagaland", "Odisha", "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu",
  "Telangana", "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal",
  "Andaman and Nicobar Islands", "Chandigarh", "Dadra and Nagar Haveli and Daman and Diu",
  "Delhi", "Jammu and Kashmir", "Ladakh", "Lakshadweep", "Puducherry",
];

async function lookupShopAddress(latitude, longitude) {
  const query = new URLSearchParams({
    format: "jsonv2",
    lat: String(latitude),
    lon: String(longitude),
    zoom: "18",
    addressdetails: "1",
  });
  const response = await fetch(`https://nominatim.openstreetmap.org/reverse?${query}`, {
    headers: { Accept: "application/json" },
  });
  if (!response.ok) throw new Error("Address lookup failed.");

  const result = await response.json();
  const address = result.address || {};
  const street = [address.house_number, address.road || address.pedestrian || address.footway]
    .filter(Boolean)
    .join(" ");
  const area = address.neighbourhood || address.suburb || address.quarter || "";

  return {
    shopAddress: [street, area].filter(Boolean).join(", ") || result.display_name || "",
    city: address.city || address.town || address.village || address.municipality || "",
    state: address.state || "",
    postalCode: (address.postcode || "").replace(/\D/g, "").slice(0, 6),
  };
}

export default function Register() {
  const navigate = useNavigate();
  const [form, setForm] = useState({
    shopName: "",
    shopAddress: "",
    state: "",
    city: "",
    postalCode: "",
    locationLatitude: "",
    locationLongitude: "",
    gstin: "",
    ownerName: "",
    ownerContact: "",
    ownerEmail: "",
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [locationLoading, setLocationLoading] = useState(false);
  const [locationMessage, setLocationMessage] = useState("");
  const [step, setStep] = useState(1);
  const [gstRegistered, setGstRegistered] = useState("");
  const [addressOpen, setAddressOpen] = useState(false);

  const handleChange = (event) => {
    const { name, value } = event.target;
    const normalizedValue = name === "postalCode"
      ? value.replace(/\D/g, "").slice(0, 6)
      : name === "gstin"
        ? value.replace(/\s/g, "").toUpperCase().slice(0, 15)
        : value;
    setForm((previous) => ({ ...previous, [name]: normalizedValue }));
  };

  const detectLocation = () => {
    if (!navigator.geolocation) {
      setLocationMessage("Location detection is not available in this browser.");
      return;
    }
    setLocationLoading(true);
    setLocationMessage("");
    navigator.geolocation.getCurrentPosition(
      async ({ coords }) => {
        let address = null;
        try {
          address = await lookupShopAddress(coords.latitude, coords.longitude);
        } catch {
          address = null;
        }

        setForm((previous) => ({
          ...previous,
          locationLatitude: coords.latitude,
          locationLongitude: coords.longitude,
          shopAddress: address?.shopAddress || previous.shopAddress,
          city: previous.city || address?.city || "",
          state: previous.state || address?.state || "",
          postalCode: previous.postalCode || address?.postalCode || "",
        }));
        if (address?.shopAddress) {
          setAddressOpen(true);
          setLocationMessage("Address filled from your location. Review it before continuing.");
        } else {
          setLocationMessage("Coordinates saved. Enter the street address below if needed.");
          setAddressOpen(true);
        }
        setLocationLoading(false);
      },
      () => {
        setLocationMessage("Location was not shared. You can continue without it.");
        setLocationLoading(false);
      },
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 30000 }
    );
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (loading) return;
    if (step === 1) {
      setError("");
      setStep(2);
      return;
    }
    setError("");
    setLoading(true);
    try {
      await registerUser({
        ...form,
        shopName: form.shopName.trim(),
        shopAddress: form.shopAddress.trim(),
        state: form.state.trim(),
        city: form.city.trim(),
        postalCode: form.postalCode.trim(),
        locationLatitude: form.locationLatitude === "" ? null : Number(form.locationLatitude),
        locationLongitude: form.locationLongitude === "" ? null : Number(form.locationLongitude),
        gstin: gstRegistered === "yes" ? form.gstin.trim().toUpperCase() : null,
        ownerName: form.ownerName.trim(),
        ownerContact: form.ownerContact.trim(),
        ownerEmail: form.ownerEmail.trim(),
      });
      navigate("/verify-otp", {
        replace: true,
        state: { email: form.ownerEmail.trim() },
      });
    } catch (requestError) {
      setError(requestError?.response?.data?.message || "Registration failed. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-page register-page">
      <section className="login-story" aria-label="Smart Retail overview">
        <div className="login-category-collage" role="img" aria-label="Retail categories: grocery, pharmacy, apparel, and general merchandise">
          <img src="https://images.unsplash.com/photo-1698466632366-09fa1d925de6?auto=format&fit=crop&w=1200&q=85" alt="" />
          <img src="https://images.unsplash.com/photo-1696861286643-341a8d7a79e9?auto=format&fit=crop&w=1200&q=85" alt="" />
          <img src="https://images.unsplash.com/photo-1441984904996-e0b6ba687e04?auto=format&fit=crop&w=1200&q=85" alt="" />
          <img src="https://images.unsplash.com/photo-1681625731358-54ccba21a786?auto=format&fit=crop&w=1200&q=85" alt="" />
        </div>
        <div className="login-brand">
          <div className="login-brand-mark" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round"><path d="M3 4h2l2.1 10.1a2 2 0 0 0 2 1.6h8.8a2 2 0 0 0 1.9-1.4L22 8H7" /><circle cx="10" cy="20" r="1" /><circle cx="18" cy="20" r="1" /></svg></div>
          <div><strong>Smart Retail</strong><span>Retail management system</span></div>
          <span className="login-secure"><i />Secure workspace</span>
        </div>

        <div className="login-story-content">
          <p className="login-story-eyebrow">MADE FOR THE EVERYDAY WORK OF RETAIL</p>
          <h1>Keep your shop<br /><span>moving forward.</span></h1>
          <p className="login-story-intro">The essentials for running your business, together in one place.</p>
          <div className="login-category-tags" role="group" aria-label="Supported retail categories">
            <span>Grocery</span><span>Pharmacy</span><span>Apparel</span><span>General retail</span>
          </div>
          <div className="login-capabilities">
            <div><span>01</span><p><strong>Inventory</strong><small>Stock levels and expiry dates</small></p></div>
            <div><span>02</span><p><strong>Billing</strong><small>Sales and daily totals</small></p></div>
            <div><span>03</span><p><strong>Customers</strong><small>Contact details in one place</small></p></div>
            <div><span>04</span><p><strong>Reports</strong><small>A clearer view of your shop</small></p></div>
          </div>
        </div>
        <p className="login-story-footer">A BETTER VIEW OF YOUR DAY-TO-DAY BUSINESS</p>
      </section>

      <section className="login-panel register-panel" aria-labelledby="register-heading">
        <div className="login-form-wrap register-form-wrap">
          <p className="login-form-eyebrow"></p>
          <div className="login-form-heading">
            <h2 id="register-heading">QUICK BUSINESS SETUP</h2>
            <p>{step === 1 ? "Start with your shop and owner details." : "Add your location and tax details."}</p>
          </div>

          <div className="register-stepper" aria-label={`Step ${step} of 2`}>
            <div className={step === 1 ? "current" : "complete"}><span>{step > 1 ? "✓" : "1"}</span><strong>Business</strong></div>
            <i className={step === 2 ? "complete" : ""} />
            <div className={step === 2 ? "current" : ""}><span>2</span><strong>Location & tax</strong></div>
          </div>

          {error && <div className="auth-error" role="alert">{error}</div>}

          <form className="login-form register-form" onSubmit={handleSubmit}>
            {step === 1 ? <div className="register-step-content">
              <div className="form-group">
                <label htmlFor="shopName">Shop name</label>
                <input id="shopName" type="text" name="shopName" placeholder="Enter shop name" value={form.shopName} onChange={handleChange} required />
              </div>

              <div className="form-group">
                <label htmlFor="ownerName">Owner name</label>
                <input id="ownerName" type="text" name="ownerName" placeholder="Owner name" value={form.ownerName} onChange={handleChange} autoComplete="name" required />
              </div>

              <div className="form-group">
                <label htmlFor="ownerContact">Mobile number</label>
                <input id="ownerContact" type="tel" name="ownerContact" placeholder="10 digit number" value={form.ownerContact} onChange={handleChange} autoComplete="tel-national" inputMode="numeric" maxLength={10} pattern="[0-9]{10}" required />
              </div>
            </div> : <div className="register-step-content">
              <div className="form-group">
                <label htmlFor="ownerEmail">Owner email</label>
                <input id="ownerEmail" type="email" name="ownerEmail" placeholder="owner@example.com" value={form.ownerEmail} onChange={handleChange} autoComplete="email" inputMode="email" pattern={"[^\\s@]+@[^\\s@]+\\.[^\\s@]+"} title="Enter an email address with a valid domain, such as name@example.com." required />
              </div>

              <section className="register-location-section" aria-labelledby="register-location-heading">
              <div className="register-location-heading">
                <div>
                  <span>LOCATION</span>
                  <h3 id="register-location-heading">Where is your shop?</h3>
                </div>
                <small>Street address and GPS are optional</small>
              </div>

              <div className="register-location-grid">
                <div className="form-group">
                  <label htmlFor="state">State</label>
                  <select id="state" name="state" value={form.state} onChange={handleChange} required>
                    <option value="">Select state</option>
                    {indianStates.map((state) => <option key={state} value={state}>{state}</option>)}
                  </select>
                </div>
                <div className="form-group">
                  <label htmlFor="city">City</label>
                  <input id="city" type="text" name="city" placeholder="City or town" value={form.city} onChange={handleChange} autoComplete="address-level2" required />
                </div>
                <div className="form-group register-pin-group">
                  <label htmlFor="postalCode">PIN code</label>
                  <input id="postalCode" type="text" name="postalCode" placeholder="6-digit PIN" value={form.postalCode} onChange={handleChange} autoComplete="postal-code" inputMode="numeric" maxLength={6} pattern="[1-9][0-9]{5}" title="Enter a valid 6-digit Indian PIN code." required />
                </div>
              </div>

              <div className="register-location-options">
                <div className="register-location-capture">
                  <button type="button" onClick={detectLocation} disabled={locationLoading}>
                    {locationLoading ? "Finding location..." : form.locationLatitude !== "" ? "Update current location" : "Use current location"}
                  </button>
                  <span>{locationMessage || (form.locationLatitude !== "" ? `Location saved · ${Number(form.locationLatitude).toFixed(4)}, ${Number(form.locationLongitude).toFixed(4)}` : "Share your location to fill the address automatically")}</span>
                </div>

                <details className="register-address-details" open={addressOpen} onToggle={(event) => setAddressOpen(event.currentTarget.open)}>
                  <summary>Add a street address <span>Optional</span></summary>
                  <div className="form-group register-address-group">
                    <label htmlFor="shopAddress">Street address</label>
                    <textarea id="shopAddress" name="shopAddress" placeholder="Building, street, area" value={form.shopAddress} onChange={handleChange} rows={2} autoComplete="street-address" />
                  </div>
                </details>
              </div>
              </section>

              <fieldset className="register-gst-fieldset">
                <legend>Does your business have a GSTIN?</legend>
                <div className="register-gst-choice">
                  <label className={gstRegistered === "yes" ? "selected" : ""}><input type="radio" name="gstRegistered" value="yes" checked={gstRegistered === "yes"} onChange={() => setGstRegistered("yes")} required />Yes</label>
                  <label className={gstRegistered === "no" ? "selected" : ""}><input type="radio" name="gstRegistered" value="no" checked={gstRegistered === "no"} onChange={() => { setGstRegistered("no"); setForm((current) => ({ ...current, gstin: "" })); }} required />No</label>
                </div>
              </fieldset>

              {gstRegistered === "yes" && <div className="form-group register-gstin-group">
                <label htmlFor="gstin">GSTIN</label>
                <input id="gstin" type="text" name="gstin" placeholder="15-character GSTIN" value={form.gstin} onChange={handleChange} autoCapitalize="characters" maxLength={15} pattern="[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][A-Z0-9]Z[A-Z0-9]" title="Enter a valid 15-character GSTIN." required />
              </div>}
            </div>}

            <div className="register-step-actions">
              {step === 2 && <button type="button" className="register-back-button" onClick={() => { setStep(1); setError(""); }}>Back</button>}
              <button type="submit" disabled={loading} className="primary-button">
                {loading ? "Sending OTP..." : step === 1 ? "Continue" : "Send verification code"}
              </button>
            </div>
          </form>

          <div className="login-register-prompt">
            <span>Already have an account?</span>
            <button type="button" className="link-button" onClick={() => navigate("/login")}>
              Sign in <span aria-hidden="true">→</span>
            </button>
          </div>
        </div>
        <p className="login-panel-footer">SMART RETAIL <span>·</span> BUSINESS MANAGEMENT</p>
      </section>
    </div>
  );
}
