package com.health.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.health.domain.dto.UserLoginDTO;
import com.health.domain.dto.UserRegisterDTO;
import com.health.domain.entity.User;
import com.health.domain.vo.UserVO;

public interface UserService extends IService<User> {

    String login(UserLoginDTO userLoginDTO);

    void register(UserRegisterDTO userRegisterDTO);

    UserVO getUserInfo(Long userId);

    UserVO getUserInfoByUsername(String username);

    void updateUserInfo(UserVO userVO);

    Integer getUserStatus(Long userId);

    void updateUserStatus(Long userId, Integer status);

    com.baomidou.mybatisplus.core.metadata.IPage<com.health.domain.vo.AdminUserVO> getUserList(int pageNum, int pageSize);
}