package edu.nju.workshop.statistics;

import java.nio.charset.StandardCharsets;

/** CSV 使用 BOM、CRLF 和双引号转义，文本字段禁止作为电子表格公式执行. */
final class CsvWriter {
  private final StringBuilder content = new StringBuilder("\uFEFF");

  void row(Object... fields) {
    for (int i = 0; i < fields.length; i++) {
      if (i > 0) {
        content.append(',');
      }
      Object field = fields[i];
      String value = field == null ? "" : field.toString();
      if (field instanceof String && formula(value)) {
        value = "'" + value;
      }
      content.append('"').append(value.replace("\"", "\"\"")).append('"');
    }
    content.append("\r\n");
  }

  byte[] bytes() {
    return content.toString().getBytes(StandardCharsets.UTF_8);
  }

  private boolean formula(String value) {
    int index = 0;
    while (index < value.length()) {
      char c = value.charAt(index);
      if (c == '\t' || c == '\r' || c == '\n') {
        return true;
      }
      if (!Character.isWhitespace(c) && !Character.isSpaceChar(c)
          && !Character.isISOControl(c) && c != '\uFEFF') {
        return "=+-@".indexOf(c) >= 0;
      }
      index++;
    }
    return false;
  }
}
