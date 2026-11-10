package edu.nju.workshop;

import edu.nju.workshop.shared.BeijingTime;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WorkshopApplication {

  static {
    TimeZone.setDefault(TimeZone.getTimeZone(BeijingTime.ZONE));
  }

  public static void main(String[] args) {
    SpringApplication.run(WorkshopApplication.class, args);
  }
}
