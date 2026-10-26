import { useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { verifyOtp, registerUser } from "../../services/authApi";
import "../../styles/otpverify.css";

const OTP_LENGTH = 6;
const OTP_DURATION = 15 * 60;

export default function VerifyOtp() {
  const navigate = useNavigate();
  const location = useLocation();

  const email = location.state?.email || "";

  const [otp, setOtp] = useState(
    Array(OTP_LENGTH).fill("")
  );

  const [timer, setTimer] = useState(OTP_DURATION);
  const [loading, setLoading] = useState(false);
  const [resending, setResending] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const inputRefs = useRef([]);

  useEffect(() => {
    if (!email) {
      navigate("/register", { replace: true });
    }
  }, [email, navigate]);

  useEffect(() => {
    if (timer <= 0) {
      return;
    }

    const interval = setInterval(() => {
      setTimer((previous) => {
        if (previous <= 1) {
          clearInterval(interval);
          return 0;
        }

        return previous - 1;
      });
    }, 1000);

    return () => clearInterval(interval);
  }, [timer]);

  const formatTimer = () => {
    const minutes = Math.floor(timer / 60);
    const seconds = timer % 60;

    return `${String(minutes).padStart(2, "0")}:${String(
      seconds
    ).padStart(2, "0")}`;
  };

  const handleChange = (index, value) => {
    const digit = value.replace(/\D/g, "").slice(-1);

    const updatedOtp = [...otp];
    updatedOtp[index] = digit;

    setOtp(updatedOtp);
    setError("");
    setSuccess("");

    if (digit && index < OTP_LENGTH - 1) {
      inputRefs.current[index + 1]?.focus();
    }
  };

  const handleKeyDown = (index, event) => {
    if (
      event.key === "Backspace" &&
      !otp[index] &&
      index > 0
    ) {
      inputRefs.current[index - 1]?.focus();
    }

    if (
      event.key === "ArrowLeft" &&
      index > 0
    ) {
      inputRefs.current[index - 1]?.focus();
    }

    if (
      event.key === "ArrowRight" &&
      index < OTP_LENGTH - 1
    ) {
      inputRefs.current[index + 1]?.focus();
    }
  };

  const handlePaste = (event) => {
    event.preventDefault();

    const pastedValue = event.clipboardData
      .getData("text")
      .replace(/\D/g, "")
      .slice(0, OTP_LENGTH);

    if (!pastedValue) {
      return;
    }

    const updatedOtp = Array(OTP_LENGTH).fill("");

    pastedValue.split("").forEach((digit, index) => {
      updatedOtp[index] = digit;
    });

    setOtp(updatedOtp);
    setError("");
    setSuccess("");

    const nextIndex = Math.min(
      pastedValue.length,
      OTP_LENGTH - 1
    );

    inputRefs.current[nextIndex]?.focus();
  };

  const handleSubmit = async (event) => {
    event.preventDefault();

    setError("");
    setSuccess("");

    const otpValue = otp.join("");

    if (otpValue.length !== OTP_LENGTH) {
      setError("Please enter the complete 6-digit OTP.");
      return;
    }

    if (timer <= 0) {
      setError(
        "This OTP has expired. Please request a new OTP."
      );
      return;
    }

    setLoading(true);

    try {
      await verifyOtp({
        email,
        otp: otpValue,
      });

      setSuccess(
        "Email verified successfully. Redirecting to login..."
      );

      setTimeout(() => {
        navigate("/login", { replace: true });
      }, 1200);
    } catch (error) {
      setError(
        error?.response?.data?.message ||
          "Invalid or expired OTP. Please try again."
      );
    } finally {
      setLoading(false);
    }
  };

  const handleResend = async () => {
    if (timer > 0 || resending) {
      return;
    }

    setError("");
    setSuccess("");
    setResending(true);

    try {
      /*
       * registerUser() sends a new OTP.
       *
       * IMPORTANT:
       * This requires the registration data to still be
       * available in localStorage.
       */
      const registrationData =
        localStorage.getItem("registrationData");

      if (!registrationData) {
        setError(
          "Registration details not found. Please register again."
        );
        return;
      }

      const formData = JSON.parse(registrationData);

      await registerUser(formData);

      setOtp(Array(OTP_LENGTH).fill(""));
      setTimer(OTP_DURATION);

      setSuccess(
        "A new OTP has been sent to your email."
      );

      setTimeout(() => {
        inputRefs.current[0]?.focus();
      }, 100);
    } catch (error) {
      setError(
        error?.response?.data?.message ||
          "Unable to resend OTP. Please try again."
      );
    } finally {
      setResending(false);
    }
  };

  return (
    <div className="auth-container">
      <div className="auth-card otp-card">

        <div className="auth-brand-pill">
          Smart Retail
        </div>

        <h1 className="auth-title">
          Verify your email
        </h1>

        <p className="auth-subtitle">
          We have sent a 6-digit verification code to{" "}
          <span className="otp-email">
            {email || "your email"}
          </span>
        </p>

        {error && (
          <div className="auth-error">
            {error}
          </div>
        )}

        {success && (
          <div className="auth-success">
            {success}
          </div>
        )}

        <form onSubmit={handleSubmit}>

          <div
            className="otp-input-row"
            onPaste={handlePaste}
          >
            {otp.map((digit, index) => (
              <input
                key={index}
                ref={(element) => {
                  inputRefs.current[index] = element;
                }}
                className={`otp-box ${
                  digit ? "otp-box-filled" : ""
                } ${error ? "otp-box-error" : ""}`}
                type="text"
                inputMode="numeric"
                maxLength={1}
                value={digit}
                autoComplete="one-time-code"
                onChange={(event) =>
                  handleChange(
                    index,
                    event.target.value
                  )
                }
                onKeyDown={(event) =>
                  handleKeyDown(index, event)
                }
                aria-label={`OTP digit ${index + 1}`}
              />
            ))}
          </div>

          <button
            type="submit"
            disabled={loading || timer <= 0}
            className="primary-button"
          >
            {loading
              ? "Verifying..."
              : "Verify Email"}
          </button>

        </form>

        <div className="otp-resend-row">

          {timer > 0 ? (
            <span className="otp-timer">
              Resend code in{" "}
              <strong>{formatTimer()}</strong>
            </span>
          ) : (
            <button
              type="button"
              className="otp-resend-link"
              onClick={handleResend}
              disabled={resending}
            >
              {resending
                ? "Sending..."
                : "Resend OTP"}
            </button>
          )}

        </div>

        <hr className="auth-divider" />

        <p className="auth-footer-text">
          Entered the wrong email?{" "}
          <button
            type="button"
            className="auth-link-button"
            onClick={() => navigate("/register")}
          >
            Go back
          </button>
        </p>

      </div>
    </div>
  );
}