package edu.nju.workshop;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.nju.workshop.shared.MutableClock;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
abstract class WorkshopTestBase {

  static final String PASSWORD = "Demo-2026";
  static final String HASH = new BCryptPasswordEncoder().encode(PASSWORD);

  @Autowired
  JdbcTemplate jdbc;

  @Autowired
  MockMvc mvc;

  @Autowired
  MutableClock clock;

  /**
   * 测试用 TEST_DB_* 覆盖应用的 SPRING_DATASOURCE_* 连接，重置数据前另行校验库名。
   */
  @DynamicPropertySource
  static void testDatabase(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> required("TEST_DB_URL"));
    registry.add("spring.datasource.username", () -> required("TEST_DB_USERNAME"));
    registry.add("spring.datasource.password", () -> required("TEST_DB_PASSWORD"));
  }

  @BeforeEach
  void resetTestDatabase() {
    assertTestDatabase();
    for (String table : new String[] {
        "operation_logs", "registrations", "sessions", "activities", "users"}) {
      jdbc.execute("DROP TABLE IF EXISTS " + table);
    }
    executeScript("db/V001__schema.sql");
  }

  void executeScript(String classpath) {
    new ResourceDatabasePopulator(new ClassPathResource(classpath))
        .execute(Objects.requireNonNull(jdbc.getDataSource()));
  }

  void user(long id, String account, String name, String role) {
    jdbc.update(
        "INSERT INTO users (id, account, password_hash, display_name, role)"
            + " VALUES (?,?,?,?,?)",
        id, account, HASH, name, role);
  }

  void activity(long id, long organizerId, String name, String status,
      String pendingName, String pendingDescription) {
    jdbc.update(
        "INSERT INTO activities (id, name, description, organizer_id,"
            + " publication_status, pending_name, pending_description, revision_pending)"
            + " VALUES (?,?,?,?,?,?,?,?)",
        id, name, "简介：" + name, organizerId, status, pendingName, pendingDescription,
        pendingName == null ? 0 : 1);
  }

  void session(long id, long activityId, String start, String end, String deadline,
      int capacity, String status) {
    jdbc.update(
        "INSERT INTO sessions (id, activity_id, start_time, end_time,"
            + " registration_deadline, location, capacity, publication_status)"
            + " VALUES (?,?,?,?,?,?,?,?)",
        id, activityId, start, end, deadline, "仙林校区 · 逸夫楼 A203", capacity, status);
  }

  void registration(long id, long userId, long sessionId, String status) {
    jdbc.update(
        "INSERT INTO registrations (id, participant_id, session_id, registered_at, status)"
            + " VALUES (?,?,?,?,?)",
        id, userId, sessionId, "2026-11-01 09:00:00", status);
  }

  MockHttpSession login(String account, String role) throws Exception {
    MvcResult result = mvc.perform(formLogin("/login").user(account).password(PASSWORD))
        .andExpect(authenticated().withRoles(role))
        .andExpect(redirectedUrl("/workshops"))
        .andReturn();
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  String body(MockHttpSession session, String path) throws Exception {
    return mvc.perform(get(path).session(session))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString(StandardCharsets.UTF_8);
  }

  private static String required(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("缺少环境变量 " + name);
    }
    return value;
  }

  private void assertTestDatabase() {
    String catalog = jdbc.queryForObject("SELECT DATABASE()", String.class);
    String currentUser = jdbc.queryForObject("SELECT CURRENT_USER()", String.class);
    if (!"workshop_test".equals(catalog) || currentUser == null
        || currentUser.toLowerCase().contains("workshop_dev")) {
      throw new IllegalStateException("拒绝重置非测试库");
    }
    try (Connection connection = Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
      String url = connection.getMetaData().getURL();
      if (url == null || !url.contains("/workshop_test") || url.contains("workshop_dev")) {
        throw new IllegalStateException("测试连接必须指向 workshop_test");
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("无法校验测试库连接", ex);
    }
  }
}
