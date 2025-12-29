package com.nortal.library.core.service;

import static com.nortal.library.core.ErrorCodes.*;

import com.nortal.library.core.Result;
import com.nortal.library.core.domain.Book;
import com.nortal.library.core.port.BookRepository;
import java.util.Optional;

/**
 * Service responsible for book CRUD operations and lifecycle management.
 *
 * <p>Handles creation, updating, and deletion of books with appropriate validation and data
 * integrity checks.
 */
public class BookManagementService {
  private final BookRepository bookRepository;

  public BookManagementService(BookRepository bookRepository) {
    this.bookRepository = bookRepository;
  }

  /**
   * Creates a new book in the library.
   *
   * @param id the unique ID for the book
   * @param title the book title
   * @return Result with success or failure reason (INVALID_REQUEST, BOOK_ALREADY_EXISTS)
   */
  public Result createBook(String id, String title) {
    if (id == null || title == null) {
      return Result.failure(INVALID_REQUEST);
    }
    // Check if book with this ID already exists
    if (bookRepository.existsById(id)) {
      return Result.failure(BOOK_ALREADY_EXISTS);
    }
    bookRepository.save(new Book(id, title));
    return Result.success();
  }

  /**
   * Updates an existing book's title.
   *
   * @param id the ID of the book to update
   * @param title the new title
   * @return Result with success or failure reason (BOOK_NOT_FOUND, INVALID_REQUEST)
   */
  public Result updateBook(String id, String title) {
    Optional<Book> existing = bookRepository.findById(id);
    if (existing.isEmpty()) {
      return Result.failure(BOOK_NOT_FOUND);
    }
    if (title == null) {
      return Result.failure(INVALID_REQUEST);
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
      return Result.failure(BOOK_NOT_FOUND);
    }
    Book book = existing.get();

    // Prevent deletion if book is currently loaned
    if (book.getLoanedTo() != null) {
      return Result.failure(BOOK_LOANED);
    }

    // Prevent deletion if book has reservations
    if (!book.getReservationQueue().isEmpty()) {
      return Result.failure(BOOK_RESERVED);
    }

    bookRepository.delete(book);
    return Result.success();
  }
}
