# Architecture Decision Records (ADR) & Environment Capabilities

## 1. Environment Capability Report (2026-10-04)
- **Host Platform:** Cloud Linux container with Android SDK (CompileSdk 36, MinSdk 24) and Gradle 9.1.1 + AGP 9.1.1 + Kotlin 2.2.10.
- **Gradle & Test Execution:** Local JVM unit and Robolectric tests are supported via `gradle :app:testDebugUnitTest`. Instrumented tests requiring device ADB are forbidden and not supported in this headless container.
- **Firebase Status:** Firebase BOM 34.17.0 and Google Services plugin are in Gradle configuration. Phase 1 focuses on 100% offline-first local Room storage with IFRS accounting rules. Cloud sync and auth are slated for Phase 3.
- **Secrets Management:** Secrets Gradle Plugin injects `.env` properties into `BuildConfig`.
- **Packaging & APK:** Debug and release configurations with APK build support.

---

## 2. ADR-001: Money Representation & Currency Conversion
- **Status:** Accepted
- **Context:** Floating point numbers (`Float`, `Double`) introduce rounding errors (e.g. `0.1 + 0.2 != 0.3`) which violate financial accounting standards.
- **Decision:** All monetary amounts are stored as 64-bit signed integers (`Long`) representing the smallest currency unit (minor unit, scale = 2, e.g. 100 minor = 1.00 YER/USD/SAR).
- **Exchange Rates:** Stored in micro-units (`scale = 6`, 1.0 = 1,000,000L). Conversions perform integer half-up rounding (`(amount * rateMicros + 500_000) / 1_000_000`). Rounding remainders in multi-line journal drafts are allocated to the final balancing line to guarantee debit-credit equality down to the exact minor unit.

---

## 3. ADR-002: Single Source of Truth & Immutability
- **Status:** Accepted
- **Context:** Storing cached balances in party or account tables leads to drift, race conditions, and audit failure.
- **Decision:**
  1. Ledger lines (`journal_lines`) are the single source of truth.
  2. Balances are derived dynamically by aggregate queries over `journal_lines`.
  3. Direct SQL `BEFORE UPDATE` and `BEFORE DELETE` triggers reject any alteration or removal of journal rows.
  4. Voiding or adjusting a document creates a compensating `REVERSAL` journal entry and a new revised entry within an atomic database transaction.

---

## 4. ADR-003: Direct Service-Based Costing (No Auto COGS)
- **Status:** Accepted
- **Context:** ISPs purchase wholesale bandwidth/satellite up-links (e.g., Starlink) and sell vouchers. Physical voucher cards have negligible printing cost compared to the recurring service cost.
- **Decision:** Direct service-based costing model. Account `5101` is debited purely from actual expense vouchers allocated to upstream ISP services. Card inventory is tracked solely by physical voucher count (`quantity`) without arbitrary percentage estimations or inventory capitalization.

---

## 5. ADR-004: Unified Party Entity & Walk-in Cash Customer
- **Status:** Accepted
- **Context:** Customers, retail grocery agents, equipment suppliers, and capital partners often overlap (e.g., an agent who is also a partner).
- **Decision:** A single unified `parties` table with role flags (`isCustomer`, `isVendor`, `isPartner`). A permanent pre-seeded system party `WALK_IN_CASH` represents anonymous counter sales, eliminating null-party edge cases.

---

## 6. ADR-005: Atomic Sequential Document Numbering
- **Status:** Accepted
- **Context:** Tax regulations and accounting standards forbid gaps or duplicate invoice numbers.
- **Decision:** Document numbers are assigned inside `LedgerWriter` within the database transaction using an atomic `number_sequences` counter keyed by `(documentType, fiscalYear)`. Timestamps and random IDs are prohibited for document numbers.

---

## 7. ADR-006: Offline-First Cloud Sync & Append-Only Mirrored Ledger
- **Status:** Accepted
- **Context:** Wireless distributors and ISPs operate in areas with intermittent connectivity. Double-entry ledgers cannot tolerate duplicate entries or split-brain ledgers.
- **Decision:**
  1. Room database is the local authoritative source of truth.
  2. All write operations enqueue changes to a durable local `sync_outbox` table.
  3. Journal entries and lines are strictly append-only in Cloud Firestore (`allow update, delete: if false;`).
  4. Non-ledger master data uses Last-Write-Wins on `updatedAt`. Any overridden local version is preserved in `conflict_log`.
  5. Invariant validation (`LedgerInvariants.verifyAll()`) runs after every pull/merge cycle; any discrepancy immediately freezes cloud integration.

---

## 8. ADR-007: AI Sandboxing (Proposals Only, Zero Direct Writes)
- **Status:** Accepted
- **Context:** LLMs are non-deterministic and can hallucinate financial figures. Allowing an AI model to write directly to an IFRS ledger would violate accounting integrity.
- **Decision:**
  1. The AI layer (Gemini) has zero write permissions to `LedgerWriter` or financial DAOs.
  2. All AI outputs (such as invoice OCR) are strictly represented as in-memory drafts (`DraftPurchaseInvoice`).
  3. Every transaction requires explicit human review and authorization before being committed to the ledger.
  4. Q&A and Smart Summaries rely exclusively on structured read-only queries with citations from verified ledger reports.

---

## 9. ADR-008: Role-Based Access Control (RBAC) Enforcement Layer
- **Status:** Accepted
- **Context:** POS cashiers must not modify chart of accounts, delete transactions, or close fiscal periods.
- **Decision:**
  1. Roles (`OWNER`, `ACCOUNTANT`, `CASHIER`, `VIEWER`) are enforced programmatically in the UseCase/Service layer (`RbacManager.enforce()`).
  2. Unauthorized actions throw `RbacException` and record security violations in `audit_log`.
  3. UI buttons and features adapt dynamically based on active user credentials.
