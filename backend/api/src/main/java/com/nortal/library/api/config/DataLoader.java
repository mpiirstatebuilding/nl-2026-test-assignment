package com.nortal.library.api.config;

import com.nortal.library.core.domain.Book;
import com.nortal.library.core.domain.Member;
import com.nortal.library.core.port.BookRepository;
import com.nortal.library.core.port.MemberRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;

@Configuration
public class DataLoader {

  @Bean
  CommandLineRunner seedData(BookRepository bookRepository, MemberRepository memberRepository) {
    return args -> {
      // Clear all existing data first
      // H2 is configured with DB_CLOSE_DELAY=-1, so the database persists across
      // @DirtiesContext resets in tests. We need to clear it manually to ensure
      // each test starts with a clean slate.
      bookRepository.findAll().forEach(bookRepository::delete);
      memberRepository.findAll().forEach(memberRepository::delete);

      // Use repositories directly to bypass duplicate checks in service layer
      // This ensures seed data is always loaded correctly

      // Create seed data for members
      memberRepository.save(new Member("m1", "Kertu"));
      memberRepository.save(new Member("m2", "Rasmus"));
      memberRepository.save(new Member("m3", "Liis"));
      memberRepository.save(new Member("m4", "Markus"));

      // Create seed data for books
      bookRepository.save(new Book("b1", "Clean Code"));
      bookRepository.save(new Book("b2", "Domain-Driven Design"));
      bookRepository.save(new Book("b3", "Refactoring"));
      bookRepository.save(new Book("b4", "Effective Java"));
      bookRepository.save(new Book("b5", "Design Patterns"));
      bookRepository.save(new Book("b6", "The Pragmatic Programmer"));

      Book overdueBook = new Book("vb1", "The Secret to Punctuality");
      overdueBook.setLoanedTo("m4");
      overdueBook.setDueDate(LocalDate.of(2025, 12, 15));
      overdueBook.setFirstDueDate(LocalDate.of(2025, 12, 1));
      bookRepository.save(overdueBook);
    };
  }
}
