package com.nortal.library.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Domain entity representing a library member.
 *
 * <p>Members can borrow books and make reservations. The system enforces a maximum of 5
 * simultaneous loans per member (defined by {@code LibraryService.MAX_LOANS}).
 *
 * <p><b>Design Note:</b> Member's current loans and reservations are not stored directly on this
 * entity. Instead, they are derived by querying the Book repository for books where {@code
 * loanedTo} matches the member's ID or where the member appears in {@code reservationQueue}.
 *
 * <p>This design keeps the Member entity simple and ensures consistency, as loan information is
 * maintained in a single place (the Book entity).
 */
@Entity
@Table(name = "members")
@Getter
@Setter
@NoArgsConstructor
public class Member {

  /** Unique identifier for the member. */
  @Id private String id;

  /** Full name of the member. Cannot be null. */
  @Column(nullable = false)
  private String name;

  /**
   * Constructs a new Member with the specified ID and name.
   *
   * @param id unique identifier for the member
   * @param name full name of the member
   */
  public Member(String id, String name) {
    this.id = id;
    this.name = name;
  }
}
