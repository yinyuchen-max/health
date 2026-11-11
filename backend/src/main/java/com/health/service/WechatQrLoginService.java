package com.health.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.common.exception.BusinessException;
import com.health.common.security.TokenDigests;
import com.health.domain.entity.User;
import com.health.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WechatQrLoginService {
    private static final Logger log = LoggerFactory.getLogger(WechatQrLoginService.class);
    private static final String LOGIN_PAGE = "pages/web-login/web-login";
    private static final int TTL_SECONDS = 180;
    private final StringRedisTemplate redis;
    private final WechatAccessTokenService accessTokens;
    private final UserMapper users;
    private final ObjectMapper json;
    private final RestTemplate http;
    private final DefaultRedisScript<String> script = new DefaultRedisScript<>();
    @Value("${wechat.miniapp.web-login-env:release}") private String envVersion = "release";

    public WechatQrLoginService(StringRedisTemplate redis, WechatAccessTokenService accessTokens,
                               UserMapper users, ObjectMapper json) {
        this.redis = redis; this.accessTokens = accessTokens; this.users = users; this.json = json;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000); factory.setReadTimeout(10000);
        this.http = new RestTemplate(factory);
        script.setLocation(new ClassPathResource("auth/wechat-qr-session.lua"));
        script.setResultType(String.class);
    }

    public record QrSession(String ticket, String proof, String qrCode, long expiresAt) {
        @Override public String toString() { return "QrSession[redacted]"; }
    }

    public QrSession create() {
        if (!List.of("release", "trial", "develop").contains(envVersion))
            throw new BusinessException("微信扫码登录环境配置错误");
        String ticket = UUID.randomUUID().toString().replace("-", ""); // scene 最多 32 字符
        String proof = TokenDigests.randomToken(); // 不放入小程序码，只交给发起登录的浏览器
        String accessToken = accessTokens.getAccessToken();
        if (accessToken == null || accessToken.isBlank()) throw new BusinessException("微信扫码登录尚未就绪，请稍后重试");
        byte[] image;
        String mime;
        try {
            image = http.postForObject("https://api.weixin.qq.com/wxa/getwxacodeunlimit?access_token={token}",
                    Map.of("scene", ticket, "page", LOGIN_PAGE, "width", 360,
                            "env_version", envVersion, "check_path", "release".equals(envVersion)), byte[].class, accessToken);
            boolean png = image != null && image.length >= 8 && image[0] == (byte) 0x89 && image[1] == 'P' && image[2] == 'N' && image[3] == 'G';
            boolean jpeg = image != null && image.length >= 3 && image[0] == (byte) 0xff && image[1] == (byte) 0xd8 && image[2] == (byte) 0xff;
            if (!png && !jpeg) {
                int errorCode = -1;
                if (image != null) {
                    var error = json.readTree(image);
                    errorCode = error.path("errcode").asInt(-1);
                }
                log.warn("微信小程序码生成失败：errcode={}, env={}, page={}", errorCode, envVersion, LOGIN_PAGE);
                if (errorCode == 40001 || errorCode == 40014 || errorCode == 42001) {
                    accessTokens.clearAccessToken();
                    throw new BusinessException("微信登录凭据已失效（" + errorCode + "），请刷新二维码重试");
                }
                if (errorCode == 41030) {
                    if ("release".equals(envVersion)) {
                        log.warn("正式版未包含扫码确认页。请发布该页面；本地联调可配置 WECHAT_WEB_LOGIN_ENV=develop 后重启后端");
                        throw new BusinessException("正式版扫码确认页面尚未发布（41030），请联系管理员；本地联调请使用开发版配置");
                    }
                    throw new BusinessException("扫码确认页面不可用（41030），请确认已上传包含该页面的小程序开发版或体验版");
                }
                throw new BusinessException("微信小程序码生成失败（错误码 " + errorCode + "），请联系管理员检查微信接口配置");
            }
            mime = png ? "image/png" : "image/jpeg";
        } catch (BusinessException e) { throw e; }
        catch (Exception e) { throw new BusinessException("微信扫码登录暂时不可用，请稍后重试"); }
        String result = execute(ticket, "create", TokenDigests.sha256(proof), Integer.toString(TTL_SECONDS));
        if (!"PENDING".equals(result)) throw new BusinessException("创建登录二维码失败，请重试");
        return new QrSession(ticket, proof, "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(image),
                System.currentTimeMillis() + TTL_SECONDS * 1000L);
    }

    public String status(String ticket, String proof) {
        validate(ticket, proof);
        String result = execute(ticket, "status", TokenDigests.sha256(proof), "");
        if ("INVALID".equals(result) || result == null) throw new BusinessException("登录二维码验证失败，请刷新二维码");
        return result;
    }

    public void confirm(String ticket, Long userId) {
        validateTicket(ticket);
        User user = users.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus()) || user.getWechatOpenid() == null)
            throw new BusinessException("请先使用微信登录小程序");
        if (!"CONFIRMED".equals(execute(ticket, "confirm", "", userId.toString())))
            throw new BusinessException("二维码已过期或已使用，请在网页刷新后重新扫码");
    }

    public User consume(String ticket, String proof) {
        validate(ticket, proof);
        String result = execute(ticket, "consume", TokenDigests.sha256(proof), "");
        if (result == null || !result.matches("[0-9]+")) throw new BusinessException("二维码未确认、已过期或已使用，请重新扫码");
        User user = users.selectById(Long.valueOf(result));
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) throw new BusinessException("账号不可用");
        return user;
    }

    private String execute(String ticket, String action, String proofHash, String value) {
        return redis.execute(script, List.of("auth:wechat-qr:" + ticket), action, proofHash, value);
    }
    private void validate(String ticket, String proof) {
        validateTicket(ticket);
        if (proof == null || !proof.matches("[A-Za-z0-9_-]{43}")) throw new BusinessException("登录二维码验证失败");
    }
    private void validateTicket(String ticket) {
        if (ticket == null || !ticket.matches("[a-f0-9]{32}")) throw new BusinessException("无效的登录二维码");
    }
}
