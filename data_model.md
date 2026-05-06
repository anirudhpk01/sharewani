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
| 14 | total_chatbot_transferred | CBOL | Integer | CLOSED-like cS append | Channel array contains a CHATBOT token (BOT / SIERRA / CBOT / CHATBOT / CIVR_SIERRA) AND it's not last. | +1 | — | organization, market, createdDateTime, conversationStatus, channel | 0 | Not a CBOL conversation. |
| 15 | total_chatagent_transferred | CBOL | Integer | CLOSED-like cS append | Channel array contains a CHATAGENT token (GENESYS / CHATAGENT / CHATAGENTCLOUD / CBOL_GENESYS) AND it's not last. | +1 | — | organization, market, createdDateTime, conversationStatus, channel | 0 | Not a CBOL conversation. |
| 16 | total_ivr_contained | VOL | Integer | CLOSED-like cS append | Last real channel entry is an IVR token. Last real = channel[-2] if channel[-1].key == 'VOL' (closing marker); else fallback to channel[-1]. | +1 | — | organization, market, createdDateTime, conversationStatus, channel | 1 | channel[-1].key == 'VOL' (closing marker) → fallback to channel[-2] = IVR. → +1. |
| 17 | total_voicebot_contained | VOL | Integer | CLOSED-like cS append | Last real channel entry is a VOICEBOT token (BOT / SIERRA). Same last-real rule with VOL closing marker. | +1 | — | organization, market, createdDateTime, conversationStatus, channel | 0 | Last real channel ≠ a VOICEBOT token. |
| 18 | total_voiceagent_contained | VOL | Integer | CLOSED-like cS append | Last real channel entry is a VOICEAGENT token. Same last-real rule with VOL closing marker. | +1 | — | organization, market, createdDateTime, conversationStatus, channel | 0 | Last real channel = IVR, not a VOICEAGENT token. |
| 19 | total_chatbot_contained | CBOL | Integer | CLOSED-like cS append | Last real channel entry is a CHATBOT token. Last real = channel[-2] if channel[-1].key == 'CBOL'; else fallback to channel[-1]. | +1 | — | organization, market, createdDateTime, conversationStatus, channel | 0 | Not a CBOL conversation. |
| 20 | total_chatagent_contained | CBOL | Integer | CLOSED-like cS append | Last real channel entry is a CHATAGENT token. Same last-real rule with CBOL closing marker. | +1 | — | organization, market, createdDateTime, conversationStatus, channel | 0 | Not a CBOL conversation. |
| 21 | total_ivr_in_progress | VOL | Integer | Non-closing cS update where channel changed | len(cS) > 1, conversation NOT yet closed, latest channel entry's key is an IVR token. | +1 for IVR | −1 for the previous channel category (the entry just before IVR in the channel array) | organization, market, createdDateTime, conversationStatus, channel | 0 | Net deltas through the lifecycle: +1 (hop to IVR), −1 (hop to VOICEAGENTCLOUD), +1 (hop back to IVR), −1 (closing VOL hop) = 0. |
| 22 | total_voicebot_in_progress | VOL | Integer | Non-closing cS update where channel changed | Same as #21 but latest channel category == VOICEBOT. | +1 | −1 previous channel category | organization, market, createdDateTime, conversationStatus, channel | 0 | Never visited. |
| 23 | total_voiceagent_in_progress | VOL | Integer | Non-closing cS update where channel changed | Same as #21 but latest channel category == VOICEAGENT. | +1 | −1 previous channel category | organization, market, createdDateTime, conversationStatus, channel | 0 | Net deltas: +1 (hop to VOICEAGENTCLOUD), −1 (hop to IVR) = 0. |
| 24 | total_chatbot_in_progress | CBOL | Integer | Non-closing cS update where channel changed | Same as #21 but latest channel category == CHATBOT. | +1 | −1 previous channel category | organization, market, createdDateTime, conversationStatus, channel | 0 | Not a CBOL conversation. |
| 25 | total_chatagent_in_progress | CBOL | Integer | Non-closing cS update where channel changed | Same as #21 but latest channel category == CHATAGENT. | +1 | −1 previous channel category | organization, market, createdDateTime, conversationStatus, channel | 0 | Not a CBOL conversation. |
| 26 | total_voicebot_to_agent_transfer | VOL | Integer | CLOSED-like cS append | Channel array contains a VOICEBOT entry whose index is strictly less than that of a VOICEAGENT entry. | +1 | — | organization, market, createdDateTime, conversationStatus, channel | 0 | No VOICEBOT entry in channel array, so no voicebot-before-voiceagent ordering possible. |
| 27 | total_chatbot_to_agent_transfer | CBOL | Integer | CLOSED-like cS append | Channel array contains a CHATBOT entry whose index is strictly less than that of a CHATAGENT entry. | +1 | — | organization, market, createdDateTime, conversationStatus, channel | 0 | Not a CBOL conversation. |
| 28 | total_voicebot_to_ivr_transfer | VOL | Integer | CLOSED-like cS append | Channel array contains a VOICEBOT entry whose index is strictly less than that of an IVR entry. | +1 | — | organization, market, createdDateTime, conversationStatus, channel | 0 | No VOICEBOT entry in channel array. |
| 29 | total_voiceagent_handling_time | VOL | Integer  (ms or s, fixed unit) | CLOSED-like cS append | Sum over channel array: for each VOICEAGENT entry, (next_entry.ts − this_entry.ts). | + Σ Δt | — | organization, market, createdDateTime, conversationStatus, channel  (with timestamps) | 13 519 ms | Δ from VOICEAGENTCLOUD@09:01:35.671 to next entry IVR@09:01:49.190  =  13 519 ms. |
| 30 | total_chatagent_handling_time | CBOL | Integer | CLOSED-like cS append | Same as #29 but for CHATAGENT entries. | + Σ Δt | — | organization, market, createdDateTime, conversationStatus, channel  (with timestamps) | 0 | Not a CBOL conversation. |
| 31 | total_voicebot_handling_time | VOL | Integer | CLOSED-like cS append | Same as #29 but for VOICEBOT entries. | + Σ Δt | — | organization, market, createdDateTime, conversationStatus, channel  (with timestamps) | 0 | Not visited. |
| 32 | total_chatbot_handling_time | CBOL | Integer | CLOSED-like cS append | Same as #29 but for CHATBOT entries. | + Σ Δt | — | organization, market, createdDateTime, conversationStatus, channel  (with timestamps) | 0 | Not a CBOL conversation. |
| 33 | total_ivr_handling_time | VOL | Integer | CLOSED-like cS append | Same as #29 but for IVR entries. | + Σ Δt | — | organization, market, createdDateTime, conversationStatus, channel  (with timestamps) | 26 926 ms | Σ over IVR entries:  (VAC@09:01:35.671 − IVR@09:01:20.596) = 15 075 ms  +  (VOL@09:02:01.041 − IVR@09:01:49.190) = 11 851 ms  =  26 926 ms. |
| 34 | voice_call_timeline | VOL | Array<{ts, count}> | First INSERT (VOL) | Append (or upsert) {ts: bucket-rounded createdDateTime, count: +1} on every first VOL insert. | +1 at ts | — | organization, conversationId, conversationType, createdDateTime, market | [ { ts: 2026-04-30T09:01:03.062,  count: 1 } ] | +1 at the VOL doc's createdDateTime when it was first INSERTed. |
| 35 | chat_call_timeline | CBOL | Array<{ts, count}> | First INSERT (CBOL) | Same as #34 but on first CBOL insert. | +1 at ts | — | organization, conversationId, conversationType, createdDateTime, market | [ ] | No CBOL inserts. |
| 1 | total | 1 | First INSERT of the VOL doc (cS=[NEW]). +1 to total. |  |  |  |  |  | 1 | First INSERT of the VOL doc (cS=[NEW]). +1 to total. |
| 2 | in_progress_card | 0 | +1 on first INSERT, −1 when cS appended CLOSED. |  |  |  |  |  | 0 | +1 on first INSERT, −1 when cS appended CLOSED. |
| 3 | closed_card | 1 | Latest cS key on the VOL doc is CLOSED → +1. |  |  |  |  |  | 1 | Latest cS key on the VOL doc is CLOSED → +1. |
| 4 | total_ended_by_customer | 0 | Latest cS key is CLOSED, not ENDED_BY_CUSTOMER. |  |  |  |  |  | 0 | Latest cS key is CLOSED, not ENDED_BY_CUSTOMER. |

---

## 4. Output 2 — per-conversation latest state

**Storage shape:** one Redis hash keyed by `conversationId`. Updated at the SAME stages as Output 1, but the contribution is the conversation's own latest state — every counter becomes a 0/1 flag (mutually exclusive within its group), and handling-time fields hold absolute durations for THIS conversation only.

**Excluded from Output 2:** `voice_call_timeline`, `chat_call_timeline`, `intent_distribution`, `sentiment_distribution`. These are aggregate-only.

**Naming convention:** drop the leading `total_` prefix used in Output 1 (e.g. `total_ivr_transferred → ivr_transferred`).

### 4.1 Field definitions  (per-conversation latest state)

Each field is shown with an **inline worked-example column** — the value the field would hold for the single 'Doc 1' VOL example used in §3 (a different, simpler example from the three-call walkthrough in §5; for the three-call values per conversation see §5.5).

