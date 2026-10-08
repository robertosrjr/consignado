package br.com.consignado.platform.starter;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.DispatcherServlet;

/** Registra o tratamento global de erros apenas em aplicacoes web servlet. */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(DispatcherServlet.class)
public class PlatformWebAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  PlatformProblemDetailAdvice platformProblemDetailAdvice() {
    return new PlatformProblemDetailAdvice();
  }
}
