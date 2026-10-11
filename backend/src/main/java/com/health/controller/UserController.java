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
import com.health.service.AuthSessionService;
import com.health.domain.dto.AuthTokens;
import com.health.common.security.AuthCookies;
import com.health.common.exception.UnauthorizedException;
import org.springframework.security.web.csrf.CsrfToken;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import com.health.domain.entity.User;
import com.health.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

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

    @Autowired private AuthSessionService authSessions;
    @Autowired private AuthCookies authCookies;

    @GetMapping("/csrf")
    public Result<Map<String, String>> csrf(CsrfToken csrf) {
        return Result.success(Map.of("token", csrf.getToken(), "headerName", csrf.getHeaderName()));
    }

    @PostMapping("/login")
    @RateLimit(key = "login", maxRequests = 5, timeWindow = 1, timeUnit = TimeUnit.MINUTES, limitBy = LimitType.IP)
    public Result<AuthTokens> login(@RequestBody @Valid UserLoginDTO dto, HttpServletRequest request, HttpServletResponse response) {
        User user = userService.login(dto);
        String previous = authCookies.read(request, AuthCookies.REFRESH);
        if (previous != null) {
            try { authSessions.revokeRefresh(previous, AuthSessionService.WEB); }
            catch (UnauthorizedException ignored) { /* an old/revoked cookie cannot block a fresh login */ }
        }
        AuthTokens tokens = authSessions.issue(user, AuthSessionService.WEB);
        authCookies.writeRefresh(response, tokens.refreshToken(), tokens.refreshExpiresAt());
        return Result.success(tokens.forBrowser());
    }

    @PostMapping("/native-login")
    @RateLimit(key = "login", maxRequests = 5, timeWindow = 1, timeUnit = TimeUnit.MINUTES, limitBy = LimitType.IP)
    public Result<AuthTokens> nativeLogin(@RequestBody @Valid UserLoginDTO dto) {
        return Result.success(authSessions.issue(userService.login(dto), AuthSessionService.NATIVE));
    }

    @PostMapping("/refresh")
    public Result<AuthTokens> refresh(HttpServletRequest request, HttpServletResponse response) {
        AuthTokens tokens = authSessions.refresh(authCookies.read(request, AuthCookies.REFRESH), AuthSessionService.WEB);
        authCookies.writeRefresh(response, tokens.refreshToken(), tokens.refreshExpiresAt());
        return Result.success(tokens.forBrowser());
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
        @Override public String toString() { return "RefreshRequest[redacted]"; }
    }
    @PostMapping("/native-refresh")
    public Result<AuthTokens> nativeRefresh(@RequestBody @Valid RefreshRequest dto) {
        return Result.success(authSessions.refresh(dto.refreshToken(), AuthSessionService.NATIVE));
    }

    @PostMapping("/web-logout")
    public Result<Void> webLogout(HttpServletRequest request, HttpServletResponse response) {
        String refresh = authCookies.read(request, AuthCookies.REFRESH);
        if (refresh != null) {
            try { authSessions.revokeRefresh(refresh, AuthSessionService.WEB); }
            catch (UnauthorizedException ignored) { /* already expired/revoked */ }
        }
        authCookies.clearRefresh(response);
        return Result.success();
    }

    @PostMapping("/native-logout")
    public Result<Void> nativeLogout(@RequestBody @Valid RefreshRequest dto) {
        try { authSessions.revokeRefresh(dto.refreshToken(), AuthSessionService.NATIVE); }
        catch (UnauthorizedException ignored) { /* idempotent */ }
        return Result.success();
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
            authSessions.revokeAccess(token);
            tokenBlacklistService.addToBlacklist(token, jwtUtil.getExpirationDateFromToken(token));
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
     * 成功后撤销该用户全部设备会话，强制重新登录
     */
    @PutMapping("/password")
    @RateLimit(key = "change-password", maxRequests = 5, timeWindow = 1, timeUnit = TimeUnit.MINUTES, limitBy = LimitType.USER)
    public Result<Void> changePassword(@RequestBody @Valid ChangePasswordDTO dto, HttpServletRequest request) {
        Long currentUserId = securityUtil.getCurrentUserId();
        userService.changePassword(currentUserId, dto);

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