| # | Field | Type | Trigger stage | Calculation / definition | Affected by input fields | Inline example value | Inline example derivation |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | conversationId | String  (key) | Every event | Conversation primary key. | conversationId | 385815b8-40b1-4875-8766-2f2defd84a5c | Doc 1, Doc 2, Doc 3, Doc 4 (all share this id). |
| 2 | market | String | First INSERT | Carried through from input for downstream filtering. | market | UK_FD | Carried from Doc 1 (first INSERT). |
| 3 | createdDateTime | DateTime | First INSERT | Stamped once on creation. | createdDateTime | 2026-04-30T09:01:03.062 | Carried from Doc 1 (first INSERT). |
| 4 | lastModifiedDateTime | DateTime | Every event | Updated on each CDC event (across ALL related docs sharing this conversationId). | lastModifiedDateTime | 2026-04-30T09:02:04.062 | Latest across all 4 docs — Doc 4's lastModifiedDateTime is the freshest. |
| 5 | in_progress | 0 / 1 flag | First INSERT (→1)  &  CLOSED (→0) | Mirrors Output 1 in_progress_card contribution for this conversation. | organization, conversationStatus | 0 | Set to 1 at first INSERT, cleared on CLOSED. |
| 6 | closed | 0 / 1 flag | CLOSED-like cS append | Set to 1 on closure; mutually exclusive with in_progress. | organization, conversationStatus | 1 | Latest cS key = CLOSED. |
| 7 | ended_by_customer | 0 / 1 flag | CLOSED-like cS append | 1 iff latest cS key == ENDED_BY_CUSTOMER. | organization, conversationStatus | 0 | Latest cS key ≠ ENDED_BY_CUSTOMER. |
| 8 | ended_by_system | 0 / 1 flag | CLOSED-like cS append | 1 iff latest cS key == ENDED_BY_SYSTEM. | organization, conversationStatus | 0 | Latest cS key ≠ ENDED_BY_SYSTEM. |
| 9 | is_new | 0 / 1 flag | Every cS update | 1 while latest cS key ∈ {NEW, INITIATED}; flips to 0 on first non-NEW/non-INITIATED append. Mirrors new_card. | organization, conversationStatus | 0 | cS has progressed past NEW/INITIATED (TRANSFERRED appended at update 3). |
| 10 | is_voice | 0 / 1 flag | First INSERT | 1 iff conversationType == 'VOICE'. | organization, conversationType | 1 | Doc 1 conversationType = VOICE. |
| 11 | is_chat | 0 / 1 flag | First INSERT | 1 iff conversationType == 'CHAT'. | organization, conversationType | 0 | Not a CBOL conversation. |
| 12 | ivr_transferred | 0 / 1 flag | CLOSED-like cS append (VOL) | 1 iff IVR appears in channel array and is not last real entry. | organization, conversationStatus, channel | 1 | IVR at channel[0] is not the last real entry. |
| 13 | voicebot_transferred | 0 / 1 flag | CLOSED-like cS append (VOL) | 1 iff a VOICEBOT token appears and is not last real entry. | organization, conversationStatus, channel | 0 | No VOICEBOT in channel array. |
| 14 | voiceagent_transferred | 0 / 1 flag | CLOSED-like cS append (VOL) | 1 iff a VOICEAGENT token appears and is not last real entry. | organization, conversationStatus, channel | 1 | VOICEAGENTCLOUD at channel[1] is not last real entry. |
| 15 | chatbot_transferred | 0 / 1 flag | CLOSED-like cS append (CBOL) | 1 iff a CHATBOT token appears and is not last real entry. | organization, conversationStatus, channel | 0 | Not CBOL. |
| 16 | chatagent_transferred | 0 / 1 flag | CLOSED-like cS append (CBOL) | 1 iff a CHATAGENT token appears and is not last real entry. | organization, conversationStatus, channel | 0 | Not CBOL. |
| 17 | ivr_contained | 0 / 1 flag | CLOSED-like cS append (VOL) | 1 iff the last real channel entry is an IVR token. Last real = channel[-2] if channel[-1].key == 'VOL'; else fallback to channel[-1]. | organization, conversationStatus, channel | 1 | channel[-1].key == 'VOL' → fallback to channel[-2] = IVR. → 1. |
| 18 | voicebot_contained | 0 / 1 flag | CLOSED-like cS append (VOL) | 1 iff the last real channel entry is a VOICEBOT token (same last-real rule with VOL closing marker). | organization, conversationStatus, channel | 0 | Last real ≠ VOICEBOT. |
| 19 | voiceagent_contained | 0 / 1 flag | CLOSED-like cS append (VOL) | 1 iff the last real channel entry is a VOICEAGENT token (same last-real rule with VOL closing marker). | organization, conversationStatus, channel | 0 | Last real = IVR, not VOICEAGENT. |
| 20 | chatbot_contained | 0 / 1 flag | CLOSED-like cS append (CBOL) | 1 iff the last real channel entry is a CHATBOT token. Last real = channel[-2] if channel[-1].key == 'CBOL'; else fallback to channel[-1]. | organization, conversationStatus, channel | 0 | Not CBOL. |
| 21 | chatagent_contained | 0 / 1 flag | CLOSED-like cS append (CBOL) | 1 iff the last real channel entry is a CHATAGENT token (same last-real rule with CBOL closing marker). | organization, conversationStatus, channel | 0 | Not CBOL. |
| 22 | ivr_in_progress | 0 / 1 flag | Non-closing channel hop (VOL) | 1 while live channel category == IVR; flips off when conversation moves on or closes. | organization, conversationStatus, channel | 0 | Conversation closed; flag cleared. |
| 23 | voicebot_in_progress | 0 / 1 flag | Non-closing channel hop (VOL) | 1 while live channel category == VOICEBOT. | organization, conversationStatus, channel | 0 | Never live. |
| 24 | voiceagent_in_progress | 0 / 1 flag | Non-closing channel hop (VOL) | 1 while live channel category == VOICEAGENT. | organization, conversationStatus, channel | 0 | Was 1 between the VOICEAGENTCLOUD hop and the next IVR hop; cleared at close. |
| 25 | chatbot_in_progress | 0 / 1 flag | Non-closing channel hop (CBOL) | 1 while live channel category == CHATBOT. | organization, conversationStatus, channel | 0 | Not CBOL. |
| 26 | chatagent_in_progress | 0 / 1 flag | Non-closing channel hop (CBOL) | 1 while live channel category == CHATAGENT. | organization, conversationStatus, channel | 0 | Not CBOL. |
| 27 | voicebot_to_agent_transfer | 0 / 1 flag | CLOSED-like cS append (VOL) | 1 iff a VOICEBOT entry sits before a VOICEAGENT entry in channel array. | organization, conversationStatus, channel | 0 | No VOICEBOT in channel array. |
| 28 | chatbot_to_agent_transfer | 0 / 1 flag | CLOSED-like cS append (CBOL) | 1 iff a CHATBOT entry sits before a CHATAGENT entry in channel array. | organization, conversationStatus, channel | 0 | Not CBOL. |
| 29 | voicebot_to_ivr_transfer | 0 / 1 flag | CLOSED-like cS append (VOL) | 1 iff a VOICEBOT entry sits before an IVR entry in channel array. | organization, conversationStatus, channel | 0 | No VOICEBOT in channel array. |
| 30 | voiceagent_handling_time | Integer  (duration) | CLOSED-like cS append (VOL) | Σ over channel array of (next_ts − this_ts) for each VOICEAGENT entry. | organization, conversationStatus, channel  (with timestamps) | 13 519 ms | VOICEAGENTCLOUD@09:01:35.671 → IVR@09:01:49.190. |
| 31 | chatagent_handling_time | Integer | CLOSED-like cS append (CBOL) | Same calc on CHATAGENT entries. | organization, conversationStatus, channel  (with timestamps) | 0 | Not CBOL. |
| 32 | voicebot_handling_time | Integer | CLOSED-like cS append (VOL) | Same calc on VOICEBOT entries. | organization, conversationStatus, channel  (with timestamps) | 0 | Not visited. |
| 33 | chatbot_handling_time | Integer | CLOSED-like cS append (CBOL) | Same calc on CHATBOT entries. | organization, conversationStatus, channel  (with timestamps) | 0 | Not CBOL. |
| 34 | ivr_handling_time | Integer | CLOSED-like cS append (VOL) | Same calc on IVR entries. | organization, conversationStatus, channel  (with timestamps) | 26 926 ms | (VAC − IVR₀) 15 075 ms  +  (VOL − IVR₁) 11 851 ms. |
| 35 | channels_visited | Array<String>  (optional) | Every channel-hop update | Optional: ordered list of channel-array keys for debugging / replay. | organization, channel | [ "IVR", "VOICEAGENTCLOUD", "IVR", "VOL" ] | Ordered channel-array keys, including the closing VOL marker. |
| 1 | conversationId | 385815b8-40b1-4875-8766-2f2defd84a5c | Doc 1, Doc 2, Doc 3, Doc 4 (all share this id). |  |  | 385815b8-40b1-4875-8766-2f2defd84a5c | Doc 1, Doc 2, Doc 3, Doc 4 (all share this id). |
| 2 | market | UK_FD | Carried from Doc 1 (first INSERT). |  |  | UK_FD | Carried from Doc 1 (first INSERT). |
| 3 | createdDateTime | 2026-04-30T09:01:03.062 | Carried from Doc 1 (first INSERT). |  |  | 2026-04-30T09:01:03.062 | Carried from Doc 1 (first INSERT). |

---

## 5. Worked example — three calls end-to-end

Three concurrent calls run from `t=0s` onwards in market `UK_FD`. Every CDC event is processed in order; deltas applied to Output 1 (per-bucket totals) and Output 2 (per-conversation latest state) are shown in-line so a first-time reader can follow how each metric grows. All three windows {1H, 24H, 30D} of the `UK_FD` bucket receive the same delta — a single per-bucket ledger is shown.

### 5.1 Cast

