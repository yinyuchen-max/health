package com.health.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.health.common.exception.BusinessException;
import com.health.common.utils.JwtUtil;
import com.health.domain.dto.WechatLoginDTO;
import com.health.domain.dto.WechatLoginResponseDTO;
import com.health.domain.entity.User;
import com.health.domain.vo.UserVO;
import com.health.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 微信登录服务
 * 流程：code → 微信 code2Session → openid → 绑定/创建用户 → 签发 JWT
 */
@Service
public class WechatAuthService {

    private static final Logger log = LoggerFactory.getLogger(WechatAuthService.class);
    private static final String CODE2SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session?appid={appid}&secret={secret}&js_code={code}&grant_type=authorization_code";

    @Value("${wechat.miniapp.app-id:}")
    private String appId;

    @Value("${wechat.miniapp.app-secret:}")
    private String appSecret;

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    public WechatAuthService(UserMapper userMapper, JwtUtil jwtUtil, ObjectMapper objectMapper, StringRedisTemplate redisTemplate) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 微信登录
     */
    public WechatLoginResponseDTO wechatLogin(WechatLoginDTO dto) {
        // 1. 调用微信 code2Session 接口获取 openid
        String openid = code2Session(dto.getCode());

        // 2. 根据 openid 查找用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getWechatOpenid, openid)
        );

        // 3. 如果用户不存在，自动创建
        if (user == null) {
            user = createWechatUser(openid);
        }

        // 4. 检查用户状态
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用");
        }

        // 5. 签发 JWT
        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());

        // 6. 构造响应
        WechatLoginResponseDTO response = new WechatLoginResponseDTO();
        response.setToken(token);

        UserVO userVO = new UserVO();
        userVO.setId(user.getId());
        userVO.setUsername(user.getUsername());
        userVO.setEmail(user.getEmail());
        userVO.setPhone(user.getPhone());
        userVO.setRole(user.getRole());
        userVO.setGender(user.getGender());
        userVO.setAge(user.getAge());
        userVO.setHeight(user.getHeight());
        userVO.setWeight(user.getWeight());
        userVO.setAvatar(user.getAvatar());
        response.setUserInfo(userVO);

        return response;
    }

    /**
     * 调用微信 code2Session 接口
     */
    private String code2Session(String code) {
        log.info("微信 code2Session 请求：appid={}, code={}", appId, code);
        
        String url = CODE2SESSION_URL
                .replace("{appid}", appId)
                .replace("{secret}", appSecret)
                .replace("{code}", code);

        try {
            // 微信 API 返回 text/plain，先用 String 接收再解析
            String responseStr = restTemplate.getForObject(url, String.class);
            log.info("微信 code2Session 响应：{}", responseStr);
            
            if (responseStr == null || responseStr.isBlank()) {
                log.error("微信 code2Session 失败：响应为空");
                throw new BusinessException("微信接口无响应，请检查网络连接");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(responseStr, Map.class);

            if (result.containsKey("errcode") && !Integer.valueOf(0).equals(result.get("errcode"))) {
                Integer errcode = (Integer) result.get("errcode");
                String errmsg = (String) result.get("errmsg");
                log.error("微信 code2Session 失败：errcode={}, errmsg={}", errcode, errmsg);
                throw new BusinessException("微信登录失败：" + errmsg + " (errcode=" + errcode + ")");
            }

            String openid = (String) result.get("openid");
            if (openid == null || openid.isBlank()) {
                log.error("微信 code2Session 响应中无 openid");
                throw new BusinessException("微信登录失败：响应中无 openid");
            }

            // 保存 session_key 到 Redis，用于后续解密微信运动数据
            String sessionKey = (String) result.get("session_key");
            if (sessionKey != null) {
                redisTemplate.opsForValue().set("wx_session:" + openid, sessionKey, 7, TimeUnit.DAYS);
            }
            
            return openid;
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("微信 code2Session 异常：{}", e.getMessage(), e);
            throw new BusinessException("微信登录异常：" + e.getMessage());
        }
    }

    /**
     * 创建微信用户
     */
    private User createWechatUser(String openid) {
        User user = new User();
        // 使用 openid 前 8 位作为默认用户名，避免重复
        user.setUsername("wx_" + openid.substring(0, Math.min(8, openid.length())));
        user.setPassword(""); // 微信用户无密码
        user.setWechatOpenid(openid);
        user.setRole("user");
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        user.setDeleted(0);

        userMapper.insert(user);
        log.info("创建微信用户成功：openid={}, username={}", openid, user.getUsername());
        return user;
    }

    /**
     * 解密微信运动数据
     */
    public Map<String, Object> decryptRunData(String openid, String encryptedData, String iv) {
        try {
            String sessionKey = redisTemplate.opsForValue().get("wx_session:" + openid);
            if (sessionKey == null) {
                throw new BusinessException("session_key 已过期，请重新登录");
            }

            byte[] dataByte = Base64.getDecoder().decode(encryptedData);
            byte[] keyByte = Base64.getDecoder().decode(sessionKey);
            byte[] ivByte = Base64.getDecoder().decode(iv);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(keyByte, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(ivByte);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

            byte[] resultByte = cipher.doFinal(dataByte);
            String result = new String(resultByte, "UTF-8");

            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = objectMapper.readValue(result, Map.class);
            return resultMap;
        } catch (Exception e) {
            log.error("解密微信运动数据失败：{}", e.getMessage(), e);
            throw new BusinessException("解密失败：" + e.getMessage());
        }
    }
}
