package br.com.consignado.platform.dispatcher;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** As regras de roteamento e o despacho nao dependem de AWS, Lambda nem Spring. */
@AnalyzeClasses(
    packages = "br.com.consignado.platform.dispatcher",
    importOptions = ImportOption.DoNotIncludeTests.class)
class DispatcherArchTest {

  @ArchTest
  static final ArchRule routing_and_dispatch_are_framework_free =
      noClasses()
          .that()
          .resideInAnyPackage(
              "br.com.consignado.platform.dispatcher.routing..",
              "br.com.consignado.platform.dispatcher.dispatch..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "software.amazon..",
              "com.amazonaws..",
              "org.springframework..",
              "br.com.consignado.platform.dispatcher.aws..",
              "br.com.consignado.platform.dispatcher.lambda..");

  @ArchTest
  static final ArchRule the_lambda_artifact_does_not_use_spring =
      noClasses()
          .that()
          .resideInAPackage("br.com.consignado.platform.dispatcher..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("org.springframework..");
}
