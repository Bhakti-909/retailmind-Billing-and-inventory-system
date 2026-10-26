import api from "./api";

export const registerUser = async (data) => {
  const response = await api.post(
    "/auth/register",
    data
  );

  return response.data;
};

export const verifyOtp = async (data) => {
  const response = await api.post(
    "/auth/verify-otp",
    data
  );

  return response.data;
};

export const loginUser = async (data) => {
  const response = await api.post(
    "/auth/login",
    data
  );

  return response.data;
};

export const getCurrentUser = async () => {
  const response = await api.get("/auth/me");
  return response.data;
};

export const changePassword = async (data) => {
  const response = await api.put("/auth/password", data);
  return response.data;
};

export const requestPasswordReset = async (data) => {
  const response = await api.post("/auth/password/forgot", data);
  return response.data;
};

export const verifyPasswordResetOtp = async (data) => {
  const response = await api.post("/auth/password/verify-otp", data);
  return response.data;
};

export const resetPassword = async (data) => {
  const response = await api.post("/auth/password/reset", data);
  return response.data;
};

export const updateBusinessGstin = async (data) => {
  const response = await api.put("/auth/business/gstin", data);
  return response.data;
};
