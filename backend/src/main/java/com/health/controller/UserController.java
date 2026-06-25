package com.health.controller;

import com.health.common.utils.JwtUtil;
import com.health.common.utils.Result;
import com.health.domain.dto.UserLoginDTO;
import com.health.domain.dto.UserRegisterDTO;
import com.health.domain.vo.UserVO;
import com.health.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public Result<String> login(@RequestBody @Valid UserLoginDTO userLoginDTO) {
        String token = userService.login(userLoginDTO);
        return Result.success(token);
    }

    @PostMapping("/register")
    public Result<Void> register(@RequestBody @Valid UserRegisterDTO userRegisterDTO) {
        userService.register(userRegisterDTO);
        return Result.success();
    }

    @GetMapping("/info")
    public Result getInfo(HttpServletRequest request) {
        // 从请求头中获取token
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);
            try {
                // 从token中获取用户名
                String username = jwtUtil.extractUsername(token);
                // 根据用户名获取用户信息
                var userInfo = userService.getUserInfoByUsername(username);
                return Result.success(userInfo);
            } catch (Exception e) {
                return Result.failed("获取用户信息失败: " + e.getMessage());
            }
        }
        return Result.failed("未提供认证令牌");
    }

    @PutMapping("/info")
    public Result updateUserInfo(@RequestBody UserVO userVO) {
        userService.updateUserInfo(userVO);
        return Result.success();
    }

    @GetMapping("/status/{userId}")
    public Result<Integer> getUserStatus(@PathVariable Long userId) {
        try {
            Integer status = userService.getUserStatus(userId);
            return Result.success(status);
        } catch (Exception e) {
            return Result.failed(e.getMessage());
        }
    }

    @PutMapping("/status/{userId}")
    public Result updateUserStatus(@PathVariable Long userId, @RequestParam Integer status) {
        try {
            userService.updateUserStatus(userId, status);
            String statusText = status == 1 ? "启用" : "禁用";
            return Result.success("用户状态已更新为：" + statusText);
        } catch (Exception e) {
            return Result.failed(e.getMessage());
        }
    }

    /**
     * 管理员专用：启用或禁用用户账号
     * @param request HTTP请求，用于获取token验证权限
     * @param userId 要操作的用户ID
     * @param status 状态值：0-禁用，1-启用
     * @return 操作结果
     */
    @PutMapping("/admin/toggle-status/{userId}")
    public Result adminToggleUserStatus(HttpServletRequest request,
                                        @PathVariable Long userId,
                                        @RequestParam Integer status) {
        // 从请求头中获取token
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Result.failed("未提供认证令牌");
        }

        String token = authorization.substring(7);
        try {
            // 验证是否为管理员
            if (!jwtUtil.isAdmin(token)) {
                return Result.failed("权限不足，仅管理员可执行此操作");
            }

            // 验证状态值
            if (status != 0 && status != 1) {
                return Result.failed("无效的状态值，只能为0（禁用）或1（启用）");
            }

            // 执行状态更新
            userService.updateUserStatus(userId, status);
            String statusText = status == 1 ? "启用" : "禁用";
            String operatorUsername = jwtUtil.extractUsername(token);
            return Result.success("管理员 [" + operatorUsername + "] 已将用户状态更新为：" + statusText);
        } catch (Exception e) {
            return Result.failed("操作失败: " + e.getMessage());
        }
    }

    /**
     * 管理员专用：获取用户列表
     * @param request HTTP请求，用于获取token验证权限
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @return 用户列表分页数据
     */
    @GetMapping("/admin/list")
    public Result getUserList(HttpServletRequest request,
                              @RequestParam(defaultValue = "1") int pageNum,
                              @RequestParam(defaultValue = "10") int pageSize) {
        // 从请求头中获取token
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Result.failed("未提供认证令牌");
        }

        String token = authorization.substring(7);
        try {
            // 验证是否为管理员
            if (!jwtUtil.isAdmin(token)) {
                return Result.failed("权限不足，仅管理员可查看用户列表");
            }

            // 获取用户列表
            var page = userService.getUserList(pageNum, pageSize);
            return Result.success(page);
        } catch (Exception e) {
            return Result.failed("获取用户列表失败: " + e.getMessage());
        }
    }
}