package com.nortal.library.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for borrowing a book.
 *
 * <p>Used by the POST /api/borrow endpoint.
 *
 * @param bookId ID of the book to borrow (required, non-blank)
 * @param memberId ID of the member borrowing the book (required, non-blank)
 */
public record BorrowRequest(@NotBlank String bookId, @NotBlank String memberId) {}
