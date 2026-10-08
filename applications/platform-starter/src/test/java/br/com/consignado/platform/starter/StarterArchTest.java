package br.com.consignado.platform.starter;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** O starter nao contem logica de negocio nem depende de AWS SDK, JPA ou de servicos. */
@AnalyzeClasses(
    packages = "br.com.consignado.platform.starter",
    importOptions = ImportOption.DoNotIncludeTests.class)
class StarterArchTest {

  @ArchTest
  static final ArchRule starter_has_no_cloud_or_persistence_dependencies =
      noClasses()
          .that()
          .resideInAPackage("br.com.consignado.platform.starter..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("software.amazon..", "jakarta.persistence..", "org.hibernate..");

  @ArchTest
  static final ArchRule starter_does_not_depend_on_services =
      noClasses()
          .that()
          .resideInAPackage("br.com.consignado.platform.starter..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "br.com.consignado.platform.ingestion..",
              "br.com.consignado.platform.dispatcher..",
              "br.com.consignado.platform.worker..",
              "br.com.consignado.platform.legacy..");
}
