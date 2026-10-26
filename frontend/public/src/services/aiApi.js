import axios from "axios";
import { API_GATEWAY_URL, attachSessionToken, requireSessionToken } from "./apiBase";
import { consumeNovaSse } from "./novaSse";

const AI_API_URL = `${API_GATEWAY_URL}/api/ai`;

const aiApi = axios.create({
  baseURL: AI_API_URL,
  headers: { "Content-Type": "application/json" },
  timeout: 190000,
});

attachSessionToken(aiApi);

export const askBusinessAgent = (message, history = []) => aiApi.post("/chat", { message, history }).then((response) => response.data);

export async function streamBusinessAgent(message, history = [], onToken, onReport, signal) {
  const token = requireSessionToken();
  const response = await fetch(`${AI_API_URL}/chat/stream`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({ message, history }),
    signal,
  });

  if (!response.ok) {
    let messageText = `Nova request failed (${response.status}).`;
    try {
      const body = await response.json();
      messageText = body.message || body.error || messageText;
    } catch {
      // Keep the status message when the server did not return JSON.
    }
    throw new Error(messageText);
  }
  await consumeNovaSse(response, onToken, onReport);
}
