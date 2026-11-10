package edu.nju.workshop;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.jdbc.core.JdbcTemplate;

final class SqlScripts {

  private SqlScripts() {
  }

  static void execute(JdbcTemplate jdbc, String classpath) {
    String sql = read(classpath);
    StringBuilder statement = new StringBuilder();
    for (String line : sql.split("\n")) {
      String trimmed = line.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("--")) {
        continue;
      }
      statement.append(line).append('\n');
      if (trimmed.endsWith(";")) {
        String command = statement.toString().trim();
        jdbc.execute(command.substring(0, command.length() - 1));
        statement.setLength(0);
      }
    }
  }

  private static String read(String classpath) {
    try (InputStream in = SqlScripts.class.getClassLoader().getResourceAsStream(classpath)) {
      if (in == null) {
        throw new IllegalStateException("缺少脚本 " + classpath);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new IllegalStateException("无法读取脚本 " + classpath, ex);
    }
  }
}