| Call | conversationId | Type | Market | Path through channels | One-line summary |
| --- | --- | --- | --- | --- | --- |
| call1 | conv1   (= conversationId) | VOICE | UK_FD | VOL → IVR → BOT → GENESYS → VOL (CLOSED) | Long voice call, transferred IVR → voicebot → voiceagent before closing.  Three master VOL channel-array entries before close. |
| call2 | conv2 | VOICE | UK_FD | VOL → IVR → VOL (CLOSED) | Short, IVR-self-contained voice call.  One master VOL channel-array entry before close. |
| call3 | conv3 | CHAT | UK_FD | CBOL → BOT → GENESYS → CBOL (CLOSED) | Chat conversation, escalated chatbot → chatagent before closing.  Two master CBOL channel-array entries before close. |

### 5.2 Event ledger

INS = INSERT, UPD = UPDATE. Doc = `VOL` / `CBOL` master lifecycle doc, or `IVR` / `BOT` / `GENESYS` channel doc. Each event below is one CDC event; ⚙ = metric-changing event, ⚪ = received-but-skipped event (housekeeping only, no metric delta beyond bumping `Output 2.lastModifiedDateTime`).

#### ⚙ Event 1 — `t=09:00:00`, call1, doc=`VOL`, INS

**Relevant input fields received:**
```
conversationId=conv1, organization=VOL, conversationType=VOICE, market=UK_FD, createdDateTime=09:00:00,
cS=[{NEW: 09:00:00}], iS=[{INITIATED: 09:00:00}]
```
**Stage / what just happened:**  First INSERT — conversation NEW.

**Output 1 deltas (`UK_FD : metric ± value`):**
```
total +1, in_progress_card +1, new_card +1, total_voice +1,  voice_call_timeline += {ts: 09:00:00, count: +1}
```
**Output 2 deltas for this conversation (`field ← value`):**
```
in_progress=1, is_new=1, is_voice=1, market=UK_FD, createdDateTime=09:00:00, lastModifiedDateTime=09:00:00, channels_visited=[]
```

#### ⚙ Event 2 — `t=09:00:00`, call2, doc=`VOL`, INS

**Relevant input fields received:**
```
conversationId=conv2, organization=VOL, conversationType=VOICE, market=UK_FD, createdDateTime=09:00:00,
cS=[{NEW: 09:00:00}], iS=[{INITIATED: 09:00:00}]
```
**Stage / what just happened:**  First INSERT — conversation NEW.

**Output 1 deltas (`UK_FD : metric ± value`):**
```
total +1, in_progress_card +1, new_card +1, total_voice +1,  voice_call_timeline += {ts: 09:00:00, count: +1}
```
**Output 2 deltas for this conversation (`field ← value`):**
```
in_progress=1, is_new=1, is_voice=1, market=UK_FD, createdDateTime=09:00:00, lastModifiedDateTime=09:00:00, channels_visited=[]
```

#### ⚙ Event 3 — `t=09:00:00`, call3, doc=`CBOL`, INS

**Relevant input fields received:**
```
conversationId=conv3, organization=CBOL, conversationType=CHAT, market=UK_FD, createdDateTime=09:00:00,
cS=[{NEW: 09:00:00}], iS=[{INITIATED: 09:00:00}]
```
**Stage / what just happened:**  First INSERT — conversation NEW.

**Output 1 deltas (`UK_FD : metric ± value`):**
```
total +1, in_progress_card +1, new_card +1, total_chat +1,  chat_call_timeline += {ts: 09:00:00, count: +1}
```
**Output 2 deltas for this conversation (`field ← value`):**
```
in_progress=1, is_new=1, is_chat=1, market=UK_FD, createdDateTime=09:00:00, lastModifiedDateTime=09:00:00, channels_visited=[]
```

#### ⚪ Event 4 — `t=09:00:05`, call1, doc=`IVR`, INS

**Relevant input fields received:**
```
conversationId=conv1, organization=IVR, market=UK_FD, createdDateTime=09:00:05
(intent / sentiment absent)
```
**Stage / what just happened:**  Channel doc spins up. No enrichment yet.

**Output 1 deltas (`UK_FD : metric ± value`):**
```
(no metric impact)
```
**Output 2 deltas for this conversation (`field ← value`):**
```
(no metric impact — intent/sentiment not tracked in Output 2)
```

#### ⚪ Event 5 — `t=09:00:05`, call2, doc=`IVR`, INS

**Relevant input fields received:**
```
conversationId=conv2, organization=IVR, market=UK_FD, createdDateTime=09:00:05
(intent / sentiment absent)
```
**Stage / what just happened:**  Channel doc spins up.

**Output 1 deltas (`UK_FD : metric ± value`):**
```
(no metric impact)
```
**Output 2 deltas for this conversation (`field ← value`):**
```
(no metric impact)
```

#### ⚪ Event 6 — `t=09:00:05`, call3, doc=`BOT`, INS

**Relevant input fields received:**
```
conversationId=conv3, organization=CIVR_SIERRA, market=UK_FD, createdDateTime=09:00:05
(intent / sentiment absent)
```
**Stage / what just happened:**  Chatbot doc spins up.

**Output 1 deltas (`UK_FD : metric ± value`):**
```
(no metric impact)
```
**Output 2 deltas for this conversation (`field ← value`):**
```
(no metric impact)
```

#### ⚙ Event 7 — `t=09:00:10`, call1, doc=`IVR`, UPD

**Relevant input fields received:**
```
intent=[{Balance_Enquiry: 09:00:10}],  sentiment=[{NEUTRAL: 09:00:10}]
```
**Stage / what just happened:**  First intent/sentiment for conv1. Replace-on-update: prev latest = none.

**Output 1 deltas (`UK_FD : metric ± value`):**
```
intent_distribution[Balance_Enquiry] +1, sentiment_distribution[NEUTRAL] +1,
with_intent +1, with_sentiment +1
```
**Output 2 deltas for this conversation (`field ← value`):**
```
(intent/sentiment not tracked in Output 2; only lastModifiedDateTime would bump if it were the latest across all docs — but the master VOL is still at 09:00:00 so this 09:00:10 becomes Output 2.lastModifiedDateTime)
```

#### ⚙ Event 8 — `t=09:00:10`, call2, doc=`IVR`, UPD

**Relevant input fields received:**
```
intent=[{Card_Block_Request: 09:00:10}],  sentiment=[{NEUTRAL: 09:00:10}]
```
**Stage / what just happened:**  First intent/sentiment for conv2.

**Output 1 deltas (`UK_FD : metric ± value`):**
```
intent_distribution[Card_Block_Request] +1, sentiment_distribution[NEUTRAL] +1 (already from conv1) → freq[NEUTRAL]=2,
with_intent +1, with_sentiment +1
```
**Output 2 deltas for this conversation (`field ← value`):**
```
lastModifiedDateTime ← 09:00:10
```

#### ⚙ Event 9 — `t=09:00:10`, call3, doc=`BOT`, UPD

**Relevant input fields received:**
```
intent=[{Statement_Help: 09:00:10}],  sentiment=[{POSITIVE: 09:00:10}]
```
**Stage / what just happened:**  First intent/sentiment for conv3.

**Output 1 deltas (`UK_FD : metric ± value`):**
```
intent_distribution[Statement_Help] +1, sentiment_distribution[POSITIVE] +1,
with_intent +1, with_sentiment +1
```
**Output 2 deltas for this conversation (`field ← value`):**
```
lastModifiedDateTime ← 09:00:10
```

#### ⚙ Event 10 — `t=09:00:15`, call1, doc=`VOL`, UPD

**Relevant input fields received:**
```
cS += {INITIATED: 09:00:15},  iS += {TRANSFERRED: 09:00:15},
channel += {IVR: 09:00:15}   ← first channel hop
```
**Stage / what just happened:**  Hand-off: VOL → IVR.  Latest cS key still ∈ {NEW, INITIATED} → new_card stays 1.

**Output 1 deltas (`UK_FD : metric ± value`):**
```
total_ivr_in_progress +1   (no previous channel category → no offsetting −1)
```
**Output 2 deltas for this conversation (`field ← value`):**
```
ivr_in_progress=1, channels_visited=['IVR'], lastModifiedDateTime ← 09:00:15
```

#### ⚙ Event 11 — `t=09:00:15`, call2, doc=`VOL`, UPD

**Relevant input fields received:**
```
cS += {INITIATED: 09:00:15},  iS += {TRANSFERRED: 09:00:15},
channel += {IVR: 09:00:15}
```
**Stage / what just happened:**  Hand-off: VOL → IVR.

**Output 1 deltas (`UK_FD : metric ± value`):**
```
total_ivr_in_progress +1
```
**Output 2 deltas for this conversation (`field ← value`):**
```
ivr_in_progress=1, channels_visited=['IVR'], lastModifiedDateTime ← 09:00:15
```

#### ⚙ Event 12 — `t=09:00:15`, call3, doc=`CBOL`, UPD

**Relevant input fields received:**
```
cS += {INITIATED: 09:00:15},  iS += {TRANSFERRED: 09:00:15},
channel += {BOT: 09:00:15}
```
**Stage / what just happened:**  Hand-off: CBOL → chatbot.

**Output 1 deltas (`UK_FD : metric ± value`):**
```
total_chatbot_in_progress +1
```
**Output 2 deltas for this conversation (`field ← value`):**
```
chatbot_in_progress=1, channels_visited=['BOT'], lastModifiedDateTime ← 09:00:15
```

#### ⚙ Event 13 — `t=09:00:20`, call1, doc=`IVR`, UPD

**Relevant input fields received:**
```
intent=[…, {Balance_Enquiry: 09:00:20}],  sentiment=[…, {NEUTRAL: 09:00:20}]
(latest values unchanged)
```
**Stage / what just happened:**  Replace-on-update: latest intent / sentiment for conv1 are identical to before.

