# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a **Nortal LEAP 2026 coding assignment** - a library management system with a Spring Boot backend (Java 21) and Angular 20 frontend. The backend implements loan/reservation logic with sophisticated business rules including reservation queues and automatic book handoff.

**Assignment Focus:** Fix backend business logic bugs while keeping the API contract unchanged. The frontend is for testing only; all work is in the Java backend.

## Quick Start Commands

### Backend (Java 21 + Gradle)
```bash
# Start API server (port 8080, H2 in-memory with seed data)
node tools/run-backend.mjs start
# OR: cd backend && ./gradlew :api:bootRun

# Run tests (always run before committing)
node tools/run-backend.mjs test
# OR: cd backend && ./gradlew test

# Format code (required before submission)
node tools/run-backend.mjs format
# OR: cd backend && ./gradlew spotlessApply

# Build without tests
node tools/run-backend.mjs build-skip
# OR: cd backend && ./gradlew build -x test

# Performance smoke test (requires API running)
node tools/run-perf.mjs
```

### Frontend (Angular 20, Node.js 20+)
```bash
# Install dependencies
node tools/run-frontend.mjs install
# OR: cd frontend && npm install

# Start dev server (http://localhost:4200)
node tools/run-frontend.mjs start
# OR: cd frontend && npm start

# Format code
cd frontend && npm run format
```

### Development Tools
```bash
# Check environment setup (auto-detects OS issues)
node tools/doctor.mjs
```

## Backend Architecture

### Module Structure (Hexagonal/Ports & Adapters)

```
backend/
├── core/          Pure business logic (no framework dependencies)
│   ├── domain/    Book.java, Member.java entities
│   ├── port/      BookRepository, MemberRepository interfaces
│   └── LibraryService.java (main business logic - 486 lines)
├── persistence/   Data access layer
│   ├── jpa/       Spring Data JPA repositories
│   └── adapter/   Repository adapters implementing core ports
└── api/           REST layer + application startup
    ├── controller/ REST endpoints
    ├── dto/        Request/response records
    └── config/     Spring configuration
```

**Key Design Principles:**
- **Core** defines interfaces (ports); **persistence** provides implementations (adapters)
- Business logic in `LibraryService` has zero Spring dependencies (pure Java + Jakarta annotations)
- Controllers never expose domain entities directly - always convert to DTOs
- Result-based error handling (no exceptions for business rule violations)

### Critical Domain Entities

**Book** (`backend/core/src/main/java/com/nortal/library/core/domain/Book.java`)
```java
@Entity
class Book {
  String id;                    // Unique identifier
  String title;                 // Book title
  String loanedTo;             // Current borrower member ID (null = available)
  LocalDate dueDate;           // Loan expiration (null = available)
  List<String> reservationQueue; // FIFO queue of member IDs
}
```

**Member** (`backend/core/src/main/java/com/nortal/library/core/domain/Member.java`)
```java
@Entity
class Member {
  String id;    // Unique identifier
  String name;  // Member name
}
```

**Note:** Loans are embedded in Book state (no separate Loan entity). Member's loans/reservations are derived by scanning all books.

### Business Logic Hub: LibraryService

**Location:** `backend/core/src/main/java/com/nortal/library/core/LibraryService.java`

**Key Constants:**
- `MAX_LOANS = 5` - Maximum books per member
- `DEFAULT_LOAN_DAYS = 14` - Loan period in days

**Critical Methods & Business Rules:**

#### borrowBook(bookId, memberId)
- Prevents double loans (book already loaned → `ALREADY_LOANED`)
- Enforces reservation queue (only position 0 can borrow → `RESERVED`)
- Checks borrow limit (5 books max → `BORROW_LIMIT`)
- Auto-removes borrower from queue if they were head of queue
- Sets due date to 14 days from today

#### returnBook(bookId, memberId?)
- **`memberId` is OPTIONAL** (per API contract: `POST /api/return { bookId, memberId? }`)
- Book must be currently loaned to be returned
- When `memberId` provided: validates it matches book's current borrower (security check)
- When `memberId` is null: accepts return without validation (use case: unauthenticated contexts)
- Clears loan state (loanedTo, dueDate)
- **Automatic handoff:** Processes reservation queue via `processReservationQueue(book)`
  - Finds first eligible member (exists + not at borrow limit)
  - Loans book to them automatically
  - Removes them from queue
  - Returns their ID in `ResultWithNext.nextMemberId`

