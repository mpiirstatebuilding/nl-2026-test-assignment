# Project Analysis: Nortal LEAP 2026 Library Management System

## Project Overview

This is a coding assignment project for Nortal LEAP 2026, implementing a library management system with book borrowing and reservation functionality.

### Technology Stack
- **Backend**: Java 21, Spring Boot 3.3.4, Gradle, H2 in-memory database
- **Frontend**: Angular 20, TypeScript 5.9
- **Architecture**: Clean architecture with 3 modules (core/persistence/api)

### Project Structure
```
├── backend/          # Spring Boot application
│   ├── core/        # Domain entities + LibraryService (business logic)
│   ├── persistence/ # JPA repositories for H2
│   └── api/         # REST controllers, security, seed data
├── frontend/         # Angular 20 UI
└── tools/           # Helper scripts (doctor, run-backend, run-frontend, run-perf)
```

---

## Critical Problems to Fix

### 1. **Double Loan Prevention** ❌ CRITICAL
**Location**: `backend/core/src/main/java/com/nortal/library/core/LibraryService.java:24-40`

**Problem**: The `borrowBook` method does not check if a book is already loaned before assigning it to a new borrower.

```java
public Result borrowBook(String bookId, String memberId) {
    // ... validation ...
    Book entity = book.get();
    entity.setLoanedTo(memberId);  // ⚠️ NO CHECK IF ALREADY LOANED!
    entity.setDueDate(LocalDate.now().plusDays(DEFAULT_LOAN_DAYS));
    bookRepository.save(entity);
    return Result.success();
}
```

**Impact**:
- Same book can be borrowed by multiple members simultaneously
- Previous borrower is silently overwritten
- Data integrity violation

**Fix Required**: Add validation to check `entity.getLoanedTo() != null` before allowing borrow.

---

### 2. **Reservation Queue Not Enforced on Borrow** ❌ CRITICAL
**Location**: `backend/core/src/main/java/com/nortal/library/core/LibraryService.java:24-40`

**Problem**: Members can bypass the reservation queue by directly borrowing a book.

**Impact**:
- "Line jumping" - anyone can borrow a book even if others are waiting
- Reservation system is useless
- Unfair to members who properly reserved

**Fix Required**: When borrowing, if `reservationQueue` is not empty, only the member at position 0 should be allowed to borrow.

---

### 3. **Duplicate Reservations Allowed** ❌ CRITICAL
**Location**: `backend/core/src/main/java/com/nortal/library/core/LibraryService.java:57-70`

**Problem**: Same member can reserve a book multiple times.

```java
public Result reserveBook(String bookId, String memberId) {
    // ... validation ...
    Book entity = book.get();
    entity.getReservationQueue().add(memberId);  // ⚠️ NO DUPLICATE CHECK!
    bookRepository.save(entity);
    return Result.success();
}
```

**Impact**:
- Queue pollution with duplicate entries
- Member appears multiple times in line
- Incorrect queue processing logic

**Fix Required**: Check if `memberId` already exists in `reservationQueue` before adding.

---

### 4. **No Immediate Loan on Available Book Reservation** ❌ CRITICAL
**Location**: `backend/core/src/main/java/com/nortal/library/core/LibraryService.java:57-70`

**Problem**: When reserving an available book (not currently loaned), the member is added to queue instead of immediately receiving the book.

**Impact**:
- Member must wait unnecessarily for an available book
- Poor user experience
- Violates assignment requirement: "Reserving an available book should immediately loan it"

**Fix Required**: Check if `entity.getLoanedTo() == null` when reserving; if available and member is eligible, loan immediately instead of queuing.

---

### 5. **Return Book Does Not Validate Returner** ❌ CRITICAL
**Location**: `backend/core/src/main/java/com/nortal/library/core/LibraryService.java:42-55`

**Problem**: Anyone can return any book; no validation that the returner is the actual borrower.

```java
public ResultWithNext returnBook(String bookId, String memberId) {
    // ... validation ...
    Book entity = book.get();
    entity.setLoanedTo(null);  // ⚠️ NO VALIDATION OF CURRENT BORROWER!
    entity.setDueDate(null);
    // ...
}
```

**Impact**:
- Security/integrity issue
- Anyone can return books they didn't borrow
- No accountability

**Fix Required**: Validate that `memberId` matches `entity.getLoanedTo()` before allowing return.

---

### 6. **Return Book Does Not Process Reservation Queue** ❌ CRITICAL
**Location**: `backend/core/src/main/java/com/nortal/library/core/LibraryService.java:42-55`

**Problem**: The return logic identifies the next member in queue but doesn't automatically loan the book to them.

```java
public ResultWithNext returnBook(String bookId, String memberId) {
    // ... validation ...
    Book entity = book.get();
    entity.setLoanedTo(null);
    entity.setDueDate(null);
    String nextMember =
        entity.getReservationQueue().isEmpty() ? null : entity.getReservationQueue().get(0);
    bookRepository.save(entity);  // ⚠️ QUEUE NOT MODIFIED! BOOK NOT LOANED TO NEXT!
    return ResultWithNext.success(nextMember);
}
```

**Impact**:
- Reservation queue is never processed
- Members in queue never receive the book
- Manual intervention required
- Violates assignment requirement

**Fix Required**:
1. Loop through `reservationQueue` to find first eligible member
2. Check each member exists and is under borrow limit
3. Automatically loan to first eligible member
4. Remove that member from queue
5. Skip ineligible members and continue

---

### 7. **Inefficient Borrow Limit Check** ⚠️ PERFORMANCE
**Location**: `backend/core/src/main/java/com/nortal/library/core/LibraryService.java:90-101`

