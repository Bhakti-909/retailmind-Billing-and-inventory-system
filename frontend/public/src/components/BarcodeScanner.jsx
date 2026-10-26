import { useEffect, useRef, useState } from "react";
import "./BarcodeScanner.css";

export default function BarcodeScanner({ onDetected, onClose, continuous = false }) {
  const videoRef = useRef(null);
  const onDetectedRef = useRef(onDetected);
  const lastBarcodeRef = useRef("");
  const lastBarcodeSeenAtRef = useRef(0);
  const initialFeedback = continuous
    ? { type: "ready", title: "Ready to scan", detail: "Scan a product barcode to add it to the bill." }
    : null;
  const [devices, setDevices] = useState([]);
  const [selectedDeviceId, setSelectedDeviceId] = useState("");
  const [manualBarcode, setManualBarcode] = useState("");
  const [cameraError, setCameraError] = useState("");
  const [startingCamera, setStartingCamera] = useState(true);
  const [scanFeedback, setScanFeedback] = useState(initialFeedback);
  const scanFeedbackRef = useRef(initialFeedback);

  const updateScanFeedback = (feedback) => {
    scanFeedbackRef.current = feedback;
    setScanFeedback(feedback);
  };

  useEffect(() => {
    if (scanFeedback?.type !== "warning") return undefined;
    const timeoutId = window.setTimeout(() => {
      if (scanFeedbackRef.current?.type === "warning") {
        updateScanFeedback({ type: "ready", title: "Ready for next product", detail: "Move a barcode into view to scan." });
      }
    }, 1000);
    return () => window.clearTimeout(timeoutId);
  }, [scanFeedback]);

  onDetectedRef.current = onDetected;

  useEffect(() => {
    let cancelled = false;
    let controls;

    const startCamera = async () => {
      setCameraError("");
      setStartingCamera(true);
      try {
        if (!window.isSecureContext && !["localhost", "127.0.0.1"].includes(window.location.hostname)) {
          setCameraError("Camera access needs HTTPS or localhost. You can still enter the barcode below.");
          return;
        }
        const [{ BrowserMultiFormatReader }, { DecodeHintType }] = await Promise.all([
          import("@zxing/browser"),
          import("@zxing/library"),
        ]);
        if (cancelled) return;
        const hints = new Map([[DecodeHintType.TRY_HARDER, true]]);
        const reader = new BrowserMultiFormatReader(hints);
        const availableDevices = await BrowserMultiFormatReader.listVideoInputDevices();
        if (cancelled) return;
        setDevices(availableDevices);
        const preferredDevice = availableDevices.find((device) => /back|rear|environment/i.test(device.label));
        const deviceId = selectedDeviceId || preferredDevice?.deviceId;

        const videoConstraints = deviceId
          ? { deviceId: { exact: deviceId }, width: { ideal: 1920 }, height: { ideal: 1080 }, focusMode: { ideal: "continuous" } }
          : { facingMode: { ideal: "environment" }, width: { ideal: 1920 }, height: { ideal: 1080 }, focusMode: { ideal: "continuous" } };
        const scannerControls = await reader.decodeFromConstraints(
          { video: videoConstraints },
          videoRef.current,
          (result, _error, scanControls) => {
            const barcode = result?.getText().trim();
            if (barcode) {
              lastBarcodeSeenAtRef.current = Date.now();
              if (continuous) {
                if (lastBarcodeRef.current !== barcode) {
                  lastBarcodeRef.current = barcode;
                  updateScanFeedback(onDetectedRef.current(barcode) || {
                    type: "success",
                    title: "Barcode scanned",
                    detail: `Code: ${barcode}`,
                  });
                }
              } else {
                scanControls.stop();
                onDetectedRef.current(barcode);
              }
            } else if (continuous && lastBarcodeRef.current && Date.now() - lastBarcodeSeenAtRef.current > 500) {
              lastBarcodeRef.current = "";
              if (scanFeedbackRef.current?.type !== "warning") {
                updateScanFeedback({ type: "ready", title: "Ready for next product", detail: "Move the next barcode into view to continue." });
              }
            }
          }
        );
        BrowserMultiFormatReader.listVideoInputDevices().then((cameras) => {
          if (!cancelled && cameras.length) setDevices(cameras);
        }).catch(() => {});
        if (cancelled) scannerControls.stop();
        else controls = scannerControls;
      } catch (error) {
        if (!cancelled) {
          const denied = error?.name === "NotAllowedError" || error?.name === "PermissionDeniedError";
          setCameraError(denied
            ? "Camera access is turned off. Allow access in your browser settings, or enter the code manually."
            : "The camera could not start. Check browser permissions and try again, or enter the code manually.");
        }
      } finally {
        if (!cancelled) setStartingCamera(false);
      }
    };

    startCamera();
    return () => {
      cancelled = true;
      controls?.stop();
    };
  }, [selectedDeviceId, continuous]);

  const defaultDeviceId = devices.find((device) => /back|rear|environment/i.test(device.label))?.deviceId
    || devices[0]?.deviceId
    || "";

  const submitManualBarcode = (event) => {
    event.preventDefault();
    const barcode = manualBarcode.trim();
    if (!barcode) return;
    const feedback = onDetectedRef.current(barcode);
    if (continuous) {
      updateScanFeedback(feedback || { type: "success", title: "Barcode scanned", detail: `Code: ${barcode}` });
      setManualBarcode("");
    }
  };

  return (
    <div className="barcode-scanner-backdrop" onMouseDown={(event) => {
      if (event.target === event.currentTarget) onClose();
    }}>
      <section className="barcode-scanner-dialog" role="dialog" aria-modal="true" aria-labelledby="barcode-scanner-title">
        <header className="barcode-scanner-heading">
          <div><p>INSTANT BILLING</p><h2 id="barcode-scanner-title">Scan a product</h2></div>
          <button type="button" className="barcode-scanner-close" onClick={onClose} aria-label="Close barcode scanner">×</button>
        </header>

        {devices.length > 1 && <label className="barcode-camera-choice">Camera
          <select value={selectedDeviceId || defaultDeviceId} onChange={(event) => setSelectedDeviceId(event.target.value)}>
            {devices.map((device, index) => <option key={device.deviceId} value={device.deviceId}>{device.label || `Camera ${index + 1}`}</option>)}
          </select>
        </label>}

        <div className="barcode-camera-frame">
          <video ref={videoRef} muted playsInline autoPlay />
          {startingCamera && <span className="barcode-camera-status">Starting camera…</span>}
        </div>
        <section className="barcode-scan-guide" aria-label="Scanning tips">
          <strong>Line up the barcode inside the frame</strong>
          <p className="barcode-camera-hint">Keep the full code visible and in focus. After a scan, move it away briefly before scanning the next item.</p>
        </section>
        {scanFeedback && <div className={`barcode-scan-feedback ${scanFeedback.type || "success"}`} role={scanFeedback.type === "warning" ? "alert" : "status"}><span className="barcode-result-icon">{scanFeedback.type === "success" ? "✓" : scanFeedback.type === "warning" ? "!" : "⌁"}</span><div><strong>{scanFeedback.title || scanFeedback}</strong>{scanFeedback.detail && <small>{scanFeedback.detail}</small>}</div>{scanFeedback.type === "warning" && <button type="button" className="barcode-feedback-dismiss" onClick={() => updateScanFeedback({ type: "ready", title: "Ready for next product", detail: "Move a barcode into view to scan." })} aria-label="Dismiss barcode error">×</button>}</div>}
        {cameraError && <p className="barcode-camera-error" role="status">{cameraError}</p>}

        <form className="barcode-manual-entry" onSubmit={submitManualBarcode}>
          <label htmlFor="manual-barcode">Enter code manually</label>
          <div>
            <input id="manual-barcode" value={manualBarcode} onChange={(event) => setManualBarcode(event.target.value)} autoComplete="off" />
            <button type="submit" disabled={!manualBarcode.trim()}>Use code</button>
          </div>
        </form>
        {continuous && <button type="button" className="barcode-scanner-done" onClick={onClose}>Close scanner</button>}
      </section>
    </div>
  );
}
