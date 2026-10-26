import { createContext, useEffect, useState } from "react";
import { flushSync } from "react-dom";
import {
  getCurrentUser,
  loginUser,
} from "../services/authApi";

export const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  // Read cached user immediately from localStorage so first render is correct
  const [user, setUser] = useState(() => {
    // A cached profile alone is not an authenticated session. Protected pages
    // must never call tenant APIs unless a JWT is present as well.
    if (!localStorage.getItem("token")) return null;
    try {
      const cached = localStorage.getItem("user");
      return cached ? JSON.parse(cached) : null;
    } catch {
      return null;
    }
  });

  // loading = true only when a token exists that needs server-side validation
  const [loading, setLoading] = useState(() => {
    return Boolean(localStorage.getItem("token"));
  });

  // On mount: validate existing token with the server
  useEffect(() => {
    const token = localStorage.getItem("token");
    if (!token) {
      setLoading(false);
      return;
    }

    getCurrentUser()
      .then((userData) => {
        setUser(userData);
        localStorage.setItem("user", JSON.stringify(userData));
      })
      .catch(() => {
        // Token expired or invalid — clear everything
        localStorage.removeItem("token");
        localStorage.removeItem("user");
        setUser(null);
      })
      .finally(() => {
        setLoading(false);
      });
  }, []);

  const login = async (username, password) => {
    const response = await loginUser({ username, password });
    if (!response?.token) {
      throw new Error("Login did not return a session token. Please try again.");
    }

    localStorage.setItem("token", response.token);

    // Confirm the newly issued JWT against the auth service before opening the
    // protected dashboard. Every business service will receive this same token.
    let profile;
    try {
      profile = await getCurrentUser();
    } catch (error) {
      localStorage.removeItem("token");
      localStorage.removeItem("user");
      throw new Error(
        error?.response?.data?.message ||
          "The server could not validate your login token. Please sign in again.",
      );
    }

    const userData = {
      username: profile.username || response.username,
      role: profile.role || response.role,
      businessId: profile.businessId || response.businessId,
      shopName: profile.shopName || response.shopName,
      email: profile.email || response.email,
      ownerName: profile.ownerName || response.ownerName,
      ownerContact: profile.contactNumber || response.ownerContact || response.contactNumber,
      shopAddress: profile.shopAddress || response.shopAddress,
      gstin: profile.gstin || response.gstin,
      state: profile.state || response.state,
      city: profile.city || response.city,
      postalCode: profile.postalCode || response.postalCode,
      locationLatitude: profile.locationLatitude ?? response.locationLatitude,
      locationLongitude: profile.locationLongitude ?? response.locationLongitude,
    };

    localStorage.setItem("user", JSON.stringify(userData));

    // flushSync forces React to commit this state update SYNCHRONOUSLY
    // before control returns to the caller. This ensures that by the time
    // Login.jsx's useEffect checks isAuthenticated, it is already true.
    // Without this, React 18 batches the update and isAuthenticated may
    // still be false when navigate("/dashboard") fires — causing the
    // "first login redirects back to /login" bug.
    flushSync(() => {
      setUser(userData);
    });

    return response;
  };

  const updateUser = (updates) => {
    const updatedUser = { ...(user || {}), ...updates };
    localStorage.setItem("user", JSON.stringify(updatedUser));
    setUser(updatedUser);
  };

  const logout = () => {
    localStorage.removeItem("token");
    localStorage.removeItem("user");
    flushSync(() => {
      setUser(null);
    });
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        loading,
        login,
        logout,
        updateUser,
        isAuthenticated: Boolean(user && localStorage.getItem("token")),
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}
