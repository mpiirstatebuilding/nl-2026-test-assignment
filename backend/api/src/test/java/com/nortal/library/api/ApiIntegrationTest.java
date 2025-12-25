package com.nortal.library.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.nortal.library.api.dto.BookResponse;
import com.nortal.library.api.dto.BooksResponse;
import com.nortal.library.api.dto.BorrowRequest;
import com.nortal.library.api.dto.CancelReservationRequest;
import com.nortal.library.api.dto.CreateBookRequest;
import com.nortal.library.api.dto.CreateMemberRequest;
import com.nortal.library.api.dto.DeleteBookRequest;
import com.nortal.library.api.dto.DeleteMemberRequest;
import com.nortal.library.api.dto.LoanExtensionRequest;
import com.nortal.library.api.dto.MemberResponse;
import com.nortal.library.api.dto.MemberSummaryResponse;
import com.nortal.library.api.dto.MembersResponse;
import com.nortal.library.api.dto.ReserveRequest;
import com.nortal.library.api.dto.ResultResponse;
import com.nortal.library.api.dto.ResultWithNextResponse;
import com.nortal.library.api.dto.ReturnRequest;
import com.nortal.library.api.dto.UpdateBookRequest;
import com.nortal.library.api.dto.UpdateMemberRequest;
import java.time.LocalDate;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
class ApiIntegrationTest {

  @LocalServerPort int port;

  private final TestRestTemplate rest = new TestRestTemplate();

  @Test
  void listsSeedBooksAndMembers() {
    BooksResponse books = rest.getForObject(url("/api/books"), BooksResponse.class);
    MembersResponse members = rest.getForObject(url("/api/members"), MembersResponse.class);

    assertThat(books).isNotNull();
    assertThat(books.items()).hasSizeGreaterThanOrEqualTo(6);
    assertThat(books.items()).allSatisfy(b -> assertThat(b.id()).isNotBlank());

    assertThat(members).isNotNull();
    assertThat(members.items()).hasSizeGreaterThanOrEqualTo(4);
    assertThat(members.items()).allSatisfy(m -> assertThat(m.id()).isNotBlank());
  }

  @Test
  void bookCrudRoundtrip() {
    ResultResponse created =
        rest.postForObject(
            url("/api/books"), new CreateBookRequest("vb1", "Visible Book"), ResultResponse.class);
    assertThat(created.ok()).isTrue();

    ResultResponse updated =
        rest.exchange(
                url("/api/books"),
                HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(new UpdateBookRequest("vb1", "Renamed")),
                ResultResponse.class)
            .getBody();
    assertThat(updated.ok()).isTrue();

    BookResponse book =
        rest.getForObject(url("/api/books"), BooksResponse.class).items().stream()
            .filter(b -> b.id().equals("vb1"))
            .findFirst()
            .orElse(null);
    assertThat(book).isNotNull();
    assertThat(book.title()).isEqualTo("Renamed");

    ResultResponse deleted =
        rest.exchange(
                url("/api/books"),
                HttpMethod.DELETE,
                new org.springframework.http.HttpEntity<>(new DeleteBookRequest("vb1")),
                ResultResponse.class)
            .getBody();
    assertThat(deleted.ok()).isTrue();

    BooksResponse afterDelete = rest.getForObject(url("/api/books"), BooksResponse.class);
    assertThat(afterDelete.items().stream().noneMatch(b -> Objects.equals(b.id(), "vb1"))).isTrue();
  }

  @Test
  void memberCrudRoundtrip() {
    ResultResponse created =
        rest.postForObject(
            url("/api/members"),
            new CreateMemberRequest("vm1", "Visible Member"),
            ResultResponse.class);
    assertThat(created.ok()).isTrue();

    ResultResponse updated =
        rest.exchange(
                url("/api/members"),
                HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(
                    new UpdateMemberRequest("vm1", "Renamed")),
                ResultResponse.class)
            .getBody();
    assertThat(updated.ok()).isTrue();

    MemberResponse member =
        rest.getForObject(url("/api/members"), MembersResponse.class).items().stream()
            .filter(m -> m.id().equals("vm1"))
            .findFirst()
            .orElse(null);
    assertThat(member).isNotNull();
    assertThat(member.name()).isEqualTo("Renamed");

    ResultResponse deleted =
        rest.exchange(
                url("/api/members"),
                HttpMethod.DELETE,
                new org.springframework.http.HttpEntity<>(new DeleteMemberRequest("vm1")),
                ResultResponse.class)
            .getBody();
    assertThat(deleted.ok()).isTrue();

    MembersResponse afterDelete = rest.getForObject(url("/api/members"), MembersResponse.class);
    assertThat(afterDelete.items().stream().noneMatch(m -> Objects.equals(m.id(), "vm1"))).isTrue();
  }

