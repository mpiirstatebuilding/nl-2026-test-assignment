package com.nortal.library.api.config;

import com.nortal.library.core.LibraryService;
import com.nortal.library.core.port.BookRepository;
import com.nortal.library.core.port.MemberRepository;
import com.nortal.library.core.service.BookManagementService;
import com.nortal.library.core.service.LibraryQueryService;
import com.nortal.library.core.service.LoanService;
import com.nortal.library.core.service.MemberManagementService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for library services.
 *
 * <p>Configures the service layer using a modular architecture:
 *
 * <ul>
 *   <li>Specialized services for different business domains
 *   <li>LibraryService as a facade providing unified API
 * </ul>
 */
@Configuration
public class LibraryConfig {

  @Bean
  LoanService loanService(BookRepository bookRepository, MemberRepository memberRepository) {
    return new LoanService(bookRepository, memberRepository);
  }

  @Bean
  LibraryQueryService libraryQueryService(
      BookRepository bookRepository, MemberRepository memberRepository) {
    return new LibraryQueryService(bookRepository, memberRepository);
  }

  @Bean
  BookManagementService bookManagementService(BookRepository bookRepository) {
    return new BookManagementService(bookRepository);
  }

  @Bean
  MemberManagementService memberManagementService(
      BookRepository bookRepository, MemberRepository memberRepository) {
    return new MemberManagementService(bookRepository, memberRepository);
  }

  @Bean
  LibraryService libraryService(
      LoanService loanService,
      LibraryQueryService queryService,
      BookManagementService bookManagement,
      MemberManagementService memberManagement) {
    return new LibraryService(loanService, queryService, bookManagement, memberManagement);
  }
}
