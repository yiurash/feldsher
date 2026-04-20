package com.feldsher.user.vo;

import lombok.Data;

@Data
public class LoginVO {

    private Long userId;
    private String phone;
    private String nickname;
    private String avatar;
    private String token;
    private String tokenType;
}
