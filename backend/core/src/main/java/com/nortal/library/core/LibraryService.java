package com.nortal.library.core;

import com.nortal.library.core.domain.Book;
import com.nortal.library.core.domain.Member;
import com.nortal.library.core.port.BookRepository;
import com.nortal.library.core.port.MemberRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Core business logic service for library operations.
 *
 * <p>This service implements the following key business rules:
 *
 * <ul>
 *   <li><b>Borrow Limits:</b> Members can borrow up to {@value MAX_LOANS} books simultaneously
 *   <li><b>Reservation Queue:</b> Books have a FIFO reservation queue; only the member at the head
 *       can borrow a reserved book
 *   <li><b>Automatic Handoff:</b> When a book is returned, it's automatically loaned to the next
 *       eligible member in the reservation queue
 *   <li><b>Immediate Loan:</b> Reserving an available book immediately loans it if the member is
 *       eligible
 *   <li><b>Data Integrity:</b> Books cannot be deleted if loaned or reserved; members cannot be
 *       deleted if they have active loans
 * </ul>
 *
 * <p>All operations that modify state validate business rules before making changes and return
 * Result objects indicating success or failure with appropriate reason codes.
 */
public class LibraryService {
  /** Maximum number of books a member can borrow simultaneously. */
  private static final int MAX_LOANS = 5;

  /** Default loan period in days. */
  private static final int DEFAULT_LOAN_DAYS = 14;

  private final BookRepository bookRepository;
  private final MemberRepository memberRepository;

  public LibraryService(BookRepository bookRepository, MemberRepository memberRepository) {
    this.bookRepository = bookRepository;
    this.memberRepository = memberRepository;
  }

  /**
   * Borrows a book for a member.
   *
   * <p>Business rules enforced:
   *
   * <ul>
   *   <li>Book must not already be loaned to another member
   *   <li>Member must not exceed the maximum borrow limit ({@value MAX_LOANS} books)
   *   <li>If book has a reservation queue, only the member at the head can borrow it
   *   <li>Member is automatically removed from reservation queue upon successful borrow
   * </ul>
   *
   * @param bookId the ID of the book to borrow
   * @param memberId the ID of the member borrowing the book
   * @return Result with success or failure reason (BOOK_NOT_FOUND, MEMBER_NOT_FOUND, BORROW_LIMIT,
   *     ALREADY_LOANED, RESERVED)
   */
  public Result borrowBook(String bookId, String memberId) {
    Optional<Book> book = bookRepository.findById(bookId);
    if (book.isEmpty()) {
      return Result.failure("BOOK_NOT_FOUND");
    }
    if (!memberRepository.existsById(memberId)) {
      return Result.failure("MEMBER_NOT_FOUND");
    }
    if (!canMemberBorrow(memberId)) {
      return Result.failure("BORROW_LIMIT");
    }
    Book entity = book.get();

    // Prevent double loans: check if book is already loaned
    if (entity.getLoanedTo() != null) {
      return Result.failure("ALREADY_LOANED");
    }

    // Enforce reservation queue: only member at head of queue can borrow
    if (!entity.getReservationQueue().isEmpty()) {
      String firstInQueue = entity.getReservationQueue().get(0);
      if (!memberId.equals(firstInQueue)) {
        return Result.failure("RESERVED");
      }
      // Remove the member from queue since they're now borrowing
      entity.getReservationQueue().remove(0);
    }

    entity.setLoanedTo(memberId);
    entity.setDueDate(LocalDate.now().plusDays(DEFAULT_LOAN_DAYS));
    bookRepository.save(entity);
    return Result.success();
  }

