# Model card: the business's assistant

A chat for a business that banks with Tameio: questions in plain Greek about its account, expenses, loans and the
bank's terms, answered from its own figures and the bank's documents.

| | |
| --- | --- |
| Task | A business owner's question in, a short answer in Greek out, with what was looked up and the document it came from |
| Model | Claude Opus 5.5 (`claude-opus-5-5`) through the Anthropic API, at low effort; the server-side fallback answers when the model declines for safety reasons |
| Orchestration | A LangGraph graph: Claude, tools, back to Claude, until it answers, at most six turns |
| Retrieval | The bank's documents (`ml/assistant/src/assistant/knowledge/`) split by LangChain's Markdown splitter, embedded with `paraphrase-multilingual-MiniLM-L12-v2` as ONNX (fastembed), searched by cosine similarity |
| Code | `ml/assistant/` (service), `backend/.../assistant/` (the endpoint that checks who asks), `frontend/src/features/assistant/` |
| Decision | [ADR 0012](../adr/0012-assistant-reads-only-for-the-signed-in-business.md) |

## What it can and cannot do

| It can | It cannot |
| --- | --- |
| Give balances, the latest movements, expenses by category and VAT, loan status and the next instalment | Move money, apply for or approve a loan, change anything |
| Explain the bank's terms (rates, amounts, how a loan is reviewed, VAT, security) and cite the document | See another business, users, passwords, staff or documents |
| Say it does not know when the documents do not say | Give tax, legal or investment advice |

## How it is kept safe

- **The business is never the model's to choose.** The backend checks that the signed-in user belongs to the
  business and sends that business with the question; no tool takes a business as an argument, so no prompt can
  point it at another one.
- **Read only, in the database too.** The service connects as `tameio_assistant`, a role that can only read the six
  tables its tools need. A test checks the privileges.
- **Short conversations.** At most twenty messages of up to 2,000 characters, user and assistant only (a
  `system` message is refused), and at most six calls to Claude per question.
- **Said plainly to the user.** The panel says the answers are written by AI, may be wrong, and never approve a loan
  or move money; a credit decision is always a person's (ADR 0005).

## Evaluation

`python -m assistant.evals` measures **retrieval**: twelve questions, each with the document that answers it; a hit
is that document among the three passages returned. Today 11 of 12 (92%); the miss is "how do I send my expenses
to my accountant?", whose answer (the CSV export) is in a section about the expense book. A test requires at least
90%, and runs in CI with the real embedding model.

`python -m assistant.evals --agent` runs the **agent** against a sample database of two businesses, through the
Claude API (a few cents). Each case checks the tool the question needs was called, the figure from it is in the
answer, and nothing it must not say is: the other business's figures, a money transfer, a loan promise.

The agent's loop is also tested with a scripted stand-in for Claude, so the tests need no key and cost nothing: a
tool call and its answer, the request's model, effort, fallbacks and strict tool schemas, a refusal, a failing tool,
and the cap on turns.

## Observability

Prometheus scrapes `/metrics/`: calls to Claude by stop reason, input, output and cached tokens, tool calls by tool,
and the time of each call and each answer; Grafana already reads Prometheus.

## Limits

- **The bank's documents are short and invented**, written for this demo; retrieval is measured on them only.
- **Every answer costs a call to a hosted model**; without an API key the assistant says it is not configured and
  the rest of the app is unaffected.
- **The conversation is not stored**: it lives in the browser and goes with each question, so it ends with the page.
