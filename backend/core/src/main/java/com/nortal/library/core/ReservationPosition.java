package com.nortal.library.core;

/**
 * Represents a member's position in a book's reservation queue.
 *
 * @param bookId ID of the reserved book
 * @param position 0-indexed position in the reservation queue
 */
public record ReservationPosition(String bookId, int position) {}
