package com.nortal.library.core;

import com.nortal.library.core.domain.Book;
import com.nortal.library.core.domain.Member;
import com.nortal.library.core.service.BookManagementService;
import com.nortal.library.core.service.LibraryQueryService;
import com.nortal.library.core.service.LoanService;
import com.nortal.library.core.service.MemberManagementService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Facade service providing a unified interface to all library operations.
 *
 * <p>This service delegates to specialized services for different concerns:
 *
 * <ul>
 *   <li>{@link LoanService} - Borrowing, returning, reserving, and extending loans
 *   <li>{@link LibraryQueryService} - Searching and retrieving library data
 *   <li>{@link BookManagementService} - Book CRUD operations
 *   <li>{@link MemberManagementService} - Member CRUD operations
 * </ul>
 *
 * <p>This design follows the Single Responsibility Principle, making each service focused on a
 * specific domain area while maintaining a simple, unified API for consumers.
 */
public class LibraryService {
  private final LoanService loanService;
  private final LibraryQueryService queryService;
  private final BookManagementService bookManagement;
  private final MemberManagementService memberManagement;

  public LibraryService(
      LoanService loanService,
      LibraryQueryService queryService,
      BookManagementService bookManagement,
      MemberManagementService memberManagement) {
    this.loanService = loanService;
    this.queryService = queryService;
    this.bookManagement = bookManagement;
    this.memberManagement = memberManagement;
  }

  // ===== Loan Operations (delegated to LoanService) =====

  /**
   * Borrows a book for a member.
   *
   * @see LoanService#borrowBook(String, String)
   */
  public Result borrowBook(String bookId, String memberId) {
    return loanService.borrowBook(bookId, memberId);
  }

  /**
   * Returns a book and automatically loans it to the next eligible member in the reservation queue.
   *
   * @see LoanService#returnBook(String, String)
   */
  public ResultWithNext returnBook(String bookId, String memberId) {
    return loanService.returnBook(bookId, memberId);
  }

  /**
   * Reserves a book for a member, or immediately loans it if available.
   *
   * @see LoanService#reserveBook(String, String)
   */
  public Result reserveBook(String bookId, String memberId) {
    return loanService.reserveBook(bookId, memberId);
  }

  /**
   * Cancels a member's reservation for a book.
   *
   * @see LoanService#cancelReservation(String, String)
   */
  public Result cancelReservation(String bookId, String memberId) {
    return loanService.cancelReservation(bookId, memberId);
  }

  /**
   * Extends the due date of a loaned book.
   *
   * @see LoanService#extendLoan(String, String, int)
   */
  public Result extendLoan(String bookId, String memberId, int days) {
    return loanService.extendLoan(bookId, memberId, days);
  }

  /**
   * Checks if a member is eligible to borrow another book based on the borrow limit.
   *
   * @see LoanService#canMemberBorrow(String)
   */
  public boolean canMemberBorrow(String memberId) {
    return loanService.canMemberBorrow(memberId);
  }

  // ===== Query Operations (delegated to LibraryQueryService) =====

  /**
   * Searches for books based on various criteria.
   *
   * @see LibraryQueryService#searchBooks(String, Boolean, String)
   */
  public List<Book> searchBooks(String titleContains, Boolean availableOnly, String loanedTo) {
    return queryService.searchBooks(titleContains, availableOnly, loanedTo);
  }

  /**
   * Retrieves all books with due dates before the specified date.
   *
   * @see LibraryQueryService#overdueBooks(LocalDate)
   */
  public List<Book> overdueBooks(LocalDate today) {
    return queryService.overdueBooks(today);
  }

  /**
   * Retrieves a summary of a member's current loans and reservations.
   *
   * @see LibraryQueryService#memberSummary(String)
   */
  public MemberSummary memberSummary(String memberId) {
    return queryService.memberSummary(memberId);
  }

  /**
   * Finds a book by its ID.
   *
   * @see LibraryQueryService#findBook(String)
   */
  public Optional<Book> findBook(String id) {
    return queryService.findBook(id);
  }

  /**
   * Retrieves all books in the library.
   *
   * @see LibraryQueryService#allBooks()
   */
  public List<Book> allBooks() {
    return queryService.allBooks();
  }

  /**
   * Retrieves all members.
   *
   * @see LibraryQueryService#allMembers()
   */
  public List<Member> allMembers() {
    return queryService.allMembers();
  }

  // ===== Book Management (delegated to BookManagementService) =====

  /**
   * Creates a new book in the library.
   *
   * @see BookManagementService#createBook(String, String)
   */
  public Result createBook(String id, String title) {
    return bookManagement.createBook(id, title);
  }

  /**
   * Updates an existing book's title.
   *
   * @see BookManagementService#updateBook(String, String)
   */
  public Result updateBook(String id, String title) {
    return bookManagement.updateBook(id, title);
  }

  /**
   * Deletes a book from the library.
   *
   * @see BookManagementService#deleteBook(String)
   */
  public Result deleteBook(String id) {
    return bookManagement.deleteBook(id);
  }

  // ===== Member Management (delegated to MemberManagementService) =====

  /**
   * Creates a new member.
   *
   * @see MemberManagementService#createMember(String, String)
   */
  public Result createMember(String id, String name) {
    return memberManagement.createMember(id, name);
  }

  /**
   * Updates an existing member's name.
   *
   * @see MemberManagementService#updateMember(String, String)
   */
  public Result updateMember(String id, String name) {
    return memberManagement.updateMember(id, name);
  }

  /**
   * Deletes a member from the library system.
   *
   * @see MemberManagementService#deleteMember(String)
   */
  public Result deleteMember(String id) {
    return memberManagement.deleteMember(id);
  }
}
