package sn.boutique.noyau.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentation OpenAPI (Swagger UI disponible en profil dev sur /swagger-ui.html).
 */
@Configuration
public class OpenApiConfig {

    public static final String SCHEMA_JWT = "jetonAcces";

    @Bean
    OpenAPI documentationApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("API Boutique informatique")
                        .version("v1")
                        .description("""
                                API de la boutique en ligne de matériel informatique (Sénégal).
                                Montants en FCFA (XOF), entiers sans décimales.
                                Les erreurs suivent le format ProblemDetail (RFC 9457)."""))
                .components(new Components()
                        .addSecuritySchemes(SCHEMA_JWT, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Jeton d'accès JWT (durée de vie : 15 minutes)")));
    }
}
