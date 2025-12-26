package com.nortal.library.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nortal.library.core.LibraryService.Result;
import com.nortal.library.core.LibraryService.ResultWithNext;
import com.nortal.library.core.domain.Book;
import com.nortal.library.core.domain.Member;
import com.nortal.library.core.port.BookRepository;
import com.nortal.library.core.port.MemberRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LibraryServiceTest {

  @Mock private BookRepository bookRepository;

  @Mock private MemberRepository memberRepository;

  @InjectMocks private LibraryService service;

  private Book testBook;
  private Member testMember;

  @BeforeEach
  void setUp() {
    testBook = new Book("b1", "Test Book");
    testBook.setReservationQueue(new ArrayList<>());
    testMember = new Member("m1", "Test Member");
  }

  // ==================== BORROW BOOK TESTS ====================

  @Test
  void borrowBook_Success() {
    // Given: Book available, member eligible
    when(bookRepository.findById("b1")).thenReturn(Optional.of(testBook));
    when(memberRepository.existsById("m1")).thenReturn(true);
    when(bookRepository.countByLoanedTo("m1")).thenReturn(0L); // Member has 0 books

    // When
    Result result = service.borrowBook("b1", "m1");

    // Then
    assertThat(result.ok()).isTrue();
    assertThat(result.reason()).isNull();
    assertThat(testBook.getLoanedTo()).isEqualTo("m1");
    assertThat(testBook.getDueDate()).isNotNull();
    assertThat(testBook.getDueDate()).isAfter(LocalDate.now());
    verify(bookRepository).save(testBook);
  }

  @Test
  void borrowBook_BookNotFound() {
    // Given: Book doesn't exist
    when(bookRepository.findById("b1")).thenReturn(Optional.empty());

    // When
    Result result = service.borrowBook("b1", "m1");

    // Then
    assertThat(result.ok()).isFalse();
    assertThat(result.reason()).isEqualTo("BOOK_NOT_FOUND");
    verify(bookRepository, never()).save(any());
  }

  @Test
  void borrowBook_MemberNotFound() {
    // Given: Member doesn't exist
    when(bookRepository.findById("b1")).thenReturn(Optional.of(testBook));
    when(memberRepository.existsById("m1")).thenReturn(false);

    // When
    Result result = service.borrowBook("b1", "m1");

    // Then
    assertThat(result.ok()).isFalse();
    assertThat(result.reason()).isEqualTo("MEMBER_NOT_FOUND");
    verify(bookRepository, never()).save(any());
  }

  @Test
  void borrowBook_AlreadyLoaned() {
    // Given: Book already loaned to another member
    testBook.setLoanedTo("m2");
    testBook.setDueDate(LocalDate.now().plusDays(7));
    when(bookRepository.findById("b1")).thenReturn(Optional.of(testBook));
    when(memberRepository.existsById("m1")).thenReturn(true);

    // When
    Result result = service.borrowBook("b1", "m1");

    // Then
    assertThat(result.ok()).isFalse();
    assertThat(result.reason()).isEqualTo("BOOK_UNAVAILABLE");
    verify(bookRepository, never()).save(any());
  }

  @Test
  void borrowBook_ExceedsBorrowLimit() {
    // Given: Member already has 5 books (at limit)
    when(bookRepository.findById("b1")).thenReturn(Optional.of(testBook));
    when(memberRepository.existsById("m1")).thenReturn(true);
    when(bookRepository.countByLoanedTo("m1")).thenReturn(5L); // At limit

    // When
    Result result = service.borrowBook("b1", "m1");

    // Then
    assertThat(result.ok()).isFalse();
    assertThat(result.reason()).isEqualTo("BORROW_LIMIT");
    verify(bookRepository, never()).save(any());
  }

  @Test
  void borrowBook_ReservationQueue_OnlyHeadCanBorrow() {
    // Given: Book has reservation queue, m2 is at head, m1 tries to borrow
    testBook.getReservationQueue().add("m2");
    testBook.getReservationQueue().add("m1");

    when(bookRepository.findById("b1")).thenReturn(Optional.of(testBook));
    when(memberRepository.existsById("m1")).thenReturn(true);

    // When
    Result result = service.borrowBook("b1", "m1");

    // Then
    assertThat(result.ok()).isFalse();
    assertThat(result.reason()).isEqualTo("RESERVED");
    verify(bookRepository, never()).save(any());
  }

  @Test
  void borrowBook_ReservationQueue_HeadCanBorrow() {
    // Given: Book has reservation queue, m1 is at head
    testBook.getReservationQueue().add("m1");
    testBook.getReservationQueue().add("m2");

    when(bookRepository.findById("b1")).thenReturn(Optional.of(testBook));
    when(memberRepository.existsById("m1")).thenReturn(true);
    when(bookRepository.countByLoanedTo("m1")).thenReturn(0L);

    // When
    Result result = service.borrowBook("b1", "m1");

    // Then
    assertThat(result.ok()).isTrue();
    assertThat(testBook.getLoanedTo()).isEqualTo("m1");
    assertThat(testBook.getReservationQueue()).doesNotContain("m1"); // Removed from queue
    assertThat(testBook.getReservationQueue()).containsExactly("m2");
    verify(bookRepository).save(testBook);
  }

  // ==================== RETURN BOOK TESTS ====================

  @Test
  void returnBook_Success_NoQueue() {
    // Given: Book loaned, no reservation queue
    testBook.setLoanedTo("m1");
    testBook.setDueDate(LocalDate.now().plusDays(7));

    when(bookRepository.findById("b1")).thenReturn(Optional.of(testBook));

    // When
    ResultWithNext result = service.returnBook("b1", "m1");

    // Then
    assertThat(result.ok()).isTrue();
    assertThat(result.nextMemberId()).isNull(); // No one in queue
    assertThat(testBook.getLoanedTo()).isNull();
    assertThat(testBook.getDueDate()).isNull();
    verify(bookRepository).save(testBook); // Only one save at the end
  }

  @Test
  void returnBook_WithAutomaticHandoff() {
    // Given: Book loaned to m1, m2 in queue and eligible
    testBook.setLoanedTo("m1");
    testBook.setDueDate(LocalDate.now().plusDays(7));
    testBook.getReservationQueue().add("m2");

    when(bookRepository.findById("b1")).thenReturn(Optional.of(testBook));
    when(memberRepository.existsById("m2")).thenReturn(true);
    when(bookRepository.countByLoanedTo("m2")).thenReturn(0L); // m2 eligible

    // When
    ResultWithNext result = service.returnBook("b1", "m1");

    // Then
    assertThat(result.ok()).isTrue();
    assertThat(result.nextMemberId()).isEqualTo("m2");
    assertThat(testBook.getLoanedTo()).isEqualTo("m2"); // Automatically loaned to m2
    assertThat(testBook.getDueDate()).isNotNull();
    assertThat(testBook.getReservationQueue()).isEmpty(); // m2 removed from queue
    verify(bookRepository).save(testBook); // Only one save at the end
  }

  @Test
  void returnBook_SkipsIneligibleMembers() {
    // Given: Book loaned, m2 in queue but at borrow limit, m3 in queue and eligible
    testBook.setLoanedTo("m1");
    testBook.setDueDate(LocalDate.now().plusDays(7));
    testBook.getReservationQueue().add("m2");
    testBook.getReservationQueue().add("m3");

    when(bookRepository.findById("b1")).thenReturn(Optional.of(testBook));
    when(memberRepository.existsById("m2")).thenReturn(true);
    when(memberRepository.existsById("m3")).thenReturn(true);
    when(bookRepository.countByLoanedTo("m2")).thenReturn(5L); // m2 at limit
    when(bookRepository.countByLoanedTo("m3")).thenReturn(0L); // m3 eligible

    // When
    ResultWithNext result = service.returnBook("b1", "m1");

    // Then
    assertThat(result.ok()).isTrue();
    assertThat(result.nextMemberId()).isEqualTo("m3"); // m2 skipped, m3 got the book
    assertThat(testBook.getLoanedTo()).isEqualTo("m3");
    assertThat(testBook.getReservationQueue()).isEmpty(); // Both removed
  }

  @Test
  void returnBook_BookNotFound() {
    // Given: Book doesn't exist
    when(bookRepository.findById("b1")).thenReturn(Optional.empty());

    // When
    ResultWithNext result = service.returnBook("b1", "m1");

    // Then
    assertThat(result.ok()).isFalse();
    verify(bookRepository, never()).save(any());
  }

  @Test
  void returnBook_NotLoaned() {
    // Given: Book not currently loaned
    when(bookRepository.findById("b1")).thenReturn(Optional.of(testBook));

    // When
    ResultWithNext result = service.returnBook("b1", "m1");

    // Then
    assertThat(result.ok()).isFalse();
    verify(bookRepository, never()).save(any());
  }

  @Test
  void returnBook_WithOptionalMemberId() {
    // Given: Book loaned, memberId is null (security fix: now requires memberId)
    testBook.setLoanedTo("m1");
    testBook.setDueDate(LocalDate.now().plusDays(7));

    when(bookRepository.findById("b1")).thenReturn(Optional.of(testBook));

    // When: Try to return without memberId
    ResultWithNext result = service.returnBook("b1", null);

    // Then: Should fail (authorization required)
    assertThat(result.ok()).isFalse();
    verify(bookRepository, never()).save(any());
  }

  // ==================== RESERVE BOOK TESTS ====================

  @Test
  void reserveBook_ImmediateLoanWhenAvailable() {
    // Given: Book available, member eligible
    when(bookRepository.findById("b1")).thenReturn(Optional.of(testBook));
    when(memberRepository.existsById("m1")).thenReturn(true);
    when(bookRepository.countByLoanedTo("m1")).thenReturn(0L); // Member eligible

    // When
    Result result = service.reserveBook("b1", "m1");

    // Then
    assertThat(result.ok()).isTrue();
    assertThat(testBook.getLoanedTo()).isEqualTo("m1"); // Immediately loaned
    assertThat(testBook.getDueDate()).isNotNull();
    assertThat(testBook.getReservationQueue()).isEmpty(); // Not queued
    verify(bookRepository).save(testBook);
  }

  @Test
  void reserveBook_AddsToQueueWhenLoaned() {
    // Given: Book already loaned to m2
    testBook.setLoanedTo("m2");
    testBook.setDueDate(LocalDate.now().plusDays(7));

    when(bookRepository.findById("b1")).thenReturn(Optional.of(testBook));
    when(memberRepository.existsById("m1")).thenReturn(true);

    // When
    Result result = service.reserveBook("b1", "m1");

    // Then
    assertThat(result.ok()).isTrue();
    assertThat(testBook.getReservationQueue()).containsExactly("m1");
    assertThat(testBook.getLoanedTo()).isEqualTo("m2"); // Still loaned to m2
    verify(bookRepository).save(testBook);
  }

  @Test
  void reserveBook_PreventsDuplicateReservation() {
    // Given: Member already in reservation queue
    testBook.setLoanedTo("m2");
    testBook.getReservationQueue().add("m1");

    when(bookRepository.findById("b1")).thenReturn(Optional.of(testBook));
    when(memberRepository.existsById("m1")).thenReturn(true);

    // When
    Result result = service.reserveBook("b1", "m1");

    // Then
    assertThat(result.ok()).isFalse();
    assertThat(result.reason()).isEqualTo("ALREADY_RESERVED");
    verify(bookRepository, never()).save(any());
  }

  @Test
  void reserveBook_PreventsIfAlreadyBorrowed() {
    // Given: Member already has this book loaned
    testBook.setLoanedTo("m1");

    when(bookRepository.findById("b1")).thenReturn(Optional.of(testBook));
    when(memberRepository.existsById("m1")).thenReturn(true);

    // When
    Result result = service.reserveBook("b1", "m1");

    // Then
    assertThat(result.ok()).isFalse();
    assertThat(result.reason()).isEqualTo("ALREADY_BORROWED");
    verify(bookRepository, never()).save(any());
  }

  // ==================== DELETE BOOK TESTS ====================

  @Test
  void deleteBook_Success() {
    // Given: Book exists, not loaned, no reservations
    when(bookRepository.findById("b1")).thenReturn(Optional.of(testBook));

    // When
    Result result = service.deleteBook("b1");

    // Then
    assertThat(result.ok()).isTrue();
    verify(bookRepository).delete(testBook);
  }

  @Test
  void deleteBook_FailsWhenLoaned() {
    // Given: Book loaned
    testBook.setLoanedTo("m1");
    when(bookRepository.findById("b1")).thenReturn(Optional.of(testBook));

    // When
    Result result = service.deleteBook("b1");

    // Then
    assertThat(result.ok()).isFalse();
    assertThat(result.reason()).isEqualTo("BOOK_LOANED");
    verify(bookRepository, never()).delete(any());
  }

  @Test
  void deleteBook_FailsWhenHasReservations() {
    // Given: Book has reservations
    testBook.getReservationQueue().add("m1");
    when(bookRepository.findById("b1")).thenReturn(Optional.of(testBook));

    // When
    Result result = service.deleteBook("b1");

    // Then
    assertThat(result.ok()).isFalse();
    assertThat(result.reason()).isEqualTo("BOOK_RESERVED");
    verify(bookRepository, never()).delete(any());
  }

  // ==================== DELETE MEMBER TESTS ====================

  @Test
  void deleteMember_Success() {
    // Given: Member exists, no active loans, not in any queues
    when(memberRepository.findById("m1")).thenReturn(Optional.of(testMember));
    when(bookRepository.existsByLoanedTo("m1")).thenReturn(false);
    when(bookRepository.findByReservationQueueContaining("m1")).thenReturn(List.of());

    // When
    Result result = service.deleteMember("m1");

    // Then
    assertThat(result.ok()).isTrue();
    verify(memberRepository).delete(testMember);
  }

  @Test
  void deleteMember_RemovesFromAllReservationQueues() {
    // Given: Member in multiple reservation queues
    Book book1 = new Book("b1", "Book 1");
    book1.setLoanedTo("m2");
    book1.setDueDate(LocalDate.now().plusDays(7));
    book1.setReservationQueue(new ArrayList<>(List.of("m1", "m3")));

    Book book2 = new Book("b2", "Book 2");
    book2.setReservationQueue(new ArrayList<>(List.of("m1")));

    when(memberRepository.findById("m1")).thenReturn(Optional.of(testMember));
    when(bookRepository.existsByLoanedTo("m1")).thenReturn(false);
    when(bookRepository.findByReservationQueueContaining("m1")).thenReturn(List.of(book1, book2));

    // When
    Result result = service.deleteMember("m1");

    // Then
    assertThat(result.ok()).isTrue();
    assertThat(book1.getReservationQueue()).containsExactly("m3");
    assertThat(book2.getReservationQueue()).isEmpty();
    verify(bookRepository, times(2)).save(any(Book.class));
    verify(memberRepository).delete(testMember);
  }

  @Test
  void deleteMember_FailsWhenHasActiveLoans() {
    // Given: Member has active loans
    when(memberRepository.findById("m1")).thenReturn(Optional.of(testMember));
    when(bookRepository.existsByLoanedTo("m1")).thenReturn(true);

    // When
    Result result = service.deleteMember("m1");

    // Then
    assertThat(result.ok()).isFalse();
    assertThat(result.reason()).isEqualTo("MEMBER_HAS_LOANS");
    verify(memberRepository, never()).delete(any());
  }

  // ==================== HELPER METHOD TESTS ====================

  @Test
  void canMemberBorrow_ReturnsTrueWhenUnderLimit() {
    // Given: Member has 4 books (under limit of 5)
    when(memberRepository.existsById("m1")).thenReturn(true);
    when(bookRepository.countByLoanedTo("m1")).thenReturn(4L);

    // When
    boolean canBorrow = service.canMemberBorrow("m1");

    // Then
    assertThat(canBorrow).isTrue();
  }

  @Test
  void canMemberBorrow_ReturnsFalseWhenAtLimit() {
    // Given: Member has 5 books (at limit)
    when(memberRepository.existsById("m1")).thenReturn(true);
    when(bookRepository.countByLoanedTo("m1")).thenReturn(5L);

    // When
    boolean canBorrow = service.canMemberBorrow("m1");

    // Then
    assertThat(canBorrow).isFalse();
  }

  @Test
  void canMemberBorrow_ReturnsFalseWhenMemberNotFound() {
    // Given: Member doesn't exist
    when(memberRepository.existsById("m1")).thenReturn(false);

    // When
    boolean canBorrow = service.canMemberBorrow("m1");

    // Then
    assertThat(canBorrow).isFalse();
  }

  // ==================== BUG FIX TESTS ====================

  // Bug #1: Correct error messages for already-loaned books
  @Test
  void borrowBook_ReturnsALREADY_BORROWEDWhenMemberTriesToBorrowTheirOwnBook() {
    // Given: Book is already loaned to m1
    testBook.setLoanedTo("m1");
    testBook.setDueDate(LocalDate.now().plusDays(14));
    when(bookRepository.findById("b1")).thenReturn(Optional.of(testBook));
    when(memberRepository.existsById("m1")).thenReturn(true);

    // When: Same member tries to borrow again
    Result result = service.borrowBook("b1", "m1");

    // Then: Should return ALREADY_BORROWED
    assertThat(result.ok()).isFalse();
    assertThat(result.reason()).isEqualTo("ALREADY_BORROWED");
    verify(bookRepository, never()).save(any(Book.class));
  }

  @Test
  void borrowBook_ReturnsBOOK_UNAVAILABLEWhenBookLoanedToOtherMember() {
    // Given: Book is loaned to m1
    testBook.setLoanedTo("m1");
    testBook.setDueDate(LocalDate.now().plusDays(14));
    when(bookRepository.findById("b1")).thenReturn(Optional.of(testBook));
    when(memberRepository.existsById("m2")).thenReturn(true);
    when(bookRepository.countByLoanedTo("m2")).thenReturn(0L);

    // When: Different member m2 tries to borrow
    Result result = service.borrowBook("b1", "m2");

    // Then: Should return BOOK_UNAVAILABLE
    assertThat(result.ok()).isFalse();
    assertThat(result.reason()).isEqualTo("BOOK_UNAVAILABLE");
    verify(bookRepository, never()).save(any(Book.class));
  }

  // Bug #2: Return validation bypass
  @Test
  void returnBook_FailsWhenMemberIdIsNull() {
    // Given: Book is loaned to m1
    testBook.setLoanedTo("m1");
    testBook.setDueDate(LocalDate.now().plusDays(14));
    when(bookRepository.findById("b1")).thenReturn(Optional.of(testBook));

    // When: Attempt to return without memberId
    ResultWithNext result = service.returnBook("b1", null);

    // Then: Should fail (authorization check)
    assertThat(result.ok()).isFalse();
    verify(bookRepository, never()).save(any(Book.class));
  }

  @Test
  void returnBook_FailsWhenWrongMemberTriesToReturn() {
    // Given: Book is loaned to m1
    testBook.setLoanedTo("m1");
    testBook.setDueDate(LocalDate.now().plusDays(14));
    when(bookRepository.findById("b1")).thenReturn(Optional.of(testBook));

    // When: Different member m2 tries to return
    ResultWithNext result = service.returnBook("b1", "m2");

    // Then: Should fail (not the borrower)
    assertThat(result.ok()).isFalse();
    verify(bookRepository, never()).save(any(Book.class));
  }

  @Test
  void returnBook_SucceedsOnlyWhenCurrentBorrowerReturns() {
    // Given: Book is loaned to m1
    testBook.setLoanedTo("m1");
    testBook.setDueDate(LocalDate.now().plusDays(14));
    when(bookRepository.findById("b1")).thenReturn(Optional.of(testBook));

    // When: Current borrower m1 returns
    ResultWithNext result = service.returnBook("b1", "m1");

    // Then: Should succeed
    assertThat(result.ok()).isTrue();
    assertThat(testBook.getLoanedTo()).isNull();
    assertThat(testBook.getDueDate()).isNull();
    verify(bookRepository).save(testBook);
  }

  // Bug #3: ExtendLoan authorization bypass
  @Test
  void extendLoan_FailsWhenWrongMemberTriesToExtend() {
    // Given: Book is loaned to m1
    testBook.setLoanedTo("m1");
    testBook.setDueDate(LocalDate.now().plusDays(14));
    when(bookRepository.findById("b1")).thenReturn(Optional.of(testBook));
    when(memberRepository.existsById("m2")).thenReturn(true);

    // When: Different member m2 tries to extend
    Result result = service.extendLoan("b1", "m2", 7);

    // Then: Should fail (not the borrower)
    assertThat(result.ok()).isFalse();
    assertThat(result.reason()).isEqualTo("NOT_BORROWER");
    verify(bookRepository, never()).save(any(Book.class));
  }

  @Test
  void extendLoan_SucceedsWhenCurrentBorrowerExtends() {
    // Given: Book is loaned to m1 with due date 14 days from now
    LocalDate originalDueDate = LocalDate.now().plusDays(14);
    testBook.setLoanedTo("m1");
    testBook.setDueDate(originalDueDate);
    when(bookRepository.findById("b1")).thenReturn(Optional.of(testBook));
    when(memberRepository.existsById("m1")).thenReturn(true);

    // When: Current borrower m1 extends by 7 days
    Result result = service.extendLoan("b1", "m1", 7);

    // Then: Should succeed and update due date
    assertThat(result.ok()).isTrue();
    assertThat(testBook.getDueDate()).isEqualTo(originalDueDate.plusDays(7));
    verify(bookRepository).save(testBook);
  }

  @Test
  void extendLoan_FailsWhenMemberNotFound() {
    // Given: Book is loaned
    testBook.setLoanedTo("m1");
    testBook.setDueDate(LocalDate.now().plusDays(14));
    when(bookRepository.findById("b1")).thenReturn(Optional.of(testBook));
    when(memberRepository.existsById("m999")).thenReturn(false);

    // When: Non-existent member tries to extend
    Result result = service.extendLoan("b1", "m999", 7);

    // Then: Should fail
    assertThat(result.ok()).isFalse();
    assertThat(result.reason()).isEqualTo("MEMBER_NOT_FOUND");
    verify(bookRepository, never()).save(any(Book.class));
  }

  @Test
  void extendLoan_FailsWhenBookHasReservations() {
    // Given: Book is loaned to m1 but has reservations (others are waiting)
    testBook.setLoanedTo("m1");
    testBook.setDueDate(LocalDate.now().plusDays(14));
    testBook.getReservationQueue().add("m2"); // m2 is waiting
    when(bookRepository.findById("b1")).thenReturn(Optional.of(testBook));
    when(memberRepository.existsById("m1")).thenReturn(true);

    // When: Current borrower tries to extend
    Result result = service.extendLoan("b1", "m1", 7);

    // Then: Should fail (cannot extend when others are waiting)
    assertThat(result.ok()).isFalse();
    assertThat(result.reason()).isEqualTo("RESERVATION_EXISTS");
    verify(bookRepository, never()).save(any(Book.class));
  }

  @Test
  void createBook_FailsWhenIdAlreadyExists() {
    // Given: Book with ID "b1" already exists
    when(bookRepository.existsById("b1")).thenReturn(true);

    // When: Attempt to create another book with same ID
    Result result = service.createBook("b1", "Another Title");

    // Then: Should fail with BOOK_ALREADY_EXISTS
    assertThat(result.ok()).isFalse();
    assertThat(result.reason()).isEqualTo("BOOK_ALREADY_EXISTS");
    verify(bookRepository, never()).save(any(Book.class));
  }

  @Test
  void createMember_FailsWhenIdAlreadyExists() {
    // Given: Member with ID "m1" already exists
    when(memberRepository.existsById("m1")).thenReturn(true);

    // When: Attempt to create another member with same ID
    Result result = service.createMember("m1", "Another Name");

    // Then: Should fail with MEMBER_ALREADY_EXISTS
    assertThat(result.ok()).isFalse();
    assertThat(result.reason()).isEqualTo("MEMBER_ALREADY_EXISTS");
    verify(memberRepository, never()).save(any(Member.class));
  }
}
