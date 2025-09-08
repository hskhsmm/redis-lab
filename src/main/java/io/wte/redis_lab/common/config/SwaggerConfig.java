package io.wte.redis_lab.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Redis Lab API")
                        .description("Spring Boot + Redis 실습 프로젝트 API 문서")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("WTE")
                                .email("contact@wte.io")
                        )
                );
    }

    @Bean
    public OpenApiCustomizer openApiCustomizer() {
        return openApi -> {
            openApi.getPaths().values().forEach(pathItem -> 
                pathItem.readOperations().forEach(operation -> {
                    ApiResponses apiResponses = operation.getResponses();
                    
                    apiResponses.addApiResponse("400", new ApiResponse()
                            .description("잘못된 요청")
                            .content(new io.swagger.v3.oas.models.media.Content()
                                    .addMediaType("application/json", 
                                            new io.swagger.v3.oas.models.media.MediaType()
                                                    .schema(new io.swagger.v3.oas.models.media.Schema<>()
                                                            .$ref("#/components/schemas/ErrorResponse")))));
                    
                    apiResponses.addApiResponse("500", new ApiResponse()
                            .description("서버 내부 오류")
                            .content(new io.swagger.v3.oas.models.media.Content()
                                    .addMediaType("application/json", 
                                            new io.swagger.v3.oas.models.media.MediaType()
                                                    .schema(new io.swagger.v3.oas.models.media.Schema<>()
                                                            .$ref("#/components/schemas/ErrorResponse")))));
                })
            );
        };
    }
}