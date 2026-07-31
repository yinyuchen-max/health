package com.health.controller;

import com.health.common.annotation.RateLimit;
import com.health.common.annotation.RateLimit.LimitType;
import com.health.common.utils.Result;
import com.health.common.utils.SecurityUtil;
import com.health.domain.dto.UserLoginDTO;
import com.health.domain.dto.UserRegisterDTO;
import com.health.domain.vo.UserVO;
import com.health.service.TokenBlacklistService;
import com.health.service.UserService;
import com.health.common.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Autowired
    private SecurityUtil securityUtil;

    @PostMapping("/login")
    @RateLimit(key = "login", maxRequests = 5, timeWindow = 1, timeUnit = TimeUnit.MINUTES, limitBy = LimitType.IP)
    public Result<String> login(@RequestBody @Valid UserLoginDTO userLoginDTO) {
        String token = userService.login(userLoginDTO);
        return Result.success(token);
    }

    @PostMapping("/register")
    @RateLimit(key = "register", maxRequests = 3, timeWindow = 1, timeUnit = TimeUnit.MINUTES, limitBy = LimitType.IP)
    public Result<Void> register(@RequestBody @Valid UserRegisterDTO userRegisterDTO) {
        userService.register(userRegisterDTO);
        return Result.success();
    }

    /**
     * 用户登出接口
     * 将当前 token 加入黑名单
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);
            try {
                Date expiration = jwtUtil.getExpirationDateFromToken(token);
                tokenBlacklistService.addToBlacklist(token, expiration);
            } catch (Exception ignored) {
                // Token 解析失败也返回成功
            }
        }
        return Result.success();
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/info")
    public Result<UserVO> getUserInfo() {
        Long currentUserId = securityUtil.getCurrentUserId();
        UserVO userInfo = userService.getUserInfo(currentUserId);
        return Result.success(userInfo);
    }

    /**
     * 更新当前登录用户信息
     */
    @PutMapping("/info")
    public Result<Void> updateUserInfo(@RequestBody UserVO userVO) {
        Long currentUserId = securityUtil.getCurrentUserId();
        // 强制使用当前用户ID，防止修改他人信息
        userVO.setId(currentUserId);
        userService.updateUserInfo(userVO);
        return Result.success();
    }

    /**
     * 获取用户状态（仅管理员可用）
     */
    @GetMapping("/status/{userId}")
    public Result<Integer> getUserStatus(@PathVariable Long userId) {
        securityUtil.requireAdmin();
        Integer status = userService.getUserStatus(userId);
        return Result.success(status);
    }

    /**
     * 更新用户状态（仅管理员可用）
     */
    @PutMapping("/status/{userId}")
    public Result<String> updateUserStatus(@PathVariable Long userId, @RequestParam Integer status) {
        securityUtil.requireAdmin();
        userService.updateUserStatus(userId, status);
        String statusText = status == 1 ? "启用" : "禁用";
        return Result.success("用户状态已更新为：" + statusText);
    }

    /**
     * 管理员专用：启用或禁用用户账号
     */
    @PutMapping("/admin/toggle-status/{userId}")
    public Result<String> adminToggleUserStatus(@PathVariable Long userId, @RequestParam Integer status) {
        // SecurityConfig 已配置 /api/user/admin/** 需要 ROLE_ADMIN
        if (status != 0 && status != 1) {
            return Result.failed("无效的状态值，只能为0（禁用）或1（启用）");
        }
        userService.updateUserStatus(userId, status);
        String statusText = status == 1 ? "启用" : "禁用";
        return Result.success("用户状态已更新为：" + statusText);
    }

    /**
     * 管理员专用：获取用户列表
     */
    @GetMapping("/admin/list")
    public Result<?> getUserList(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "10") int pageSize) {
        // SecurityConfig 已配置 /api/user/admin/** 需要 ROLE_ADMIN
        var page = userService.getUserList(pageNum, pageSize);
        return Result.success(page);
    }
}
