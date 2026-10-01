package com.health.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.health.common.exception.BusinessException;
import com.health.common.enums.UserRole;
import com.health.common.enums.UserStatus;
import com.health.common.utils.JwtUtil;
import com.health.domain.dto.ChangePasswordDTO;
import com.health.domain.dto.UserLoginDTO;
import com.health.domain.dto.UserRegisterDTO;
import com.health.domain.entity.User;
import com.health.domain.vo.AdminUserVO;
import com.health.domain.vo.UserVO;
import com.health.mapper.UserMapper;
import com.health.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    /** 登录失败计数 Redis key 前缀（按用户名维度，防止仅靠 IP 限流被绕过后的无限暴力破解） */
    private static final String LOGIN_FAIL_KEY_PREFIX = "login:fail:";
    /** 窗口期内最大失败次数，达到后临时锁定 */
    private static final int MAX_LOGIN_FAILURES = 5;
    /** 锁定时长（自最后一次失败起算） */
    private static final Duration LOGIN_LOCK_DURATION = Duration.ofMinutes(15);

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public String login(UserLoginDTO userLoginDTO) {
        String failKey = LOGIN_FAIL_KEY_PREFIX + userLoginDTO.getUsername();
        assertNotLocked(failKey);

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", userLoginDTO.getUsername());
        User user = getOne(queryWrapper);
        if (user == null || !passwordEncoder.matches(userLoginDTO.getPassword(), user.getPassword())) {
            recordLoginFailure(failKey);
            throw new BusinessException("用户名或密码错误");
        }

        if (user.getStatus() == UserStatus.DISABLED.getCode()) {
            throw new BusinessException("账号已被禁用");
        }

        clearLoginFailures(failKey);
        return jwtUtil.generateToken(user.getUsername(), user.getRole());
    }

    /**
     * 失败次数达到上限时拒绝登录，直到锁定窗口过期
     */
    private void assertNotLocked(String failKey) {
        String fails;
        try {
            fails = stringRedisTemplate.opsForValue().get(failKey);
        } catch (Exception e) {
            // Redis 异常时放行（fail-open），与限流切面的容错策略一致，不阻断正常登录
            log.warn("读取登录失败计数异常，跳过锁定检查: {}", e.getMessage());
            return;
        }
        if (fails == null) {
            return;
        }
        int count;
        try {
            count = Integer.parseInt(fails);
        } catch (NumberFormatException e) {
            return;
        }
        if (count >= MAX_LOGIN_FAILURES) {
            long remainMinutes = LOGIN_LOCK_DURATION.toMinutes();
            try {
                Long ttl = stringRedisTemplate.getExpire(failKey, TimeUnit.SECONDS);
                if (ttl != null && ttl > 0) {
                    remainMinutes = Math.max(1, (ttl + 59) / 60);
                }
            } catch (Exception e) {
                log.debug("获取Redis TTL失败，使用默认锁定时间: {}", e.getMessage());
            }
            throw new BusinessException("密码错误次数过多，账号已临时锁定，请 " + remainMinutes + " 分钟后重试");
        }
    }

    private void recordLoginFailure(String failKey) {
        try {
            Long count = stringRedisTemplate.opsForValue().increment(failKey);
            stringRedisTemplate.expire(failKey, LOGIN_LOCK_DURATION);
            if (count != null && count >= MAX_LOGIN_FAILURES) {
                log.warn("登录失败次数达到上限，账号临时锁定 {} 分钟: key={}", LOGIN_LOCK_DURATION.toMinutes(), failKey);
            }
        } catch (Exception e) {
            log.warn("记录登录失败次数异常: {}", e.getMessage());
        }
    }

    private void clearLoginFailures(String failKey) {
        try {
            stringRedisTemplate.delete(failKey);
        } catch (Exception e) {
            log.warn("清除登录失败计数异常: {}", e.getMessage());
        }
    }

    @Override
    public Long register(UserRegisterDTO userRegisterDTO) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", userRegisterDTO.getUsername());
        if (getOne(queryWrapper) != null) {
            throw new BusinessException("用户名已存在");
        }
        User user = new User();
        BeanUtils.copyProperties(userRegisterDTO, user);
        // 密码加密存储
        user.setPassword(passwordEncoder.encode(userRegisterDTO.getPassword()));
        user.setRole(UserRole.USER.getCode());
        user.setStatus(UserStatus.ENABLED.getCode());
        user.setCreateTime(java.time.LocalDateTime.now());
        user.setUpdateTime(java.time.LocalDateTime.now());
        user.setDeleted(0);
        save(user);
        // MyBatis-Plus 在 insert 后回填自增主键，直接返回给调用方
        return user.getId();
    }

    @Override
    @Cacheable(value = "user:info", key = "'id:' + #userId")
    public UserVO getUserInfo(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    @Cacheable(value = "user:info", key = "'username:' + #username")
    public UserVO getUserInfoByUsername(String username) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        User user = getOne(queryWrapper);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    @CacheEvict(value = "user:info", allEntries = true)
    public void updateUserInfo(UserVO userVO) {
        User user = getById(userVO.getId());
        if (user == null) {
            throw new BusinessException("用户不存在");
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
            throw new BusinessException("用户不存在");
        }
        return user.getStatus();
    }

    @Override
    @CacheEvict(value = "user:info", allEntries = true)
    public void updateUserStatus(Long userId, Integer status) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 验证状态值：0-禁用，1-启用
        if (status != UserStatus.DISABLED.getCode() && status != UserStatus.ENABLED.getCode()) {
            throw new BusinessException("无效的状态值，只能为0（禁用）或1（启用）");
        }

        user.setStatus(status);
        user.setUpdateTime(java.time.LocalDateTime.now());
        updateById(user);
    }

    @Override
    public void changePassword(Long userId, ChangePasswordDTO dto) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 校验旧密码是否正确（后端不信任前端，自行比对）
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException("旧密码不正确");
        }

        // 新密码不能与旧密码相同（明文比对即可，防止无意义修改）
        if (passwordEncoder.matches(dto.getNewPassword(), user.getPassword())) {
            throw new BusinessException("新密码不能与旧密码相同");
        }

        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        user.setUpdateTime(java.time.LocalDateTime.now());
        updateById(user);
    }

    @Override
    public IPage<AdminUserVO> getUserList(int pageNum, int pageSize) {
        // 查询用户列表（按创建时间倒序）
        Page<User> page = new Page<>(pageNum, pageSize);
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("create_time");
        IPage<User> userPage = page(page, queryWrapper);

        // 转换为 AdminUserVO（排除密码等敏感信息）
        Page<AdminUserVO> voPage = new Page<>(pageNum, pageSize);
        voPage.setTotal(userPage.getTotal());
        voPage.setCurrent(userPage.getCurrent());
        voPage.setSize(userPage.getSize());
        
        List<AdminUserVO> voList = userPage.getRecords().stream()
            .map(user -> {
                AdminUserVO vo = new AdminUserVO();
                BeanUtils.copyProperties(user, vo);
                return vo;
            })
            .toList();
        
        voPage.setRecords(voList);
        return voPage;
    }
}