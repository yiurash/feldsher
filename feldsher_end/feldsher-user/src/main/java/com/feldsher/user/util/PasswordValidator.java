package com.feldsher.user.util;

import com.feldsher.user.config.PasswordProperties;
import com.feldsher.user.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class PasswordValidator {

    private final PasswordProperties passwordProperties;

    private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile("[a-z]");
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile("[A-Z]");
    private static final Pattern SPECIAL_PATTERN = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]");

    private static final List<String> COMMON_SEQUENCES = List.of(
            "123456", "234567", "345678", "456789", "567890",
            "098765", "987654", "876543", "765432", "654321",
            "qwerty", "asdfgh", "zxcvbn", "qwertz", "azerty"
    );

    public void validate(String password) {
        if (password == null || password.isEmpty()) {
            throw new BusinessException(400, "密码不能为空");
        }

        int minLength = passwordProperties.getMinLength();
        int maxLength = passwordProperties.getMaxLength();

        if (password.length() < minLength) {
            throw new BusinessException(400, "密码长度不能少于" + minLength + "位");
        }

        if (password.length() > maxLength) {
            throw new BusinessException(400, "密码长度不能超过" + maxLength + "位");
        }

        if (passwordProperties.getRequireDigit() && !containsDigit(password)) {
            throw new BusinessException(400, "密码必须包含数字");
        }

        if (passwordProperties.getRequireLowercase() && !containsLowercase(password)) {
            throw new BusinessException(400, "密码必须包含小写字母");
        }

        if (passwordProperties.getRequireUppercase() && !containsUppercase(password)) {
            throw new BusinessException(400, "密码必须包含大写字母");
        }

        if (passwordProperties.getRequireSpecial() && !containsSpecial(password)) {
            throw new BusinessException(400, "密码必须包含特殊字符");
        }

        if (isWeakPassword(password)) {
            throw new BusinessException(400, "密码过于简单，请使用更复杂的密码");
        }

        if (isSequential(password)) {
            throw new BusinessException(400, "密码不能包含连续的数字或字母序列");
        }

        if (hasRepeatedChars(password)) {
            throw new BusinessException(400, "密码不能包含过多重复字符");
        }
    }

    private boolean containsDigit(String password) {
        return DIGIT_PATTERN.matcher(password).find();
    }

    private boolean containsLowercase(String password) {
        return LOWERCASE_PATTERN.matcher(password).find();
    }

    private boolean containsUppercase(String password) {
        return UPPERCASE_PATTERN.matcher(password).find();
    }

    private boolean containsSpecial(String password) {
        return SPECIAL_PATTERN.matcher(password).find();
    }

    private boolean isWeakPassword(String password) {
        String lowerPassword = password.toLowerCase();
        List<String> weakPasswords = passwordProperties.getWeakPasswords();
        if (weakPasswords != null) {
            for (String weak : weakPasswords) {
                if (lowerPassword.contains(weak.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isSequential(String password) {
        String lowerPassword = password.toLowerCase();
        for (String sequence : COMMON_SEQUENCES) {
            if (lowerPassword.contains(sequence)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasRepeatedChars(String password) {
        int maxRepeated = 3;
        int count = 1;
        for (int i = 1; i < password.length(); i++) {
            if (password.charAt(i) == password.charAt(i - 1)) {
                count++;
                if (count >= maxRepeated) {
                    return true;
                }
            } else {
                count = 1;
            }
        }
        return false;
    }
}
