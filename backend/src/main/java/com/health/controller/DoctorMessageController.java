package com.health.controller;

import com.health.common.exception.ForbiddenException;
import com.health.common.utils.Result;
import com.health.common.utils.SecurityUtil;
import com.health.domain.dto.DoctorMessageDTO;
import com.health.domain.vo.ConversationVO;
import com.health.domain.vo.DoctorMessageVO;
import com.health.service.DoctorMessageService;
import com.health.service.DoctorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/doctor-chat")
public class DoctorMessageController {

    private final DoctorMessageService messageService;
    private final DoctorService doctorService;
    private final SecurityUtil securityUtil;

    public DoctorMessageController(DoctorMessageService messageService,
                                    DoctorService doctorService,
                                    SecurityUtil securityUtil) {
        this.messageService = messageService;
        this.doctorService = doctorService;
        this.securityUtil = securityUtil;
    }

    // ============ 用户发送消息给医生 ============

    /**
     * 用户发消息给医生
     */
    @PostMapping("/send-to-doctor")
    public Result<DoctorMessageVO> sendToDoctor(@RequestBody @Valid DoctorMessageDTO dto) {
        Long userId = securityUtil.getCurrentUserId();
        DoctorMessageVO vo = messageService.sendMessageToDoctor(userId, dto);
        return Result.success(vo);
    }

    /**
     * 用户获取自己的对话列表（所有对话过的医生）
     */
    @GetMapping("/my-conversations")
    public Result<List<ConversationVO>> getMyConversations() {
        Long userId = securityUtil.getCurrentUserId();
        return Result.success(messageService.getUserConversations(userId));
    }

    /**
     * 用户获取与某医生的对话历史
     */
    @GetMapping("/conversation/{doctorId}")
    public Result<List<DoctorMessageVO>> getConversationWithDoctor(
            @PathVariable Long doctorId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        Long userId = securityUtil.getCurrentUserId();
        // 标记已读
        messageService.markAsRead(doctorId, userId, "user");
        return Result.success(messageService.getConversation(doctorId, userId, page, size));
    }

    // ============ 医生端 ============

    /**
     * 医生获取自己的所有对话列表
     */
    @GetMapping("/doctor/conversations")
    public Result<List<ConversationVO>> getDoctorConversations() {
        Long doctorId = getMyDoctorId();
        return Result.success(messageService.getDoctorConversations(doctorId));
    }

    /**
     * 医生获取与某用户的对话历史
     */
    @GetMapping("/doctor/conversation/{userId}")
    public Result<List<DoctorMessageVO>> getConversationWithUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        Long doctorId = getMyDoctorId();
        // 标记已读
        messageService.markAsRead(doctorId, userId, "doctor");
        return Result.success(messageService.getConversation(doctorId, userId, page, size));
    }

    /**
     * 医生回复用户
     */
    @PostMapping("/doctor/send-to-user/{userId}")
    public Result<DoctorMessageVO> sendToUser(@PathVariable Long userId,
                                               @RequestBody @Valid DoctorMessageDTO dto) {
        Long doctorId = getMyDoctorId();
        DoctorMessageVO vo = messageService.sendMessageToUser(doctorId, userId, dto);
        return Result.success(vo);
    }

    // ============ 工具方法 ============

    /**
     * 获取当前登录用户的 doctorId（必须是已审核的医生）
     */
    private Long getMyDoctorId() {
        Long userId = securityUtil.getCurrentUserId();
        var doctorInfo = doctorService.getMyDoctorInfo(userId);
        if (doctorInfo == null || !"approved".equals(doctorInfo.getStatus())) {
            throw new ForbiddenException("您不是已认证的医生，无法使用此功能");
        }
        return doctorInfo.getId();
    }
}
