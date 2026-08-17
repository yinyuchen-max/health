package com.health.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.health.domain.dto.DoctorRegisterDTO;
import com.health.domain.entity.Doctor;
import com.health.domain.vo.DoctorVO;

import java.util.List;

public interface DoctorService extends IService<Doctor> {

    /**
     * 注册成为医生（需已登录）
     */
    DoctorVO registerAsDoctor(Long userId, DoctorRegisterDTO dto);

    /**
     * 获取当前用户的医生信息
     */
    DoctorVO getMyDoctorInfo(Long userId);

    /**
     * 获取所有已审核通过的医生列表（公开）
     */
    List<DoctorVO> getApprovedDoctorList();

    /**
     * 按科室获取医生列表（公开）
     */
    List<DoctorVO> getDoctorListByDepartment(String department);

    /**
     * 获取待审核的医生列表（仅管理员）
     */
    List<DoctorVO> getPendingDoctorList();

    /**
     * 审核医生申请（仅管理员）
     */
    void approveDoctor(Long doctorId, Long adminUserId);

    /**
     * 驳回医生申请（仅管理员）
     */
    void rejectDoctor(Long doctorId, Long adminUserId, String reason);

    /**
     * 根据科室获取已审核医生（AI推荐用）
     */
    List<DoctorVO> getDoctorsForRecommendation(String department);

    /**
     * 检查用户是否已是审核通过的医生
     */
    boolean isApprovedDoctor(Long userId);

    /**
     * 检查医生是否有权查看某患者的数据（存在指派该医生的预约关系）
     */
    boolean canViewPatient(Long doctorUserId, Long patientUserId);
}
