# Exercise 8: Hospital Appointment Booking (Camunda 8)

| File | What it is |
|---|---|
| `send-notification.bpmn` | Child process `send-notification`: start → REST connector "Send Telegram message" → end |
| `slot_decision.dmn` | Decision `slot_decision`, hit policy **UNIQUE** |
| `appointment-booking.bpmn` | Parent process `appointment-booking`. Call activities with propagation **OFF** (only mapped variables) |
| `appointment-booking-propagate-all.bpmn` | Same parent as `appointment-booking-propagate-all`, with propagation **ON** (used for the comparison in task 4) |
| `run-tests.sh` | Deploys everything and runs the three test cases through the Camunda 8 REST API |

All models target Camunda 8.7 (they pass the `camunda-compat` 8.7 lint).

---

## 1. Child process: `send-notification`

- **Inputs:** `recipientId` (Telegram `chat_id`) and `messageText`.
- **REST Outbound Connector** (`io.camunda:http-json:1`):
  - `POST https://api.telegram.org/bot{{secrets.TELEGRAM_BOT_TOKEN}}/sendMessage`
  - Body: `={"chat_id": recipientId, "text": messageText}`
  - Result expression: `={telegramOk: response.body.ok, telegramMessageId: response.body.result.message_id}`
  - 3 retries with a 5-second backoff. If Telegram rejects the call, the task raises an incident.

## 2. DMN: `slot_decision` (UNIQUE)

| department | bookedCount | slotStatus | suggestedAction |
|---|---|---|---|
| "Cardiology" | < 10 | AVAILABLE | Book the requested slot |
| "Cardiology" | >= 10 | **FULL** | Offer the next available Cardiology slot or add the patient to the waitlist |
| "Orthopedics" | < 8 | AVAILABLE | Book the requested slot |
| "Orthopedics" | >= 8 | FULL | Offer the next available Orthopedics slot |
| "Pediatrics" | < 15 | AVAILABLE | Book the requested slot |
| "Pediatrics" | >= 15 | FULL | Offer a slot with another pediatrician |
| any other | < 5 | AVAILABLE | Book the requested slot |
| any other | >= 5 | FULL | Ask the front desk to find an alternative slot |

No two rules overlap, so UNIQUE always returns exactly one row.

## 3. Parent process: `appointment-booking`

```
(Booking requested) → [Check slot] → <Slot status?> ─AVAILABLE→ [Book slot] → [[Send confirmation]] → (⏱ Wait until appointment, PT10M) → (Appointment booked)
                                                    └─FULL────→ [[Send slot full notice]] → (Slot full)

┌ Event subprocess "Handle cancellation" ──────────────────────────────────────────────┐
│ (✉ Cancellation received, interrupting) → [Release slot] → [[Send cancellation notice]] → (Booking cancelled) │
└───────────────────────────────────────────────────────────────────────────────────────┘
```

- **Check slot** is a business rule task that calls `slot_decision` with result variable `slotDecision`. Its output mappings flatten the result into `slotStatus` and `suggestedAction`.
- **Book slot** and **Release slot** are FEEL script tasks, so they need no job worker. They produce `booking` (`reference`, `status`, `newBookedCount`) and `release` (`status`, `releasedAt`).
- **Wait until appointment** is a 10-minute timer. It keeps the instance running after the confirmation is sent, which leaves time for a cancellation to arrive. Without it, a successful booking would finish immediately and could never be cancelled.
- **Handle cancellation** starts on the message `booking-cancellation` with correlation key `=patientId`. I made the start event **interrupting**: a cancelled booking should stop the main flow, including the timer and any running child.

**Start variables:** `patientId`, `telegramChatId`, `patientName`, `department`, `preferredSlot`, `bookedCount`.
`telegramChatId` is kept separate from `patientId`. Zeebe correlates a message to only one instance per process, so every test needs its own `patientId` even though all the messages go to the same Telegram chat.

## 4. Input/output mappings and propagation: comparison

Every call activity has the same two **input mappings**:

| Call activity | `recipientId` | `messageText` |
|---|---|---|
| Send confirmation | `=telegramChatId` | `="Hello " + patientName + ", your " + department + " appointment on " + preferredSlot + " is confirmed. Booking reference: " + booking.reference` |
| Send slot full notice | `=telegramChatId` | `="Sorry " + patientName + ", the " + department + " slot on " + preferredSlot + " is full. " + suggestedAction + "."` |
| Send cancellation notice | `=telegramChatId` | `="Hi " + patientName + ", your " + department + " appointment on " + preferredSlot + " has been cancelled and the slot released."` |

