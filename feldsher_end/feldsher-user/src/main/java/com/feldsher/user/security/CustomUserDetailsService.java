package com.feldsher.user.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.feldsher.user.entity.SysUser;
import com.feldsher.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final SysUserMapper sysUserMapper;

    @Override
    public UserDetails loadUserByUsername(String phone) throws UsernameNotFoundException {
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUser::getPhone, phone);
        SysUser user = sysUserMapper.selectOne(queryWrapper);

        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + phone);
        }

        List<String> roles = sysUserMapper.selectRoleCodesByUserId(user.getId());
        if (roles == null) {
            roles = Collections.emptyList();
        }

        return new CustomUserDetails(
                user.getId(),
                user.getPhone(),
                user.getPassword(),
                user.getStatus(),
                roles
        );
    }

    public UserDetails loadUserByUserId(Long userId) throws UsernameNotFoundException {
        SysUser user = sysUserMapper.selectById(userId);

        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + userId);
        }

        List<String> roles = sysUserMapper.selectRoleCodesByUserId(user.getId());
        if (roles == null) {
            roles = Collections.emptyList();
        }

        return new CustomUserDetails(
                user.getId(),
                user.getPhone(),
                user.getPassword(),
                user.getStatus(),
                roles
        );
    }
}
