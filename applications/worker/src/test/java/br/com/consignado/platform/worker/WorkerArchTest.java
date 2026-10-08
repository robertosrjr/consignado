package br.com.consignado.platform.worker;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Regra de dependencia hexagonal: infrastructure -> application -> domain (NFR-12). */
@AnalyzeClasses(
    packages = "br.com.consignado.platform.worker",
    importOptions = ImportOption.DoNotIncludeTests.class)
class WorkerArchTest {

  private static final String DOMAIN = "br.com.consignado.platform.worker.domain..";
  private static final String APPLICATION = "br.com.consignado.platform.worker.application..";
  private static final String INFRASTRUCTURE = "br.com.consignado.platform.worker.infrastructure..";

  @ArchTest
  static final ArchRule domain_has_no_dependency_on_other_layers_or_frameworks =
      noClasses()
          .that()
          .resideInAPackage(DOMAIN)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              APPLICATION,
              INFRASTRUCTURE,
              "org.springframework..",
              "software.amazon..",
              "jakarta..",
              "io.micrometer..",
              "io.github.resilience4j..",
              "tools.jackson..");

  @ArchTest
  static final ArchRule application_does_not_depend_on_infrastructure_or_frameworks =
      noClasses()
          .that()
          .resideInAPackage(APPLICATION)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              INFRASTRUCTURE,
              "org.springframework..",
              "software.amazon..",
              "jakarta..",
              "io.micrometer..",
              "io.github.resilience4j..",
              "tools.jackson..",
              "br.com.consignado.platform.starter..",
              "br.com.consignado.platform.aws..");

  @ArchTest
  static final ArchRule inbound_adapters_do_not_use_outbound_adapters =
      noClasses()
          .that()
          .resideInAPackage("..infrastructure.adapter.in..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..infrastructure.adapter.out..");

  @ArchTest
  static final ArchRule use_cases_live_in_the_application_layer =
      classes().that().haveSimpleNameEndingWith("Service").should().resideInAPackage(APPLICATION);
}