**Problem**: Scans ALL books in the database to count loans for one member.

```java
public boolean canMemberBorrow(String memberId) {
    if (!memberRepository.existsById(memberId)) {
      return false;
    }
    int active = 0;
    for (Book book : bookRepository.findAll()) {  // ⚠️ SCANS ALL BOOKS!
      if (memberId.equals(book.getLoanedTo())) {
        active++;
      }
    }
    return active < MAX_LOANS;
}
```

**Impact**:
- O(n) complexity where n = total books
- Called on every borrow attempt and during return processing
- Could scan thousands of books
- No early exit optimization

**Fix Required**:
- Add early exit: `if (active >= MAX_LOANS) break;`
- Better: Add `countByLoanedTo(memberId)` repository method for database-level query

---

## Additional Issues

### 8. **Build Artifacts Committed** ⚠️ VERSION CONTROL
**Location**: Git status shows uncommitted build artifacts

**Problem**: Git status shows:
```
?? backend/api/build/
?? backend/core/build/
?? backend/persistence/build/
```

**Impact**:
- Build artifacts should not be in version control
- `.gitignore` already has `backend/build` but not submodule builds
- Pollutes repository

**Fix Required**: Update `.gitignore` to include `backend/*/build/`

---

### 9. **Incomplete .gitignore** ⚠️ VERSION CONTROL
**Location**: `.gitignore:19`

**Problem**: `.idea` directory is listed but should be `/.idea` or `.idea/` for proper pattern matching.

**Current**:
```
# IDE / editor
.idea
```

**Fix Required**: Change to `.idea/` for consistency with other patterns.

---

### 10. **Root package-lock.json Not Ignored** ⚠️ VERSION CONTROL
**Location**: Git status shows `?? package-lock.json` in root

**Problem**: Root `package-lock.json` exists but appears to be unintended (no root `package.json` exists).

**Fix Required**: Either remove the file or add it to `.gitignore` if it's not needed.

---

### 11. **Mysterious .output.txt File** ⚠️ VERSION CONTROL
**Location**: Git status shows `?? .output.txt`

**Problem**: Untracked file of unknown purpose.

**Fix Required**: Review file contents and either commit if important or add to `.gitignore`.

---

### 12. **No Delete Validation** ⚠️ DATA INTEGRITY
**Location**: `LibraryService.java` - delete methods

**Problem**:
- `deleteBook` doesn't check if book is currently loaned or has reservations
- `deleteMember` doesn't check if member has active loans or is in reservation queues

**Impact**:
- Orphaned data references
- Broken reservation queues
- Data integrity issues

**Fix Required**: Add validation before allowing deletions.

---

### 13. **Modified frontend/package-lock.json Not Committed** ⚠️ VERSION CONTROL
**Location**: Git status shows `M frontend/package-lock.json`

**Problem**: Package lock file has changes that aren't committed.

**Impact**:
- Dependency version drift
- Inconsistent builds across environments

**Fix Required**: Review changes and commit if intentional, or revert if accidental.

---

## Test Coverage Gaps

### Unit Tests
**Location**: `backend/src/test/java/`

**Issues**:
- Tests don't cover critical edge cases:
  - Double loan attempts
  - Queue jumping scenarios
  - Invalid return attempts
  - Duplicate reservations
  - Borrow limit edge cases
- Integration tests only cover happy paths

**Fix Required**: Add comprehensive test cases for all business rules.

---

## Assignment Requirements Checklist

### ✅ Completed
- Basic CRUD operations for books/members
- API structure and contracts
- Frontend UI (not part of assignment focus)
- Authentication framework (optional, present)

### ❌ Not Implemented (Required by Assignment)
1. **Prevent double loans** - Currently allows multiple simultaneous loans
2. **Respect reservation queues** - Currently allows queue jumping
3. **Only current borrower can return** - Currently anyone can return
4. **Reject duplicate reservations** - Currently allows duplicates
5. **Immediate loan on available book reservation** - Currently always queues
6. **Automatic handoff on return** - Currently just identifies next member
7. **Skip ineligible members in queue** - Not implemented
8. **Efficient borrow limit check** - Currently O(n) scan

### ⚠️ Partially Implemented
- **Borrow limit enforcement** - Logic exists but inefficient

---

## Recommended Fix Priority

### Priority 1: Critical Business Logic (Must Fix)
1. Add double loan prevention in `borrowBook`
2. Add queue enforcement in `borrowBook`
3. Add returner validation in `returnBook`
4. Implement automatic queue processing in `returnBook`
5. Add duplicate reservation check in `reserveBook`
6. Add immediate loan on available book in `reserveBook`

### Priority 2: Performance & Code Quality
7. Optimize `canMemberBorrow` with early exit
8. Consider database-level query for loan counting

### Priority 3: Data Integrity
9. Add validation to delete operations
10. Add comprehensive test coverage

### Priority 4: Repository Hygiene
11. Fix `.gitignore` patterns
12. Clean up untracked files
13. Commit or revert `package-lock.json` changes

---

## Summary

**Project State**: Functional foundation with basic CRUD operations, but missing all critical business rule enforcement specified in the assignment.

**Main Issue**: The `LibraryService` class implements a naive "happy path" version of library operations without validation, constraint checking, or queue management.

**Effort Required**: All fixes can be implemented within the existing `LibraryService` class without changing the API surface. Estimated 2-4 hours of focused development to implement all required business logic.

**Risk Level**: High - current implementation allows data corruption, unfair resource allocation, and violates core assignment requirements. Would fail automated tests.
