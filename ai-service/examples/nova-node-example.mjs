const authBaseUrl = process.env.AUTH_URL ?? "http://localhost:8081/api/auth";
const aiBaseUrl = process.env.AI_URL ?? "http://localhost:8087/api/ai";
const username = process.env.RETAIL_USERNAME;
const password = process.env.RETAIL_PASSWORD;

if (!username || !password) {
  throw new Error("Set RETAIL_USERNAME and RETAIL_PASSWORD for an existing retail account.");
}

async function postJson(url, body, token) {
  const response = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
  });
  const result = await response.json();
  if (!response.ok) {
    throw new Error(result.message ?? `Request failed with HTTP ${response.status}`);
  }
  return result;
}

// The signed-in account's JWT selects its business. Never send a businessId from the client.
const session = await postJson(`${authBaseUrl}/login`, { username, password });
const question = process.argv.slice(2).join(" ")
  || "Summarize my sales, best-selling products, and inventory that needs attention.";

const result = await postJson(`${aiBaseUrl}/chat`, { message: question }, session.token);
console.log(result.answer);
