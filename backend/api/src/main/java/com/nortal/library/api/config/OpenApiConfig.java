package com.nortal.library.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger configuration for the Library Management API.
 *
 * <p>Provides interactive API documentation accessible at:
 *
 * <ul>
 *   <li>Swagger UI: <a
 *       href="http://localhost:8080/swagger-ui.html">http://localhost:8080/swagger-ui.html</a>
 *   <li>OpenAPI JSON: <a
 *       href="http://localhost:8080/v3/api-docs">http://localhost:8080/v3/api-docs</a>
 * </ul>
 */
@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI libraryOpenAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Library Management System API")
                .description(
                    """
                    RESTful API for managing library book loans, reservations, and member operations.

                    **Key Features:**
                    - Book borrowing and returning with automatic handoff
                    - Reservation queue management (FIFO)
                    - Member borrow limit enforcement (max 5 books)
                    - Loan extension and overdue tracking

                    **Business Rules:**
                    - Only the current borrower can return or extend a book
                    - Reservation queue ensures fair access to popular books
                    - Automatic handoff to next eligible member when book is returned
                    - Immediate loan when reserving an available book

                    **Assignment Context:**
                    This is a Nortal LEAP 2026 coding assignment submission.
                    """)
                .version("1.0")
                .license(new License().name("Assignment Project").url("")))
        .servers(
            List.of(
                new Server()
                    .url("http://localhost:8080")
                    .description("Local development server (H2 in-memory database)")));
  }
}
