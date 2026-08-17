package com.health.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.health.domain.dto.DoctorMessageDTO;
import com.health.domain.entity.DoctorMessage;
import com.health.domain.vo.ConversationVO;
import com.health.domain.vo.DoctorMessageVO;

import java.util.List;

public interface DoctorMessageService extends IService<DoctorMessage> {

    /**
     * 用户发送消息给医生
     */
    DoctorMessageVO sendMessageToDoctor(Long userId, DoctorMessageDTO dto);

    /**
     * 医生回复用户消息
     */
    DoctorMessageVO sendMessageToUser(Long doctorId, Long userId, DoctorMessageDTO dto);

    /**
     * 获取医生与某用户之间的对话历史
     */
    List<DoctorMessageVO> getConversation(Long doctorId, Long userId, int page, int size);

    /**
     * 获取医生的所有对话列表（每个用户一条记录）
     */
    List<ConversationVO> getDoctorConversations(Long doctorId);

    /**
     * 获取用户的所有对话列表（每个医生一条记录）
     */
    List<ConversationVO> getUserConversations(Long userId);

    /**
     * 标记对话已读
     */
    void markAsRead(Long doctorId, Long userId, String readerType);

    /**
     * 获取未读消息总数
     */
    int getUnreadCount(Long doctorId, Long userId, String readerType);
}
