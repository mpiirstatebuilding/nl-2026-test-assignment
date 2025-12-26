package com.nortal.library.api.controller;

import com.nortal.library.api.dto.BookResponse;
import com.nortal.library.api.dto.BooksResponse;
import com.nortal.library.api.dto.CreateBookRequest;
import com.nortal.library.api.dto.DeleteBookRequest;
import com.nortal.library.api.dto.ResultResponse;
import com.nortal.library.api.dto.UpdateBookRequest;
import com.nortal.library.core.LibraryService;
import com.nortal.library.core.domain.Book;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books")
@Tag(name = "Books", description = "Book catalog management operations")
public class BookController {

  private final LibraryService libraryService;

  public BookController(LibraryService libraryService) {
    this.libraryService = libraryService;
  }

  @Operation(
      summary = "Get all books",
      description =
          "Returns a list of all books in the library with their current loan status and reservation queue")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Success - returns all books",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = BooksResponse.class),
                examples =
                    @ExampleObject(
                        name = "Books list",
                        value =
                            """
                        {
                          "items": [
                            {
                              "id": "b1",
                              "title": "The Great Gatsby",
                              "loanedTo": "m1",
                              "dueDate": "2026-01-15",
                              "reservationQueue": ["m2", "m3"]
                            },
                            {
                              "id": "b2",
                              "title": "1984",
                              "loanedTo": null,
                              "dueDate": null,
                              "reservationQueue": []
                            }
                          ]
                        }
                        """)))
  })
  @GetMapping
  public BooksResponse list() {
    return new BooksResponse(libraryService.allBooks().stream().map(this::toResponse).toList());
  }

  @Operation(
      summary = "Search books",
      description =
          "Search for books using filters. All parameters are optional and can be combined. "
              + "Returns books matching all specified criteria.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Success - returns matching books (may be empty)",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = BooksResponse.class),
                examples = {
                  @ExampleObject(
                      name = "Search by title",
                      description = "Find books with 'Great' in title",
                      value =
                          """
                          {
                            "items": [
                              {
                                "id": "b1",
                                "title": "The Great Gatsby",
                                "loanedTo": null,
                                "dueDate": null,
                                "reservationQueue": []
                              }
                            ]
                          }
                          """),
                  @ExampleObject(
                      name = "Search available books",
                      description = "Find all available (not loaned) books",
                      value =
                          """
                          {
                            "items": [
                              {
                                "id": "b2",
                                "title": "1984",
                                "loanedTo": null,
                                "dueDate": null,
                                "reservationQueue": []
                              }
                            ]
                          }
                          """),
                  @ExampleObject(
                      name = "Search by borrower",
                      description = "Find all books loaned to member m1",
                      value =
                          """
                          {
                            "items": [
                              {
                                "id": "b1",
                                "title": "The Great Gatsby",
                                "loanedTo": "m1",
                                "dueDate": "2026-01-15",
                                "reservationQueue": []
                              }
                            ]
                          }
                          """)
                }))
  })
  @GetMapping("/search")
  public BooksResponse search(
      @RequestParam(value = "titleContains", required = false) String titleContains,
      @RequestParam(value = "available", required = false) Boolean available,
      @RequestParam(value = "loanedTo", required = false) String loanedTo) {
    return new BooksResponse(
        libraryService.searchBooks(titleContains, available, loanedTo).stream()
            .map(this::toResponse)
            .toList());
  }

  @Operation(
      summary = "Create new book",
      description = "Add a new book to the library catalog. Book ID must be unique.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Book created successfully",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ResultResponse.class),
                examples =
                    @ExampleObject(
                        name = "Success",
                        value =
                            """
                        {
                          "ok": true,
                          "reason": null
                        }
                        """))),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid request or book ID already exists",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ResultResponse.class),
                examples =
                    @ExampleObject(
                        name = "Book already exists",
                        value =
                            """
                        {
                          "ok": false,
                          "reason": "INVALID_REQUEST"
                        }
                        """)))
  })
  @PostMapping
  public ResultResponse create(@RequestBody @Valid CreateBookRequest request) {
    LibraryService.Result result = libraryService.createBook(request.id(), request.title());
    return new ResultResponse(result.ok(), result.reason());
  }

  @Operation(
      summary = "Update book",
      description = "Update an existing book's title. Book ID cannot be changed.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Book updated successfully",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ResultResponse.class),
                examples =
                    @ExampleObject(
                        name = "Success",
                        value =
                            """
                        {
                          "ok": true,
                          "reason": null
                        }
                        """))),
    @ApiResponse(
        responseCode = "404",
        description = "Book not found",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ResultResponse.class),
                examples =
                    @ExampleObject(
                        name = "Book not found",
                        value =
                            """
                        {
                          "ok": false,
                          "reason": "BOOK_NOT_FOUND"
                        }
                        """)))
  })
  @PutMapping
  public ResultResponse update(@RequestBody @Valid UpdateBookRequest request) {
    LibraryService.Result result = libraryService.updateBook(request.id(), request.title());
    return new ResultResponse(result.ok(), result.reason());
  }

  @Operation(
      summary = "Delete book",
      description =
          "Remove a book from the library catalog. Cannot delete books that are currently loaned or have reservations.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Book deleted successfully",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ResultResponse.class),
                examples =
                    @ExampleObject(
                        name = "Success",
                        value =
                            """
                        {
                          "ok": true,
                          "reason": null
                        }
                        """))),
    @ApiResponse(
        responseCode = "400",
        description = "Cannot delete - book is loaned or has reservations",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ResultResponse.class),
                examples =
                    @ExampleObject(
                        name = "Book is in use",
                        value =
                            """
                        {
                          "ok": false,
                          "reason": "BOOK_IN_USE"
                        }
                        """))),
    @ApiResponse(
        responseCode = "404",
        description = "Book not found",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ResultResponse.class),
                examples =
                    @ExampleObject(
                        name = "Book not found",
                        value =
                            """
                        {
                          "ok": false,
                          "reason": "BOOK_NOT_FOUND"
                        }
                        """)))
  })
  @DeleteMapping
  public ResultResponse delete(@RequestBody @Valid DeleteBookRequest request) {
    LibraryService.Result result = libraryService.deleteBook(request.id());
    return new ResultResponse(result.ok(), result.reason());
  }

  private BookResponse toResponse(Book book) {
    return new BookResponse(
        book.getId(),
        book.getTitle(),
        book.getLoanedTo(),
        book.getDueDate(),
        book.getReservationQueue());
  }
}