  /**
   * Returns a book and automatically loans it to the next eligible member in the reservation queue.
   *
   * <p>Business rules enforced:
   *
   * <ul>
   *   <li>Only the current borrower can return the book (when memberId is provided)
   *   <li>If memberId is null, uses the book's current borrower (for unauthenticated contexts)
   *   <li>Book must be currently loaned to be returned
   *   <li>If reservation queue exists, automatically loan to first eligible member
   *   <li>Skip members who no longer exist or have reached their borrow limit
   *   <li>Remove processed members from the queue
   * </ul>
   *
   * @param bookId the ID of the book being returned
   * @param memberId the ID of the member returning the book (optional; if null, uses current
   *     borrower)
   * @return ResultWithNext with the ID of the member who received the book next (or null if no one)
   */
  public ResultWithNext returnBook(String bookId, String memberId) {
    Optional<Book> book = bookRepository.findById(bookId);
    if (book.isEmpty()) {
      return ResultWithNext.failure();
    }

    Book entity = book.get();

    // If book is not currently loaned, return cannot proceed
    if (entity.getLoanedTo() == null) {
      return ResultWithNext.failure();
    }

    // If memberId is provided, validate that the returner is the current borrower
    // If memberId is null, use the book's current borrower (for unauthenticated contexts)
    if (memberId != null && !memberId.equals(entity.getLoanedTo())) {
      return ResultWithNext.failure();
    }

    // Clear the current loan
    entity.setLoanedTo(null);
    entity.setDueDate(null);

    // Process reservation queue: find first eligible member and loan to them automatically
    String nextMemberId = processReservationQueue(entity);

    bookRepository.save(entity);
    return ResultWithNext.success(nextMemberId);
  }

  /**
   * Processes the reservation queue to find the next eligible member and loans the book to them.
   *
   * <p>Iterates through the queue, removing ineligible members (deleted or at borrow limit), and
   * loans to the first eligible member found.
   *
   * @param book the book being processed
   * @return the ID of the member who received the book, or null if queue is empty or no eligible
   *     members found
   */
  private String processReservationQueue(Book book) {
    while (!book.getReservationQueue().isEmpty()) {
      String candidateMemberId = book.getReservationQueue().get(0);

      // Check if candidate exists and is under borrow limit
      if (memberRepository.existsById(candidateMemberId) && canMemberBorrow(candidateMemberId)) {
        // Loan to this member
        book.setLoanedTo(candidateMemberId);
        book.setDueDate(LocalDate.now().plusDays(DEFAULT_LOAN_DAYS));
        book.getReservationQueue().remove(0);
        return candidateMemberId;
      } else {
        // Skip ineligible member (deleted or at limit) and continue to next
        book.getReservationQueue().remove(0);
      }
    }
    return null;
  }

  /**
   * Reserves a book for a member, or immediately loans it if available.
   *
   * <p>Business rules enforced:
   *
   * <ul>
   *   <li>Member cannot reserve a book they already have borrowed
   *   <li>Member cannot reserve the same book multiple times
   *   <li>If book is available and member is eligible, loan immediately instead of queuing
   *   <li>Otherwise, add member to the end of the reservation queue
   * </ul>
   *
   * @param bookId the ID of the book to reserve
   * @param memberId the ID of the member making the reservation
   * @return Result with success or failure reason (BOOK_NOT_FOUND, MEMBER_NOT_FOUND,
   *     ALREADY_BORROWED, ALREADY_RESERVED)
   */
  public Result reserveBook(String bookId, String memberId) {
    Optional<Book> book = bookRepository.findById(bookId);
    if (book.isEmpty()) {
      return Result.failure("BOOK_NOT_FOUND");
    }
    if (!memberRepository.existsById(memberId)) {
      return Result.failure("MEMBER_NOT_FOUND");
    }

    Book entity = book.get();

    // Reject if member is currently borrowing this book
    if (memberId.equals(entity.getLoanedTo())) {
      return Result.failure("ALREADY_BORROWED");
    }

    // Reject duplicate reservations
    if (entity.getReservationQueue().contains(memberId)) {
      return Result.failure("ALREADY_RESERVED");
    }

    // If book is available and member is eligible, loan it immediately
    if (entity.getLoanedTo() == null && canMemberBorrow(memberId)) {
      entity.setLoanedTo(memberId);
      entity.setDueDate(LocalDate.now().plusDays(DEFAULT_LOAN_DAYS));
      bookRepository.save(entity);
      return Result.success();
    }

    // Otherwise, add to reservation queue
    entity.getReservationQueue().add(memberId);
    bookRepository.save(entity);
    return Result.success();
  }

