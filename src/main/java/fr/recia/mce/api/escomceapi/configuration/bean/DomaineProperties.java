package fr.recia.mce.api.escomceapi.configuration.bean;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "domaine")
@Data
public class DomaineProperties {

    private List<String> gestionRecia = new ArrayList<>();
    private List<String> gestionInclude = new ArrayList<>();
    private List<String> gestionExclude = new ArrayList<>();

}