| | **Propagation ON** (`appointment-booking-propagate-all`) | **Propagation OFF** (`appointment-booking`) |
|---|---|---|
| Setting | `propagateAllParentVariables=true`, `propagateAllChildVariables=true`, no output mappings | both `false`, plus an output mapping `telegramOk → confirmationNotificationSent` / `slotFullNotificationSent` / `cancellationNotificationSent` |
| Child variables in Operate | `recipientId`, `messageText` **plus every parent variable**: `patientId`, `telegramChatId`, `patientName`, `department`, `preferredSlot`, `bookedCount`, `slotDecision`, `slotStatus`, `suggestedAction`, `booking` … | **only** `recipientId`, `messageText`, then `telegramOk`, `telegramMessageId` |
| Variables returned to parent | **all** child variables: `recipientId`, `messageText`, `telegramOk`, `telegramMessageId` are written into the parent scope. A second call (e.g. the cancellation notice) overwrites the values from the first | only the one mapped flag, under a name unique to each call. No leftovers, no overwrites |
| Coupling | The child can read any parent data, and the parent silently picks up whatever the child creates. Renaming a child variable can break or pollute the parent | The interface is explicit (2 inputs, 1 output). The child is truly reusable and its contract is visible in the model |
| Data exposure / payload | Patient data (name, department, slot) is copied into every child instance, and each instance carries a larger payload | Minimal data, which suits healthcare/PII. Smaller payloads |

**Conclusion:** propagation ON is convenient for a quick prototype. Propagation OFF with explicit mappings is the correct choice for a reusable child process like `send-notification`, because it gives a clear contract, no variable leakage and less patient data copied around.

## 5. Deploy and test

### One-time setup
1. **Telegram bot:** in Telegram, message **@BotFather** → `/newbot` → copy the token. Send any message to your new bot, then open `https://api.telegram.org/bot<TOKEN>/getUpdates` and copy `result[0].message.chat.id`. That number is your `CHAT_ID`.
2. **Connector secret:**
   - SaaS: Console → your cluster → **Connector Secrets** → create `TELEGRAM_BOT_TOKEN`.
   - C8 Run / Docker: add `TELEGRAM_BOT_TOKEN=<token>` to the connectors environment (for C8 Run, `connectors-application.properties` or the env before `./start.sh`) and restart.
3. **API access (SaaS only):** Console → cluster → **API** → create a client with the *Zeebe* scope. Download the credentials and `source` the env file.

### Run
Deploy from the Modeler (select all four files and click Deploy) or use the script:

```bash
export CHAT_ID=<your chat id>
./run-tests.sh all                 # deploy + all 3 cases against appointment-booking (propagation OFF)
./run-tests.sh success appointment-booking-propagate-all   # same case on the propagation-ON version
```

| # | Case | `patientId` | Expected path | Telegram message |
|---|---|---|---|---|
| 1 | Cardiology, `bookedCount = 3` | P-1001 | Check slot → AVAILABLE → Book slot → Send confirmation → waits at timer (completes after 10 min) | "Hello Om Pathania, your Cardiology appointment … is confirmed. Booking reference: BK-P-1001-4" |
| 2 | Cardiology, `bookedCount = 12` | P-1002 | Check slot → FULL → Send slot full notice → Slot full | "Sorry Om Pathania, the Cardiology slot … is full. Offer the next available Cardiology slot or add the patient to the waitlist." |
| 3 | Booking (`bookedCount = 5`) then cancellation | P-1003 | Confirmation sent → waiting at timer → message `booking-cancellation` (key `P-1003`) interrupts → Release slot → Send cancellation notice → Booking cancelled | Confirmation, then "Hi Om Pathania, your Cardiology appointment … has been cancelled and the slot released." |

To publish the cancellation by hand (Modeler: **Publish message** in the deploy menu, or Postman):
`POST /v2/messages/publication` with `{"name":"booking-cancellation","correlationKey":"P-1003"}`.

## 6. Screenshots to take in Operate

1. **Parent instance (test 1):** `appointment-booking`, with the path highlighted up to *Wait until appointment*. Open the variables panel (`slotStatus = "AVAILABLE"`, `booking`, `confirmationNotificationSent = true`).
2. **Child instance:** click *Send confirmation* → **View called process instance** → the `send-notification` instance (completed). Its variables panel shows only `recipientId` and `messageText`.
3. **Slot full (test 2):** the parent took the FULL branch, with `slotStatus = "FULL"`.
4. **Cancellation path (test 3):** the parent shows the main flow terminated at the timer and the *Handle cancellation* event subprocess completed. Include the child instance linked from *Send cancellation notice*.
5. **Comparison:** the child instance of `appointment-booking-propagate-all`, whose variables panel lists all the parent variables. Compare it with screenshot 2.

## Notes
- The REST task uses the template id `io.camunda.connectors.HttpJson.v2`. If the Modeler says the template version is unknown, select the task and choose **Update template**. Runtime behaviour does not depend on the template.
- The decision, the child and the parent are linked by ID (`slot_decision`, `send-notification`) with "latest" binding, so deploy all files together, or deploy the DMN and child first.
