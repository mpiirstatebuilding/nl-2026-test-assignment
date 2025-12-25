package com.nortal.library.core.port;

import com.nortal.library.core.domain.Book;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BookRepository {
  Optional<Book> findById(String id);

  List<Book> findAll();

  Book save(Book book);

  void delete(Book book);

  boolean existsById(String id);

  // Optimized query methods for performance improvements

  /** Counts the number of books currently loaned to a specific member. */
  long countByLoanedTo(String memberId);

  /** Finds all books currently loaned to a specific member. */
  List<Book> findByLoanedTo(String memberId);

  /** Finds all books where the member is in the reservation queue. */
  List<Book> findByReservationQueueContaining(String memberId);

  /** Finds all books with due dates before the specified date (overdue books). */
  List<Book> findByDueDateBefore(LocalDate date);

  /** Checks if any books are currently loaned to the specified member. */
  boolean existsByLoanedTo(String memberId);

  /** Finds all books with titles containing the specified text (case-insensitive). */
  List<Book> findByTitleContainingIgnoreCase(String title);

  /** Finds all available books (not currently loaned to anyone). */
  List<Book> findByLoanedToIsNull();
}
