package com.feldsher.user.service;

import com.feldsher.user.dto.LoginDTO;
import com.feldsher.user.dto.RegisterDTO;
import com.feldsher.user.vo.LoginVO;
import com.feldsher.user.vo.UserVO;

public interface AuthService {

    void register(RegisterDTO registerDTO);

    LoginVO login(LoginDTO loginDTO);

    void logout(String token);

    UserVO getUserInfo(Long userId);
}
