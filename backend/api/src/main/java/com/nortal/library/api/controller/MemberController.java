package com.nortal.library.api.controller;

import com.nortal.library.api.dto.CreateMemberRequest;
import com.nortal.library.api.dto.DeleteMemberRequest;
import com.nortal.library.api.dto.MemberResponse;
import com.nortal.library.api.dto.MemberSummaryResponse;
import com.nortal.library.api.dto.MembersResponse;
import com.nortal.library.api.dto.ResultResponse;
import com.nortal.library.api.dto.UpdateMemberRequest;
import com.nortal.library.core.LibraryService;
import com.nortal.library.core.domain.Member;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
@Tag(name = "Members", description = "Member management operations")
public class MemberController {

  private final LibraryService libraryService;

  public MemberController(LibraryService libraryService) {
    this.libraryService = libraryService;
  }

  @Operation(
      summary = "Get all members",
      description = "Returns a list of all library members")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Success - returns all members",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = MembersResponse.class),
                examples =
                    @ExampleObject(
                        name = "Members list",
                        value =
                            """
                        {
                          "items": [
                            {
                              "id": "m1",
                              "name": "Alice Smith"
                            },
                            {
                              "id": "m2",
                              "name": "Bob Johnson"
                            }
                          ]
                        }
                        """)))
  })
  @GetMapping
  public MembersResponse list() {
    return new MembersResponse(libraryService.allMembers().stream().map(this::toResponse).toList());
  }

  @Operation(
      summary = "Get member summary",
      description =
          "Returns a member's current loans and reservations with detailed information including due dates and queue positions")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Success - returns member's loans and reservations",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = MemberSummaryResponse.class),
                examples =
                    @ExampleObject(
                        name = "Member summary",
                        value =
                            """
                        {
                          "ok": true,
                          "reason": null,
                          "loans": [
                            {
                              "bookId": "b1",
                              "title": "The Great Gatsby",
                              "dueDate": "2026-01-15"
                            }
                          ],
                          "reservations": [
                            {
                              "bookId": "b3",
                              "title": "1984",
                              "position": 1
                            }
                          ]
                        }
                        """))),
    @ApiResponse(
        responseCode = "404",
        description = "Member not found",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = MemberSummaryResponse.class),
                examples =
                    @ExampleObject(
                        name = "Member not found",
                        value =
                            """
                        {
                          "ok": false,
                          "reason": "MEMBER_NOT_FOUND",
                          "loans": null,
                          "reservations": null
                        }
                        """)))
  })
  @GetMapping("/{memberId}/summary")
  public MemberSummaryResponse summary(@PathVariable("memberId") String memberId) {
    LibraryService.MemberSummary summary = libraryService.memberSummary(memberId);
    if (!summary.ok()) {
      return MemberSummaryResponse.failure(summary.reason());
    }

    var loans =
        summary.loans().stream()
            .map(
                book ->
                    new MemberSummaryResponse.BookLoanSummary(
                        book.getId(), book.getTitle(), book.getDueDate()))
            .toList();
    var reservations =
        summary.reservations().stream()
            .map(
                reservation -> {
                  String title =
                      libraryService
                          .findBook(reservation.bookId())
                          .map(com.nortal.library.core.domain.Book::getTitle)
                          .orElse(null);
                  return new MemberSummaryResponse.ReservationSummary(
                      reservation.bookId(), title, reservation.position());
                })
            .toList();
    return MemberSummaryResponse.success(loans, reservations);
  }

  @Operation(
      summary = "Create new member",
      description = "Add a new member to the library. Member ID must be unique.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Member created successfully",
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
        description = "Invalid request or member ID already exists",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ResultResponse.class),
                examples =
                    @ExampleObject(
                        name = "Member already exists",
                        value =
                            """
                        {
                          "ok": false,
                          "reason": "INVALID_REQUEST"
                        }
                        """)))
  })
  @PostMapping
  public ResultResponse create(@RequestBody @Valid CreateMemberRequest request) {
    LibraryService.Result result = libraryService.createMember(request.id(), request.name());
    return new ResultResponse(result.ok(), result.reason());
  }

  @Operation(
      summary = "Update member",
      description = "Update an existing member's name. Member ID cannot be changed.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Member updated successfully",
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
        description = "Member not found",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ResultResponse.class),
                examples =
                    @ExampleObject(
                        name = "Member not found",
                        value =
                            """
                        {
                          "ok": false,
                          "reason": "MEMBER_NOT_FOUND"
                        }
                        """)))
  })
  @PutMapping
  public ResultResponse update(@RequestBody @Valid UpdateMemberRequest request) {
    LibraryService.Result result = libraryService.updateMember(request.id(), request.name());
    return new ResultResponse(result.ok(), result.reason());
  }

  @Operation(
      summary = "Delete member",
      description =
          "Remove a member from the library. Cannot delete members who have active loans.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Member deleted successfully",
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
        description = "Cannot delete - member has active loans",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ResultResponse.class),
                examples =
                    @ExampleObject(
                        name = "Member has active loans",
                        value =
                            """
                        {
                          "ok": false,
                          "reason": "MEMBER_HAS_LOANS"
                        }
                        """))),
    @ApiResponse(
        responseCode = "404",
        description = "Member not found",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ResultResponse.class),
                examples =
                    @ExampleObject(
                        name = "Member not found",
                        value =
                            """
                        {
                          "ok": false,
                          "reason": "MEMBER_NOT_FOUND"
                        }
                        """)))
  })
  @DeleteMapping
  public ResultResponse delete(@RequestBody @Valid DeleteMemberRequest request) {
    LibraryService.Result result = libraryService.deleteMember(request.id());
    return new ResultResponse(result.ok(), result.reason());
  }

  private MemberResponse toResponse(Member member) {
    return new MemberResponse(member.getId(), member.getName());
  }
}
