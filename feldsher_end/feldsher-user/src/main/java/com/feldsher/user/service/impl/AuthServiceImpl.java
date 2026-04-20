package com.feldsher.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.feldsher.user.dto.LoginDTO;
import com.feldsher.user.dto.RegisterDTO;
import com.feldsher.user.entity.SysRole;
import com.feldsher.user.entity.SysUser;
import com.feldsher.user.entity.SysUserRole;
import com.feldsher.user.exception.BusinessException;
import com.feldsher.user.mapper.SysRoleMapper;
import com.feldsher.user.mapper.SysUserMapper;
import com.feldsher.user.mapper.SysUserRoleMapper;
import com.feldsher.user.service.AuthService;
import com.feldsher.user.util.JwtUtil;
import com.feldsher.user.util.PasswordValidator;
import com.feldsher.user.vo.LoginVO;
import com.feldsher.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import cn.hutool.core.util.StrUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final PasswordValidator passwordValidator;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String DEFAULT_ROLE_CODE = "PATIENT";
    private static final String BLACKLIST_KEY_PREFIX = "token:blacklist:";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterDTO registerDTO) {
        String phone = registerDTO.getPhone();
        String password = registerDTO.getPassword();
        String confirmPassword = registerDTO.getConfirmPassword();

        if (!StrUtil.equals(password, confirmPassword)) {
            throw new BusinessException(400, "两次输入的密码不一致");
        }

        passwordValidator.validate(password);

        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUser::getPhone, phone);
        Long count = sysUserMapper.selectCount(queryWrapper);
        if (count > 0) {
            throw new BusinessException(400, "该手机号已注册");
        }

        SysUser user = new SysUser();
        user.setPhone(phone);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(StrUtil.isNotBlank(registerDTO.getNickname()) ? registerDTO.getNickname() : "用户" + phone.substring(7));
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        user.setDeleted(0);
        sysUserMapper.insert(user);

        LambdaQueryWrapper<SysRole> roleQueryWrapper = new LambdaQueryWrapper<>();
        roleQueryWrapper.eq(SysRole::getRoleCode, DEFAULT_ROLE_CODE);
        SysRole defaultRole = sysRoleMapper.selectOne(roleQueryWrapper);

        if (defaultRole != null) {
            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(user.getId());
            userRole.setRoleId(defaultRole.getId());
            userRole.setCreateTime(LocalDateTime.now());
            sysUserRoleMapper.insert(userRole);
        }

        log.info("用户注册成功: phone={}", phone);
    }

    @Override
    public LoginVO login(LoginDTO loginDTO) {
        String phone = loginDTO.getPhone();
        String password = loginDTO.getPassword();

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(phone, password)
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof com.feldsher.user.security.CustomUserDetails userDetails)) {
            throw new BusinessException("登录失败");
        }

        List<String> roles = userDetails.getRoles();
        String rolesStr = String.join(",", roles);

        String token = jwtUtil.generateToken(userDetails.getUserId(), userDetails.getPhone(), rolesStr);

        log.info("用户登录成功: userId={}, phone={}", userDetails.getUserId(), phone);

        LoginVO loginVO = new LoginVO();
        loginVO.setUserId(userDetails.getUserId());
        loginVO.setPhone(userDetails.getPhone());
        SysUser user = sysUserMapper.selectById(userDetails.getUserId());
        if (user != null) {
            loginVO.setNickname(user.getNickname());
            loginVO.setAvatar(user.getAvatar());
        }
        loginVO.setToken(token);
        loginVO.setTokenType("Bearer");

        return loginVO;
    }

    @Override
    public void logout(String token) {
        if (StrUtil.isNotBlank(token)) {
            try {
                if (jwtUtil.validateToken(token)) {
                    Long userId = jwtUtil.getUserId(token);
                    String key = BLACKLIST_KEY_PREFIX + token.hashCode();
                    long ttl = jwtUtil.isTokenExpired(token) ? 0 :
                            jwtUtil.parseToken(token).getExpiration().getTime() - System.currentTimeMillis();
                    if (ttl > 0) {
                        stringRedisTemplate.opsForValue().set(key, userId.toString(), ttl, TimeUnit.MILLISECONDS);
                    }
                    log.info("用户登出成功: userId={}", userId);
                }
            } catch (Exception e) {
                log.warn("Token处理异常: {}", e.getMessage());
            }
        }
        SecurityContextHolder.clearContext();
    }

    @Override
    public UserVO getUserInfo(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        List<String> roles = sysUserMapper.selectRoleCodesByUserId(userId);

        UserVO userVO = new UserVO();
        userVO.setId(user.getId());
        userVO.setPhone(user.getPhone());
        userVO.setNickname(user.getNickname());
        userVO.setAvatar(user.getAvatar());
        userVO.setEmail(user.getEmail());
        userVO.setStatus(user.getStatus());
        userVO.setRoles(roles);
        userVO.setCreateTime(user.getCreateTime());

        return userVO;
    }
}
