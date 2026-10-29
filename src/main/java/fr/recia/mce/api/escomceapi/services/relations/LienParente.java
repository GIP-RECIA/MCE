package fr.recia.mce.api.escomceapi.services.relations;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public enum LienParente {
    Mere("10", "Mère"),
    Pere("20", "Père"),
    Fratrie("37", "Fratrie"),
    Ascendant("38", "Ascendant"),
    MembreFamille("39", "MembreFamille"),
    Educateur("41", "Educateur"),
    AssistantFamilial("42", "AssistantFamilial"),
    GardeEnfant("43", "GardeEnfant"),
    Tuteur("50", "Tuteur"),
    ASE("51", "ASE"),
    EleveLuiMeme("70", "EleveLuiMeme"),
    AutreLien("90", "AutreLien"),
    Maitre("1001", "Maître"),
    Apprenti("1002", "Apprenti");

    private final String code;
    private final String label;
    private static final Map<String, LienParente> BY_CODE = new HashMap<>();

    static {
        for (LienParente type : values()) {
            BY_CODE.put(type.code, type);
        }
    }

    LienParente(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static LienParente fromCode(String code) {
        LienParente type = BY_CODE.get(code);
        if (type == null) {
            throw new IllegalArgumentException("Code inconnu : " + code);
        }
        return type;
    }

    public static String getLabelByCode(String code) {
        return fromCode(code).getLabel();
    }
}
