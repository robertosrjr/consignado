package br.com.consignado.platform.ingestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Ingestion API: borda de absorcao multi-tenant do credito consignado. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class IngestionApplication {

  public static void main(String[] args) {
    SpringApplication.run(IngestionApplication.class, args);
  }
}
