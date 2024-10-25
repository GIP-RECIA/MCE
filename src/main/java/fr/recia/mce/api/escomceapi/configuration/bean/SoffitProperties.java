package fr.recia.mce.api.escomceapi.configuration.bean;

import lombok.Data;

@Data
public class SoffitProperties {

    private String jwtSignatureKey;

    @Override
    public String toString() {
        return "\"SoffitProperties\": {" +
                "\n\t\"jwtSignatureKey\": \"" + jwtSignatureKey + "\"" +
                "\n}";
    }

}
