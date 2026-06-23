package fr.recia.mce.api.escomceapi.configuration.bean;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "mail")
@Data
public class MailProperties {

    private String regexValideAddr;
    private String regexsDomainesExclus;
    private String domainesConfiance;

}
