package com.nortal.library.api.controller;

import com.nortal.library.api.dto.BookResponse;
import com.nortal.library.api.dto.BooksResponse;
import com.nortal.library.api.dto.BorrowRequest;
import com.nortal.library.api.dto.CancelReservationRequest;
import com.nortal.library.api.dto.LoanExtensionRequest;
import com.nortal.library.api.dto.ReserveRequest;
import com.nortal.library.api.dto.ResultResponse;
import com.nortal.library.api.dto.ResultWithNextResponse;
import com.nortal.library.api.dto.ReturnRequest;
import com.nortal.library.core.LibraryService;
import com.nortal.library.core.Result;
import com.nortal.library.core.ResultWithNext;
import com.nortal.library.core.domain.Book;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(
    name = "Book Loans & Reservations",
    description =
        "Operations for borrowing, returning, reserving books, and managing loan extensions")
public class LoanController {

  private final LibraryService libraryService;

  public LoanController(LibraryService libraryService) {
    this.libraryService = libraryService;
  }

  @PostMapping("/borrow")
  @Operation(
      summary = "Borrow a book",
      description =
          "Allows a member to borrow a book. Enforces borrow limit (5 books max) and reservation queue order. Only the member at the head of the reservation queue can borrow a reserved book.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Success or business rule violation",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ResultResponse.class),
                examples = {
                  @ExampleObject(name = "Success", value = "{\"ok\": true, \"reason\": null}"),
                  @ExampleObject(
                      name = "Book not found",
                      value = "{\"ok\": false, \"reason\": \"BOOK_NOT_FOUND\"}"),
                  @ExampleObject(
                      name = "Member at borrow limit",
                      value = "{\"ok\": false, \"reason\": \"BORROW_LIMIT\"}"),
                  @ExampleObject(
                      name = "Book already borrowed by member",
                      value = "{\"ok\": false, \"reason\": \"ALREADY_BORROWED\"}"),
                  @ExampleObject(
                      name = "Book loaned to someone else",
                      value = "{\"ok\": false, \"reason\": \"BOOK_UNAVAILABLE\"}"),
                  @ExampleObject(
                      name = "Book reserved for another member",
                      value = "{\"ok\": false, \"reason\": \"RESERVED\"}")
                }))
  })
  public ResultResponse borrow(@RequestBody @Valid BorrowRequest request) {
    Result result = libraryService.borrowBook(request.bookId(), request.memberId());
    return new ResultResponse(result.ok(), result.reason());
  }

  @PostMapping("/reserve")
  @Operation(
      summary = "Reserve a book",
      description =
          "Reserves a book for a member. If the book is available and the member is eligible (under borrow limit), the book is loaned immediately instead of being queued. Otherwise, the member is added to the end of the FIFO reservation queue.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Success or business rule violation",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ResultResponse.class),
                examples = {
                  @ExampleObject(
                      name = "Success (immediate loan)",
                      value = "{\"ok\": true, \"reason\": null}",
                      description = "Book was available and loaned immediately"),
                  @ExampleObject(
                      name = "Success (queued)",
                      value = "{\"ok\": true, \"reason\": null}",
                      description = "Member added to reservation queue"),
                  @ExampleObject(
                      name = "Already borrowed",
                      value = "{\"ok\": false, \"reason\": \"ALREADY_BORROWED\"}"),
                  @ExampleObject(
                      name = "Already reserved",
                      value = "{\"ok\": false, \"reason\": \"ALREADY_RESERVED\"}")
                }))
  })
  public ResultResponse reserve(@RequestBody @Valid ReserveRequest request) {
    Result result = libraryService.reserveBook(request.bookId(), request.memberId());
    return new ResultResponse(result.ok(), result.reason());
  }

  @PostMapping("/cancel-reservation")
  public ResultResponse cancelReservation(@RequestBody @Valid CancelReservationRequest request) {
    Result result = libraryService.cancelReservation(request.bookId(), request.memberId());
    return new ResultResponse(result.ok(), result.reason());
  }

  @PostMapping("/return")
  @Operation(
      summary = "Return a book",
      description =
          "Returns a borrowed book. Only the current borrower can return the book (memberId optional at API level but required by business logic for authorization). Automatically hands off the book to the next eligible member in the reservation queue.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Success or business rule violation",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ResultWithNextResponse.class),
                examples = {
                  @ExampleObject(
                      name = "Success (no handoff)",
                      value = "{\"ok\": true, \"nextMemberId\": null}"),
                  @ExampleObject(
                      name = "Success with automatic handoff",
                      value = "{\"ok\": true, \"nextMemberId\": \"m2\"}",
                      description =
                          "Book was automatically loaned to member m2 from the reservation queue"),
                  @ExampleObject(
                      name = "Authorization failed",
                      value = "{\"ok\": false, \"nextMemberId\": null}",
                      description = "Returned by wrong member or memberId is null")
                }))
  })
  public ResultWithNextResponse returnBook(@RequestBody @Valid ReturnRequest request) {
    ResultWithNext result = libraryService.returnBook(request.bookId(), request.memberId());
    return new ResultWithNextResponse(result.ok(), result.nextMemberId());
  }

  @PostMapping("/extend")
  @Operation(
      summary = "Extend a book loan",
      description =
          "Extends the due date of a borrowed book by the specified number of days. Only the current borrower can extend their loan (memberId optional at API level but required by business logic). Supports negative values to shorten the loan period.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Success or business rule violation",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ResultResponse.class),
                examples = {
                  @ExampleObject(name = "Success", value = "{\"ok\": true, \"reason\": null}"),
                  @ExampleObject(
                      name = "Invalid extension",
                      value = "{\"ok\": false, \"reason\": \"INVALID_EXTENSION\"}",
                      description = "Extension days cannot be zero"),
                  @ExampleObject(
                      name = "Not borrower",
                      value = "{\"ok\": false, \"reason\": \"NOT_BORROWER\"}",
                      description = "Only current borrower can extend"),
                  @ExampleObject(
                      name = "Max extension reached",
                      value = "{\"ok\": false, \"reason\": \"MAX_EXTENSION_REACHED\"}",
                      description =
                          "Extension would exceed maximum limit (90 days from first due date)")
                }))
  })
  public ResultResponse extend(@RequestBody @Valid LoanExtensionRequest request) {
    Result result = libraryService.extendLoan(request.bookId(), request.memberId(), request.days());
    return new ResultResponse(result.ok(), result.reason());
  }

  @GetMapping("/overdue")
  public BooksResponse overdue() {
    return new BooksResponse(
        libraryService.overdueBooks(LocalDate.now()).stream().map(this::toResponse).toList());
  }

  private BookResponse toResponse(Book book) {
    return new BookResponse(
        book.getId(),
        book.getTitle(),
        book.getLoanedTo(),
        book.getDueDate(),
        book.getFirstDueDate(),
        book.getReservationQueue());
  }
}