  /**
   * Cancels a member's reservation for a book.
   *
   * @param bookId the ID of the book
   * @param memberId the ID of the member whose reservation to cancel
   * @return Result with success or failure reason (BOOK_NOT_FOUND, MEMBER_NOT_FOUND, NOT_RESERVED)
   */
  public Result cancelReservation(String bookId, String memberId) {
    Optional<Book> book = bookRepository.findById(bookId);
    if (book.isEmpty()) {
      return Result.failure("BOOK_NOT_FOUND");
    }
    if (!memberRepository.existsById(memberId)) {
      return Result.failure("MEMBER_NOT_FOUND");
    }

    Book entity = book.get();
    boolean removed = entity.getReservationQueue().remove(memberId);
    if (!removed) {
      return Result.failure("NOT_RESERVED");
    }
    bookRepository.save(entity);
    return Result.success();
  }

  /**
   * Checks if a member is eligible to borrow another book based on the borrow limit.
   *
   * <p>A member can borrow if they have fewer than {@value MAX_LOANS} books currently on loan.
   *
   * <p><b>Performance note:</b> This method performs an O(n) scan of all books where n = total
   * books in the library. For large datasets, consider adding a repository method like {@code
   * countByLoanedTo(memberId)} to push the query to the database level.
   *
   * @param memberId the ID of the member to check
   * @return true if member exists and has fewer than MAX_LOANS active loans, false otherwise
   */
  public boolean canMemberBorrow(String memberId) {
    if (!memberRepository.existsById(memberId)) {
      return false;
    }
    // Optimized: O(1) query instead of O(n) scan
    return bookRepository.countByLoanedTo(memberId) < MAX_LOANS;
  }

  public List<Book> searchBooks(String titleContains, Boolean availableOnly, String loanedTo) {
    // Optimized: Use database queries instead of in-memory filtering when possible
    List<Book> books;

    // Start with the most specific query
    if (loanedTo != null) {
      books = bookRepository.findByLoanedTo(loanedTo);
    } else if (Boolean.TRUE.equals(availableOnly)) {
      books = bookRepository.findByLoanedToIsNull();
    } else if (Boolean.FALSE.equals(availableOnly)) {
      // Get all loaned books (inverse of available)
      books = bookRepository.findAll().stream().filter(b -> b.getLoanedTo() != null).toList();
    } else {
      books = bookRepository.findAll();
    }

    // Apply title filter in memory if needed
    if (titleContains != null) {
      String searchTerm = titleContains.toLowerCase();
      books = books.stream().filter(b -> b.getTitle().toLowerCase().contains(searchTerm)).toList();
    }

    return books;
  }

  public List<Book> overdueBooks(LocalDate today) {
    // Optimized: O(1) database query instead of O(n) scan
    return bookRepository.findByDueDateBefore(today);
  }

  public Result extendLoan(String bookId, int days) {
    if (days == 0) {
      return Result.failure("INVALID_EXTENSION");
    }
    Optional<Book> book = bookRepository.findById(bookId);
    if (book.isEmpty()) {
      return Result.failure("BOOK_NOT_FOUND");
    }
    Book entity = book.get();
    if (entity.getLoanedTo() == null) {
      return Result.failure("NOT_LOANED");
    }
    LocalDate baseDate =
        entity.getDueDate() == null
            ? LocalDate.now().plusDays(DEFAULT_LOAN_DAYS)
            : entity.getDueDate();
    entity.setDueDate(baseDate.plusDays(days));
    bookRepository.save(entity);
    return Result.success();
  }

  public MemberSummary memberSummary(String memberId) {
    if (!memberRepository.existsById(memberId)) {
      return new MemberSummary(false, "MEMBER_NOT_FOUND", List.of(), List.of());
    }
    // Optimized: O(2) queries instead of O(n) scan
    List<Book> loans = bookRepository.findByLoanedTo(memberId);
    List<Book> booksWithReservations = bookRepository.findByReservationQueueContaining(memberId);
    List<ReservationPosition> reservations = new ArrayList<>();
    for (Book book : booksWithReservations) {
      int idx = book.getReservationQueue().indexOf(memberId);
      if (idx >= 0) {
        reservations.add(new ReservationPosition(book.getId(), idx));
      }
    }
    return new MemberSummary(true, null, loans, reservations);
  }

  public Optional<Book> findBook(String id) {
    return bookRepository.findById(id);
  }

