package com.nortal.library.core;

import com.nortal.library.core.domain.Book;
import java.util.List;

/**
 * Summary of a member's loans and reservations.
 *
 * @param ok true if member exists and summary was retrieved
 * @param reason error code if member not found
 * @param loans list of books currently loaned to the member
 * @param reservations list of book reservations with queue positions
 */
public record MemberSummary(
    boolean ok, String reason, List<Book> loans, List<ReservationPosition> reservations) {}
