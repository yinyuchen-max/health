package com.health.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.health.common.utils.JwtUtil;
import com.health.domain.dto.UserLoginDTO;
import com.health.domain.dto.UserRegisterDTO;
import com.health.domain.entity.User;
import com.health.domain.vo.AdminUserVO;
import com.health.domain.vo.UserVO;
import com.health.mapper.UserMapper;
import com.health.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public String login(UserLoginDTO userLoginDTO) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", userLoginDTO.getUsername());
        User user = getOne(queryWrapper);
        if (user == null || !userLoginDTO.getPassword().equals(user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }

        if (user.getStatus() == 0) {
            throw new RuntimeException("账号已被禁用");
        }

        return jwtUtil.generateToken(user.getUsername());
    }

    @Override
    public void register(UserRegisterDTO userRegisterDTO) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", userRegisterDTO.getUsername());
        if (getOne(queryWrapper) != null) {
            throw new RuntimeException("用户名已存在");
        }
        User user = new User();
        BeanUtils.copyProperties(userRegisterDTO, user);
        user.setPassword(userRegisterDTO.getPassword());
        user.setStatus(1);
        user.setCreateTime(java.time.LocalDateTime.now());
        user.setUpdateTime(java.time.LocalDateTime.now());
        user.setDeleted(0);
        save(user);
    }

    @Override
    public UserVO getUserInfo(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public UserVO getUserInfoByUsername(String username) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        User user = getOne(queryWrapper);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public void updateUserInfo(UserVO userVO) {
        User user = getById(userVO.getId());
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 只更新允许修改的字段
        user.setEmail(userVO.getEmail());
        user.setPhone(userVO.getPhone());
        user.setGender(userVO.getGender());
        user.setAge(userVO.getAge());
        user.setHeight(userVO.getHeight());
        user.setWeight(userVO.getWeight());
        user.setUpdateTime(java.time.LocalDateTime.now());

        updateById(user);
    }

    @Override
    public Integer getUserStatus(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        return user.getStatus();
    }

    @Override
    public void updateUserStatus(Long userId, Integer status) {
        User user = getById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 验证状态值：0-禁用，1-启用
        if (status != 0 && status != 1) {
            throw new RuntimeException("无效的状态值，只能为0（禁用）或1（启用）");
        }

        user.setStatus(status);
        user.setUpdateTime(java.time.LocalDateTime.now());
        updateById(user);
    }

    @Override
    public com.baomidou.mybatisplus.core.metadata.IPage<com.health.domain.vo.AdminUserVO> getUserList(int pageNum, int pageSize) {
        // 查询用户列表（按创建时间倒序）
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<User> page = 
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User> queryWrapper = 
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        queryWrapper.orderByDesc("create_time");
        com.baomidou.mybatisplus.core.metadata.IPage<User> userPage = page(page, queryWrapper);

        // 转换为 AdminUserVO（排除密码等敏感信息）
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.health.domain.vo.AdminUserVO> voPage = 
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        voPage.setTotal(userPage.getTotal());
        voPage.setCurrent(userPage.getCurrent());
        voPage.setSize(userPage.getSize());
        
        List<com.health.domain.vo.AdminUserVO> voList = userPage.getRecords().stream()
            .map(user -> {
                com.health.domain.vo.AdminUserVO vo = new com.health.domain.vo.AdminUserVO();
                org.springframework.beans.BeanUtils.copyProperties(user, vo);
                return vo;
            })
            .collect(java.util.stream.Collectors.toList());
        
        voPage.setRecords(voList);
        return voPage;
    }
}