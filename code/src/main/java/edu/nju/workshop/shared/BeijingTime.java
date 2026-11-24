package edu.nju.workshop.shared;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** 业务规则使用北京时间，数据库保存对应的墙上时间. */
public final class BeijingTime {

  public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

  private BeijingTime() {
  }

  /** 将绝对时刻转换为数据库和业务页面使用的北京时间. */
  public static LocalDateTime toLocal(Instant instant) {
    return LocalDateTime.ofInstant(instant, ZONE);
  }
}
