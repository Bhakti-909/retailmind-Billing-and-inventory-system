import test from "node:test";
import assert from "node:assert/strict";
import { consumeNovaSse } from "./novaSse.js";

const responseFrom = (chunks, { close = true, onCancel = () => {} } = {}) => {
  const encoder = new TextEncoder();
  const body = new ReadableStream({
    start(controller) {
      chunks.forEach((chunk) => controller.enqueue(encoder.encode(chunk)));
      if (close) controller.close();
    },
    cancel: onCancel,
  });
  return { body };
};

test("dispatches streamed answer tokens once and stops on terminal done event", async () => {
  let cancelled = false;
  const tokens = [];
  const reports = [];
  const response = responseFrom([
    "event: token\ndata: Hello\n\n",
    "event: report\ndata: /reports?from=2026-10-01\n\n",
    "event: done\ndata: complete\n\n",
  ], { close: false, onCancel: () => { cancelled = true; } });

  await consumeNovaSse(response, (token) => tokens.push(token), (report) => reports.push(report));

  assert.deepEqual(tokens, ["Hello"]);
  assert.deepEqual(reports, ["/reports?from=2026-10-01"]);
  assert.equal(cancelled, true);
});

test("rejects an SSE error event so the caller can clear its loading state", async () => {
  const response = responseFrom(["event: error\ndata: Model unavailable\n\n"]);
  await assert.rejects(consumeNovaSse(response, () => {}), /Model unavailable/);
});

test("treats unexpected connection close as incomplete after preserving emitted tokens", async () => {
  const tokens = [];
  const response = responseFrom(["event: token\ndata: partial\n\n"]);
  await assert.rejects(consumeNovaSse(response, (token) => tokens.push(token)), /ended before completion/);
  assert.deepEqual(tokens, ["partial"]);
});

test("handles event frames split across network chunks without duplicating answer text", async () => {
  const tokens = [];
  const response = responseFrom([
    "event: token\ndata: Final",
    " answer\n\nevent: done\ndata: complete\n\n",
  ]);
  await consumeNovaSse(response, (token) => tokens.push(token));
  assert.deepEqual(tokens, ["Final answer"]);
});
