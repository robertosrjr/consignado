package br.com.consignado.platform.legacy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Core Banking simulado (Mock Legacy). */
@SpringBootApplication
@ConfigurationPropertiesScan
public class LegacyApplication {

  public static void main(String[] args) {
    SpringApplication.run(LegacyApplication.class, args);
  }
}
