package fr.recia.mce.api.escomceapi.configuration;

import fr.recia.mce.api.escomceapi.configuration.bean.CorsProperties;
import fr.recia.mce.api.escomceapi.configuration.bean.CustomLdapProperties;
import fr.recia.mce.api.escomceapi.configuration.bean.ServiceProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import javax.annotation.PostConstruct;
import com.fasterxml.jackson.core.JsonProcessingException;

@Configuration
@ConfigurationProperties(prefix = "app", ignoreUnknownFields = false)
@Data
@Validated
@Slf4j
public class MCEProperties {

    private CorsProperties cors = new CorsProperties();
    private CustomLdapProperties ldap = new CustomLdapProperties();
    private ServiceProperties service = new ServiceProperties();
    private JwtProperties jwt = new JwtProperties();

    @PostConstruct
    private void init() throws JsonProcessingException {
        log.info("Loaded properties: {}", this);
    }

    @Override
    public String toString() {
        return "{\n\"MCEProperties\":{"
                + ",\n\t \"cors\":" + cors
                + ",\n\t \"ldap\":" + ldap
                + ",\n\t \"service\":" + service
                + ",\n\t \"jwt\":" + jwt
                + "\n\t}\n}";
    }

    @Data
    public static class JwtProperties {
        private String secret;
    }
}