  @Test
  void borrowAndReturnHappyPath() {
    ResultResponse borrow =
        rest.postForObject(url("/api/borrow"), new BorrowRequest("b1", "m1"), ResultResponse.class);
    assertThat(borrow.ok()).isTrue();

    ResultWithNextResponse returned =
        rest.postForObject(
            url("/api/return"), new ReturnRequest("b1", "m1"), ResultWithNextResponse.class);
    assertThat(returned.ok()).isTrue();
    assertThat(returned.nextMemberId()).isNull();

    BookResponse book =
        rest.getForObject(url("/api/books"), BooksResponse.class).items().stream()
            .filter(b -> b.id().equals("b1"))
            .findFirst()
            .orElseThrow();
    assertThat(book.loanedTo()).isNull();
  }

  @Test
  void returnWithoutMemberIdFailsInBusinessLogic() {
    // Borrow a book first
    ResultResponse borrow =
        rest.postForObject(url("/api/borrow"), new BorrowRequest("b1", "m1"), ResultResponse.class);
    assertThat(borrow.ok()).isTrue();

    // Return WITHOUT memberId (API accepts it per README spec, but business logic rejects it)
    ResultWithNextResponse returned =
        rest.postForObject(
            url("/api/return"), new ReturnRequest("b1", null), ResultWithNextResponse.class);
    assertThat(returned.ok()).isFalse(); // Business logic rejects null memberId

    // Verify book is still loaned to m1 (return was rejected)
    BookResponse book =
        rest.getForObject(url("/api/books"), BooksResponse.class).items().stream()
            .filter(b -> b.id().equals("b1"))
            .findFirst()
            .orElseThrow();
    assertThat(book.loanedTo()).isEqualTo("m1");
  }

  @Test
  void returnWithWrongMemberIdFails() {
    // m1 borrows a book
    rest.postForObject(url("/api/borrow"), new BorrowRequest("b1", "m1"), ResultResponse.class);

    // m2 tries to return it (should fail)
    ResultWithNextResponse returned =
        rest.postForObject(
            url("/api/return"), new ReturnRequest("b1", "m2"), ResultWithNextResponse.class);
    assertThat(returned.ok()).isFalse();

    // Verify book is still loaned to m1
    BookResponse book =
        rest.getForObject(url("/api/books"), BooksResponse.class).items().stream()
            .filter(b -> b.id().equals("b1"))
            .findFirst()
            .orElseThrow();
    assertThat(book.loanedTo()).isEqualTo("m1");
  }

  @Test
  void reserveAndCancelReservation() {
    rest.postForObject(url("/api/borrow"), new BorrowRequest("b2", "m1"), ResultResponse.class);
    ResultResponse reserved =
        rest.postForObject(
            url("/api/reserve"), new ReserveRequest("b2", "m2"), ResultResponse.class);
    assertThat(reserved.ok()).isTrue();

    ResultResponse canceled =
        rest.postForObject(
            url("/api/cancel-reservation"),
            new CancelReservationRequest("b2", "m2"),
            ResultResponse.class);
    assertThat(canceled.ok()).isTrue();
  }

  @Test
  void extendLoanUpdatesDueDate() {
    ResultResponse borrow =
        rest.postForObject(url("/api/borrow"), new BorrowRequest("b3", "m1"), ResultResponse.class);
    assertThat(borrow.ok()).isTrue();

    BooksResponse afterBorrow = rest.getForObject(url("/api/books"), BooksResponse.class);
    LocalDate dueDate =
        afterBorrow.items().stream()
            .filter(b -> b.id().equals("b3"))
            .map(BookResponse::dueDate)
            .findFirst()
            .orElse(null);
    assertThat(dueDate).isNotNull();

    ResultResponse extended =
        rest.postForObject(
            url("/api/extend"), new LoanExtensionRequest("b3", "m1", 3), ResultResponse.class);
    assertThat(extended.ok()).isTrue();

    BooksResponse afterExtend = rest.getForObject(url("/api/books"), BooksResponse.class);
    LocalDate extendedDate =
        afterExtend.items().stream()
            .filter(b -> b.id().equals("b3"))
            .map(BookResponse::dueDate)
            .findFirst()
            .orElse(null);
    assertThat(extendedDate).isAfter(dueDate);
  }

