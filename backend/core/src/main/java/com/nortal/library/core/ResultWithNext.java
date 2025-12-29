package com.nortal.library.core;

/**
 * Result wrapper for return operations that may hand off to another member.
 *
 * @param ok true if operation succeeded, false otherwise
 * @param nextMemberId ID of member who received the book next via automatic handoff, or null
 */
public record ResultWithNext(boolean ok, String nextMemberId) {
  public static ResultWithNext success(String nextMemberId) {
    return new ResultWithNext(true, nextMemberId);
  }

  public static ResultWithNext failure() {
    return new ResultWithNext(false, null);
  }
}
