package com.nortal.library.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to return a book.
 *
 * <p>Note: memberId is optional at the API contract level (per README: { bookId, memberId? }), but
 * the business logic requires it for authorization. Requests without memberId will be accepted by
 * the API but rejected by LibraryService with {"ok":false}.
 */
public record ReturnRequest(@NotBlank String bookId, String memberId) {}
