package edu.nju.workshop;

import edu.nju.workshop.shared.BeijingTime;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 校园工作坊应用入口，启动时统一使用北京时间. */
@SpringBootApplication
public class WorkshopApplication {

  static {
    TimeZone.setDefault(TimeZone.getTimeZone(BeijingTime.ZONE));
  }

  /** 启动提供页面和业务接口的 Spring Boot 应用. */
  public static void main(String[] args) {
    SpringApplication.run(WorkshopApplication.class, args);
  }
}
