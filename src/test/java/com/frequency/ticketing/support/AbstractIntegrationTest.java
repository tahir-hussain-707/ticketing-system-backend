package com.frequency.ticketing.support;

import com.frequency.ticketing.domain.user.User;
import com.frequency.ticketing.domain.user.UserRole;
import com.frequency.ticketing.repository.UserRepository;
import com.frequency.ticketing.web.dto.LoginRequest;
import com.frequency.ticketing.web.dto.LoginResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared base for full-stack tests: one PostgreSQL Testcontainer, started once and reused across
 * every subclass in the JVM (research.md: singleton-container pattern), against which Flyway
 * migrations run for real (constitution Principle III — no H2 substitute).
 *
 * <p>Also provides {@link #loginAs} — every endpoint except login now requires authentication
 * (spec 005 FR-006), so subclasses authenticate as one of a small fixed set of seeded test users
 * rather than each writing their own login boilerplate.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

  // pgvector/pgvector:pg16 (not the plain postgres:16-alpine image), so migration V6's
  // `CREATE EXTENSION vector` and the knowledge-base similarity queries run against the real
  // extension — 005-auth-rag-chatbot research.md "Testing the AI integration".
  protected static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
          DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

  static {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @LocalServerPort protected int port;

  @Autowired protected TestRestTemplate restTemplate;
  @Autowired protected UserRepository userRepository;
  @Autowired protected PasswordEncoder passwordEncoder;

  private static final String TEST_PASSWORD = "test-password";
  private static final Map<String, UUID> SEEDED_USER_IDS = new ConcurrentHashMap<>();

  protected String baseUrl(String path) {
    return "http://localhost:" + port + "/api/v1" + path;
  }

  /** Test-user emails, exposed so a test can assert against "the other" user of the same role. */
  public enum TestUser {
    ADMIN("admin-test@example.test", UserRole.ADMIN),
    SUPPORT_1("support1-test@example.test", UserRole.SUPPORT),
    SUPPORT_2("support2-test@example.test", UserRole.SUPPORT),
    GENERAL_1("general1-test@example.test", UserRole.GENERAL),
    GENERAL_2("general2-test@example.test", UserRole.GENERAL);

    public final String email;
    public final UserRole role;

    TestUser(String email, UserRole role) {
      this.email = email;
      this.role = role;
    }
  }

  /** Ensures the given test user exists, and returns its id. Idempotent across test classes. */
  protected UUID seed(TestUser testUser) {
    return SEEDED_USER_IDS.computeIfAbsent(
        testUser.email,
        email ->
            userRepository
                .findByEmailIgnoreCase(email)
                .map(User::getId)
                .orElseGet(
                    () -> {
                      User user =
                          userRepository.save(
                              new User(
                                  testUser.name(), email, passwordEncoder.encode(TEST_PASSWORD), testUser.role));
                      return user.getId();
                    }));
  }

  /** Logs in as {@code testUser} (seeding it first if needed) and returns headers with the session cookie. */
  protected HttpHeaders loginAs(TestUser testUser) {
    seed(testUser);
    ResponseEntity<LoginResponse> response =
        restTemplate.postForEntity(
            baseUrl("/auth/login"),
            new LoginRequest(testUser.email, TEST_PASSWORD),
            LoginResponse.class);
    List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
    HttpHeaders headers = new HttpHeaders();
    if (cookies != null) {
      headers.put(HttpHeaders.COOKIE, cookies);
    }
    return headers;
  }

  protected UUID idOf(TestUser testUser) {
    return seed(testUser);
  }

  /** Convenience for a GET/POST/PATCH with only the auth header and no body. */
  protected <T> HttpEntity<T> authEntity(HttpHeaders headers) {
    return new HttpEntity<>(null, headers);
  }

  protected <T> HttpEntity<T> authEntity(T body, HttpHeaders headers) {
    HttpHeaders withJson = new HttpHeaders();
    withJson.addAll(headers);
    withJson.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
    return new HttpEntity<>(body, withJson);
  }
}