  public List<Book> allBooks() {
    return bookRepository.findAll();
  }

  public List<Member> allMembers() {
    return memberRepository.findAll();
  }

  public Result createBook(String id, String title) {
    if (id == null || title == null) {
      return Result.failure("INVALID_REQUEST");
    }
    bookRepository.save(new Book(id, title));
    return Result.success();
  }

  public Result updateBook(String id, String title) {
    Optional<Book> existing = bookRepository.findById(id);
    if (existing.isEmpty()) {
      return Result.failure("BOOK_NOT_FOUND");
    }
    if (title == null) {
      return Result.failure("INVALID_REQUEST");
    }
    Book book = existing.get();
    book.setTitle(title);
    bookRepository.save(book);
    return Result.success();
  }

  /**
   * Deletes a book from the library.
   *
   * <p>Data integrity rules enforced:
   *
   * <ul>
   *   <li>Cannot delete a book that is currently on loan
   *   <li>Cannot delete a book that has members in its reservation queue
   * </ul>
   *
   * @param id the ID of the book to delete
   * @return Result with success or failure reason (BOOK_NOT_FOUND, BOOK_LOANED, BOOK_RESERVED)
   */
  public Result deleteBook(String id) {
    Optional<Book> existing = bookRepository.findById(id);
    if (existing.isEmpty()) {
      return Result.failure("BOOK_NOT_FOUND");
    }
    Book book = existing.get();

    // Prevent deletion if book is currently loaned
    if (book.getLoanedTo() != null) {
      return Result.failure("BOOK_LOANED");
    }

    // Prevent deletion if book has reservations
    if (!book.getReservationQueue().isEmpty()) {
      return Result.failure("BOOK_RESERVED");
    }

    bookRepository.delete(book);
    return Result.success();
  }

  public Result createMember(String id, String name) {
    if (id == null || name == null) {
      return Result.failure("INVALID_REQUEST");
    }
    memberRepository.save(new Member(id, name));
    return Result.success();
  }

  public Result updateMember(String id, String name) {
    Optional<Member> existing = memberRepository.findById(id);
    if (existing.isEmpty()) {
      return Result.failure("MEMBER_NOT_FOUND");
    }
    if (name == null) {
      return Result.failure("INVALID_REQUEST");
    }
    Member member = existing.get();
    member.setName(name);
    memberRepository.save(member);
    return Result.success();
  }

  /**
   * Deletes a member from the library system.
   *
   * <p>Data integrity rules enforced:
   *
   * <ul>
   *   <li>Cannot delete a member who currently has books on loan
   *   <li>Member is automatically removed from all reservation queues before deletion
   * </ul>
   *
   * @param id the ID of the member to delete
   * @return Result with success or failure reason (MEMBER_NOT_FOUND, MEMBER_HAS_LOANS)
   */
  public Result deleteMember(String id) {
    Optional<Member> existing = memberRepository.findById(id);
    if (existing.isEmpty()) {
      return Result.failure("MEMBER_NOT_FOUND");
    }

    // Check if member has active loans - must return books first
    // Optimized: O(1) query instead of O(n) scan
    if (bookRepository.existsByLoanedTo(id)) {
      return Result.failure("MEMBER_HAS_LOANS");
    }

    // Remove member from all reservation queues to maintain data integrity
    // Optimized: Only fetch books where member is in queue
    List<Book> booksWithReservations = bookRepository.findByReservationQueueContaining(id);
    for (Book book : booksWithReservations) {
      book.getReservationQueue().remove(id);
      bookRepository.save(book);
    }

    memberRepository.delete(existing.get());
    return Result.success();
  }

  public record Result(boolean ok, String reason) {
    public static Result success() {
      return new Result(true, null);
    }

    public static Result failure(String reason) {
      return new Result(false, reason);
    }
  }

  public record ResultWithNext(boolean ok, String nextMemberId) {
    public static ResultWithNext success(String nextMemberId) {
      return new ResultWithNext(true, nextMemberId);
    }

    public static ResultWithNext failure() {
      return new ResultWithNext(false, null);
    }
  }

  public record MemberSummary(
      boolean ok, String reason, List<Book> loans, List<ReservationPosition> reservations) {}

  public record ReservationPosition(String bookId, int position) {}
}
