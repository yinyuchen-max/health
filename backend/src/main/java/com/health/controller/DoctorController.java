package com.health.controller;

import com.health.common.annotation.RateLimit;
import com.health.common.annotation.RateLimit.LimitType;
import com.health.common.utils.Result;
import com.health.common.utils.SecurityUtil;
import com.health.common.exception.ForbiddenException;
import com.health.domain.dto.DoctorFullRegisterDTO;
import com.health.domain.dto.DoctorRegisterDTO;
import com.health.domain.entity.DoctorAppointment;
import com.health.domain.vo.DoctorVO;
import com.health.service.DoctorAppointmentService;
import com.health.service.DoctorService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/doctor")
public class DoctorController {

    private final DoctorService doctorService;
    private final DoctorAppointmentService doctorAppointmentService;
    private final SecurityUtil securityUtil;

    public DoctorController(DoctorService doctorService, DoctorAppointmentService doctorAppointmentService, SecurityUtil securityUtil) {
        this.doctorService = doctorService;
        this.doctorAppointmentService = doctorAppointmentService;
        this.securityUtil = securityUtil;
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

    /**
     * 患者创建预约
     */
    @PostMapping("/appointment")
    @RateLimit(key = "create-appointment", maxRequests = 10, timeWindow = 1, timeUnit = TimeUnit.MINUTES, limitBy = LimitType.USER)
    public Result<DoctorAppointment> createAppointment(@RequestBody DoctorAppointment appointment) {
        Long userId = securityUtil.getCurrentUserId();
        appointment.setUserId(userId);
        appointment.setStatus("pending");
        doctorAppointmentService.save(appointment);
        return Result.success(appointment);
    }

    /**
     * 获取当前用户的预约列表
     */
    @GetMapping("/my-patient-appointments")
    public Result<List<DoctorAppointment>> getMyPatientAppointments() {
        Long userId = securityUtil.getCurrentUserId();
        QueryWrapper<DoctorAppointment> qw = new QueryWrapper<>();
        qw.eq("user_id", userId);
        qw.orderByDesc("appointment_time");
        return Result.success(doctorAppointmentService.list(qw));
    }

    // ============ 公开接口 ============

    /**
     * 医生完整注册（无需登录）：创建账号 + 提交医生申请
     * 编排下沉到 DoctorService.fullRegister 的单个事务中，
     * 任一步失败整体回滚，不会留下无申请的孤儿账号
     */
    @PostMapping("/full-register")
    @RateLimit(key = "doctor-full-register", maxRequests = 2, timeWindow = 10, timeUnit = TimeUnit.MINUTES, limitBy = LimitType.IP)
    public Result<DoctorVO> fullRegister(@RequestBody @Valid DoctorFullRegisterDTO dto) {
        return Result.success(doctorService.fullRegister(dto));
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
