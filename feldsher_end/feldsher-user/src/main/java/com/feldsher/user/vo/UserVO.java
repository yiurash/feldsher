package com.feldsher.user.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserVO {

    private Long id;
    private String phone;
    private String nickname;
    private String avatar;
    private String email;
    private Integer status;
    private List<String> roles;
    private LocalDateTime createTime;
}
