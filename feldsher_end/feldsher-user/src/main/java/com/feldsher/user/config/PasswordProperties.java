package com.feldsher.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "password")
public class PasswordProperties {

    private Integer minLength = 8;
    private Integer maxLength = 20;
    private Boolean requireDigit = true;
    private Boolean requireLowercase = true;
    private Boolean requireUppercase = false;
    private Boolean requireSpecial = false;
    private List<String> weakPasswords;
}
