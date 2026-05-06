# RealTime Conversation Analytics — Complete Data Model

> A single-document specification of the conversation-analytics data model: input schemas, the canonical lifecycle, the per-market aggregate metrics (Output 1), the per-conversation latest-state (Output 2), and a fully worked end-to-end example.

---

## Table of contents

1.  [What this system does](#1-what-this-system-does)
2.  [Input data model](#2-input-data-model)
    1.  [Input 1 — VOL / CBOL master lifecycle document](#21-input-1--vol--cbol-master-lifecycle-document)
    2.  [Input 2 — non-VOL/CBOL channel-side enrichment document](#22-input-2--non-volcbol-channel-side-enrichment-document)
    3.  [Conversation flow model — the canonical lifecycle](#23-conversation-flow-model--the-canonical-lifecycle)
    4.  [Channel token categorisation](#24-channel-token-categorisation)
3.  [Output 1 — per-market aggregate metrics](#3-output-1--per-market-aggregate-metrics)
4.  [Output 2 — per-conversation latest state](#4-output-2--per-conversation-latest-state)
5.  [Worked example — three calls end-to-end](#5-worked-example--three-calls-end-to-end)
    1.  [Cast](#51-cast)
    2.  [Event ledger](#52-event-ledger)
    3.  [Final state of every input MongoDB document](#53-final-state-of-every-input-mongodb-document)
    4.  [Final Output 1 bucket values](#54-final-output-1-bucket-values)
    5.  [Final Output 2 — per-conversation hashes](#55-final-output-2--per-conversation-hashes)

---

## 1. What this system does

Every voice call and chat conversation in the contact-centre platform emits a stream of MongoDB Change-Data-Capture (CDC) events as it progresses through IVR, bots and live agents. This data model consumes those events in real time and produces two outputs continuously:

- **Output 1 — per-market metric buckets** (Redis hash per bucket key, e.g. `UK_FD:1H`). Powers live operational dashboards and trend reporting.
- **Output 2 — per-conversation latest state** (Redis hash per `conversationId`). Powers drill-down, audit and search.

All metrics update in real time as the underlying events arrive; there is no batch lag.

### At a glance

| Quantity | Value | What it is |
| --- | --- | --- |
| Input fields consumed | **11** | end-to-end |
| Output 1 metrics | **33 + 2** | aggregates + 2 timeline series |
| Output 2 fields | **35** | per conversation |
| Time windows per market | configurable | e.g. 1 hour, 24 hour, 30 day |
| End-to-end latency | real-time | from CDC event to dashboard |
| Storage | Redis | hash per bucket / hash per conversation |

### How data flows

Two families of MongoDB documents arrive as CDC events and are routed by their `organization` field:

- `organization == "VOL"`  →  voice-conversation lifecycle (master doc)
- `organization == "CBOL"` →  chat-conversation lifecycle (master doc)
- `organization == anything else` (e.g. `"IVR"`, `"VOL_GENESYS"`, `"CIVR_SIERRA"`, `"CBOL_GENESYS"`) →  channel-side enrichment (intent / sentiment)

Master VOL/CBOL docs drive the lifecycle counters via their `conversationStatus`, `interactionStatus` and `channel` arrays. Channel-side docs contribute only `intent` and `sentiment` enrichment, joined back to the master via shared `conversationId`.

### Questions this model answers in real time

| Theme | Examples |
| --- | --- |
| Operational | How many conversations are currently in progress in a given market like UK or HK?  What's the per-channel-containment rate over configurable time spans?  How many chat/voice conversations escalated from chatbot to agent in a given timespan? |
| Customer experience | What are customers calling about right now? (live intent distribution)  How has sentiment data been processed in the last hour?  Which intents end most often in voicebot containment vs agent escalation? |
| Performance | What's the average voicebot handling time across all calls today?  How many voice-bot-to-agent transfers happened in a given timespan?  When during the day do call volumes spike? (voice / chat call timelines) |
| Drill-down | Given a specific `conversationId`, can show its full channel path, transfer history, and per-channel handling times — without scanning raw CDC logs. |

### Design principles

- **Real-time first** — every metric is a simple in-memory delta on a Redis hash; no batch jobs, no recomputation.
- **Idempotent** — duplicate CDC events are safely absorbed; first-insert counters are guarded against re-emission.
- **Time-windowed by construction** — rolling windows are buckets, not queries; windows roll over naturally.
- **Separation of concerns** — Output 1 (aggregate trends) and Output 2 (per-conversation drill-down) are decoupled and can scale independently.
- **Schema-bounded** — only 11 input fields are consumed; everything else flowing through CDC is ignored.  Adding a new metric is a localised change.

---

## 2. Input data model

**Source:** MongoDB CDC events (insert / update). Each event represents one conversation document. Two document families exist (see §1). Throughout this model, any rule written for VOL applies symmetrically to CBOL unless explicitly noted. **Only 11 fields are consumed end-to-end:** `conversationId`, `market`, `createdDateTime`, `lastModifiedDateTime`, `conversationStatus`, `interactionStatus`, `channel`, `customerId`, `intent`, `sentiment`, `organization` (intent & sentiment are not used for VOL/CBOL events).

### 2.1 Input 1 — VOL / CBOL master lifecycle document

These documents carry the call/chat lifecycle (status transitions, channel transitions). VOL = voice; CBOL = chat-manager.

| # | Field | Schema / type | Used? | Purpose in the model | Notes |
| --- | --- | --- | --- | --- | --- |
| 1 | conversationId | String | Yes | Stable identifier; primary key for joining lifecycle, intent and sentiment events. |  |
| 2 | organization | String  ('VOL' \| 'CBOL') | Yes | Routes the document into the VOL/CBOL lifecycle pipeline. | VOL ⇒ voice. CBOL ⇒ chat-manager layer. |
| 3 | conversationType | String  ('VOICE' \| 'CHAT') | Yes | Drives total_voice / total_chat counters (incremented once on first insert). |  |
| 4 | market | String  (e.g. 'UK_FD','HK_FD','US_FD') | Yes | Used to pick the market portion of the bucket key (e.g. UK_FD:1H, HK_FD:24H). |  |
| 5 | createdDateTime | DateTime | Yes | Used for first-insert bucketing relative to 'now' (1H / 24H / 30D windows). |  |
| 6 | lastModifiedDateTime | DateTime | Yes | Wall-clock of the current update event. |  |
| 7 | conversationStatus  (cS) | Array of single-key objects, append-only: <br> [ {NEW: ts}, {INITIATED: ts}, {TRANSFERRED: ts}, … , {CLOSED\|ENDED_BY_CUSTOMER\|ENDED_BY_SYSTEM\|ENDED: ts} ] | Yes | Drives stage detection. Latest entry's KEY tells us which stage we're in. | Closed-like keys: CLOSED, ENDED_BY_CUSTOMER, ENDED_BY_SYSTEM, ENDED. |
| 8 | interactionStatus  (iS) | Array of single-key objects, append-only: <br> [ {INITIATED: ts}, {TRANSFERRED: ts}, {INITIATED: ts}, … , {CLOSED-like: ts} ] | Yes | Mirror of cS but from the flow's perspective. Combined with cS to disambiguate the stage. |  |
| 9 | channel | Array of single-key objects, append-only: <br> [ {CHANNEL_NAME: ts}, … , {VOL: ts} ] (CBOL ends with {CBOL: ts}) | Yes | Each entry is a channel hop. Last entry returns to VOL/CBOL on closure (treated as a closing marker, not as a real channel for transferred / contained logic). Latest real entry's KEY identifies the current channel. | Used for transferred / contained / in_progress / handling-time metrics. |
| 10 | customerId | String | Yes | Carried through; not used for any aggregate calculation in this model. |  |
| 11 | intent | Array  (not used on VOL/CBOL) | No | Ignored on VOL/CBOL events. |  |
| 12 | sentiment | Array  (not used on VOL/CBOL) | No | Ignored on VOL/CBOL events. |  |

### 2.2 Input 2 — non-VOL/CBOL channel-side enrichment document

These documents carry intent / sentiment enrichment from a specific channel system (IVR, voicebot, chatbot, voiceagent, chatagent). They are joined back to the conversation via shared `conversationId`. Documents without an `intent` *or* `sentiment` array contribute nothing.

| # | Field | Schema / type | Used? | Purpose in the model | Notes |
| --- | --- | --- | --- | --- | --- |
| 1 | conversationId | String | Yes | Joins the enrichment back to the VOL/CBOL conversation. |  |
| 2 | organization | String  (anything other than 'VOL' / 'CBOL') | Yes | Routes the document into the intent/sentiment pipeline. Examples seen: IVR, VOL_GENESYS, CIVR_SIERRA, etc. |  |
| 3 | market | String | Yes | Bucket key market component. |  |
| 4 | createdDateTime | DateTime | Yes | Bucket key time component. |  |
| 5 | intent | MongoDB shape: <br> [ { intentId, intent, timestamp }, … ] <br> Logical shape (what we extract): <br> [ { INTENT_VALUE : ts }, … ] | Yes (if present) | Take the entry with the latest 'timestamp'; its 'intent' value becomes the conversation's current intent and is folded into the intent frequency map. | Skip the document if the field is absent / empty. |
| 6 | sentiment | MongoDB shape: <br> [ { sentimentId, sentiment, timestamp }, … ]  (analogous to intent) <br> Logical shape: <br> [ { SENTIMENT_VALUE : ts }, … ] | Yes (if present) | Same logic as intent, on the sentiment field. | Skip if field absent / empty. |
| 7 | conversationStatus / interactionStatus / channel / conversationType / customerId / lastModifiedDateTime | — | No | Ignored on non VOL/CBOL events. |  |
| Update # | conversationStatus  (cS) | interactionStatus  (iS) | channel | Semantic meaning | Metric impact |
| 1 (insert) | {NEW: t1} | {INITIATED: t1} | (empty) | Conversation created; flow initiated inside VOL/CBOL. | Triggers: total +1, total_voice / total_chat +1, new_card +1, in_progress_card +1, voice / chat call timeline +1. |
| 2 | + {INITIATED: t2} | + {TRANSFERRED: t2} | + {CHANNEL_1: t2} | Flow leaves VOL/CBOL and is initiated inside CHANNEL_1. | Triggers: <CHANNEL_1>_in_progress +1 (no previous channel category, so no offsetting −1). |

### 2.3 Conversation flow model — the canonical lifecycle

This is the **source-of-truth** for how `conversationStatus` (cS), `interactionStatus` (iS) and `channel` evolve on the master VOL/CBOL doc as a conversation progresses. **Every master cS update is paired with an iS update; updates 2 through n also carry a single `channel` append.** There is no cS-only or iS-only master event.

| Update # | cS append | iS append | channel append | Semantic meaning | Metric impact |
| --- | --- | --- | --- | --- | --- |
| 1 (insert) | {NEW: t1} | {INITIATED: t1} | (empty) | Conversation created; flow initiated inside VOL/CBOL. | Triggers: total +1, total_voice / total_chat +1, new_card +1, in_progress_card +1, voice / chat call timeline +1. |
| 2 | + {INITIATED: t2} | + {TRANSFERRED: t2} | + {CHANNEL_1: t2} | Flow leaves VOL/CBOL and is initiated inside CHANNEL_1. | Triggers: <CHANNEL_1>_in_progress +1 (no previous channel category, so no offsetting −1). |
| 3 … n−1 | + {TRANSFERRED: ti} | + {INITIATED: ti} | + {CHANNEL_i: ti} | Hop from previous channel into CHANNEL_i. | On the FIRST cS append whose key is not NEW/INITIATED, new_card −1.  <CHANNEL_i>_in_progress +1 and −1 for the previous channel category (the entry just before in the channel array). |
| n  (close) | + {CLOSED \| ENDED_BY_CUSTOMER \| ENDED_BY_SYSTEM \| ENDED: tn} | + {CLOSED-like: tn} | + {VOL \| CBOL: tn} | Conversation closes; flow returns to VOL/CBOL. The trailing VOL/CBOL entry is a closing marker, not a real channel hop. | Triggers: closed_card +1, in_progress_card −1, ended_by_customer / ended_by_system +1 (no extra in_progress decrement). All *_in_progress channel flags decrement to 0 (latest channel category for this conversation is undone). transferred / contained / *_to_*_transfer / handling-time metrics finalised from the full channel array. |

**Closed-like cS keys:** `CLOSED`, `ENDED_BY_CUSTOMER`, `ENDED_BY_SYSTEM`, `ENDED`. Any of these as the latest cS append finalises the conversation.

**`new_card` decrement timing:** fires on the FIRST cS append whose key is not in `{NEW, INITIATED}` — i.e. usually on the first `TRANSFERRED`, but on `CLOSED` if the conversation closes straight from its first channel without ever transferring elsewhere.

**Closing-marker convention:** the trailing `{VOL: ts}` (or `{CBOL: ts}` for chat) entry on the channel array is a closing marker, NOT a real channel hop. For *_contained metrics, when `channel[-1].key ∈ {VOL, CBOL}`, fall back to `channel[-2]` as the 'last real channel'; otherwise use `channel[-1]`.

### 2.4 Channel token categorisation

How a `channel`-array key (e.g. `"VOICEAGENTCLOUD"`, `"CIVR_SIERRA"`) is mapped to a category (IVR / VOICEBOT / VOICEAGENT / CHATBOT / CHATAGENT). Substring match, case-insensitive.

| Category | Applies to | Match rule on the channel-array key | Drives metrics |
| --- | --- | --- | --- |
| IVR | VOL  only | Token contains 'IVR'. | total_ivr_* |
| VOICEBOT | VOL  only | Token contains 'BOT' or 'SIERRA'  (and is NOT a CBOL chatbot token). | total_voicebot_* |
| VOICEAGENT | VOL  only | Token contains 'GENESYS', 'VOL_GENESYS', or 'VOICEAGENTCLOUD'. | total_voiceagent_* |
| CHATBOT | CBOL only | Token contains 'BOT', 'SIERRA', 'CBOT', 'CHATBOT', or 'CIVR_SIERRA'. | total_chatbot_* |
| CHATAGENT | CBOL only | Token contains 'GENESYS', 'CHATAGENT', 'CHATAGENTCLOUD', or 'CBOL_GENESYS'. | total_chatagent_* |
| VOL / CBOL | Both | Terminal entry written on closure when the flow returns home. Treated as a closing marker, not a real channel. | Marks closure; never counted as a 'channel hop'. 'Last real channel' rule for *_contained metrics: if channel[-1].key ∈ {VOL, CBOL} → use channel[-2]; otherwise fallback to channel[-1]. |

---

## 3. Output 1 — per-market aggregate metrics

**Storage shape:** one Redis hash per bucket key. Bucket key = `MARKET:WINDOW`, e.g. `UK_FD:1H`, `UK_FD:24H`, `UK_FD:30D`, `HK_FD:1H`, etc.

**Routing rule per CDC event:** `bucket_market = doc.market`; the same delta is applied to every rolling window the event currently fits inside (1H, 24H, 30D), determined by `now − doc.createdDateTime`. Aggregates roll over naturally as time advances.

**Handling-time semantics:** handling-time fields are summed; a separate `_count` companion is assumed for averages.

### 3.1 Bucket key examples

| Bucket key | Market | Window | Membership rule |
| --- | --- | --- | --- |
| UK_FD:1H | UK_FD | Last 1 hour | events with createdDateTime ≥ now − 1h |
| UK_FD:24H | UK_FD | Last 24 hours | events with createdDateTime ≥ now − 24h |
| UK_FD:30D | UK_FD | Last 30 days | events with createdDateTime ≥ now − 30d |
| HK_FD:1H | HK_FD | Last 1 hour | events with createdDateTime ≥ now − 1h |
| HK_FD:24H | HK_FD | Last 24 hours | events with createdDateTime ≥ now − 24h |
| HK_FD:30D | HK_FD | Last 30 days | events with createdDateTime ≥ now − 30d |

### 3.2 Metric definitions  (33 metrics + 2 timelines)

Each metric below is shown with an **inline worked-example column** — the value the metric would take when processing a single sample VOL conversation (the 'Doc 1' example from the Input sheet, where one VOL conversation goes IVR → VOICEAGENTCLOUD → IVR → close, with one intent-bearing non-VOL doc). This is a *different* example from the larger three-call worked example in §5; it is kept here only to make every metric definition concrete. For the larger end-to-end example, see §5.4.

| # | Metric | Source org | Type | Trigger stage | Calculation logic | Increment | Decrement / coupled change | Affected by input fields | Inline example value | Inline example derivation |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | total | VOL & CBOL | Integer | First INSERT of the conversation | Once per conversationId, on the very first VOL/CBOL doc received. | +1 | — | organization, conversationId, market, createdDateTime | 1 | First INSERT of the VOL doc (cS=[NEW]). +1 to total. |
| 2 | in_progress_card | VOL & CBOL | Integer | First INSERT (and on closure) | +1 once per conversation on first INSERT; −1 on closure update. | +1 on first insert | −1 on CLOSED-like cS append | organization, conversationId, market, createdDateTime, conversationStatus | 0 | +1 on first INSERT, −1 when cS appended CLOSED. |
| 3 | closed_card | VOL & CBOL | Integer | CLOSED-like cS append | Latest cS entry KEY ∈ {CLOSED, ENDED_BY_CUSTOMER, ENDED_BY_SYSTEM, ENDED}. | +1 | — | organization, market, createdDateTime, conversationStatus | 1 | Latest cS key on the VOL doc is CLOSED → +1. |
| 4 | total_ended_by_customer | VOL & CBOL | Integer | CLOSED-like cS append | Latest cS entry KEY == ENDED_BY_CUSTOMER. | +1 | — | organization, market, createdDateTime, conversationStatus | 0 | Latest cS key is CLOSED, not ENDED_BY_CUSTOMER. |
| 5 | total_ended_by_system | VOL & CBOL | Integer | CLOSED-like cS append | Latest cS entry KEY == ENDED_BY_SYSTEM. | +1 | — | organization, market, createdDateTime, conversationStatus | 0 | Latest cS key is CLOSED, not ENDED_BY_SYSTEM. |
| 6 | new_card | VOL & CBOL | Integer | Every cS update | +1 on first insert (cS=[NEW]); stays while latest key ∈ {NEW, INITIATED}; −1 on the first cS append whose key is NOT NEW/INITIATED. | +1 on first insert | −1 on the cS append that moves it past NEW/INITIATED | organization, conversationId, market, createdDateTime, conversationStatus | 0 | +1 at first INSERT (cS=[NEW]); −1 on the cS append where key=TRANSFERRED (third cS entry). |
| 7 | total_voice | VOL | Integer | First INSERT | First INSERT && conversationType == 'VOICE'. | +1 | — | organization, conversationId, conversationType, market, createdDateTime | 1 | conversationType=VOICE on the first VOL INSERT → +1. |
| 8 | total_chat | CBOL | Integer | First INSERT | First INSERT && conversationType == 'CHAT'. | +1 | — | organization, conversationId, conversationType, market, createdDateTime | 0 | No CBOL document in this conversation. |
| 9 | intent_distribution | non VOL/CBOL | Map<intent, percentage> | Every non-VOL/CBOL event with non-null intent | Maintain (a) freq map of LATEST intent per conversation and (b) count of conversations with non-null intent. Reported value = freq[intent] / total_with_intent × 100. | freq[new_latest_intent] +1; if conv had a previous latest intent, freq[old_latest_intent] −1 | — | organization, conversationId, intent, market, createdDateTime | { "Customer_Correspondence address_Amend" : 100.0% } | Doc 4 (organization=IVR, non VOL/CBOL) carries an intent array. Latest entry by 'timestamp' is at 2026-04-30T08:57:43.734Z → 'Customer_Correspondence address_Amend'. Only one conversation contributes to intent so far → 100.0%. |
| 10 | sentiment_distribution | non VOL/CBOL | Map<sentiment, percentage> | Every non-VOL/CBOL event with non-null sentiment | Same logic as intent_distribution but on the sentiment field. | freq[new_latest_sentiment] +1; freq[old_latest_sentiment] −1 if it had one | — | organization, conversationId, sentiment, market, createdDateTime | { } | No sentiment field on any of the 4 documents. |
| 11 | total_ivr_transferred | VOL | Integer | CLOSED-like cS append | Channel array contains an IVR token AND that IVR token is NOT the last real channel entry. | +1 | — | organization, market, createdDateTime, conversationStatus, channel | 1 | Channel array has IVR at index 0 (not last real channel) → +1. |
| 12 | total_voicebot_transferred | VOL | Integer | CLOSED-like cS append | Channel array contains a VOICEBOT token (BOT / SIERRA) AND it's not last. | +1 | — | organization, market, createdDateTime, conversationStatus, channel | 0 | No VOICEBOT token in the channel array. |
| 13 | total_voiceagent_transferred | VOL | Integer | CLOSED-like cS append | Channel array contains a VOICEAGENT token (GENESYS / VOL_GENESYS / VOICEAGENTCLOUD) AND it's not last. | +1 | — | organization, market, createdDateTime, conversationStatus, channel | 1 | VOICEAGENTCLOUD at index 1 (not last real channel) → +1. |
| 14 | total_chatbot_transferred | CBOL | I