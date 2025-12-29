package com.nortal.library.core.service;

import static com.nortal.library.core.ErrorCodes.*;

import com.nortal.library.core.MemberSummary;
import com.nortal.library.core.ReservationPosition;
import com.nortal.library.core.domain.Book;
import com.nortal.library.core.domain.Member;
import com.nortal.library.core.port.BookRepository;
import com.nortal.library.core.port.MemberRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service responsible for read-only query operations on library data.
 *
 * <p>Provides methods for searching, filtering, and retrieving library information without
 * modifying state.
 */
public class LibraryQueryService {
  private final BookRepository bookRepository;
  private final MemberRepository memberRepository;

  public LibraryQueryService(BookRepository bookRepository, MemberRepository memberRepository) {
    this.bookRepository = bookRepository;
    this.memberRepository = memberRepository;
  }

  /**
   * Searches for books based on various criteria.
   *
   * @param titleContains partial title match (case-insensitive), or null for no title filter
   * @param availableOnly true for only available books, false for only loaned books, null for all
   * @param loanedTo filter by borrower member ID, or null for no borrower filter
   * @return list of books matching all specified criteria
   */
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

  /**
   * Retrieves all books with due dates before the specified date.
   *
   * @param today the date to compare against (typically today's date)
   * @return list of overdue books
   */
  public List<Book> overdueBooks(LocalDate today) {
    // Optimized: O(1) database query instead of O(n) scan
    return bookRepository.findByDueDateBefore(today);
  }

  /**
   * Retrieves a summary of a member's current loans and reservations.
   *
   * @param memberId the ID of the member
   * @return MemberSummary containing loans and reservations with queue positions
   */
  public MemberSummary memberSummary(String memberId) {
    if (!memberRepository.existsById(memberId)) {
      return new MemberSummary(false, MEMBER_NOT_FOUND, List.of(), List.of());
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

  /**
   * Finds a book by its ID.
   *
   * @param id the book ID
   * @return Optional containing the book if found, empty otherwise
   */
  public Optional<Book> findBook(String id) {
    return bookRepository.findById(id);
  }

  /**
   * Retrieves all books in the library.
   *
   * @return list of all books
   */
  public List<Book> allBooks() {
    return bookRepository.findAll();
  }

  /**
   * Retrieves all members.
   *
   * @return list of all members
   */
  public List<Member> allMembers() {
    return memberRepository.findAll();
  }
}
