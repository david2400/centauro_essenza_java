package com.essenza.draco.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registra la protección por ruta (el interceptor decide qué rutas cubre, ver {@link RouteRules}). */
@Configuration
public class RouteAuthorizationConfig implements WebMvcConfigurer {

    private final RouteAuthorizationInterceptor interceptor;

    public RouteAuthorizationConfig(RouteAuthorizationInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor);
    }
}
