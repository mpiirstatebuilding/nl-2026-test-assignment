package com.nortal.library.persistence.jpa;

import com.nortal.library.core.domain.Book;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaBookRepository extends JpaRepository<Book, String> {
  // Spring Data JPA auto-implements these from method names
  long countByLoanedTo(String memberId);

  List<Book> findByLoanedTo(String memberId);

  List<Book> findByDueDateBefore(LocalDate date);

  boolean existsByLoanedTo(String memberId);

  List<Book> findByTitleContainingIgnoreCase(String title);

  List<Book> findByLoanedToIsNull();

  // Custom query needed for ElementCollection search
  @Query("SELECT b FROM Book b JOIN b.reservationQueue rq WHERE rq = :memberId")
  List<Book> findByReservationQueueContaining(@Param("memberId") String memberId);
}
