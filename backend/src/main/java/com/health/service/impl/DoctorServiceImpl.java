package com.health.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.health.domain.dto.DoctorRegisterDTO;
import com.health.domain.entity.Doctor;
import com.health.domain.entity.DoctorAppointment;
import com.health.domain.entity.User;
import com.health.domain.vo.DoctorVO;
import com.health.mapper.DoctorMapper;
import com.health.mapper.UserMapper;
import com.health.service.DoctorAppointmentService;
import com.health.service.DoctorService;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class DoctorServiceImpl extends ServiceImpl<DoctorMapper, Doctor>
        implements DoctorService {

    private static final Logger log = LoggerFactory.getLogger(DoctorServiceImpl.class);

    private final UserMapper userMapper;
    private final JavaMailSender mailSender;
    private final DoctorAppointmentService doctorAppointmentService;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    public DoctorServiceImpl(UserMapper userMapper, JavaMailSender mailSender,
                             DoctorAppointmentService doctorAppointmentService) {
        this.userMapper = userMapper;
        this.mailSender = mailSender;
        this.doctorAppointmentService = doctorAppointmentService;
    }

    @Override
    public DoctorVO registerAsDoctor(Long userId, DoctorRegisterDTO dto) {
        // 检查是否已提交过申请
        QueryWrapper<Doctor> qw = new QueryWrapper<>();
        qw.eq("user_id", userId);
        Doctor existing = getOne(qw);
        if (existing != null) {
            if ("approved".equals(existing.getStatus())) {
                throw new RuntimeException("您已经是注册医生，无需重复申请");
            }
            if ("pending".equals(existing.getStatus())) {
                throw new RuntimeException("您的医生申请正在审核中，请耐心等待");
            }
            if ("rejected".equals(existing.getStatus())) {
                // 允许重新提交，更新原有记录
                existing.setRealName(dto.getRealName());
                existing.setHospital(dto.getHospital());
                existing.setDepartment(dto.getDepartment());
                existing.setTitle(dto.getTitle());
                existing.setSpecialization(dto.getSpecialization());
                existing.setLicenseNumber(dto.getLicenseNumber());
                existing.setIntroduction(dto.getIntroduction());
                existing.setStatus("pending");
                existing.setRejectReason(null);
                existing.setApprovedBy(null);
                existing.setApprovedAt(null);
                updateById(existing);
                return toVO(existing);
            }
        }

        Doctor doctor = new Doctor();
        BeanUtils.copyProperties(dto, doctor);
        doctor.setUserId(userId);
        doctor.setStatus("pending");
        doctor.setCreatedAt(LocalDateTime.now());
        doctor.setUpdatedAt(LocalDateTime.now());
        doctor.setDeleted(0);
        save(doctor);
        return toVO(doctor);
    }

    @Override
    public DoctorVO getMyDoctorInfo(Long userId) {
        QueryWrapper<Doctor> qw = new QueryWrapper<>();
        qw.eq("user_id", userId);
        Doctor doctor = getOne(qw);
        if (doctor == null) {
            return null;
        }
        return toVO(doctor);
    }

    @Override
    public List<DoctorVO> getApprovedDoctorList() {
        QueryWrapper<Doctor> qw = new QueryWrapper<>();
        qw.eq("status", "approved").eq("deleted", 0);
        qw.orderByDesc("created_at");
        List<Doctor> doctors = list(qw);
        return doctors.stream().map(this::toVO).toList();
    }

    @Override
    public List<DoctorVO> getDoctorListByDepartment(String department) {
        QueryWrapper<Doctor> qw = new QueryWrapper<>();
        qw.eq("status", "approved").eq("deleted", 0);
        if (department != null && !department.isBlank()) {
            qw.eq("department", department);
        }
        qw.orderByDesc("created_at");
        return list(qw).stream().map(this::toVO).toList();
    }

    @Override
    public List<DoctorVO> getPendingDoctorList() {
        QueryWrapper<Doctor> qw = new QueryWrapper<>();
        qw.eq("status", "pending").eq("deleted", 0);
        qw.orderByAsc("created_at");
        return list(qw).stream().map(this::toVO).toList();
    }

    @Override
    public void approveDoctor(Long doctorId, Long adminUserId) {
        Doctor doctor = getById(doctorId);
        if (doctor == null) {
            throw new RuntimeException("医生申请不存在");
        }
        if (!"pending".equals(doctor.getStatus())) {
            throw new RuntimeException("该申请已被处理");
        }
        doctor.setStatus("approved");
        doctor.setApprovedBy(adminUserId);
        doctor.setApprovedAt(LocalDateTime.now());
        doctor.setRejectReason(null);
        updateById(doctor);

        // 发送邮件通知
        sendAuditResultEmail(doctor, true, null);
    }

    @Override
    public void rejectDoctor(Long doctorId, Long adminUserId, String reason) {
        Doctor doctor = getById(doctorId);
        if (doctor == null) {
            throw new RuntimeException("医生申请不存在");
        }
        if (!"pending".equals(doctor.getStatus())) {
            throw new RuntimeException("该申请已被处理");
        }
        doctor.setStatus("rejected");
        doctor.setApprovedBy(adminUserId);
        doctor.setApprovedAt(LocalDateTime.now());
        doctor.setRejectReason(reason);
        updateById(doctor);

        // 发送邮件通知
        sendAuditResultEmail(doctor, false, reason);
    }

    @Override
    public List<DoctorVO> getDoctorsForRecommendation(String department) {
        return getDoctorListByDepartment(department);
    }

    @Override
    public boolean isApprovedDoctor(Long userId) {
        QueryWrapper<Doctor> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).eq("status", "approved").eq("deleted", 0);
        return count(qw) > 0;
    }

    @Override
    public boolean canViewPatient(Long doctorUserId, Long patientUserId) {
        if (doctorUserId == null || patientUserId == null) {
            return false;
        }
        // 必须是已审核医生
        QueryWrapper<Doctor> doctorQw = new QueryWrapper<>();
        doctorQw.eq("user_id", doctorUserId).eq("status", "approved").eq("deleted", 0);
        Doctor doctor = getOne(doctorQw);
        if (doctor == null) {
            return false;
        }
        // 存在患者预约该医生的记录
        QueryWrapper<DoctorAppointment> apptQw = new QueryWrapper<>();
        apptQw.eq("doctor_id", doctor.getId())
              .eq("user_id", patientUserId)
              .ne("status", "cancelled");
        return doctorAppointmentService.count(apptQw) > 0;
    }

    /**
     * Doctor -> DoctorVO
     */
    private DoctorVO toVO(Doctor doctor) {
        DoctorVO vo = new DoctorVO();
        BeanUtils.copyProperties(doctor, vo);
        // 补充 username
        QueryWrapper<User> uqw = new QueryWrapper<>();
        uqw.eq("id", doctor.getUserId());
        User user = userMapper.selectOne(uqw);
        if (user != null) {
            vo.setUsername(user.getUsername());
        }
        return vo;
    }

    /**
     * 发送审核结果邮件通知
     */
    @Async
    public void sendAuditResultEmail(Doctor doctor, boolean approved, String reason) {
        try {
            // 查询用户邮箱
            QueryWrapper<User> uqw = new QueryWrapper<>();
            uqw.eq("id", doctor.getUserId());
            User user = userMapper.selectOne(uqw);
            if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
                log.warn("医生 {} 没有配置邮箱，跳过审核通知邮件", doctor.getRealName());
                return;
            }

            if (fromEmail == null || fromEmail.isBlank()) {
                log.warn("邮件发送方未配置（spring.mail.username），跳过审核通知邮件");
                return;
            }

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(user.getEmail());

            String statusText = approved ? "审核通过" : "审核未通过";
            helper.setSubject("【健康管理系统】医生认证" + statusText);

            String htmlContent = buildAuditEmailHtml(doctor, user, approved, reason);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("医生审核结果邮件发送成功: to={}, result={}", user.getEmail(), statusText);
        } catch (Exception e) {
            log.error("医生审核结果邮件发送失败: {}", e.getMessage(), e);
        }
    }

    private String buildAuditEmailHtml(Doctor doctor, User user, boolean approved, String reason) {
        String statusColor = approved ? "#52c41a" : "#ff4d4f";
        String statusIcon = approved ? "✅" : "❌";
        String statusText = approved ? "审核通过" : "审核未通过";
        String statusDesc = approved
            ? "您的医生资质已通过审核，现在可以登录系统使用医生工作台，接收患者预约和在线咨询。"
            : "很抱歉，您的医生资质审核未通过。您可以登录系统查看驳回原因，并修改信息后重新提交申请。";

        String reasonHtml = "";
        if (!approved && reason != null && !reason.isBlank()) {
            reasonHtml = """
                <tr><td style="padding:0 40px 20px;">
                    <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#fff2f0;border-radius:8px;border-left:4px solid #ff4d4f;">
                    <tr><td style="padding:16px 20px;">
                        <h4 style="color:#ff4d4f;margin:0 0 6px;font-size:15px;">驳回原因</h4>
                        <p style="color:#555;margin:0;font-size:14px;line-height:1.6;">%s</p>
                    </td></tr>
                    </table>
                </td></tr>
                """.formatted(reason);
        }

        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"></head>
            <body style="margin:0;padding:0;background-color:#f4f7fa;font-family:'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif;">
            <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f4f7fa;padding:30px 0;">
            <tr><td align="center">
            <table width="600" cellpadding="0" cellspacing="0" style="background-color:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.08);">
                <tr><td style="background:linear-gradient(135deg,#1f2430 0%%,#334155 100%%);padding:30px 40px;text-align:center;">
                    <h1 style="color:#ffffff;margin:0;font-size:24px;">🏥 健康管理系统</h1>
                </td></tr>
                <tr><td style="padding:30px 40px 10px;">
                    <h2 style="color:#333;margin:0;font-size:20px;">%s，您好！</h2>
                </td></tr>
                <tr><td style="padding:10px 40px 20px;">
                    <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f0f5ff;border-radius:8px;border-left:4px solid %s;">
                    <tr><td style="padding:20px 24px;">
                        <h3 style="color:%s;margin:0 0 8px;font-size:18px;">%s 医生认证%s</h3>
                        <p style="color:#555;margin:0;font-size:15px;line-height:1.6;">%s</p>
                    </td></tr>
                    </table>
                </td></tr>
                %s
                <tr><td style="padding:0 40px 20px;">
                    <p style="color:#555;margin:0;font-size:14px;line-height:1.8;">
                        姓名：%s<br/>
                        医院：%s<br/>
                        科室：%s
                    </p>
                </td></tr>
                <tr><td style="background-color:#f8f9fa;padding:20px 40px;text-align:center;border-top:1px solid #eee;">
                    <p style="color:#999;margin:0;font-size:12px;line-height:1.8;">
                        此邮件由健康管理系统自动发送，请勿直接回复。<br/>
                        如有疑问，请登录系统联系管理员。
                    </p>
                </td></tr>
            </table>
            </td></tr>
            </table>
            </body></html>
            """.formatted(user.getUsername(), statusColor, statusColor, statusIcon, statusText, statusDesc, reasonHtml, doctor.getRealName(), doctor.getHospital(), doctor.getDepartment());
    }
}
