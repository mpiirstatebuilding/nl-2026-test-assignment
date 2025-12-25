package com.nortal.library.persistence.adapter;

import com.nortal.library.core.domain.Book;
import com.nortal.library.core.port.BookRepository;
import com.nortal.library.persistence.jpa.JpaBookRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class BookRepositoryAdapter implements BookRepository {

  private final JpaBookRepository jpaRepository;

  public BookRepositoryAdapter(JpaBookRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public Optional<Book> findById(String id) {
    return jpaRepository.findById(id);
  }

  @Override
  public List<Book> findAll() {
    return jpaRepository.findAll();
  }

  @Override
  public Book save(Book book) {
    return jpaRepository.save(book);
  }

  @Override
  public void delete(Book book) {
    jpaRepository.delete(book);
  }

  @Override
  public boolean existsById(String id) {
    return jpaRepository.existsById(id);
  }

  // Optimized query methods

  @Override
  public long countByLoanedTo(String memberId) {
    return jpaRepository.countByLoanedTo(memberId);
  }

  @Override
  public List<Book> findByLoanedTo(String memberId) {
    return jpaRepository.findByLoanedTo(memberId);
  }

  @Override
  public List<Book> findByReservationQueueContaining(String memberId) {
    return jpaRepository.findByReservationQueueContaining(memberId);
  }

  @Override
  public List<Book> findByDueDateBefore(LocalDate date) {
    return jpaRepository.findByDueDateBefore(date);
  }

  @Override
  public boolean existsByLoanedTo(String memberId) {
    return jpaRepository.existsByLoanedTo(memberId);
  }

  @Override
  public List<Book> findByTitleContainingIgnoreCase(String title) {
    return jpaRepository.findByTitleContainingIgnoreCase(title);
  }

  @Override
  public List<Book> findByLoanedToIsNull() {
    return jpaRepository.findByLoanedToIsNull();
  }
}
