package com.rentatools.shared.infrastructure.web;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registra el {@link CurrentActorArgumentResolver} en Spring MVC.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final CurrentActorArgumentResolver currentActorArgumentResolver;

    public WebMvcConfig(CurrentActorArgumentResolver currentActorArgumentResolver) {
        this.currentActorArgumentResolver = currentActorArgumentResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentActorArgumentResolver);
    }
}
