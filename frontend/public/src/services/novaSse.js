/** Consume Nova's named SSE events and return as soon as its terminal event arrives. */
export async function consumeNovaSse(response, onToken, onReport) {
  if (!response.body) throw new Error("Your browser cannot read Nova's streamed response.");

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let eventName = "message";
  let dataLines = [];
  let completed = false;

  const dispatchEvent = () => {
    if (!dataLines.length) {
      eventName = "message";
      return;
    }
    const data = dataLines.join("\n");
    if (eventName === "token") onToken(data);
    if (eventName === "report") onReport?.(data);
    if (eventName === "done") completed = true;
    if (eventName === "error") throw new Error(data || "Nova could not complete the response.");
    dataLines = [];
    eventName = "message";
  };

  const processLine = (line) => {
    if (line === "") dispatchEvent();
    else if (line.startsWith("event:")) eventName = line.slice(6).trim();
    else if (line.startsWith("data:")) dataLines.push(line.slice(5).replace(/^ /, ""));
  };

  try {
    while (!completed) {
      const { value, done } = await reader.read();
      buffer += decoder.decode(value || new Uint8Array(), { stream: !done });
      const lines = buffer.split(/\r?\n/);
      buffer = lines.pop() || "";
      for (const line of lines) {
        processLine(line);
        if (completed) break;
      }
      if (done) break;
    }
    if (!completed && buffer) processLine(buffer);
    if (!completed) dispatchEvent();
    if (!completed) throw new Error("Nova's response ended before completion. Please try again.");
    await reader.cancel();
  } catch (error) {
    await reader.cancel(error).catch(() => {});
    throw error;
  } finally {
    reader.releaseLock();
  }
}
