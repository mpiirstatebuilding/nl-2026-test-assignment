package com.nortal.library.core.service;

import static com.nortal.library.core.ErrorCodes.*;

import com.nortal.library.core.Result;
import com.nortal.library.core.domain.Book;
import com.nortal.library.core.domain.Member;
import com.nortal.library.core.port.BookRepository;
import com.nortal.library.core.port.MemberRepository;
import java.util.List;
import java.util.Optional;

/**
 * Service responsible for member CRUD operations and lifecycle management.
 *
 * <p>Handles creation, updating, and deletion of members with appropriate validation and data
 * integrity checks.
 */
public class MemberManagementService {
  private final BookRepository bookRepository;
  private final MemberRepository memberRepository;

  public MemberManagementService(BookRepository bookRepository, MemberRepository memberRepository) {
    this.bookRepository = bookRepository;
    this.memberRepository = memberRepository;
  }

  /**
   * Creates a new member.
   *
   * @param id the unique ID for the member
   * @param name the member's name
   * @return Result with success or failure reason (INVALID_REQUEST, MEMBER_ALREADY_EXISTS)
   */
  public Result createMember(String id, String name) {
    if (id == null || name == null) {
      return Result.failure(INVALID_REQUEST);
    }
    // Check if member with this ID already exists
    if (memberRepository.existsById(id)) {
      return Result.failure(MEMBER_ALREADY_EXISTS);
    }
    memberRepository.save(new Member(id, name));
    return Result.success();
  }

  /**
   * Updates an existing member's name.
   *
   * @param id the ID of the member to update
   * @param name the new name
   * @return Result with success or failure reason (MEMBER_NOT_FOUND, INVALID_REQUEST)
   */
  public Result updateMember(String id, String name) {
    Optional<Member> existing = memberRepository.findById(id);
    if (existing.isEmpty()) {
      return Result.failure(MEMBER_NOT_FOUND);
    }
    if (name == null) {
      return Result.failure(INVALID_REQUEST);
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
      return Result.failure(MEMBER_NOT_FOUND);
    }

    // Check if member has active loans - must return books first
    // Optimized: O(1) query instead of O(n) scan
    if (bookRepository.existsByLoanedTo(id)) {
      return Result.failure(MEMBER_HAS_LOANS);
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
}