**Output 1 deltas (`UK_FD : metric ± value`):**
```
(no net change — freq decrement and increment cancel)
```
**Output 2 deltas for this conversation (`field ← value`):**
```
lastModifiedDateTime ← 09:00:20
```

#### ⚙ Event 14 — `t=09:00:20`, call2, doc=`IVR`, UPD

**Relevant input fields received:**
```
intent=[…, {Card_Block_Request: 09:00:20}],  sentiment=[…, {NEGATIVE: 09:00:20}]
(sentiment changed: NEUTRAL → NEGATIVE)
```
**Stage / what just happened:**  Replace-on-update: conv2's latest sentiment shifts.

**Output 1 deltas (`UK_FD : metric ± value`):**
```
sentiment_distribution: freq[NEUTRAL] −1, freq[NEGATIVE] +1
(latest intent unchanged → no net intent delta)
```
**Output 2 deltas for this conversation (`field ← value`):**
```
lastModifiedDateTime ← 09:00:20
```

#### ⚙ Event 15 — `t=09:00:20`, call3, doc=`BOT`, UPD

**Relevant input fields received:**
```
intent=[…, {Statement_Help: 09:00:20}],  sentiment=[…, {POSITIVE: 09:00:20}]
(latest values unchanged)
```
**Stage / what just happened:**  Replace-on-update: identical latest values.

**Output 1 deltas (`UK_FD : metric ± value`):**
```
(no net change)
```
**Output 2 deltas for this conversation (`field ← value`):**
```
lastModifiedDateTime ← 09:00:20
```

#### ⚙ Event 16 — `t=09:00:25`, call2, doc=`VOL`, UPD

**Relevant input fields received:**
```
cS += {CLOSED: 09:00:25},  iS += {CLOSED: 09:00:25},
channel += {VOL: 09:00:25}   ← closing marker
```
**Stage / what just happened:**  CLOSED — finalise call2.  This is also the FIRST non-NEW/INITIATED cS append for call2 → new_card −1. channel[-1].key=='VOL' → fallback rule for last real channel = channel[-2] = IVR. Real channels = [IVR] (single entry → IVR is also the only/last) → ivr_contained=1, ivr_transferred=0.

**Output 1 deltas (`UK_FD : metric ± value`):**
```
new_card −1, closed_card +1, in_progress_card −1, total_ivr_in_progress −1,
total_ivr_contained +1,  total_ivr_handling_time +10 000 ms  ((25−15)·1000)
```
**Output 2 deltas for this conversation (`field ← value`):**
```
is_new=0, in_progress=0, closed=1, ivr_in_progress=0, ivr_contained=1,
ivr_handling_time=10 000 ms, channels_visited=['IVR','VOL'], lastModifiedDateTime ← 09:00:25
```

#### ⚪ Event 17 — `t=09:00:30`, call1, doc=`BOT`, INS

**Relevant input fields received:**
```
conversationId=conv1, organization=CIVR_SIERRA, market=UK_FD, createdDateTime=09:00:30
(intent / sentiment absent)
```
**Stage / what just happened:**  New voicebot channel doc spins up for conv1.

**Output 1 deltas (`UK_FD : metric ± value`):**
```
(no metric impact)
```
**Output 2 deltas for this conversation (`field ← value`):**
```
(no metric impact)
```

#### ⚪ Event 18 — `t=09:00:30`, call3, doc=`GENESYS`, INS

**Relevant input fields received:**
```
conversationId=conv3, organization=CBOL_GENESYS, market=UK_FD, createdDateTime=09:00:30
(intent / sentiment absent)
```
**Stage / what just happened:**  New chatagent channel doc spins up for conv3.

**Output 1 deltas (`UK_FD : metric ± value`):**
```
(no metric impact)
```
**Output 2 deltas for this conversation (`field ← value`):**
```
(no metric impact)
```

#### ⚙ Event 19 — `t=09:00:35`, call1, doc=`BOT`, UPD

**Relevant input fields received:**
```
intent=[{Statement_Request: 09:00:35}],  sentiment=[{POSITIVE: 09:00:35}]
```
**Stage / what just happened:**  Voicebot updates conv1's latest intent/sentiment (replace-on-update).

**Output 1 deltas (`UK_FD : metric ± value`):**
```
intent_distribution: freq[Balance_Enquiry] −1, freq[Statement_Request] +1
sentiment_distribution: freq[NEUTRAL] −1, freq[POSITIVE] +1
```
**Output 2 deltas for this conversation (`field ← value`):**
```
lastModifiedDateTime ← 09:00:35
```

#### ⚙ Event 20 — `t=09:00:35`, call3, doc=`GENESYS`, UPD

**Relevant input fields received:**
```
intent=[{Statement_Resolved: 09:00:35}],  sentiment=[{POSITIVE: 09:00:35}]
```
**Stage / what just happened:**  Chatagent updates conv3's latest intent (Statement_Help → Statement_Resolved); sentiment unchanged.

**Output 1 deltas (`UK_FD : metric ± value`):**
```
intent_distribution: freq[Statement_Help] −1, freq[Statement_Resolved] +1
(sentiment unchanged → no delta)
```
**Output 2 deltas for this conversation (`field ← value`):**
```
lastModifiedDateTime ← 09:00:35
```

#### ⚙ Event 21 — `t=09:00:40`, call1, doc=`VOL`, UPD

**Relevant input fields received:**
```
cS += {TRANSFERRED: 09:00:40},  iS += {INITIATED: 09:00:40},
channel += {BOT: 09:00:40}   ← hop into voicebot
```
**Stage / what just happened:**  Hop into voicebot.  Channel array now [IVR, BOT]; live category shifts IVR → VOICEBOT. FIRST non-NEW/INITIATED cS append for call1 → new_card −1.

**Output 1 deltas (`UK_FD : metric ± value`):**
```
new_card −1, total_ivr_in_progress −1, total_voicebot_in_progress +1
```
**Output 2 deltas for this conversation (`field ← value`):**
```
is_new=0, ivr_in_progress=0, voicebot_in_progress=1, channels_visited=['IVR','BOT'], lastModifiedDateTime ← 09:00:40
```

#### ⚙ Event 22 — `t=09:00:40`, call3, doc=`CBOL`, UPD

**Relevant input fields received:**
```
cS += {TRANSFERRED: 09:00:40},  iS += {INITIATED: 09:00:40},
channel += {GENESYS: 09:00:40}   ← hop into chatagent
```
**Stage / what just happened:**  Hop into chatagent.  Channel array now [BOT, GENESYS]; live category shifts CHATBOT → CHATAGENT. FIRST non-NEW/INITIATED cS append for call3 → new_card −1.

**Output 1 deltas (`UK_FD : metric ± value`):**
```
new_card −1, total_chatbot_in_progress −1, total_chatagent_in_progress +1
```
**Output 2 deltas for this conversation (`field ← value`):**
```
is_new=0, chatbot_in_progress=0, chatagent_in_progress=1, channels_visited=['BOT','GENESYS'], lastModifiedDateTime ← 09:00:40
```

#### ⚙ Event 23 — `t=09:00:45`, call1, doc=`BOT`, UPD

**Relevant input fields received:**
```
intent=[…, {Statement_Request: 09:00:45}],  sentiment=[…, {POSITIVE: 09:00:45}]
(latest unchanged)
```
**Stage / what just happened:**  Replace-on-update: identical latest values.

**Output 1 deltas (`UK_FD : metric ± value`):**
```
(no net change)
```
**Output 2 deltas for this conversation (`field ← value`):**
```
lastModifiedDateTime ← 09:00:45
```

#### ⚙ Event 24 — `t=09:00:45`, call3, doc=`GENESYS`, UPD

**Relevant input fields received:**
```
intent=[…, {Statement_Resolved: 09:00:45}],  sentiment=[…, {POSITIVE: 09:00:45}]
(latest unchanged)
```
**Stage / what just happened:**  Replace-on-update: identical latest values.

**Output 1 deltas (`UK_FD : metric ± value`):**
```
(no net change)
```
**Output 2 deltas for this conversation (`field ← value`):**
```
lastModifiedDateTime ← 09:00:45
```

#### ⚙ Event 25 — `t=09:00:50`, call3, doc=`CBOL`, UPD

**Relevant input fields received:**
```
cS += {CLOSED: 09:00:50},  iS += {CLOSED: 09:00:50},
channel += {CBOL: 09:00:50}   ← closing marker
```
**Stage / what just happened:**  CLOSED — finalise call3.  channel[-1].key=='CBOL' → fallback to channel[-2] = GENESYS. Real channels=[BOT, GENESYS] → BOT not last (chatbot_transferred=1), GENESYS is last (chatagent_contained=1). BOT before GENESYS → chatbot_to_agent_transfer=1.

**Output 1 deltas (`UK_FD : metric ± value`):**
```
closed_card +1, in_progress_card −1, total_chatagent_in_progress −1,
total_chatbot_transferred +1, total_chatagent_contained +1,
total_chatbot_to_agent_transfer +1,
total_chatbot_handling_time +25 000 ms ((40−15)·1000),
total_chatagent_handling_time +10 000 ms ((50−40)·1000)
```
**Output 2 deltas for this conversation (`field ← value`):**
```
in_progress=0, closed=1, chatagent_in_progress=0,
chatbot_transferred=1, chatagent_contained=1, chatbot_to_agent_transfer=1,
chatbot_handling_time=25 000 ms, chatagent_handling_time=10 000 ms,
channels_visited=['BOT','GENESYS','CBOL'], lastModifiedDateTime ← 09:00:50
```

#### ⚙ Event 26 — `t=09:00:55`, call1, doc=`GENESYS`, INS

