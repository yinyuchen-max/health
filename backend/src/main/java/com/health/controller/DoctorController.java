package com.health.controller;

import com.health.common.annotation.RateLimit;
import com.health.common.annotation.RateLimit.LimitType;
import com.health.common.utils.Result;
import com.health.common.utils.SecurityUtil;
import com.health.common.exception.ForbiddenException;
import com.health.domain.dto.DoctorFullRegisterDTO;
import com.health.domain.dto.DoctorRegisterDTO;
import com.health.domain.dto.UserRegisterDTO;
import com.health.domain.entity.DoctorAppointment;
import com.health.domain.vo.DoctorVO;
import com.health.service.DoctorAppointmentService;
import com.health.service.DoctorService;
import com.health.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/doctor")
public class DoctorController {

    private final DoctorService doctorService;
    private final DoctorAppointmentService doctorAppointmentService;
    private final UserService userService;
    private final SecurityUtil securityUtil;
    private final JdbcTemplate jdbcTemplate;

    public DoctorController(DoctorService doctorService, DoctorAppointmentService doctorAppointmentService, UserService userService, SecurityUtil securityUtil, JdbcTemplate jdbcTemplate) {
        this.doctorService = doctorService;
        this.doctorAppointmentService = doctorAppointmentService;
        this.userService = userService;
        this.securityUtil = securityUtil;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ensureTablesExist() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS doctor (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    real_name VARCHAR(50) NOT NULL,
                    hospital VARCHAR(100) NOT NULL,
                    department VARCHAR(50) NOT NULL,
                    title VARCHAR(30) DEFAULT NULL,
                    specialization VARCHAR(200) DEFAULT NULL,
                    license_number VARCHAR(50) NOT NULL,
                    introduction TEXT,
                    status ENUM('pending','approved','rejected') DEFAULT 'pending',
                    reject_reason VARCHAR(500) DEFAULT NULL,
                    approved_by BIGINT DEFAULT NULL,
                    approved_at DATETIME DEFAULT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    deleted TINYINT DEFAULT 0,
                    UNIQUE KEY uk_user_id (user_id),
                    INDEX idx_status (status),
                    INDEX idx_department (department),
                    CONSTRAINT doctor_ibfk_1 FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='医生信息表'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS doctor_message (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    doctor_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
                    sender_id BIGINT NOT NULL,
                    sender_type ENUM('user','doctor') NOT NULL,
                    content TEXT NOT NULL,
                    is_read TINYINT(1) DEFAULT 0,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    deleted TINYINT DEFAULT 0,
                    INDEX idx_doctor_user (doctor_id, user_id),
                    INDEX idx_sender (sender_id, sender_type),
                    INDEX idx_created_at (created_at),
                    CONSTRAINT doctor_message_ibfk_1 FOREIGN KEY (doctor_id) REFERENCES doctor(id) ON DELETE CASCADE,
                    CONSTRAINT doctor_message_ibfk_2 FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='医患对话消息表'
                """);
        // 添加预约表的 doctor_id 和 status 字段（IF NOT EXISTS 语法不适用 ALTER，用 try-catch）
        try {
            jdbcTemplate.execute("""
                    ALTER TABLE doctor_appointment
                        ADD COLUMN doctor_id BIGINT DEFAULT NULL COMMENT '医生ID' AFTER department,
                        ADD COLUMN status ENUM('pending','confirmed','completed','cancelled') DEFAULT 'pending' COMMENT '预约状态' AFTER doctor_id,
                        ADD INDEX idx_doctor_id (doctor_id)
                    """);
        } catch (Exception ignored) {
            // 字段已存在则忽略
        }
    }

    /**
     * 获取当前医生的预约列表（需验证医生身份）
     */
    @GetMapping("/my-appointments")
    public Result<List<DoctorAppointment>> getMyAppointments() {
        Long userId = securityUtil.getCurrentUserId();
        var doctorInfo = doctorService.getMyDoctorInfo(userId);
        if (doctorInfo == null || !"approved".equals(doctorInfo.getStatus())) {
            throw new ForbiddenException("您不是已认证的医生");
        }
        QueryWrapper<DoctorAppointment> qw = new QueryWrapper<>();
        qw.eq("doctor_id", doctorInfo.getId());
        qw.orderByDesc("appointment_time");
        return Result.success(doctorAppointmentService.list(qw));
    }

    // ============ 公开接口 ============

    /**
     * 医生完整注册（无需登录）：创建账号 + 提交医生申请
     */
    @PostMapping("/full-register")
    @RateLimit(key = "doctor-full-register", maxRequests = 2, timeWindow = 10, timeUnit = TimeUnit.MINUTES, limitBy = LimitType.IP)
    public Result<DoctorVO> fullRegister(@RequestBody @Valid DoctorFullRegisterDTO dto) {
        // 1. 创建用户账号
        UserRegisterDTO userDto = new UserRegisterDTO();
        userDto.setUsername(dto.getUsername());
        userDto.setPassword(dto.getPassword());
        userDto.setEmail(dto.getEmail());
        userDto.setPhone(dto.getPhone());
        userService.register(userDto);

        // 2. 获取新创建用户的ID
        var user = userService.getUserInfoByUsername(dto.getUsername());
        Long userId = user.getId();

        // 3. 创建医生申请
        DoctorRegisterDTO doctorDto = new DoctorRegisterDTO();
        doctorDto.setRealName(dto.getRealName());
        doctorDto.setHospital(dto.getHospital());
        doctorDto.setDepartment(dto.getDepartment());
        doctorDto.setTitle(dto.getTitle());
        doctorDto.setSpecialization(dto.getSpecialization());
        doctorDto.setLicenseNumber(dto.getLicenseNumber());
        doctorDto.setIntroduction(dto.getIntroduction());
        DoctorVO doctorVO = doctorService.registerAsDoctor(userId, doctorDto);

        return Result.success(doctorVO);
    }

    /**
     * 获取所有已审核通过的医生列表
     */
    @GetMapping("/list")
    public Result<List<DoctorVO>> getDoctorList(@RequestParam(required = false) String department) {
        List<DoctorVO> doctors;
        if (department != null && !department.isBlank()) {
            doctors = doctorService.getDoctorListByDepartment(department);
        } else {
            doctors = doctorService.getApprovedDoctorList();
        }
        return Result.success(doctors);
    }

    // ============ 医生注册 ============

    /**
     * 注册成为医生（需登录）
     */
    @PostMapping("/register")
    @RateLimit(key = "doctor-register", maxRequests = 2, timeWindow = 10, timeUnit = TimeUnit.MINUTES, limitBy = LimitType.USER)
    public Result<DoctorVO> registerAsDoctor(@RequestBody @Valid DoctorRegisterDTO dto) {
        Long userId = securityUtil.getCurrentUserId();
        DoctorVO doctorVO = doctorService.registerAsDoctor(userId, dto);
        return Result.success(doctorVO);
    }

    /**
     * 查看我的医生信息/审核状态
     */
    @GetMapping("/my-info")
    public Result<DoctorVO> getMyDoctorInfo() {
        Long userId = securityUtil.getCurrentUserId();
        DoctorVO info = doctorService.getMyDoctorInfo(userId);
        return Result.success(info);
    }

    /**
     * 检查当前用户是否是已审核的医生
     */
    @GetMapping("/check")
    public Result<Boolean> checkIsDoctor() {
        Long userId = securityUtil.getCurrentUserId();
        return Result.success(doctorService.isApprovedDoctor(userId));
    }

    // ============ 管理员接口 ============

    /**
     * 获取待审核的医生列表（仅管理员）
     */
    @GetMapping("/admin/pending")
    public Result<List<DoctorVO>> getPendingDoctors() {
        securityUtil.requireAdmin();
        return Result.success(doctorService.getPendingDoctorList());
    }

    /**
     * 审核通过医生（仅管理员）
     */
    @PostMapping("/admin/{id}/approve")
    public Result<Void> approveDoctor(@PathVariable Long id) {
        securityUtil.requireAdmin();
        Long adminUserId = securityUtil.getCurrentUserId();
        doctorService.approveDoctor(id, adminUserId);
        return Result.success();
    }

    /**
     * 驳回医生申请（仅管理员）
     */
    @PostMapping("/admin/{id}/reject")
    public Result<Void> rejectDoctor(@PathVariable Long id, @RequestBody(required = false) String reason) {
        securityUtil.requireAdmin();
        Long adminUserId = securityUtil.getCurrentUserId();
        doctorService.rejectDoctor(id, adminUserId, reason != null ? reason : "");
        return Result.success();
    }
}