#### reserveBook(bookId, memberId)
- Rejects if member already has book (`ALREADY_BORROWED`)
- Rejects duplicate reservations (`ALREADY_RESERVED`)
- **Smart immediate loan:** If book available AND member eligible → loans immediately (doesn't queue)
- Otherwise: adds to end of FIFO queue

#### canMemberBorrow(memberId)
- Returns true if member exists and has < 5 active loans
- **Performance Note:** O(n) scan of all books - documented as optimization opportunity
- Early exit when count reaches MAX_LOANS

#### deleteBook(id) / deleteMember(id)
- **Data integrity:** Cannot delete loaned books or books with reservations
- Cannot delete members with active loans
- `deleteMember` auto-removes member from all reservation queues before deletion

### API Endpoints

**Base URL:** `http://localhost:8080/api`

**Seed Data:** Members `m1`-`m4`, Books `b1`-`b6`

**Core Endpoints:**
```
GET    /api/books                  → { items: [{ id, title, loanedTo, dueDate, reservationQueue }] }
GET    /api/books/search?titleContains=...&available=true&loanedTo=m1
GET    /api/members                → { items: [{ id, name }] }
GET    /api/members/{id}/summary   → { loans: [...], reservations: [...] }
POST   /api/borrow                 { bookId, memberId } → { ok, reason? }
POST   /api/return                 { bookId, memberId } → { ok, nextMemberId? }
POST   /api/reserve                { bookId, memberId } → { ok, reason? }
POST   /api/cancel-reservation     { bookId, memberId } → { ok, reason? }
POST   /api/extend                 { bookId, days } → { ok, reason? }
GET    /api/overdue                → { items: [...] }
GET    /api/health                 → { status: "ok" }
```

**Response Format:**
```json
// Success
{ "ok": true, "reason": null }

// Failure with reason code
{ "ok": false, "reason": "BORROW_LIMIT" }

// Return with handoff
{ "ok": true, "nextMemberId": "m2" }
```

### Database (H2 In-Memory)

**Console:** `http://localhost:8080/h2-console`
**JDBC URL:** `jdbc:h2:mem:library`

**Tables:**
- `books` (id, title, loaned_to, due_date)
- `book_reservations` (book_id, member_id, position) - ElementCollection for queue
- `members` (id, name)

## Assignment Requirements (Backend Only)

The core assignment is to **fix business logic bugs** in `LibraryService.java` while keeping API contracts unchanged.

### Key Behaviors to Implement

**1. Prevent double loans & respect queues**
- Book already loaned must not be loaned again
- If reservation queue exists, only head member can borrow (no line-jumping)
- Returns must only succeed when initiated by current borrower

**2. Reservation lifecycle**
- Reject duplicate reservations by same member
- Reserving an available book should immediately loan it (if eligible)
- Returning book must auto-hand it to next eligible reserver in queue order
- Skip ineligible/missing members and continue down queue
- Surface who received the book in response

**3. Borrow-limit enforcement**
- Enforce max 5 books per member cleanly and efficiently
- Avoid needlessly expensive O(n) scans where possible

**Note:** According to `AI_USAGE.md`, all these requirements have been implemented.

## Code Quality & Submission

**Before any commit:**
```bash
./gradlew test              # All tests must pass
./gradlew spotlessApply     # Auto-format code (Google Java Format)
```

**Spotless Configuration:**
- Uses Google Java Format
- Auto-removes unused imports
- Standardizes import order
- Applied to all Java files in subprojects

**Required Documentation:**
- Update `AI_USAGE.md` with any AI-assisted changes (brief, honest)
- Document assumptions in code comments or AI_USAGE.md

**Deliverables:**
- Working backend (`./gradlew :api:bootRun` must run)
- All tests passing
- Code formatted with Spotless
- Clean git history (do not commit `build/`, `node_modules/`, `dist/`)

## Testing & Verification

**Visible Tests:** Minimal - located in each module's `src/test/java`

**Hidden Tests:** Scoring tests validate correctness via `./gradlew hiddenVerification` task (defined in root build.gradle)

**Manual Testing:**
1. Start backend: `./gradlew :api:bootRun`
2. Start frontend: `cd frontend && npm start`
3. Test via UI at `http://localhost:4200`
4. Or use curl/Postman against API endpoints

## Performance Optimization Opportunities

**Documented in code:**
- `canMemberBorrow()` does O(n) scan - could add `countByLoanedTo(memberId)` repository method
- `memberSummary()` scans all books - could add dedicated queries
- Caching enabled but not yet applied to methods (Caffeine dependency present)

**When optimizing:**
- Keep business logic in `LibraryService` (core module)
- Add repository methods to `BookRepository` interface (core/port)
- Implement in `BookRepositoryAdapter` and `JpaBookRepository` (persistence)

## Important Files Reference

| File | Lines | Purpose |
|------|-------|---------|
| `backend/core/.../LibraryService.java` | 486 | **All business logic - start here** |
| `backend/core/.../domain/Book.java` | 46 | Book entity with loan/queue state |
| `backend/api/.../controller/LoanController.java` | 78 | Borrow/return/reserve endpoints |
| `backend/README.md` | 35 | Backend-specific instructions |
| `AI_USAGE.md` | 145 | Complete changelog of AI-assisted work |
| `README.md` | 74 | Project overview & assignment brief |

## Development Tips

**When modifying business logic:**
1. Read the comprehensive JavaDoc in `LibraryService.java` (lines 12-31)
2. Understand the method's result codes (documented in JavaDoc)
3. Write tests first or verify with existing tests
4. Run `./gradlew test` after changes
5. Format with `./gradlew spotlessApply`

**When adding features:**
- Domain logic → `LibraryService` (core)
- Persistence needs → Add to repository interface (core/port) + implement in adapter (persistence)
- New endpoint → Add controller method (api/controller) + DTO (api/dto)
- Always maintain Result-based error handling (avoid throwing exceptions for business rules)

**When debugging:**
- Check H2 console for data state
- P6Spy logs SQL statements to console
- Use `GET /api/members/{id}/summary` to see member's loans/reservations
- Seed data initialized in `api/.../config/DataLoader.java`

## Project Context

This is a **coding assignment submission** - the solution has been completed with AI assistance (see `AI_USAGE.md` for full details). All assignment requirements have been implemented:
- ✅ Double loan prevention
- ✅ Reservation queue enforcement
- ✅ Automatic handoff on return
- ✅ Immediate loan on reserve (if available)
- ✅ Duplicate reservation rejection
- ✅ Return validation (only current borrower)
- ✅ Borrow limit enforcement with optimization
- ✅ Data integrity on delete operations
