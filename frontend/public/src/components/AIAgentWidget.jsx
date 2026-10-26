import { useEffect, useRef, useState } from "react";
import { streamBusinessAgent } from "../services/aiApi";
import "./AIAgentWidget.css";

const welcomeMessage = {
  role: "assistant",
  text: "Hi, I’m Nova. Ask me about your inventory, sales, forecasts, or retail operations.",
};

const suggestions = ["Show today's sales", "What needs attention today?", "Forecast next week's sales", "Show pricing risks"];

function getProgressMessage(question) {
  const query = question.toLowerCase();
  if (/forecast|predict|future|demand|next week|next month/.test(query)) return "Checking recent sales data for your forecast...";
  if (/sales|sale|sell|sold|revenue|bill|invoice/.test(query)) return "Checking your sales records...";
  if (/stock|inventory|product|customer|supplier|vendor|profit|margin/.test(query)) return "Checking your business records...";
  return "Nova is preparing your answer...";
}

export default function AIAgentWidget() {
  const [open, setOpen] = useState(false);
  const [question, setQuestion] = useState("");
  const [sending, setSending] = useState(false);
  const [responseStarted, setResponseStarted] = useState(false);
  const [queuedQuestionCount, setQueuedQuestionCount] = useState(0);
  const [progressMessage, setProgressMessage] = useState("");
  const [messages, setMessages] = useState([welcomeMessage]);
  const [position, setPosition] = useState(null);
  const [dragging, setDragging] = useState(false);
  const threadRef = useRef(null);
  const inputRef = useRef(null);
  const questionRef = useRef("");
  const dragRef = useRef(null);
  const sendingRef = useRef(false);
  const pendingTokensRef = useRef("");
  const tokenFrameRef = useRef(null);
  const queuedQuestionsRef = useRef([]);
  const sendRef = useRef(null);
  const abortControllerRef = useRef(null);
  const currentReportLinkRef = useRef(null);

  useEffect(() => {
    const show = () => setOpen(true);
    window.addEventListener("open-ai-agent", show);
    return () => window.removeEventListener("open-ai-agent", show);
  }, []);

  useEffect(() => {
    if (open) threadRef.current?.scrollTo({ top: threadRef.current.scrollHeight, behavior: "auto" });
  }, [messages, open, progressMessage]);

  useEffect(() => { if (open) inputRef.current?.focus(); }, [open]);
  useEffect(() => () => {
    abortControllerRef.current?.abort();
    if (tokenFrameRef.current !== null) window.cancelAnimationFrame(tokenFrameRef.current);
  }, []);

  useEffect(() => {
    if (!dragging) return undefined;
    const move = (event) => {
      if (!dragRef.current) return;
      const { pointerX, pointerY, windowX, windowY } = dragRef.current;
      const frame = document.querySelector(".ai-agent-window");
      const width = frame?.offsetWidth || 430;
      const height = frame?.offsetHeight || 610;
      const x = windowX + event.clientX - pointerX;
      const y = windowY + event.clientY - pointerY;
      setPosition({
        x: Math.max(0, Math.min(x, window.innerWidth - width)),
        y: Math.max(0, Math.min(y, window.innerHeight - height)),
      });
    };
    const stop = () => setDragging(false);
    window.addEventListener("pointermove", move);
    window.addEventListener("pointerup", stop);
    window.addEventListener("pointercancel", stop);
    return () => {
      window.removeEventListener("pointermove", move);
      window.removeEventListener("pointerup", stop);
      window.removeEventListener("pointercancel", stop);
    };
  }, [dragging]);

  useEffect(() => {
    if (!position) return undefined;
    const keepInView = () => {
      const frame = document.querySelector(".ai-agent-window");
      if (!frame) return;
      setPosition((current) => ({
        x: Math.max(0, Math.min(current.x, window.innerWidth - frame.offsetWidth)),
        y: Math.max(0, Math.min(current.y, window.innerHeight - frame.offsetHeight)),
      }));
    };
    window.addEventListener("resize", keepInView);
    return () => window.removeEventListener("resize", keepInView);
  }, [position]);

  const beginDrag = (event) => {
    if (event.button !== 0 || event.target.closest("button")) return;
    event.currentTarget.setPointerCapture?.(event.pointerId);
    const bounds = event.currentTarget.parentElement.getBoundingClientRect();
    dragRef.current = {
      pointerX: event.clientX,
      pointerY: event.clientY,
      windowX: bounds.left,
      windowY: bounds.top,
    };
    setPosition({ x: bounds.left, y: bounds.top });
    setDragging(true);
  };

  const send = async (value) => {
    const text = (value ?? questionRef.current).trim();
    if (!text) return;
    if (sendingRef.current) {
      queuedQuestionsRef.current.push(text);
      setQueuedQuestionCount(queuedQuestionsRef.current.length);
      questionRef.current = "";
      setQuestion("");
      return;
    }
    sendingRef.current = true;
    questionRef.current = "";
    setQuestion("");
    setMessages((current) => [...current, { role: "user", text }]);
    setProgressMessage(getProgressMessage(text));
    setResponseStarted(false);
    setSending(true);
    currentReportLinkRef.current = null;
    const requestController = new AbortController();
    abortControllerRef.current = requestController;
    let startedAnswer = false;
    const flushTokens = () => {
      if (tokenFrameRef.current !== null) {
        window.cancelAnimationFrame(tokenFrameRef.current);
        tokenFrameRef.current = null;
      }
      const chunk = pendingTokensRef.current;
      pendingTokensRef.current = "";
      if (!chunk) return;
      const appendToExisting = startedAnswer;
      startedAnswer = true;
      if (!appendToExisting) setResponseStarted(true);
      setMessages((current) => {
        const lastIndex = current.length - 1;
        if (appendToExisting && current[lastIndex]?.role === "assistant") {
          return current.map((message, index) => index === lastIndex ? { ...message, text: message.text + chunk } : message);
        }
        return [...current, { role: "assistant", text: chunk }];
      });
    };
    try {
      const history = messages.slice(1).filter((message) => ["user", "assistant"].includes(message.role))
        .slice(-8).map(({ role, text: content }) => ({ role, content }));
      await streamBusinessAgent(text, history, (token) => {
        pendingTokensRef.current += token;
        if (tokenFrameRef.current === null) tokenFrameRef.current = window.requestAnimationFrame(flushTokens);
      }, (reportPath) => { currentReportLinkRef.current = reportPath; }, requestController.signal);
      flushTokens();
      if (currentReportLinkRef.current) {
        const reportPath = currentReportLinkRef.current;
        setMessages((current) => {
          const index = current.reduce((last, message, messageIndex) => message.role === "assistant" ? messageIndex : last, -1);
          return index < 0 ? current : current.map((message, messageIndex) => messageIndex === index ? { ...message, reportPath } : message);
        });
      }
    } catch (error) {
      flushTokens();
      if (requestController.signal.aborted) {
        setMessages((current) => {
          const index = current.reduce((last, message, messageIndex) => message.role === "assistant" ? messageIndex : last, -1);
          if (!startedAnswer || index < 0 || current[index].error) return [...current, { role: "assistant", text: "Response stopped.", cancelled: true }];
          return current.map((message, messageIndex) => messageIndex === index ? { ...message, text: `${message.text}\n\nResponse stopped.`, cancelled: true } : message);
        });
      } else {
      const message = error?.message || error?.response?.data?.message
        || (error?.code === "ECONNABORTED"
          ? "Nova is taking longer than expected to respond. Please try again in a moment."
          : "Nova can’t reach the assistant service right now. Please try again shortly.");
      setMessages((current) => [...current, { role: "assistant", text: message, error: true }]);
      }
    } finally {
      pendingTokensRef.current = "";
      if (abortControllerRef.current === requestController) abortControllerRef.current = null;
      sendingRef.current = false;
      setSending(false);
      inputRef.current?.focus();
    }
  };

  sendRef.current = send;

  useEffect(() => {
    if (sending || sendingRef.current || queuedQuestionsRef.current.length === 0) return;
    const nextQuestion = queuedQuestionsRef.current.shift();
    setQueuedQuestionCount(queuedQuestionsRef.current.length);
    sendRef.current?.(nextQuestion);
  }, [sending, queuedQuestionCount]);

  const clearConversation = () => {
    queuedQuestionsRef.current = [];
    setQueuedQuestionCount(0);
    setMessages([welcomeMessage]);
  };
  const windowPosition = position ? { left: `${position.x}px`, top: `${position.y}px`, right: "auto", bottom: "auto" } : undefined;

  return <>
    {!open && <button className="ai-agent-launcher" onClick={() => setOpen(true)} aria-label="Open Nova assistant">
      <span className="ai-agent-launcher-icon">✦</span><span><b>Nova</b><small>Retail assistant</small></span>
    </button>}
    {open && <section className={`ai-agent-window${dragging ? " is-dragging" : ""}`} style={windowPosition} role="dialog" aria-label="Nova retail assistant">
      <header className="ai-agent-header" onPointerDown={beginDrag} title="Drag to move Nova">
        <span className="ai-agent-avatar">✦</span>
        <div><strong>Nova</strong><small><i /> Retail business assistant</small></div>
        <button type="button" onClick={clearConversation} disabled={sending} title="Clear conversation" aria-label="Clear conversation">⌫</button>
        <button type="button" onClick={() => setOpen(false)} title="Close assistant" aria-label="Close assistant">×</button>
      </header>
      <div className="ai-agent-context"><span>Inventory</span><span>Sales</span><span>Forecasts</span><span>Retail guidance</span></div>
      <div className="ai-agent-messages" ref={threadRef} aria-live="polite">
        {messages.map((message, index) => <article key={`${index}-${message.role}`} className={`ai-agent-message ${message.role}${message.error ? " error" : ""}`}>
          {message.role === "assistant" && <span className="ai-agent-message-avatar">✦</span>}
          <div className="ai-agent-message-text">{message.text}{message.reportPath && <a className="ai-agent-report-link" href={message.reportPath}>Open report and export PDF or CSV</a>}</div>
        </article>)}
        {sending && !responseStarted && <article className="ai-agent-message assistant ai-agent-thinking">
          <span className="ai-agent-message-avatar">✦</span>
          <div><div className="ai-agent-typing"><i /><i /><i /></div><small>{progressMessage}</small></div>
        </article>}
      </div>
      {!messages.some((message) => message.role === "user") && <div className="ai-agent-suggestions">{suggestions.map((suggestion) => <button key={suggestion} onClick={() => send(suggestion)}>{suggestion}</button>)}</div>}
      <form className="ai-agent-composer" onSubmit={(event) => { event.preventDefault(); send(); }}>
        <textarea ref={inputRef} rows="1" value={question} maxLength={2000} onChange={(event) => { questionRef.current = event.target.value; setQuestion(event.target.value); }} onKeyDown={(event) => { if (event.key === "Enter" && !event.shiftKey) { event.preventDefault(); send(); } }} placeholder={sending ? "Ask another question; it will queue..." : "Ask Nova about your business..."} aria-label="Ask Nova" />
        <button
          type={sending ? "button" : "submit"}
          disabled={!sending && !question.trim()}
          onClick={sending ? () => abortControllerRef.current?.abort() : undefined}
          aria-label={sending ? "Stop response" : "Send message"}
          title={sending ? "Stop response" : "Send message"}
          className={sending ? "is-stop" : "is-send"}
        >
          <span aria-hidden="true">{sending ? "■" : "➤"}</span>
        </button>
      </form>
      <footer className="ai-agent-footer">{queuedQuestionCount > 0 ? `${queuedQuestionCount} next question${queuedQuestionCount === 1 ? "" : "s"} queued; Nova will send ${queuedQuestionCount === 1 ? "it" : "them"} after this answer.` : sending ? "You can send your next question now; Nova will queue it until this answer completes." : "Answers use your business data and retail guidance. Forecasts are estimates."}</footer>
    </section>}
  </>;
}
