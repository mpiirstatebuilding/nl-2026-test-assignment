package com.nortal.library.core.service;

import static com.nortal.library.core.ErrorCodes.*;

import com.nortal.library.core.Result;
import com.nortal.library.core.ResultWithNext;
import com.nortal.library.core.domain.Book;
import com.nortal.library.core.port.BookRepository;
import com.nortal.library.core.port.MemberRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Service responsible for loan operations: borrowing, returning, reserving, and extending loans.
 *
 * <p>This service encapsulates all loan-related business logic including:
 *
 * <ul>
 *   <li>Borrow limit enforcement ({@value MAX_LOANS} books per member)
 *   <li>Reservation queue management (FIFO)
 *   <li>Automatic handoff on return
 *   <li>Loan extension with maximum limits
 * </ul>
 */
public class LoanService {
  /** Maximum number of books a member can borrow simultaneously. */
  private static final int MAX_LOANS = 5;

  /** Default loan period in days. */
  private static final int DEFAULT_LOAN_DAYS = 14;

  /** Maximum total extension period in days from first due date (approximately 3 months). */
  private static final int MAX_EXTENSION_DAYS = 90;

  /** Index of the head (first priority) position in the reservation queue. */
  private static final int QUEUE_HEAD_POSITION = 0;

  private final BookRepository bookRepository;
  private final MemberRepository memberRepository;

  public LoanService(BookRepository bookRepository, MemberRepository memberRepository) {
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
   *     ALREADY_BORROWED, BOOK_UNAVAILABLE, RESERVED)
   */
  public Result borrowBook(String bookId, String memberId) {
    Optional<Book> book = bookRepository.findById(bookId);
    if (book.isEmpty()) {
      return Result.failure(BOOK_NOT_FOUND);
    }
    if (!memberRepository.existsById(memberId)) {
      return Result.failure(MEMBER_NOT_FOUND);
    }
    if (!canMemberBorrow(memberId)) {
      return Result.failure(BORROW_LIMIT);
    }
    Book entity = book.get();

    // Prevent double loans: check if book is already loaned
    if (entity.getLoanedTo() != null) {
      // Check if the member trying to borrow is the current borrower
      if (memberId.equals(entity.getLoanedTo())) {
        return Result.failure(ALREADY_BORROWED);
      } else {
        return Result.failure(BOOK_UNAVAILABLE);
      }
    }

    // Enforce reservation queue: only member at head of queue can borrow
    if (!entity.getReservationQueue().isEmpty()) {
      String firstInQueue = entity.getReservationQueue().get(QUEUE_HEAD_POSITION);
      if (!memberId.equals(firstInQueue)) {
        return Result.failure(RESERVED);
      }
      // Remove the member from queue since they're now borrowing
      entity.getReservationQueue().remove(QUEUE_HEAD_POSITION);
    }

    entity.setLoanedTo(memberId);
    LocalDate initialDueDate = LocalDate.now().plusDays(DEFAULT_LOAN_DAYS);
    entity.setDueDate(initialDueDate);
    entity.setFirstDueDate(initialDueDate); // Set anchor point for extension limits
    bookRepository.save(entity);
    return Result.success();
  }

