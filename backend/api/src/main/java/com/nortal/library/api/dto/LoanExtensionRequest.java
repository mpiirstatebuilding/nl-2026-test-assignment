package com.nortal.library.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request to extend a book loan.
 *
 * <p>Note: memberId was added as a security fix (Bug #3) but may not be in the original API
 * contract. It is optional at the API level but required by business logic for authorization.
 * Requests without memberId will be accepted by the API but rejected by LibraryService.
 */
public record LoanExtensionRequest(
    @NotBlank String bookId, String memberId, @NotNull Integer days) {}
