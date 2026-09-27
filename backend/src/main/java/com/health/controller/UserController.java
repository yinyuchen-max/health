package com.health.controller;

import com.health.common.annotation.RateLimit;
import com.health.common.annotation.RateLimit.LimitType;
import com.health.common.utils.Result;
import com.health.common.utils.SecurityUtil;
import com.health.domain.dto.ChangePasswordDTO;
import com.health.domain.dto.UserLoginDTO;
import com.health.domain.dto.UserRegisterDTO;
import com.health.domain.dto.WechatLoginDTO;
import com.health.domain.dto.WechatLoginResponseDTO;
import com.health.domain.dto.WechatRunDataDTO;
import com.health.domain.vo.UserVO;
import com.health.service.TokenBlacklistService;
import com.health.service.UserService;
import com.health.service.WechatAuthService;
import com.health.common.utils.JwtUtil;
import com.health.domain.entity.User;
import com.health.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.Map;
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

    @Autowired
    private WechatAuthService wechatAuthService;

    @Autowired
    private UserMapper userMapper;

    @PostMapping("/login")
    @RateLimit(key = "login", maxRequests = 5, timeWindow = 1, timeUnit = TimeUnit.MINUTES, limitBy = LimitType.IP)
    public Result<String> login(@RequestBody @Valid UserLoginDTO userLoginDTO) {
        String token = userService.login(userLoginDTO);
        return Result.success(token);
    }

    /**
     * 微信小程序登录
     * 前端传 code，后端调微信 code2Session 换 openid，绑定/创建用户后签发 JWT
     */
    @PostMapping("/wechat-login")
    @RateLimit(key = "wechat-login", maxRequests = 10, timeWindow = 1, timeUnit = TimeUnit.MINUTES, limitBy = LimitType.IP)
    public Result<WechatLoginResponseDTO> wechatLogin(@RequestBody @Valid WechatLoginDTO dto) {
        WechatLoginResponseDTO response = wechatAuthService.wechatLogin(dto);
        return Result.success(response);
    }

    /**
     * 解密微信运动数据
     */
    @PostMapping("/wechat/decrypt-run-data")
    public Result<Map<String, Object>> decryptRunData(@RequestBody WechatRunDataDTO dto) {
        Long currentUserId = securityUtil.getCurrentUserId();
        User user = userMapper.selectById(currentUserId);
        if (user == null || user.getWechatOpenid() == null) {
            return Result.failed("未绑定微信账号");
        }
        Map<String, Object> data = wechatAuthService.decryptRunData(user.getWechatOpenid(), dto.getEncryptedData(), dto.getIv());
        return Result.success(data);
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
     * 修改当前登录用户的密码
     * 成功后将当前 token 加入黑名单，强制重新登录，防止旧 token 继续使用
     */
    @PutMapping("/password")
    @RateLimit(key = "change-password", maxRequests = 5, timeWindow = 1, timeUnit = TimeUnit.MINUTES, limitBy = LimitType.USER)
    public Result<Void> changePassword(@RequestBody @Valid ChangePasswordDTO dto, HttpServletRequest request) {
        Long currentUserId = securityUtil.getCurrentUserId();
        userService.changePassword(currentUserId, dto);

        // 密码修改成功后，当前 token 失效，要求用户重新登录
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);
            try {
                Date expiration = jwtUtil.getExpirationDateFromToken(token);
                tokenBlacklistService.addToBlacklist(token, expiration);
            } catch (Exception ignored) {
                // Token 解析失败不影响密码修改结果
            }
        }
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
