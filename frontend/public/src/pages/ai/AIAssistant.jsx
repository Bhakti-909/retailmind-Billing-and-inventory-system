import "./aiAssistant.css";
import "./novaArchitecture.css";

const capabilities = [
  { number: "01", title: "Business Analyst", detail: "Understand sales, gross profit, customer activity, and report trends using your business records." },
  { number: "02", title: "Smart Inventory Advisor", detail: "Estimate reorder quantities from current stock and product sales over the last 28 days, with a clear 14-day cover target." },
  { number: "03", title: "Sales Predictor", detail: "Estimate the next seven days from recent daily sales and weekday patterns." },
  { number: "04", title: "Profit Advisor", detail: "Find products with low recorded margins and review practical ways to improve profitability." },
  { number: "05", title: "AI Business Assistant", detail: "Ask questions in your own words and get answers grounded in the signed-in business data." },
];

const flowSteps = [
  { number: "01", title: "Nova chat", detail: "The signed-in user sends a question and recent conversation context." },
  { number: "02", title: "API Gateway", detail: "Forwards the JWT and Business ID, then applies request limits." },
  { number: "03", title: "Orchestrator", detail: "Understands intent and selects only the tools the question needs." },
  { number: "04", title: "Agents + model", detail: "Runs only required agents, then selects Qwen 2.5 1.5B or Llama 3.2 3B for the request." },
  { number: "05", title: "Response stream", detail: "Composes a grounded answer and streams it back to the chat." },
];

const agents = [
  { title: "Data Agent", detail: "Read-only, business-scoped sales, stock, customer, supplier and invoice queries." },
  { title: "Forecast Agent", detail: "Sales history and weekday patterns produce a seven-day estimate." },
  { title: "Analytics Agent", detail: "Calculations and margin insights use the values provided by business records." },
  { title: "Retail Advisor", detail: "Retrieves retail guidance when a question needs general business advice." },
];

export default function AIAssistant() {
  return (
    <main className="ai-agent-page">
      <header className="nova-intro">
        <div className="nova-intro-mark" aria-hidden="true">N</div>
        <div>
          <p className="nova-eyebrow">NOVA · SMART RETAIL ASSISTANT</p>
          <h1>Nova काय करेल?</h1>
          <p className="nova-description">Ask questions about your store and get clear answers based on its sales, products, and business records.</p>
        </div>
        <button type="button" onClick={() => window.dispatchEvent(new Event("open-ai-agent"))}>Open Nova <span aria-hidden="true">→</span></button>
      </header>
      <section className="nova-architecture" aria-labelledby="nova-architecture-title">
        <div className="nova-architecture-heading">
          <div><p className="nova-eyebrow">HOW NOVA WORKS</p><h2 id="nova-architecture-title">Request architecture</h2><p>Each question follows this path, with business data protected by the signed-in session.</p></div>
          <span className="nova-stream-badge"><i /> Streaming workflow</span>
        </div>
        <ol className="nova-flow-steps">
          {flowSteps.map((step) => <li key={step.number}>
            <span className="nova-flow-number">{step.number}</span><h3>{step.title}</h3><p>{step.detail}</p>
          </li>)}
        </ol>
        <div className="nova-architecture-lower">
          <div className="nova-agent-area">
            <div className="nova-subsection-heading"><span>AGENT LAYER</span><small>Only relevant tools are selected</small></div>
            <div className="nova-agent-grid">
              {agents.map((agent) => <article className="nova-agent-card" key={agent.title}><span aria-hidden="true">✦</span><div><h3>{agent.title}</h3><p>{agent.detail}</p></div></article>)}
            </div>
          </div>
          <aside className="nova-platform-area">
            <div className="nova-subsection-heading"><span>PLATFORM SERVICES</span><small>Data and model runtime</small></div>
            <div className="nova-platform-list">
              <div><b>PostgreSQL</b><span>Business records</span></div>
              <div><b>Redis</b><span>Request limits</span></div>
              <div><b>Ollama</b><span>Local language models</span></div>
            </div>
          </aside>
        </div>
        <p className="nova-architecture-note">Fast-model fallback is used only if the selected fast model fails before it starts streaming. Sales facts come from tenant-scoped tools; forecasts are estimates.</p>
      </section>
      <section className="nova-capabilities" aria-label="Nova capabilities">
        {capabilities.map((capability) => (
          <article className="nova-capability" key={capability.number}>
            <span className="nova-capability-number">{capability.number}</span>
            <div><h2>{capability.title}</h2><p>{capability.detail}</p></div>
          </article>
        ))}
      </section>
      <p className="nova-data-note">Answers use the data available to your signed-in business. Forecasts and stock suggestions are estimates.</p>
    </main>
  );
}
