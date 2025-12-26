package com.nortal.library.core.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Domain entity representing a book in the library system.
 *
 * <p>Books can be in one of two states:
 *
 * <ul>
 *   <li><b>Available:</b> loanedTo is null, dueDate is null, reservationQueue may contain members
 *   <li><b>Loaned:</b> loanedTo contains member ID, dueDate contains return date, reservationQueue
 *       may contain members
 * </ul>
 *
 * <p>The reservation queue is maintained as a FIFO (First-In-First-Out) ordered list. When a book
 * is returned, the system automatically attempts to loan it to the first eligible member in the
 * queue.
 *
 * <p><b>Design Note:</b> Loan information is embedded directly in the Book entity rather than
 * maintained as a separate Loan entity. This simplifies the domain model but means that loan
 * history is not preserved.
 */
@Entity
@Table(name = "books")
@Getter
@Setter
@NoArgsConstructor
public class Book {

  /** Unique identifier for the book. */
  @Id private String id;

  /** Title of the book. Cannot be null. */
  @Column(nullable = false)
  private String title;

  /**
   * ID of the member who currently has this book on loan.
   *
   * <p>When null, the book is available for borrowing or reservation. When non-null, the book is
   * currently on loan to the specified member.
   */
  @Column(name = "loaned_to")
  private String loanedTo;

  /**
   * Date when the current loan is due to be returned.
   *
   * <p>When null, the book is not currently on loan. When non-null, represents the date by which
   * the current borrower should return the book. Books with due dates in the past are considered
   * overdue.
   */
  @Column(name = "due_date")
  private LocalDate dueDate;

  /**
   * FIFO queue of member IDs waiting to borrow this book.
   *
   * <p>Members are added to the end of the queue when they reserve the book. The member at position
   * 0 has priority to borrow the book when it becomes available. The queue is automatically
   * processed when a book is returned, loaning it to the first eligible member.
   *
   * <p>Position in the queue is maintained via {@code @OrderColumn} to preserve FIFO ordering
   * across database operations.
   */
  @ElementCollection
  @CollectionTable(name = "book_reservations", joinColumns = @JoinColumn(name = "book_id"))
  @OrderColumn(name = "position")
  @Column(name = "member_id")
  private List<String> reservationQueue = new ArrayList<>();

  /**
   * Constructs a new Book with the specified ID and title.
   *
   * <p>The book is created in an available state (not loaned, no due date, empty reservation
   * queue).
   *
   * @param id unique identifier for the book
   * @param title title of the book
   */
  public Book(String id, String title) {
    this.id = id;
    this.title = title;
  }
}