  @Test
  void searchReturnsFilteredResults() {
    rest.postForObject(
        url("/api/books"),
        new CreateBookRequest("vb-search", "Algorithms 101"),
        ResultResponse.class);

    BooksResponse all =
        rest.getForObject(url("/api/books/search?titleContains=Algo"), BooksResponse.class);
    assertThat(all.items().stream().anyMatch(b -> b.id().equals("vb-search"))).isTrue();

    rest.postForObject(
        url("/api/borrow"), new BorrowRequest("vb-search", "m1"), ResultResponse.class);
    BooksResponse availableOnly =
        rest.getForObject(url("/api/books/search?available=true"), BooksResponse.class);
    assertThat(availableOnly.items().stream().noneMatch(b -> b.id().equals("vb-search"))).isTrue();
  }

  @Test
  void memberSummaryShowsLoansAndReservations() {
    rest.postForObject(url("/api/borrow"), new BorrowRequest("b4", "m2"), ResultResponse.class);
    rest.postForObject(url("/api/borrow"), new BorrowRequest("b5", "m1"), ResultResponse.class);
    rest.postForObject(url("/api/reserve"), new ReserveRequest("b5", "m2"), ResultResponse.class);

    MemberSummaryResponse summary =
        rest.getForObject(url("/api/members/m2/summary"), MemberSummaryResponse.class);

    assertThat(summary.ok()).isTrue();
    assertThat(summary.loans().stream().anyMatch(l -> l.bookId().equals("b4"))).isTrue();
    assertThat(
            summary.reservations().stream()
                .anyMatch(r -> r.bookId().equals("b5") && r.position() == 0))
        .isTrue();
  }

  @Test
  void overdueEndpointListsPastDue() {
    rest.postForObject(url("/api/borrow"), new BorrowRequest("b6", "m1"), ResultResponse.class);
    rest.postForObject(
        url("/api/extend"), new LoanExtensionRequest("b6", "m1", -30), ResultResponse.class);

    BooksResponse overdue = rest.getForObject(url("/api/overdue"), BooksResponse.class);
    assertThat(overdue.items().stream().anyMatch(b -> b.id().equals("b6"))).isTrue();
  }

