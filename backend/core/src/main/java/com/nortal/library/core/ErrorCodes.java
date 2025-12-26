package com.nortal.library.core;

/**
 * Constants for error reason codes returned by LibraryService operations.
 *
 * <p>These codes are used in {@link LibraryService.Result} objects to indicate why an operation
 * failed. Using constants instead of string literals ensures type safety, prevents typos, and makes
 * the codebase easier to maintain.
 *
 * <p><b>Usage:</b> When a service method returns {@code Result.failure()}, it should use one of
 * these constants: {@code Result.failure(ErrorCodes.BOOK_NOT_FOUND)}
 *
 * @see LibraryService.Result
 */
public final class ErrorCodes {

  // Entity not found errors
  /** The requested book does not exist in the system. */
  public static final String BOOK_NOT_FOUND = "BOOK_NOT_FOUND";

  /** The requested member does not exist in the system. */
  public static final String MEMBER_NOT_FOUND = "MEMBER_NOT_FOUND";

  // Borrow operation errors
  /** Member has reached the maximum loan limit (5 books). */
  public static final String BORROW_LIMIT = "BORROW_LIMIT";

  /** Member is trying to borrow a book they already have on loan. */
  public static final String ALREADY_BORROWED = "ALREADY_BORROWED";

  /** Book is currently loaned to another member. */
  public static final String BOOK_UNAVAILABLE = "BOOK_UNAVAILABLE";

  /** Book has a reservation queue and the requesting member is not at the head. */
  public static final String RESERVED = "RESERVED";

  // Reservation operation errors
  /** Member has already reserved this book. */
  public static final String ALREADY_RESERVED = "ALREADY_RESERVED";

  /** Member is not in the reservation queue for this book. */
  public static final String NOT_RESERVED = "NOT_RESERVED";

  // Loan extension errors
  /** Extension days parameter is invalid (e.g., zero days). */
  public static final String INVALID_EXTENSION = "INVALID_EXTENSION";

  /** Book is not currently on loan to anyone. */
  public static final String NOT_LOANED = "NOT_LOANED";

  /** Member trying to extend is not the current borrower. */
  public static final String NOT_BORROWER = "NOT_BORROWER";

  /** Cannot extend loan because book has members in reservation queue. */
  public static final String RESERVATION_EXISTS = "RESERVATION_EXISTS";

  // Delete operation errors
  /** Cannot delete book because it is currently on loan. */
  public static final String BOOK_LOANED = "BOOK_LOANED";

  /** Cannot delete book because it has members in reservation queue. */
  public static final String BOOK_RESERVED = "BOOK_RESERVED";

  /** Cannot delete member because they have active loans. */
  public static final String MEMBER_HAS_LOANS = "MEMBER_HAS_LOANS";

  // General validation errors
  /** Request contains invalid or missing required fields. */
  public static final String INVALID_REQUEST = "INVALID_REQUEST";

  // Creation conflict errors
  /** Cannot create book because ID already exists. */
  public static final String BOOK_ALREADY_EXISTS = "BOOK_ALREADY_EXISTS";

  /** Cannot create member because ID already exists. */
  public static final String MEMBER_ALREADY_EXISTS = "MEMBER_ALREADY_EXISTS";

  /** Private constructor to prevent instantiation of utility class. */
  private ErrorCodes() {
    throw new UnsupportedOperationException("Utility class - do not instantiate");
  }
}
