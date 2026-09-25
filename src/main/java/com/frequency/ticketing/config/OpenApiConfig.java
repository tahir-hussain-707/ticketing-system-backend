package com.frequency.ticketing.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Generates the OpenAPI description from the annotated controllers (constitution Principle I). */
@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI ticketingOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Ticket Management Backend API")
                .version("1.0")
                .description("Support Ticket Management System backend"));
  }
}
