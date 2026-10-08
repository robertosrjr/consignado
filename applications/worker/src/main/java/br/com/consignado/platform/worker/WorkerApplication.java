package br.com.consignado.platform.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Worker: consome as filas das lanes e alimenta o legado dentro do orcamento de vazao. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class WorkerApplication {

  public static void main(String[] args) {
    SpringApplication.run(WorkerApplication.class, args);
  }
}
