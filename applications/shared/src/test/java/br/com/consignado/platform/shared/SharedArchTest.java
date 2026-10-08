package br.com.consignado.platform.shared;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** NFR-U1-21: `shared` nao depende de Spring, AWS SDK, JPA nem de servicos. */
@AnalyzeClasses(
    packages = "br.com.consignado.platform.shared",
    importOptions = ImportOption.DoNotIncludeTests.class)
class SharedArchTest {

  @ArchTest
  static final ArchRule shared_has_no_framework_or_cloud_dependencies =
      noClasses()
          .that()
          .resideInAPackage("br.com.consignado.platform.shared..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "software.amazon..",
              "jakarta.persistence..",
              "org.hibernate..");

  @ArchTest
  static final ArchRule shared_does_not_depend_on_services =
      noClasses()
          .that()
          .resideInAPackage("br.com.consignado.platform.shared..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "br.com.consignado.platform.ingestion..",
              "br.com.consignado.platform.dispatcher..",
              "br.com.consignado.platform.worker..",
              "br.com.consignado.platform.legacy..",
              "br.com.consignado.platform.starter..");
}
