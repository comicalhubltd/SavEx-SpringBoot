package com.savex.money;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.properties.ConfigurationProperties;
import jakarta.annotation.PostConstruct;
@Component @ConfigurationProperties(prefix="savex.ai") @Getter @Setter
public class AiSettings {
    private boolean enabled=false;
    private String apiKey="";
    private String model="gpt-4.1-mini";
    private int dailyLimit=50;
    private boolean learningEnabled=true;
    @PostConstruct void validate(){
        if(dailyLimit<1 || dailyLimit>1000 || model.isBlank() || model.length()>100)
            throw new IllegalStateException("Invalid AI settings");
        if(enabled && apiKey.isBlank())throw new IllegalStateException("Configure OPENAI_API_KEY before enabling SavEx AI");
    }
}