  @Test
  void healthEndpointRespondsOk() {
    ResponseEntity<String> response = rest.getForEntity(url("/api/health"), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("ok");
  }

  @Test
  void borrowLimitEnforcementPreventsExcessiveLoans() {
    // Member m1 borrows 5 books (the maximum allowed)
    rest.postForObject(url("/api/borrow"), new BorrowRequest("b1", "m1"), ResultResponse.class);
    rest.postForObject(url("/api/borrow"), new BorrowRequest("b2", "m1"), ResultResponse.class);
    rest.postForObject(url("/api/borrow"), new BorrowRequest("b3", "m1"), ResultResponse.class);
    rest.postForObject(url("/api/borrow"), new BorrowRequest("b4", "m1"), ResultResponse.class);
    ResultResponse fifthBorrow =
        rest.postForObject(url("/api/borrow"), new BorrowRequest("b5", "m1"), ResultResponse.class);
    assertThat(fifthBorrow.ok()).isTrue();

    // Attempt to borrow a 6th book should fail
    ResultResponse sixthBorrow =
        rest.postForObject(url("/api/borrow"), new BorrowRequest("b6", "m1"), ResultResponse.class);
    assertThat(sixthBorrow.ok()).isFalse();
    assertThat(sixthBorrow.reason()).isEqualTo("BORROW_LIMIT");
  }

  @Test
  void doubleBorrowPrevented() {
    // m1 borrows a book
    ResultResponse firstBorrow =
        rest.postForObject(url("/api/borrow"), new BorrowRequest("b1", "m1"), ResultResponse.class);
    assertThat(firstBorrow.ok()).isTrue();

    // m2 tries to borrow the same book (should fail)
    ResultResponse secondBorrow =
        rest.postForObject(url("/api/borrow"), new BorrowRequest("b1", "m2"), ResultResponse.class);
    assertThat(secondBorrow.ok()).isFalse();
    assertThat(secondBorrow.reason()).isEqualTo("BOOK_UNAVAILABLE");
  }

  @Test
  void reservationQueueEnforcesOrder() {
    // m1 borrows a book
    rest.postForObject(url("/api/borrow"), new BorrowRequest("b1", "m1"), ResultResponse.class);

    // m2 and m3 reserve the book (creating a queue)
    ResultResponse reserve2 =
        rest.postForObject(
            url("/api/reserve"), new ReserveRequest("b1", "m2"), ResultResponse.class);
    ResultResponse reserve3 =
        rest.postForObject(
            url("/api/reserve"), new ReserveRequest("b1", "m3"), ResultResponse.class);
    assertThat(reserve2.ok()).isTrue();
    assertThat(reserve3.ok()).isTrue();

    // m1 returns the book (should auto-loan to m2)
    rest.postForObject(
        url("/api/return"), new ReturnRequest("b1", "m1"), ResultWithNextResponse.class);

    // m3 tries to borrow directly (should fail because book is now loaned to m2)
    ResultResponse directBorrow =
        rest.postForObject(url("/api/borrow"), new BorrowRequest("b1", "m3"), ResultResponse.class);
    assertThat(directBorrow.ok()).isFalse();
    assertThat(directBorrow.reason()).isEqualTo("BOOK_UNAVAILABLE");
  }

  @Test
  void returnAutomaticallyHandsOffToNextInQueue() {
    // m1 borrows a book
    rest.postForObject(url("/api/borrow"), new BorrowRequest("b1", "m1"), ResultResponse.class);

    // m2 reserves the book
    rest.postForObject(url("/api/reserve"), new ReserveRequest("b1", "m2"), ResultResponse.class);

    // m1 returns the book - should automatically loan to m2
    ResultWithNextResponse returned =
        rest.postForObject(
            url("/api/return"), new ReturnRequest("b1", "m1"), ResultWithNextResponse.class);
    assertThat(returned.ok()).isTrue();
    assertThat(returned.nextMemberId()).isEqualTo("m2");

    // Verify book is now loaned to m2
    BookResponse book =
        rest.getForObject(url("/api/books"), BooksResponse.class).items().stream()
            .filter(b -> b.id().equals("b1"))
            .findFirst()
            .orElseThrow();
    assertThat(book.loanedTo()).isEqualTo("m2");
    assertThat(book.reservationQueue()).isEmpty();
  }

  @Test
  void reservingAvailableBookLoansImmediately() {
    // Reserve an available book (should loan immediately instead of queuing)
    ResultResponse reserved =
        rest.postForObject(
            url("/api/reserve"), new ReserveRequest("b1", "m1"), ResultResponse.class);
    assertThat(reserved.ok()).isTrue();

    // Verify book is immediately loaned to m1, not in reservation queue
    BookResponse book =
        rest.getForObject(url("/api/books"), BooksResponse.class).items().stream()
            .filter(b -> b.id().equals("b1"))
            .findFirst()
            .orElseThrow();
    assertThat(book.loanedTo()).isEqualTo("m1");
    assertThat(book.reservationQueue()).isEmpty();
  }

  @Test
  void duplicateReservationRejected() {
    // m1 borrows a book
    rest.postForObject(url("/api/borrow"), new BorrowRequest("b1", "m1"), ResultResponse.class);

    // m2 reserves the book
    ResultResponse firstReserve =
        rest.postForObject(
            url("/api/reserve"), new ReserveRequest("b1", "m2"), ResultResponse.class);
    assertThat(firstReserve.ok()).isTrue();

    // m2 tries to reserve again (should fail)
    ResultResponse duplicateReserve =
        rest.postForObject(
            url("/api/reserve"), new ReserveRequest("b1", "m2"), ResultResponse.class);
    assertThat(duplicateReserve.ok()).isFalse();
    assertThat(duplicateReserve.reason()).isEqualTo("ALREADY_RESERVED");
  }

  @Test
  void cannotReserveBorrowedBook() {
    // m1 borrows a book
    rest.postForObject(url("/api/borrow"), new BorrowRequest("b1", "m1"), ResultResponse.class);

    // m1 tries to reserve the same book (should fail)
    ResultResponse reserve =
        rest.postForObject(
            url("/api/reserve"), new ReserveRequest("b1", "m1"), ResultResponse.class);
    assertThat(reserve.ok()).isFalse();
    assertThat(reserve.reason()).isEqualTo("ALREADY_BORROWED");
  }

  @Test
  void cannotDeleteLoanedBook() {
    // m1 borrows a book
    rest.postForObject(url("/api/borrow"), new BorrowRequest("b1", "m1"), ResultResponse.class);

    // Try to delete the book (should fail)
    ResultResponse deleted =
        rest.exchange(
                url("/api/books"),
                HttpMethod.DELETE,
                new org.springframework.http.HttpEntity<>(new DeleteBookRequest("b1")),
                ResultResponse.class)
            .getBody();
    assertThat(deleted.ok()).isFalse();
    assertThat(deleted.reason()).isEqualTo("BOOK_LOANED");
  }

  @Test
  void cannotDeleteBookWithReservations() {
    // Create a member at borrow limit
    rest.postForObject(
        url("/api/members"), new CreateMemberRequest("vm2", "At Limit"), ResultResponse.class);
    rest.postForObject(
        url("/api/books"), new CreateBookRequest("vb1", "Book 1"), ResultResponse.class);
    rest.postForObject(
        url("/api/books"), new CreateBookRequest("vb2", "Book 2"), ResultResponse.class);
    rest.postForObject(
        url("/api/books"), new CreateBookRequest("vb3", "Book 3"), ResultResponse.class);
    rest.postForObject(
        url("/api/books"), new CreateBookRequest("vb4", "Book 4"), ResultResponse.class);
    rest.postForObject(
        url("/api/books"), new CreateBookRequest("vb5", "Book 5"), ResultResponse.class);
    rest.postForObject(url("/api/borrow"), new BorrowRequest("vb1", "vm2"), ResultResponse.class);
    rest.postForObject(url("/api/borrow"), new BorrowRequest("vb2", "vm2"), ResultResponse.class);
    rest.postForObject(url("/api/borrow"), new BorrowRequest("vb3", "vm2"), ResultResponse.class);
    rest.postForObject(url("/api/borrow"), new BorrowRequest("vb4", "vm2"), ResultResponse.class);
    rest.postForObject(url("/api/borrow"), new BorrowRequest("vb5", "vm2"), ResultResponse.class);

    // vm2 (at limit) reserves an available book b1
    // Since they're at limit, book won't be loaned immediately - just queued
    rest.postForObject(url("/api/reserve"), new ReserveRequest("b1", "vm2"), ResultResponse.class);

    // Verify book has reservations but is not loaned
    BookResponse book =
        rest.getForObject(url("/api/books"), BooksResponse.class).items().stream()
            .filter(b -> b.id().equals("b1"))
            .findFirst()
            .orElseThrow();
    assertThat(book.loanedTo()).isNull();
    assertThat(book.reservationQueue()).contains("vm2");

    // Try to delete the book (should fail because it has reservations)
    ResultResponse deleted =
        rest.exchange(
                url("/api/books"),
                HttpMethod.DELETE,
                new org.springframework.http.HttpEntity<>(new DeleteBookRequest("b1")),
                ResultResponse.class)
            .getBody();
    assertThat(deleted.ok()).isFalse();
    assertThat(deleted.reason()).isEqualTo("BOOK_RESERVED");
  }

  @Test
  void cannotDeleteMemberWithActiveLoans() {
    // m1 borrows a book
    rest.postForObject(url("/api/borrow"), new BorrowRequest("b1", "m1"), ResultResponse.class);

    // Try to delete m1 (should fail)
    ResultResponse deleted =
        rest.exchange(
                url("/api/members"),
                HttpMethod.DELETE,
                new org.springframework.http.HttpEntity<>(new DeleteMemberRequest("m1")),
                ResultResponse.class)
            .getBody();
    assertThat(deleted.ok()).isFalse();
    assertThat(deleted.reason()).isEqualTo("MEMBER_HAS_LOANS");
  }

  @Test
  void deleteMemberRemovesFromReservationQueues() {
    // Create a new member
    rest.postForObject(
        url("/api/members"), new CreateMemberRequest("vm1", "Test Member"), ResultResponse.class);

    // m1 borrows a book
    rest.postForObject(url("/api/borrow"), new BorrowRequest("b1", "m1"), ResultResponse.class);

    // vm1 reserves the book
    rest.postForObject(url("/api/reserve"), new ReserveRequest("b1", "vm1"), ResultResponse.class);

    // Verify reservation exists
    BookResponse bookBefore =
        rest.getForObject(url("/api/books"), BooksResponse.class).items().stream()
            .filter(b -> b.id().equals("b1"))
            .findFirst()
            .orElseThrow();
    assertThat(bookBefore.reservationQueue()).contains("vm1");

    // Delete vm1 (should succeed and remove from queue)
    ResultResponse deleted =
        rest.exchange(
                url("/api/members"),
                HttpMethod.DELETE,
                new org.springframework.http.HttpEntity<>(new DeleteMemberRequest("vm1")),
                ResultResponse.class)
            .getBody();
    assertThat(deleted.ok()).isTrue();

    // Verify vm1 was removed from reservation queue
    BookResponse bookAfter =
        rest.getForObject(url("/api/books"), BooksResponse.class).items().stream()
            .filter(b -> b.id().equals("b1"))
            .findFirst()
            .orElseThrow();
    assertThat(bookAfter.reservationQueue()).doesNotContain("vm1");
  }

  @Test
  void returnSkipsIneligibleMembersInQueue() {
    // Create a new member with 5 books already borrowed (at limit)
    rest.postForObject(
        url("/api/members"), new CreateMemberRequest("vm3", "At Limit"), ResultResponse.class);
    rest.postForObject(
        url("/api/books"), new CreateBookRequest("vb1", "Book 1"), ResultResponse.class);
    rest.postForObject(
        url("/api/books"), new CreateBookRequest("vb2", "Book 2"), ResultResponse.class);
    rest.postForObject(
        url("/api/books"), new CreateBookRequest("vb3", "Book 3"), ResultResponse.class);
    rest.postForObject(
        url("/api/books"), new CreateBookRequest("vb4", "Book 4"), ResultResponse.class);
    rest.postForObject(
        url("/api/books"), new CreateBookRequest("vb5", "Book 5"), ResultResponse.class);
    rest.postForObject(url("/api/borrow"), new BorrowRequest("vb1", "vm3"), ResultResponse.class);
    rest.postForObject(url("/api/borrow"), new BorrowRequest("vb2", "vm3"), ResultResponse.class);
    rest.postForObject(url("/api/borrow"), new BorrowRequest("vb3", "vm3"), ResultResponse.class);
    rest.postForObject(url("/api/borrow"), new BorrowRequest("vb4", "vm3"), ResultResponse.class);
    rest.postForObject(url("/api/borrow"), new BorrowRequest("vb5", "vm3"), ResultResponse.class);

    // m1 borrows b1
    rest.postForObject(url("/api/borrow"), new BorrowRequest("b1", "m1"), ResultResponse.class);

    // vm3 (at limit) and m2 reserve b1
    rest.postForObject(url("/api/reserve"), new ReserveRequest("b1", "vm3"), ResultResponse.class);
    rest.postForObject(url("/api/reserve"), new ReserveRequest("b1", "m2"), ResultResponse.class);

    // m1 returns b1 - should skip vm3 (at limit) and loan to m2
    ResultWithNextResponse returned =
        rest.postForObject(
            url("/api/return"), new ReturnRequest("b1", "m1"), ResultWithNextResponse.class);
    assertThat(returned.ok()).isTrue();
    assertThat(returned.nextMemberId()).isEqualTo("m2");

    // Verify b1 is now loaned to m2
    BookResponse book =
        rest.getForObject(url("/api/books"), BooksResponse.class).items().stream()
            .filter(b -> b.id().equals("b1"))
            .findFirst()
            .orElseThrow();
    assertThat(book.loanedTo()).isEqualTo("m2");
    assertThat(book.reservationQueue()).isEmpty();
  }

  private String url(String path) {
    return "http://localhost:" + port + path;
  }
}
