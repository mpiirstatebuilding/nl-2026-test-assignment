package com.nortal.library.core;

/**
 * Standard result wrapper for library operations.
 *
 * @param ok true if operation succeeded, false otherwise
 * @param reason error code if operation failed, null if successful
 */
public record Result(boolean ok, String reason) {
  public static Result success() {
    return new Result(true, null);
  }

  public static Result failure(String reason) {
    return new Result(false, reason);
  }
}
