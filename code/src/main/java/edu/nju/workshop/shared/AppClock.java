package edu.nju.workshop.shared;

import java.time.Instant;

/** 业务时间的唯一来源，测试可以换成固定时钟. */
public interface AppClock {

  /** 返回用于业务时间判断的当前时刻. */
  Instant now();
}
