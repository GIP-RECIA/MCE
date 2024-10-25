package fr.recia.mce.api.escomceapi.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import fr.recia.mce.api.escomceapi.interceptor.SoffitInterceptor;
import fr.recia.mce.api.escomceapi.interceptor.bean.SoffitHolder;

@Configuration
public class InterceptorConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(soffitInterceptor());
    }

    @Bean
    public SoffitInterceptor soffitInterceptor() {
        return new SoffitInterceptor(soffitHolder());
    }

    @Bean
    @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
    public SoffitHolder soffitHolder() {
        return new SoffitHolder();
    }

}
