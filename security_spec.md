# Security Specification — PulseFolio

## 1. Data Invariants
1. **Authentication Invariant**: Every read (`get`, `list`) and write (`create`, `update`, `delete`) operation across `/users/{userId}/holdings/{holdingId}`, `/users/{userId}/watchlist/{itemId}`, and `/users/{userId}/emi_plans/{planId}` requires `request.auth != null`.
2. **Multi-User Isolation Invariant**: A user may only access or mutate documents nested under their own `/users/{userId}` path where `request.auth.uid == userId`, and written documents must carry `incoming().userId == request.auth.uid`.
3. **Schema & Domain Bounds Invariant**:
   - `PortfolioHolding`: `symbol` (1..20 chars), `companyName` (1..120 chars), `shares` (> 0, <= 1,000,000,000), `avgBuyPrice` (> 0, <= 1,000,000,000), `sector` (1..60 chars), optional `notes` (<= 300 chars), server timestamps `createdAt` and `updatedAt` (`<= request.time`).
   - `WatchlistItem`: `symbol` (1..20 chars), `companyName` (1..120 chars), `targetAlertPrice` (>= 0, <= 1,000,000,000), server timestamps `createdAt` and `updatedAt` (`<= request.time`).
   - `EmiPlan`: `title` (1..100 chars), `loanType` in allowed enum, `principal` (> 0), `annualRate` (> 0, <= 100), `tenureMonths` (1..600), `monthlyPrepayment` (>= 0), `monthlyEmi` (> 0), `totalInterest` (>= 0), server timestamps `createdAt` and `updatedAt` (`<= request.time`).
4. **Immutability Invariant**: `userId` and `createdAt` cannot be modified during `update` operations (`incoming().userId == existing().userId && incoming().createdAt == existing().createdAt`).

## 2. The "Dirty Dozen" Payloads
1. **Unauthenticated Read**: `unauthDb.collection("users/alice_123/holdings").get()` -> Reject (`PERMISSION_DENIED`).
2. **Cross-User Read**: `bobDb.collection("users/alice_123/holdings").doc("h1").get()` -> Reject (`PERMISSION_DENIED`).
3. **Cross-User Write**: `bobDb.collection("users/alice_123/holdings").doc("h1").set(...)` -> Reject (`PERMISSION_DENIED`).
4. **UID Spoofing in Body**: `aliceDb` writing `userId: "bob_456"` inside `/users/alice_123/holdings/h1` -> Reject (`PERMISSION_DENIED`).
5. **Shadow Update / Ghost Field**: Adding `isAdmin: true` or `verified: true` on `create` or `update` -> Reject via `.keys().hasOnly(...)` and `.affectedKeys().hasOnly(...)`.
6. **Negative Shares or Price**: `shares: -5` or `avgBuyPrice: 0` -> Reject via `isValidPortfolioHolding`.
7. **Oversized String / Resource Exhaustion**: `symbol` > 20 chars or `notes` > 300 chars -> Reject via `.size()` checks.
8. **Future Timestamp Spoofing**: `createdAt` set to a future timestamp (`> request.time`) or non-timestamp string -> Reject via `is timestamp && <= request.time`.
9. **Immutable `createdAt` Mutation on Update**: Changing `createdAt` during an `update` -> Reject via `incoming().createdAt == existing().createdAt`.
10. **Immutable `userId` Mutation on Update**: Changing `userId` during an `update` -> Reject via `incoming().userId == existing().userId`.
11. **Invalid Loan Type Enum**: `loanType: "Crypto Leverage"` in `emi_plans` -> Reject via allowlist check.
12. **ID Poisoning**: Document ID with invalid characters or length > 128 -> Reject via `isValidId(docId)`.

## 3. Red Team Audit Summary
- **Shadow Update Test**: Passed. All `create` blocks enforce `incoming().keys().hasOnly(...)` and all `update` blocks enforce `incoming().diff(existing()).affectedKeys().hasOnly(...)` plus full `isValid[Entity](incoming())`.
- **PII Blanket Test**: Passed. No public collections exist; all user documents are isolated under `/users/{userId}/...` with `request.auth.uid == userId`.
- **Query Trust Test**: Passed. Every `allow list` checks `isSignedIn() && request.auth.uid == userId && resource.data.userId == request.auth.uid`.
- **Value Poisoning Test**: Passed. Every `allow update` begins with `isSignedIn() && isValidId(...) && isValid[Entity](incoming())`.