**Relevant input fields received:**
```
conversationId=conv1, organization=VOL_GENESYS, market=UK_FD, createdDateTime=09:00:55,
intent=[{Statement_Provided: 09:00:55}],  sentiment=[{POSITIVE: 09:00:55}]
```
**Stage / what just happened:**  Voiceagent doc materialises with intent/sentiment in the same insert.

**Output 1 deltas (`UK_FD : metric ± value`):**
```
intent_distribution: freq[Statement_Request] −1, freq[Statement_Provided] +1
(sentiment unchanged → no delta)
```
**Output 2 deltas for this conversation (`field ← value`):**
```
lastModifiedDateTime ← 09:00:55
```

#### ⚙ Event 27 — `t=09:01:00`, call1, doc=`GENESYS`, UPD

**Relevant input fields received:**
```
intent=[…, {Statement_Provided: 09:01:00}],  sentiment=[…, {POSITIVE: 09:01:00}]
(latest unchanged)
```
**Stage / what just happened:**  Replace-on-update: identical latest values.

**Output 1 deltas (`UK_FD : metric ± value`):**
```
(no net change)
```
**Output 2 deltas for this conversation (`field ← value`):**
```
lastModifiedDateTime ← 09:01:00
```

#### ⚙ Event 28 — `t=09:01:05`, call1, doc=`VOL`, UPD

**Relevant input fields received:**
```
cS += {TRANSFERRED: 09:01:05},  iS += {INITIATED: 09:01:05},
channel += {GENESYS: 09:01:05}   ← hop into voiceagent
```
**Stage / what just happened:**  Hop into voiceagent.  Channel array now [IVR, BOT, GENESYS]; live category shifts VOICEBOT → VOICEAGENT.

**Output 1 deltas (`UK_FD : metric ± value`):**
```
total_voicebot_in_progress −1, total_voiceagent_in_progress +1
```
**Output 2 deltas for this conversation (`field ← value`):**
```
voicebot_in_progress=0, voiceagent_in_progress=1, channels_visited=['IVR','BOT','GENESYS'], lastModifiedDateTime ← 09:01:05
```

#### ⚙ Event 29 — `t=09:01:10`, call1, doc=`GENESYS`, UPD

**Relevant input fields received:**
```
intent=[…, {Statement_Resolved: 09:01:10}],  sentiment=[…, {POSITIVE: 09:01:10}]
(intent shifts: Statement_Provided → Statement_Resolved)
```
**Stage / what just happened:**  Voiceagent records resolution.

**Output 1 deltas (`UK_FD : metric ± value`):**
```
intent_distribution: freq[Statement_Provided] −1, freq[Statement_Resolved] +1
```
**Output 2 deltas for this conversation (`field ← value`):**
```
lastModifiedDateTime ← 09:01:10
```

#### ⚙ Event 30 — `t=09:01:15`, call1, doc=`VOL`, UPD

**Relevant input fields received:**
```
cS += {CLOSED: 09:01:15},  iS += {CLOSED: 09:01:15},
channel += {VOL: 09:01:15}   ← closing marker
```
**Stage / what just happened:**  CLOSED — finalise call1.  channel[-1].key=='VOL' → fallback to channel[-2] = GENESYS. Real channels=[IVR, BOT, GENESYS]:  IVR not last → ivr_transferred=1, BOT not last → voicebot_transferred=1, GENESYS is last → voiceagent_contained=1.  BOT before GENESYS → voicebot_to_agent_transfer=1.

**Output 1 deltas (`UK_FD : metric ± value`):**
```
closed_card +1, in_progress_card −1, total_voiceagent_in_progress −1,
total_ivr_transferred +1, total_voicebot_transferred +1, total_voiceagent_contained +1,
total_voicebot_to_agent_transfer +1,
total_ivr_handling_time +25 000 ms ((40−15)·1000),
total_voicebot_handling_time +25 000 ms ((65−40)·1000),
total_voiceagent_handling_time +10 000 ms ((75−65)·1000)
```
**Output 2 deltas for this conversation (`field ← value`):**
```
in_progress=0, closed=1, voiceagent_in_progress=0,
ivr_transferred=1, voicebot_transferred=1, voiceagent_contained=1,
voicebot_to_agent_transfer=1,
ivr_handling_time=25 000 ms, voicebot_handling_time=25 000 ms, voiceagent_handling_time=10 000 ms,
channels_visited=['IVR','BOT','GENESYS','VOL'], lastModifiedDateTime ← 09:01:15
```

#### ⚙ Event 1 — `t=conversationId`, conv1, doc=``, 


#### ⚙ Event 2 — `t=organization`, VOL, doc=``, 


#### ⚙ Event 3 — `t=conversationType`, VOICE, doc=``, 


#### ⚙ Event 4 — `t=market`, UK_FD, doc=``, 


#### ⚙ Event 5 — `t=createdDateTime`, 09:00:00, doc=``, 


#### ⚙ Event 6 — `t=lastModifiedDateTime`, 09:01:15, doc=``, 


#### ⚙ Event 7 — `t=conversationStatus  (cS)`, [ {NEW: 09:00:00},  {INITIATED: 09:00:15},  {TRANSFERRED: 09:00:40},  {TRANSFERRED: 09:01:05},  {CLOSED: 09:01:15} ], doc=``, 


#### ⚙ Event 8 — `t=interactionStatus  (iS)`, [ {INITIATED: 09:00:00},  {TRANSFERRED: 09:00:15},  {INITIATED: 09:00:40},  {INITIATED: 09:01:05},  {CLOSED: 09:01:15} ], doc=``, 


#### ⚙ Event 9 — `t=channel`, [ {IVR: 09:00:15},  {BOT: 09:00:40},  {GENESYS: 09:01:05},  {VOL: 09:01:15} ], doc=``, 


#### ⚙ Event 1 — `t=conversationId`, conv1, doc=``, 


#### ⚙ Event 2 — `t=organization`, IVR, doc=``, 


#### ⚙ Event 3 — `t=market`, UK_FD, doc=``, 


#### ⚙ Event 4 — `t=createdDateTime`, 09:00:05, doc=``, 


#### ⚙ Event 5 — `t=lastModifiedDateTime`, 09:00:20, doc=``, 


#### ⚙ Event 6 — `t=intent`, [ {Balance_Enquiry: 09:00:10},  {Balance_Enquiry: 09:00:20} ], doc=``, 


#### ⚙ Event 7 — `t=sentiment`, [ {NEUTRAL: 09:00:10},  {NEUTRAL: 09:00:20} ], doc=``, 


#### ⚙ Event 1 — `t=conversationId`, conv1, doc=``, 


#### ⚙ Event 2 — `t=organization`, CIVR_SIERRA, doc=``, 


#### ⚙ Event 3 — `t=market`, UK_FD, doc=``, 


#### ⚙ Event 4 — `t=createdDateTime`, 09:00:30, doc=``, 


#### ⚙ Event 5 — `t=lastModifiedDateTime`, 09:00:45, doc=``, 


#### ⚙ Event 6 — `t=intent`, [ {Statement_Request: 09:00:35},  {Statement_Request: 09:00:45} ], doc=``, 


#### ⚙ Event 7 — `t=sentiment`, [ {POSITIVE: 09:00:35},  {POSITIVE: 09:00:45} ], doc=``, 


#### ⚙ Event 1 — `t=conversationId`, conv1, doc=``, 


#### ⚙ Event 2 — `t=organization`, VOL_GENESYS, doc=``, 


#### ⚙ Event 3 — `t=market`, UK_FD, doc=``, 


#### ⚙ Event 4 — `t=createdDateTime`, 09:00:55, doc=``, 


#### ⚙ Event 5 — `t=lastModifiedDateTime`, 09:01:10, doc=``, 


#### ⚙ Event 6 — `t=intent`, [ {Statement_Provided: 09:00:55},  {Statement_Provided: 09:01:00},  {Statement_Resolved: 09:01:10} ], doc=``, 


#### ⚙ Event 7 — `t=sentiment`, [ {POSITIVE: 09:00:55},  {POSITIVE: 09:01:00},  {POSITIVE: 09:01:10} ], doc=``, 


#### ⚙ Event 1 — `t=conversationId`, conv2, doc=``, 


#### ⚙ Event 2 — `t=organization`, VOL, doc=``, 


#### ⚙ Event 3 — `t=conversationType`, VOICE, doc=``, 


#### ⚙ Event 4 — `t=market`, UK_FD, doc=``, 


#### ⚙ Event 5 — `t=createdDateTime`, 09:00:00, doc=``, 


#### ⚙ Event 6 — `t=lastModifiedDateTime`, 09:00:25, doc=``, 


#### ⚙ Event 7 — `t=conversationStatus  (cS)`, [ {NEW: 09:00:00},  {INITIATED: 09:00:15},  {CLOSED: 09:00:25} ], doc=``, 


#### ⚙ Event 8 — `t=interactionStatus  (iS)`, [ {INITIATED: 09:00:00},  {TRANSFERRED: 09:00:15},  {CLOSED: 09:00:25} ], doc=``, 


#### ⚙ Event 9 — `t=channel`, [ {IVR: 09:00:15},  {VOL: 09:00:25} ], doc=``, 


#### ⚙ Event 1 — `t=conversationId`, conv2, doc=``, 


#### ⚙ Event 2 — `t=organization`, IVR, doc=``, 


#### ⚙ Event 3 — `t=market`, UK_FD, doc=``, 


#### ⚙ Event 4 — `t=createdDateTime`, 09:00:05, doc=``, 


#### ⚙ Event 5 — `t=lastModifiedDateTime`, 09:00:20, doc=``, 


#### ⚙ Event 6 — `t=intent`, [ {Card_Block_Request: 09:00:10},  {Card_Block_Request: 09:00:20} ], doc=``, 


#### ⚙ Event 7 — `t=sentiment`, [ {NEUTRAL: 09:00:10},  {NEGATIVE: 09:00:20} ], doc=``, 


