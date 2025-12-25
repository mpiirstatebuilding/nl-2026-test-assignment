package com.nortal.library.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LoanExtensionRequest(
    @NotBlank String bookId, @NotBlank String memberId, @NotNull Integer days) {}