  /**
   * Returns a book and automatically loans it to the next eligible member in the reservation queue.
   *
   * <p>Business rules enforced:
   *
   * <ul>
   *   <li>Only the current borrower can return the book (memberId is required for security)
   *   <li>Book must be currently loaned to be returned
   *   <li>If reservation queue exists, automatically loan to first eligible member
   *   <li>Skip members who no longer exist or have reached their borrow limit
   *   <li>Remove processed members from the queue
   * </ul>
   *
   * @param bookId the ID of the book being returned
   * @param memberId the ID of the member returning the book (required; must match current borrower)
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

    // Validate that the returner is the current borrower (memberId is now required)
    // Per API contract, memberId should always be provided for security
    if (memberId == null || !memberId.equals(entity.getLoanedTo())) {
      return ResultWithNext.failure();
    }

    // Clear the current loan
    entity.setLoanedTo(null);
    entity.setDueDate(null);
    entity.setFirstDueDate(null); // Clear extension anchor point

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
      String candidateMemberId = book.getReservationQueue().get(QUEUE_HEAD_POSITION);

      // Check if candidate exists and is under borrow limit
      if (memberRepository.existsById(candidateMemberId) && canMemberBorrow(candidateMemberId)) {
        // Eligible member found - loan book to them automatically
        book.setLoanedTo(candidateMemberId);
        LocalDate initialDueDate = LocalDate.now().plusDays(DEFAULT_LOAN_DAYS);
        book.setDueDate(initialDueDate);
        book.setFirstDueDate(initialDueDate); // Set anchor point for extension limits
        book.getReservationQueue().remove(QUEUE_HEAD_POSITION);
        return candidateMemberId;
      } else {
        // Skip ineligible member (deleted or at limit) and continue to next
        book.getReservationQueue().remove(QUEUE_HEAD_POSITION);
      }
    }
    // No eligible member found in queue
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
      return Result.failure(BOOK_NOT_FOUND);
    }
    if (!memberRepository.existsById(memberId)) {
      return Result.failure(MEMBER_NOT_FOUND);
    }

    Book entity = book.get();

    // Reject if member is currently borrowing this book
    if (memberId.equals(entity.getLoanedTo())) {
      return Result.failure(ALREADY_BORROWED);
    }

    // Reject duplicate reservations
    if (entity.getReservationQueue().contains(memberId)) {
      return Result.failure(ALREADY_RESERVED);
    }

    // If book is available and member is eligible, loan it immediately
    if (entity.getLoanedTo() == null && canMemberBorrow(memberId)) {
      entity.setLoanedTo(memberId);
      LocalDate initialDueDate = LocalDate.now().plusDays(DEFAULT_LOAN_DAYS);
      entity.setDueDate(initialDueDate);
      entity.setFirstDueDate(initialDueDate); // Set anchor point for extension limits
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
      return Result.failure(BOOK_NOT_FOUND);
    }
    if (!memberRepository.existsById(memberId)) {
      return Result.failure(MEMBER_NOT_FOUND);
    }

    Book entity = book.get();
    boolean removed = entity.getReservationQueue().remove(memberId);
    if (!removed) {
      return Result.failure(NOT_RESERVED);
    }
    bookRepository.save(entity);
    return Result.success();
  }

  /**
   * Extends the due date of a loaned book.
   *
   * <p>Business rules enforced:
   *
   * <ul>
   *   <li>Only current borrower can extend
   *   <li>Cannot extend if book has reservations (others waiting)
   *   <li>Total extension cannot exceed {@value MAX_EXTENSION_DAYS} days from first due date
   * </ul>
   *
   * @param bookId the ID of the book to extend
   * @param memberId the ID of the member extending (must be current borrower)
   * @param days number of days to extend
   * @return Result with success or failure reason
   */
  public Result extendLoan(String bookId, String memberId, int days) {
    if (days == 0) {
      return Result.failure(INVALID_EXTENSION);
    }
    Optional<Book> book = bookRepository.findById(bookId);
    if (book.isEmpty()) {
      return Result.failure(BOOK_NOT_FOUND);
    }
    if (!memberRepository.existsById(memberId)) {
      return Result.failure(MEMBER_NOT_FOUND);
    }
    Book entity = book.get();
    if (entity.getLoanedTo() == null) {
      return Result.failure(NOT_LOANED);
    }
    // Validate that the member extending is the current borrower (authorization check)
    if (!memberId.equals(entity.getLoanedTo())) {
      return Result.failure(NOT_BORROWER);
    }
    // Cannot extend if book has reservations (others are waiting)
    if (!entity.getReservationQueue().isEmpty()) {
      return Result.failure(RESERVATION_EXISTS);
    }
    LocalDate baseDate =
        entity.getDueDate() == null
            ? LocalDate.now().plusDays(DEFAULT_LOAN_DAYS)
            : entity.getDueDate();
    // Check if extension would exceed maximum extension limit (90 days from first due date)
    if (entity.getFirstDueDate() != null) {
      LocalDate newDueDate = baseDate.plusDays(days);
      long totalDaysFromFirst = ChronoUnit.DAYS.between(entity.getFirstDueDate(), newDueDate);
      if (totalDaysFromFirst > MAX_EXTENSION_DAYS) {
        return Result.failure(MAX_EXTENSION_REACHED);
      }
    }
    entity.setDueDate(baseDate.plusDays(days));
    bookRepository.save(entity);
    return Result.success();
  }

  /**
   * Checks if a member is eligible to borrow another book based on the borrow limit.
   *
   * <p>A member can borrow if they have fewer than {@value MAX_LOANS} books currently on loan.
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
}