#### ⚙ Event 1 — `t=conversationId`, conv3, doc=``, 


#### ⚙ Event 2 — `t=organization`, CBOL, doc=``, 


#### ⚙ Event 3 — `t=conversationType`, CHAT, doc=``, 


#### ⚙ Event 4 — `t=market`, UK_FD, doc=``, 


#### ⚙ Event 5 — `t=createdDateTime`, 09:00:00, doc=``, 


#### ⚙ Event 6 — `t=lastModifiedDateTime`, 09:00:50, doc=``, 


#### ⚙ Event 7 — `t=conversationStatus  (cS)`, [ {NEW: 09:00:00},  {INITIATED: 09:00:15},  {TRANSFERRED: 09:00:40},  {CLOSED: 09:00:50} ], doc=``, 


#### ⚙ Event 8 — `t=interactionStatus  (iS)`, [ {INITIATED: 09:00:00},  {TRANSFERRED: 09:00:15},  {INITIATED: 09:00:40},  {CLOSED: 09:00:50} ], doc=``, 


#### ⚙ Event 9 — `t=channel`, [ {BOT: 09:00:15},  {GENESYS: 09:00:40},  {CBOL: 09:00:50} ], doc=``, 


#### ⚙ Event 1 — `t=conversationId`, conv3, doc=``, 


#### ⚙ Event 2 — `t=organization`, CIVR_SIERRA, doc=``, 


#### ⚙ Event 3 — `t=market`, UK_FD, doc=``, 


#### ⚙ Event 4 — `t=createdDateTime`, 09:00:05, doc=``, 


#### ⚙ Event 5 — `t=lastModifiedDateTime`, 09:00:20, doc=``, 


#### ⚙ Event 6 — `t=intent`, [ {Statement_Help: 09:00:10},  {Statement_Help: 09:00:20} ], doc=``, 


#### ⚙ Event 7 — `t=sentiment`, [ {POSITIVE: 09:00:10},  {POSITIVE: 09:00:20} ], doc=``, 


#### ⚙ Event 1 — `t=conversationId`, conv3, doc=``, 


#### ⚙ Event 2 — `t=organization`, CBOL_GENESYS, doc=``, 


#### ⚙ Event 3 — `t=market`, UK_FD, doc=``, 


#### ⚙ Event 4 — `t=createdDateTime`, 09:00:30, doc=``, 


#### ⚙ Event 5 — `t=lastModifiedDateTime`, 09:00:45, doc=``, 


#### ⚙ Event 6 — `t=intent`, [ {Statement_Resolved: 09:00:35},  {Statement_Resolved: 09:00:45} ], doc=``, 


#### ⚙ Event 7 — `t=sentiment`, [ {POSITIVE: 09:00:35},  {POSITIVE: 09:00:45} ], doc=``, 


#### ⚙ Event 1 — `t=total`, 3, doc=`+1 from each of call1, call2, call3 first inserts.`, 


#### ⚙ Event 2 — `t=in_progress_card`, 0, doc=`All three opened (+3) then closed (−3) by t=09:01:15.`, 


#### ⚙ Event 3 — `t=closed_card`, 3, doc=`+1 from each of call1@t09:01:15, call2@t09:00:25, call3@t09:00:50.`, 


#### ⚙ Event 4 — `t=total_ended_by_customer`, 0, doc=`All closes are CLOSED (not ENDED_BY_CUSTOMER).`, 


#### ⚙ Event 5 — `t=total_ended_by_system`, 0, doc=`All closes are CLOSED (not ENDED_BY_SYSTEM).`, 


#### ⚙ Event 6 — `t=new_card`, 0, doc=`+3 at the inserts; −3 at each call's first non-NEW/INITIATED cS append (call1@t09:00:40, call2@t09:00:25, call3@t09:00:40).`, 


#### ⚙ Event 7 — `t=total_voice`, 2, doc=`call1 + call2 (conversationType=VOICE).`, 


#### ⚙ Event 8 — `t=total_chat`, 1, doc=`call3 (conversationType=CHAT).`, 


#### ⚙ Event 9 — `t=intent_distribution`, { "Card_Block_Request" : 33.3% ,  "Statement_Resolved" : 66.7% }, doc=`Latest intent per conversation: call1=Statement_Resolved (last set @t09:01:10), call2=Card_Block_Request, call3=Statement_Resolved.  3 conversations with non-null intent → freq[Statement_Resolved]=2/3, freq[Card_Block_Request]=1/3.`, 


#### ⚙ Event 10 — `t=sentiment_distribution`, { "NEGATIVE" : 33.3% ,  "POSITIVE" : 66.7% }, doc=`Latest sentiment per conversation: call1=POSITIVE, call2=NEGATIVE (shifted at t09:00:20), call3=POSITIVE → freq[POSITIVE]=2/3, freq[NEGATIVE]=1/3.`, 


#### ⚙ Event 11 — `t=total_ivr_transferred`, 1, doc=`Only call1 had IVR-not-last (channel array [IVR, BOT, GENESYS]).  call2's IVR IS last real → not transferred.`, 


#### ⚙ Event 12 — `t=total_voicebot_transferred`, 1, doc=`Only call1 (BOT not last in [IVR, BOT, GENESYS]).`, 


#### ⚙ Event 13 — `t=total_voiceagent_transferred`, 0, doc=`call1's GENESYS IS last real → not transferred.`, 


#### ⚙ Event 14 — `t=total_chatbot_transferred`, 1, doc=`call3 BOT not last in [BOT, GENESYS].`, 


#### ⚙ Event 15 — `t=total_chatagent_transferred`, 0, doc=`call3 GENESYS IS last real.`, 


#### ⚙ Event 16 — `t=total_ivr_contained`, 1, doc=`Only call2 (last real channel = IVR after VOL fallback).`, 


#### ⚙ Event 17 — `t=total_voicebot_contained`, 0, doc=`Neither voice call ends in voicebot.`, 


#### ⚙ Event 18 — `t=total_voiceagent_contained`, 1, doc=`call1 (last real = GENESYS after VOL fallback).`, 


#### ⚙ Event 19 — `t=total_chatbot_contained`, 0, doc=`call3 doesn't end in chatbot.`, 


#### ⚙ Event 20 — `t=total_chatagent_contained`, 1, doc=`call3 (last real = GENESYS after CBOL fallback).`, 


#### ⚙ Event 21 — `t=total_ivr_in_progress`, 0, doc=`Net deltas across the whole timeline: +2 (call1@t09:00:15, call2@t09:00:15) then −2 (call1@t09:00:40, call2@t09:00:25).`, 


#### ⚙ Event 22 — `t=total_voicebot_in_progress`, 0, doc=`+1 (call1@t09:00:40) then −1 (call1@t09:01:05).`, 


#### ⚙ Event 23 — `t=total_voiceagent_in_progress`, 0, doc=`+1 (call1@t09:01:05) then −1 (call1@t09:01:15).`, 


#### ⚙ Event 24 — `t=total_chatbot_in_progress`, 0, doc=`+1 (call3@t09:00:15) then −1 (call3@t09:00:40).`, 


#### ⚙ Event 25 — `t=total_chatagent_in_progress`, 0, doc=`+1 (call3@t09:00:40) then −1 (call3@t09:00:50).`, 


#### ⚙ Event 26 — `t=total_voicebot_to_agent_transfer`, 1, doc=`call1 channel array [IVR, BOT, GENESYS] — BOT index < GENESYS index → +1.`, 


#### ⚙ Event 27 — `t=total_chatbot_to_agent_transfer`, 1, doc=`call3 channel array [BOT, GENESYS] — BOT index < GENESYS index → +1.`, 


#### ⚙ Event 28 — `t=total_voicebot_to_ivr_transfer`, 0, doc=`No voicebot-before-ivr ordering in any channel array.`, 


#### ⚙ Event 29 — `t=total_voiceagent_handling_time`, 10 000 ms, doc=`call1: GENESYS@t09:01:05 → next entry VOL@t09:01:15 = 10 000 ms.`, 


#### ⚙ Event 30 — `t=total_chatagent_handling_time`, 10 000 ms, doc=`call3: GENESYS@t09:00:40 → next entry CBOL@t09:00:50 = 10 000 ms.`, 


#### ⚙ Event 31 — `t=total_voicebot_handling_time`, 25 000 ms, doc=`call1: BOT@t09:00:40 → next entry GENESYS@t09:01:05 = 25 000 ms.`, 


#### ⚙ Event 32 — `t=total_chatbot_handling_time`, 25 000 ms, doc=`call3: BOT@t09:00:15 → next entry GENESYS@t09:00:40 = 25 000 ms.`, 


#### ⚙ Event 33 — `t=total_ivr_handling_time`, 35 000 ms, doc=`call1: IVR@t09:00:15 → BOT@t09:00:40 = 25 000 ms.  call2: IVR@t09:00:15 → VOL@t09:00:25 = 10 000 ms.  Sum = 35 000 ms.`, 


#### ⚙ Event 34 — `t=voice_call_timeline`, [ { ts: 09:00:00, count: 2 } ], doc=`call1 + call2 both inserted at t09:00:00.`, 


#### ⚙ Event 35 — `t=chat_call_timeline`, [ { ts: 09:00:00, count: 1 } ], doc=`call3 inserted at t09:00:00.`, 


#### ⚙ Event 1 — `t=conversationId`, conv1, doc=`conv2`, conv3


#### ⚙ Event 2 — `t=market`, UK_FD, doc=`UK_FD`, UK_FD


#### ⚙ Event 3 — `t=createdDateTime`, 09:00:00, doc=`09:00:00`, 09:00:00


#### ⚙ Event 4 — `t=lastModifiedDateTime`, 09:01:15, doc=`09:00:25`, 09:00:50


