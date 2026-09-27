package com.health.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.health.domain.dto.ChangePasswordDTO;
import com.health.domain.dto.UserLoginDTO;
import com.health.domain.dto.UserRegisterDTO;
import com.health.domain.entity.User;
import com.health.domain.vo.UserVO;

public interface UserService extends IService<User> {

    String login(UserLoginDTO userLoginDTO);

    /**
     * 注册新用户
     * @return 新创建用户的 ID（由数据库自增主键回填，避免调用方按 username 反查产生并发竞态）
     */
    Long register(UserRegisterDTO userRegisterDTO);

    UserVO getUserInfo(Long userId);

    UserVO getUserInfoByUsername(String username);

    void updateUserInfo(UserVO userVO);

    void changePassword(Long userId, ChangePasswordDTO dto);

    Integer getUserStatus(Long userId);

    void updateUserStatus(Long userId, Integer status);

    com.baomidou.mybatisplus.core.metadata.IPage<com.health.domain.vo.AdminUserVO> getUserList(int pageNum, int pageSize);
}