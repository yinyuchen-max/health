package com.health.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.health.domain.dto.DoctorMessageDTO;
import com.health.domain.entity.Doctor;
import com.health.domain.entity.DoctorMessage;
import com.health.domain.entity.User;
import com.health.domain.vo.ConversationVO;
import com.health.domain.vo.DoctorMessageVO;
import com.health.mapper.DoctorMapper;
import com.health.mapper.DoctorMessageMapper;
import com.health.mapper.UserMapper;
import com.health.service.DoctorMessageService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DoctorMessageServiceImpl extends ServiceImpl<DoctorMessageMapper, DoctorMessage>
        implements DoctorMessageService {

    private final DoctorMapper doctorMapper;
    private final UserMapper userMapper;

    public DoctorMessageServiceImpl(DoctorMapper doctorMapper, UserMapper userMapper) {
        this.doctorMapper = doctorMapper;
        this.userMapper = userMapper;
    }

    @Override
    public DoctorMessageVO sendMessageToDoctor(Long userId, DoctorMessageDTO dto) {
        if (dto.getDoctorId() == null) {
            throw new RuntimeException("请指定目标医生");
        }
        Doctor doctor = doctorMapper.selectById(dto.getDoctorId());
        if (doctor == null || !"approved".equals(doctor.getStatus())) {
            throw new RuntimeException("该医生不存在或未通过审核");
        }

        DoctorMessage msg = new DoctorMessage();
        msg.setDoctorId(dto.getDoctorId());
        msg.setUserId(userId);
        msg.setSenderId(userId);
        msg.setSenderType("user");
        msg.setContent(dto.getContent());
        msg.setIsRead(0);
        msg.setCreatedAt(LocalDateTime.now());
        msg.setDeleted(0);
        save(msg);
        return toVO(msg);
    }

    @Override
    public DoctorMessageVO sendMessageToUser(Long doctorId, Long userId, DoctorMessageDTO dto) {
        // 验证医生身份
        Doctor doctor = doctorMapper.selectById(doctorId);
        if (doctor == null || !"approved".equals(doctor.getStatus())) {
            throw new RuntimeException("医生身份验证失败");
        }

        DoctorMessage msg = new DoctorMessage();
        msg.setDoctorId(doctorId);
        msg.setUserId(userId);
        msg.setSenderId(doctor.getUserId()); // sender_id = doctor's user_id
        msg.setSenderType("doctor");
        msg.setContent(dto.getContent());
        msg.setIsRead(0);
        msg.setCreatedAt(LocalDateTime.now());
        msg.setDeleted(0);
        save(msg);
        return toVO(msg);
    }

    @Override
    public List<DoctorMessageVO> getConversation(Long doctorId, Long userId, int page, int size) {
        QueryWrapper<DoctorMessage> qw = new QueryWrapper<>();
        qw.eq("doctor_id", doctorId).eq("user_id", userId).eq("deleted", 0);
        qw.orderByAsc("created_at");

        // 简单分页
        int offset = (page - 1) * size;
        qw.last("LIMIT " + size + " OFFSET " + offset);

        return list(qw).stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public List<ConversationVO> getDoctorConversations(Long doctorId) {
        // 查所有与该医生对话过的用户
        QueryWrapper<DoctorMessage> qw = new QueryWrapper<>();
        qw.eq("doctor_id", doctorId).eq("deleted", 0);
        qw.select("user_id").groupBy("user_id");
        List<DoctorMessage> msgs = list(qw);

        List<ConversationVO> result = new ArrayList<>();
        for (DoctorMessage msg : msgs) {
            ConversationVO conv = buildConversation(doctorId, msg.getUserId(), "doctor");
            if (conv != null) result.add(conv);
        }
        result.sort((a, b) -> b.getLastMessageTime().compareTo(a.getLastMessageTime()));
        return result;
    }

    @Override
    public List<ConversationVO> getUserConversations(Long userId) {
        QueryWrapper<DoctorMessage> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).eq("deleted", 0);
        qw.select("doctor_id").groupBy("doctor_id");
        List<DoctorMessage> msgs = list(qw);

        List<ConversationVO> result = new ArrayList<>();
        for (DoctorMessage msg : msgs) {
            ConversationVO conv = buildConversation(msg.getDoctorId(), userId, "user");
            if (conv != null) result.add(conv);
        }
        result.sort((a, b) -> b.getLastMessageTime().compareTo(a.getLastMessageTime()));
        return result;
    }

    @Override
    public void markAsRead(Long doctorId, Long userId, String readerType) {
        QueryWrapper<DoctorMessage> qw = new QueryWrapper<>();
        qw.eq("doctor_id", doctorId).eq("user_id", userId).eq("is_read", 0).eq("deleted", 0);
        if ("doctor".equals(readerType)) {
            qw.eq("sender_type", "user"); // 医生读用户发的消息
        } else {
            qw.eq("sender_type", "doctor"); // 用户读医生发的消息
        }
        DoctorMessage update = new DoctorMessage();
        update.setIsRead(1);
        update(update, qw);
    }

    @Override
    public int getUnreadCount(Long doctorId, Long userId, String readerType) {
        QueryWrapper<DoctorMessage> qw = new QueryWrapper<>();
        qw.eq("doctor_id", doctorId).eq("user_id", userId).eq("is_read", 0).eq("deleted", 0);
        if ("doctor".equals(readerType)) {
            qw.eq("sender_type", "user");
        } else {
            qw.eq("sender_type", "doctor");
        }
        return (int) count(qw);
    }

    private ConversationVO buildConversation(Long doctorId, Long userId, String viewerType) {
        Doctor doctor = doctorMapper.selectById(doctorId);
        if (doctor == null) return null;
        User user = userMapper.selectById(userId);
        if (user == null) return null;

        // 最后一条消息
        QueryWrapper<DoctorMessage> lastQw = new QueryWrapper<>();
        lastQw.eq("doctor_id", doctorId).eq("user_id", userId).eq("deleted", 0);
        lastQw.orderByDesc("created_at").last("LIMIT 1");
        DoctorMessage lastMsg = getOne(lastQw);

        ConversationVO conv = new ConversationVO();
        conv.setDoctorId(doctorId);
        conv.setUserId(userId);
        conv.setDoctorName(doctor.getRealName());
        conv.setUserName(user.getUsername());
        if (lastMsg != null) {
            conv.setLastMessage(lastMsg.getContent());
            conv.setLastMessageTime(lastMsg.getCreatedAt());
        }
        conv.setUnreadCount(getUnreadCount(doctorId, userId, viewerType));
        return conv;
    }

    private DoctorMessageVO toVO(DoctorMessage msg) {
        DoctorMessageVO vo = new DoctorMessageVO();
        vo.setId(msg.getId());
        vo.setDoctorId(msg.getDoctorId());
        vo.setUserId(msg.getUserId());
        vo.setSenderId(msg.getSenderId());
        vo.setSenderType(msg.getSenderType());
        vo.setContent(msg.getContent());
        vo.setIsRead(msg.getIsRead());
        vo.setCreatedAt(msg.getCreatedAt());

        // 设置发送者名称
        if ("doctor".equals(msg.getSenderType())) {
            Doctor doctor = doctorMapper.selectById(msg.getDoctorId());
            vo.setSenderName(doctor != null ? doctor.getRealName() : "医生");
        } else {
            User user = userMapper.selectById(msg.getUserId());
            vo.setSenderName(user != null ? user.getUsername() : "用户");
        }
        return vo;
    }
}