#### ⚙ Event 5 — `t=in_progress`, 0, doc=`0`, 0


#### ⚙ Event 6 — `t=closed`, 1, doc=`1`, 1


#### ⚙ Event 7 — `t=ended_by_customer`, 0, doc=`0`, 0


#### ⚙ Event 8 — `t=ended_by_system`, 0, doc=`0`, 0


#### ⚙ Event 9 — `t=is_new`, 0, doc=`0`, 0


#### ⚙ Event 10 — `t=is_voice`, 1, doc=`1`, 0


#### ⚙ Event 11 — `t=is_chat`, 0, doc=`0`, 1


#### ⚙ Event 12 — `t=ivr_transferred`, 1, doc=`0`, 0


### 5.3 Final state of every input MongoDB document

Each document below is shown with its RELEVANT fields only (the irrelevant scaffolding fields like `_id`, `details`, `eventContent`, `partitionList`, `region`, `timeStamp`, `updationTimeList`, `verificationFlag`, `organizationId`, `originatorId` are omitted for clarity). Nine documents in total: 3 master VOL/CBOL docs + 6 channel docs.

#### Doc A — master VOL doc for call1  (lifecycle source-of-truth)

```json
{
  "conversationId": "conv1",
  "organization": "VOL",
  "conversationType": "VOICE",
  "market": "UK_FD",
  "createdDateTime": "09:00:00",
  "lastModifiedDateTime": "09:01:15",
  "conversationStatus": "[ {NEW: 09:00:00},  {INITIATED: 09:00:15},  {TRANSFERRED: 09:00:40},  {TRANSFERRED: 09:01:05},  {CLOSED: 09:01:15} ]",
  "interactionStatus": "[ {INITIATED: 09:00:00},  {TRANSFERRED: 09:00:15},  {INITIATED: 09:00:40},  {INITIATED: 09:01:05},  {CLOSED: 09:01:15} ]",
  "channel": "[ {IVR: 09:00:15},  {BOT: 09:00:40},  {GENESYS: 09:01:05},  {VOL: 09:01:15} ]"
}
```

#### Doc B — IVR channel doc for call1  (organization=IVR)

```json
{
  "conversationId": "conv1",
  "organization": "IVR",
  "market": "UK_FD",
  "createdDateTime": "09:00:05",
  "lastModifiedDateTime": "09:00:20",
  "intent": "[ {Balance_Enquiry: 09:00:10},  {Balance_Enquiry: 09:00:20} ]",
  "sentiment": "[ {NEUTRAL: 09:00:10},  {NEUTRAL: 09:00:20} ]"
}
```

#### Doc C — BOT channel doc for call1  (organization=CIVR_SIERRA)

```json
{
  "conversationId": "conv1",
  "organization": "CIVR_SIERRA",
  "market": "UK_FD",
  "createdDateTime": "09:00:30",
  "lastModifiedDateTime": "09:00:45",
  "intent": "[ {Statement_Request: 09:00:35},  {Statement_Request: 09:00:45} ]",
  "sentiment": "[ {POSITIVE: 09:00:35},  {POSITIVE: 09:00:45} ]"
}
```

#### Doc D — GENESYS channel doc for call1  (organization=VOL_GENESYS)

```json
{
  "conversationId": "conv1",
  "organization": "VOL_GENESYS",
  "market": "UK_FD",
  "createdDateTime": "09:00:55",
  "lastModifiedDateTime": "09:01:10",
  "intent": "[ {Statement_Provided: 09:00:55},  {Statement_Provided: 09:01:00},  {Statement_Resolved: 09:01:10} ]",
  "sentiment": "[ {POSITIVE: 09:00:55},  {POSITIVE: 09:01:00},  {POSITIVE: 09:01:10} ]"
}
```

#### Doc E — master VOL doc for call2

```json
{
  "conversationId": "conv2",
  "organization": "VOL",
  "conversationType": "VOICE",
  "market": "UK_FD",
  "createdDateTime": "09:00:00",
  "lastModifiedDateTime": "09:00:25",
  "conversationStatus": "[ {NEW: 09:00:00},  {INITIATED: 09:00:15},  {CLOSED: 09:00:25} ]",
  "interactionStatus": "[ {INITIATED: 09:00:00},  {TRANSFERRED: 09:00:15},  {CLOSED: 09:00:25} ]",
  "channel": "[ {IVR: 09:00:15},  {VOL: 09:00:25} ]"
}
```

#### Doc F — IVR channel doc for call2  (organization=IVR)

```json
{
  "conversationId": "conv2",
  "organization": "IVR",
  "market": "UK_FD",
  "createdDateTime": "09:00:05",
  "lastModifiedDateTime": "09:00:20",
  "intent": "[ {Card_Block_Request: 09:00:10},  {Card_Block_Request: 09:00:20} ]",
  "sentiment": "[ {NEUTRAL: 09:00:10},  {NEGATIVE: 09:00:20} ]"
}
```

#### Doc G — master CBOL doc for call3

```json
{
  "conversationId": "conv3",
  "organization": "CBOL",
  "conversationType": "CHAT",
  "market": "UK_FD",
  "createdDateTime": "09:00:00",
  "lastModifiedDateTime": "09:00:50",
  "conversationStatus": "[ {NEW: 09:00:00},  {INITIATED: 09:00:15},  {TRANSFERRED: 09:00:40},  {CLOSED: 09:00:50} ]",
  "interactionStatus": "[ {INITIATED: 09:00:00},  {TRANSFERRED: 09:00:15},  {INITIATED: 09:00:40},  {CLOSED: 09:00:50} ]",
  "channel": "[ {BOT: 09:00:15},  {GENESYS: 09:00:40},  {CBOL: 09:00:50} ]"
}
```

#### Doc H — BOT channel doc for call3  (organization=CIVR_SIERRA)

```json
{
  "conversationId": "conv3",
  "organization": "CIVR_SIERRA",
  "market": "UK_FD",
  "createdDateTime": "09:00:05",
  "lastModifiedDateTime": "09:00:20",
  "intent": "[ {Statement_Help: 09:00:10},  {Statement_Help: 09:00:20} ]",
  "sentiment": "[ {POSITIVE: 09:00:10},  {POSITIVE: 09:00:20} ]"
}
```

#### Doc I — GENESYS channel doc for call3  (organization=CBOL_GENESYS)

```json
{
  "conversationId": "conv1",
  "organization": "CBOL_GENESYS",
  "market": "UK_FD",
  "createdDateTime": "09:00:00",
  "lastModifiedDateTime": "09:01:15",
  "intent": "[ {Statement_Resolved: 09:00:35},  {Statement_Resolved: 09:00:45} ]",
  "sentiment": "[ {POSITIVE: 09:00:35},  {POSITIVE: 09:00:45} ]",
  "total": 3,
  "in_progress_card": 0,
  "closed_card": 3,
  "total_ended_by_customer": 0,
  "total_ended_by_system": 0,
  "new_card": 0,
  "total_voice": 2,
  "total_chat": 1,
  "intent_distribution": "{ \"Card_Block_Request\" : 33.3% ,  \"Statement_Resolved\" : 66.7% }",
  "sentiment_distribution": "{ \"NEGATIVE\" : 33.3% ,  \"POSITIVE\" : 66.7% }",
  "total_ivr_transferred": 1,
  "total_voicebot_transferred": 1,
  "total_voiceagent_transferred": 0,
  "total_chatbot_transferred": 1,
  "total_chatagent_transferred": 0,
  "total_ivr_contained": 1,
  "total_voicebot_contained": 0,
  "total_voiceagent_contained": 1,
  "total_chatbot_contained": 0,
  "total_chatagent_contained": 1,
  "total_ivr_in_progress": 0,
  "total_voicebot_in_progress": 0,
  "total_voiceagent_in_progress": 0,
  "total_chatbot_in_progress": 0,
  "total_chatagent_in_progress": 0,
  "total_voicebot_to_agent_transfer": 1,
  "total_chatbot_to_agent_transfer": 1,
  "total_voicebot_to_ivr_transfer": 0,
  "total_voiceagent_handling_time": "10 000 ms",
  "total_chatagent_handling_time": "10 000 ms",
  "total_voicebot_handling_time": "25 000 ms",
  "total_chatbot_handling_time": "25 000 ms",
  "total_ivr_handling_time": "35 000 ms",
  "voice_call_timeline": "[ { ts: 09:00:00, count: 2 } ]",
  "chat_call_timeline": "[ { ts: 09:00:00, count: 1 } ]",
  "in_progress": 0,
  "closed": 1,
  "ended_by_customer": 0,
  "ended_by_system": 0,
  "is_new": 0,
  "is_voice": 1,
  "is_chat": 0,
  "ivr_transferred": 1,
  "voicebot_transferred": 1,
  "voiceagent_transferred": 0,
  "chatbot_transferred": 0,
  "chatagent_transferred": 0,
  "ivr_contained": 0,
  "voicebot_contained": 0,
  "voiceagent_contained": 1,
  "chatbot_contained": 0,
  "chatagent_contained": 0,
  "ivr_in_progress": 0,
  "voicebot_in_progress": 0,
  "voiceagent_in_progress": 0,
  "chatbot_in_progress": 0,
  "chatagent_in_progress": 0,
  "voicebot_to_agent_transfer": 1,
  "chatbot_to_agent_transfer": 0,
  "voicebot_to_ivr_transfer": 0,
  "voiceagent_handling_time": "10 000 ms",
  "chatagent_handling_time": 0,
  "voicebot_handling_time": "25 000 ms",
  "chatbot_handling_time": 0,
  "ivr_handling_time": "25 000 ms",
  "channels_visited": "[ IVR, BOT, GENESYS, VOL ]"
}
```

