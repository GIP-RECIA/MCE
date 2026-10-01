package fr.recia.mce.api.escomceapi.configuration.bean;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@ConfigurationProperties(prefix = "cas")
@Data
public class CasProperties {
    private String casServiceId;
    private String casServerUrl;
    private String casProviderKey;
    private String casTicketCallback;
    private Set<String> authorizedDomains;
}