### 5.4 Final Output 1 bucket values

**Bucket key:** `UK_FD : { 1H, 24H, 30D }` — all three windows receive the same deltas because all three calls' `createdDateTime` are within seconds of each other and well under 1 hour ago.

| # | Metric | Final value | Derivation |
| --- | --- | --- | --- |
| 1 | total | 3 | +1 from each of call1, call2, call3 first inserts. |
| 2 | in_progress_card | 0 | All three opened (+3) then closed (−3) by t=09:01:15. |
| 3 | closed_card | 3 | +1 from each of call1@t09:01:15, call2@t09:00:25, call3@t09:00:50. |
| 4 | total_ended_by_customer | 0 | All closes are CLOSED (not ENDED_BY_CUSTOMER). |
| 5 | total_ended_by_system | 0 | All closes are CLOSED (not ENDED_BY_SYSTEM). |
| 6 | new_card | 0 | +3 at the inserts; −3 at each call's first non-NEW/INITIATED cS append (call1@t09:00:40, call2@t09:00:25, call3@t09:00:40). |
| 7 | total_voice | 2 | call1 + call2 (conversationType=VOICE). |
| 8 | total_chat | 1 | call3 (conversationType=CHAT). |
| 9 | intent_distribution | { "Card_Block_Request" : 33.3% ,  "Statement_Resolved" : 66.7% } | Latest intent per conversation: call1=Statement_Resolved (last set @t09:01:10), call2=Card_Block_Request, call3=Statement_Resolved.  3 conversations with non-null intent → freq[Statement_Resolved]=2/3, freq[Card_Block_Request]=1/3. |
| 10 | sentiment_distribution | { "NEGATIVE" : 33.3% ,  "POSITIVE" : 66.7% } | Latest sentiment per conversation: call1=POSITIVE, call2=NEGATIVE (shifted at t09:00:20), call3=POSITIVE → freq[POSITIVE]=2/3, freq[NEGATIVE]=1/3. |
| 11 | total_ivr_transferred | 1 | Only call1 had IVR-not-last (channel array [IVR, BOT, GENESYS]).  call2's IVR IS last real → not transferred. |
| 12 | total_voicebot_transferred | 1 | Only call1 (BOT not last in [IVR, BOT, GENESYS]). |
| 13 | total_voiceagent_transferred | 0 | call1's GENESYS IS last real → not transferred. |
| 14 | total_chatbot_transferred | 1 | call3 BOT not last in [BOT, GENESYS]. |
| 15 | total_chatagent_transferred | 0 | call3 GENESYS IS last real. |
| 16 | total_ivr_contained | 1 | Only call2 (last real channel = IVR after VOL fallback). |
| 17 | total_voicebot_contained | 0 | Neither voice call ends in voicebot. |
| 18 | total_voiceagent_contained | 1 | call1 (last real = GENESYS after VOL fallback). |
| 19 | total_chatbot_contained | 0 | call3 doesn't end in chatbot. |
| 20 | total_chatagent_contained | 1 | call3 (last real = GENESYS after CBOL fallback). |
| 21 | total_ivr_in_progress | 0 | Net deltas across the whole timeline: +2 (call1@t09:00:15, call2@t09:00:15) then −2 (call1@t09:00:40, call2@t09:00:25). |
| 22 | total_voicebot_in_progress | 0 | +1 (call1@t09:00:40) then −1 (call1@t09:01:05). |
| 23 | total_voiceagent_in_progress | 0 | +1 (call1@t09:01:05) then −1 (call1@t09:01:15). |
| 24 | total_chatbot_in_progress | 0 | +1 (call3@t09:00:15) then −1 (call3@t09:00:40). |
| 25 | total_chatagent_in_progress | 0 | +1 (call3@t09:00:40) then −1 (call3@t09:00:50). |
| 26 | total_voicebot_to_agent_transfer | 1 | call1 channel array [IVR, BOT, GENESYS] — BOT index < GENESYS index → +1. |
| 27 | total_chatbot_to_agent_transfer | 1 | call3 channel array [BOT, GENESYS] — BOT index < GENESYS index → +1. |
| 28 | total_voicebot_to_ivr_transfer | 0 | No voicebot-before-ivr ordering in any channel array. |
| 29 | total_voiceagent_handling_time | 10 000 ms | call1: GENESYS@t09:01:05 → next entry VOL@t09:01:15 = 10 000 ms. |
| 30 | total_chatagent_handling_time | 10 000 ms | call3: GENESYS@t09:00:40 → next entry CBOL@t09:00:50 = 10 000 ms. |
| 31 | total_voicebot_handling_time | 25 000 ms | call1: BOT@t09:00:40 → next entry GENESYS@t09:01:05 = 25 000 ms. |
| 32 | total_chatbot_handling_time | 25 000 ms | call3: BOT@t09:00:15 → next entry GENESYS@t09:00:40 = 25 000 ms. |
| 33 | total_ivr_handling_time | 35 000 ms | call1: IVR@t09:00:15 → BOT@t09:00:40 = 25 000 ms.  call2: IVR@t09:00:15 → VOL@t09:00:25 = 10 000 ms.  Sum = 35 000 ms. |
| 34 | voice_call_timeline | [ { ts: 09:00:00, count: 2 } ] | call1 + call2 both inserted at t09:00:00. |
| 35 | chat_call_timeline | [ { ts: 09:00:00, count: 1 } ] | call3 inserted at t09:00:00. |

### 5.5 Final Output 2 — per-conversation hashes

One Redis hash per `conversationId`. Values are 0/1 flags or absolute durations.

#### conv1 (call1)

```json
{
  "conversationId": "conv1",
  "market": "UK_FD",
  "createdDateTime": "09:00:00",
  "lastModifiedDateTime": "09:01:15",
  "in_progress": 0,
  "closed": 1,
  "ended_by_customer": 0,
  "ended_by_system": 0,
  "is_new": 0,
  "is_voice": 1,
  "is_chat": 0,
  "ivr_transferred": 1,
  "voicebot_transferred": 1,
  "voiceagent_transferred": 0,
  "chatbot_transferred": 0,
  "chatagent_transferred": 0,
  "ivr_contained": 0,
  "voicebot_contained": 0,
  "voiceagent_contained": 1,
  "chatbot_contained": 0,
  "chatagent_contained": 0,
  "ivr_in_progress": 0,
  "voicebot_in_progress": 0,
  "voiceagent_in_progress": 0,
  "chatbot_in_progress": 0,
  "chatagent_in_progress": 0,
  "voicebot_to_agent_transfer": 1,
  "chatbot_to_agent_transfer": 0,
  "voicebot_to_ivr_transfer": 0,
  "voiceagent_handling_time": "10 000 ms",
  "chatagent_handling_time": 0,
  "voicebot_handling_time": "25 000 ms",
  "chatbot_handling_time": 0,
  "ivr_handling_time": "25 000 ms",
  "channels_visited": "[ IVR, BOT, GENESYS, VOL ]"
}
```

#### conv2 (call2)

```json
{
  "conversationId": "conv2",
  "market": "UK_FD",
  "createdDateTime": "09:00:00",
  "lastModifiedDateTime": "09:00:25",
  "in_progress": 0,
  "closed": 1,
  "ended_by_customer": 0,
  "ended_by_system": 0,
  "is_new": 0,
  "is_voice": 1,
  "is_chat": 0,
  "ivr_transferred": 0,
  "voicebot_transferred": 0,
  "voiceagent_transferred": 0,
  "chatbot_transferred": 0,
  "chatagent_transferred": 0,
  "ivr_contained": 1,
  "voicebot_contained": 0,
  "voiceagent_contained": 0,
  "chatbot_contained": 0,
  "chatagent_contained": 0,
  "ivr_in_progress": 0,
  "voicebot_in_progress": 0,
  "voiceagent_in_progress": 0,
  "chatbot_in_progress": 0,
  "chatagent_in_progress": 0,
  "voicebot_to_agent_transfer": 0,
  "chatbot_to_agent_transfer": 0,
  "voicebot_to_ivr_transfer": 0,
  "voiceagent_handling_time": 0,
  "chatagent_handling_time": 0,
  "voicebot_handling_time": 0,
  "chatbot_handling_time": 0,
  "ivr_handling_time": "10 000 ms",
  "channels_visited": "[ IVR, VOL ]"
}
```

#### conv3 (call3)

```json
{
  "conversationId": "conv3",
  "market": "UK_FD",
  "createdDateTime": "09:00:00",
  "lastModifiedDateTime": "09:00:50",
  "in_progress": 0,
  "closed": 1,
  "ended_by_customer": 0,
  "ended_by_system": 0,
  "is_new": 0,
  "is_voice": 0,
  "is_chat": 1,
  "ivr_transferred": 0,
  "voicebot_transferred": 0,
  "voiceagent_transferred": 0,
  "chatbot_transferred": 1,
  "chatagent_transferred": 0,
  "ivr_contained": 0,
  "voicebot_contained": 0,
  "voiceagent_contained": 0,
  "chatbot_contained": 0,
  "chatagent_contained": 1,
  "ivr_in_progress": 0,
  "voicebot_in_progress": 0,
  "voiceagent_in_progress": 0,
  "chatbot_in_progress": 0,
  "chatagent_in_progress": 0,
  "voicebot_to_agent_transfer": 0,
  "chatbot_to_agent_transfer": 1,
  "voicebot_to_ivr_transfer": 0,
  "voiceagent_handling_time": 0,
  "chatagent_handling_time": "10 000 ms",
  "voicebot_handling_time": 0,
  "chatbot_handling_time": "25 000 ms",
  "ivr_handling_time": 0,
  "channels_visited": "[ BOT, GENESYS, CBOL ]"
}
```

---

*End of data model.*
