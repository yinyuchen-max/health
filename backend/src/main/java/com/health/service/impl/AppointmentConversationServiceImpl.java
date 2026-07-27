package com.health.service.impl;

import com.health.domain.dto.AppointmentExtractionContext;
import com.health.domain.dto.AppointmentExtractionResult;
import com.health.domain.entity.DoctorAppointment;
import com.health.service.AppointmentConversationService;
import com.health.service.AppointmentInformationExtractor;
import com.health.service.DoctorAppointmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AppointmentConversationServiceImpl implements AppointmentConversationService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentConversationServiceImpl.class);
    private static final String APPOINTMENT_OFFER = "如果您需要，我也可以帮您预约医生。是否需要预约？";
    private static final List<String> DEPARTMENTS = List.of(
            "内科", "外科", "儿科", "妇科", "皮肤科", "眼科", "耳鼻喉科", "口腔科",
            "骨科", "神经内科", "心血管内科", "消化内科", "呼吸内科", "泌尿外科"
    );
    private static final Map<String, String> DEPARTMENT_ALIASES = createDepartmentAliases();
    private static final DateTimeFormatter APPOINTMENT_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm");

    private final DoctorAppointmentService doctorAppointmentService;
    private final AppointmentInformationExtractor appointmentInformationExtractor;
    private final ConcurrentHashMap<Object, AppointmentDraft> drafts = new ConcurrentHashMap<>();
    private final Set<Object> offeredUsers = ConcurrentHashMap.newKeySet();

    public AppointmentConversationServiceImpl(DoctorAppointmentService doctorAppointmentService,
                                              AppointmentInformationExtractor appointmentInformationExtractor) {
        this.doctorAppointmentService = doctorAppointmentService;
        this.appointmentInformationExtractor = appointmentInformationExtractor;
    }

    @Override
    public String handleMessage(String userMessage, Long userId) {
        Object conversationId = conversationId(userId);
        String message = userMessage == null ? "" : userMessage.trim();
        AppointmentDraft draft = drafts.get(conversationId);

        if (draft != null && isCancellation(message)) {
            return cancelAppointment(conversationId);
        }
        if (draft != null && draft.awaitingConfirmation && isConfirmation(message)) {
            return saveConfirmedAppointment(conversationId, draft, userId);
        }
        if (draft != null && draft.awaitingConfirmation && isNegative(message)) {
            return cancelAppointment(conversationId);
        }

        if (draft == null) {
            if (offeredUsers.contains(conversationId) && isNegative(message)) {
                offeredUsers.remove(conversationId);
                return "好的，暂不预约。如果后续需要，随时告诉我。";
            }
            if (!shouldStartAppointment(message, conversationId)) {
                return null;
            }
            draft = new AppointmentDraft();
            drafts.put(conversationId, draft);
            offeredUsers.remove(conversationId);
        }

        AppointmentExtractionResult extraction;
        try {
            extraction = appointmentInformationExtractor.extract(message, draft.toExtractionContext());
        } catch (Exception exception) {
            log.error("AI预约信息提取失败 - userId: {}", userId, exception);
            return buildExtractionFailureReply(draft);
        }

        if (Boolean.TRUE.equals(extraction.getCancellation())) {
            return cancelAppointment(conversationId);
        }

        List<String> validationErrors = new ArrayList<>();
        boolean updated = applyExtraction(draft, extraction, validationErrors);
        List<String> missingFields = draft.missingFields();
        if (!missingFields.isEmpty()) {
            if (!updated && !isAffirmative(message) && !isAppointmentStartOnly(message)) {
                validationErrors.add(0, "未能从本次消息中识别出有效预约信息");
            }
            draft.awaitingConfirmation = false;
            return buildMissingFieldsReply(missingFields, validationErrors);
        }

        if (!draft.appointmentTime.isAfter(LocalDateTime.now())) {
            draft.appointmentTime = null;
            draft.awaitingConfirmation = false;
            return "预约时间必须晚于当前时间。无法使用该预约时间，请重新提供，例如：2026-07-30 09:30。";
        }

        draft.awaitingConfirmation = true;
        return buildConfirmationReply(draft);
    }

    @Override
    public String appendAppointmentOffer(String reply, Long userId) {
        offeredUsers.add(conversationId(userId));
        String safeReply = reply == null || reply.isBlank() ? "抱歉，暂时无法生成回复，请稍后重试。" : reply.trim();
        if (safeReply.contains("是否需要预约")) {
            return safeReply;
        }
        return safeReply + "\n\n" + APPOINTMENT_OFFER;
    }

    private boolean applyExtraction(AppointmentDraft draft,
                                    AppointmentExtractionResult extraction,
                                    List<String> validationErrors) {
        boolean updated = false;

        if (extraction.getPatientName() != null) {
            String patientName = extraction.getPatientName().trim();
            if (patientName.length() >= 2 && patientName.length() <= 50
                    && !patientName.contains("预约") && !patientName.contains("挂号")) {
                draft.patientName = patientName;
                updated = true;
            } else {
                validationErrors.add("无法确认姓名，请使用“姓名：利口”格式填写");
            }
        }

        if (extraction.getAge() != null) {
            if (extraction.getAge() >= 1 && extraction.getAge() <= 120) {
                draft.age = extraction.getAge();
                updated = true;
            } else {
                validationErrors.add("年龄需填写1到120之间的整数");
            }
        }

        if (extraction.getAppointmentTime() != null) {
            LocalDateTime appointmentTime = parseAppointmentTime(extraction.getAppointmentTime());
            if (appointmentTime != null) {
                draft.appointmentTime = appointmentTime;
                updated = true;
            } else {
                validationErrors.add("无法识别预约时间，请提供完整日期和时间，例如：2026-07-30 09:30");
            }
        }

        if (extraction.getPhone() != null) {
            String phone = normalizePhone(extraction.getPhone());
            if (phone != null) {
                draft.phone = phone;
                updated = true;
            } else {
                validationErrors.add("无法识别联系电话，请填写7至20位有效电话号码");
            }
        }

        if (extraction.getDepartment() != null) {
            String department = normalizeDepartment(extraction.getDepartment());
            if (department != null) {
                draft.department = department;
                updated = true;
            } else {
                validationErrors.add("无法识别预约科室，请从支持的科室中选择");
            }
        }

        return updated;
    }

    private String saveConfirmedAppointment(Object conversationId, AppointmentDraft draft, Long userId) {
        if (!draft.missingFields().isEmpty()) {
            draft.awaitingConfirmation = false;
            return buildMissingFieldsReply(draft.missingFields(), List.of("预约信息不完整，暂时不能提交"));
        }

        DoctorAppointment appointment = new DoctorAppointment();
        appointment.setUserId(userId);
        appointment.setPatientName(draft.patientName);
        appointment.setAge(draft.age);
        appointment.setAppointmentTime(draft.appointmentTime);
        appointment.setPhone(draft.phone);
        appointment.setDepartment(draft.department);

        try {
            if (!doctorAppointmentService.createAppointment(appointment)) {
                return "预约信息保存失败，请稍后回复“确认预约”重试。";
            }
        } catch (Exception exception) {
            log.error("保存医生预约失败 - userId: {}", userId, exception);
            return "预约信息保存失败，请稍后回复“确认预约”重试。";
        }

        drafts.remove(conversationId);
        return String.format(
                "预约成功！姓名：%s，年龄：%d岁，预约科室：%s，预约时间：%s，联系电话：%s。",
                draft.patientName,
                draft.age,
                draft.department,
                draft.appointmentTime.format(APPOINTMENT_TIME_FORMATTER),
                draft.phone
        );
    }

    private String buildConfirmationReply(AppointmentDraft draft) {
        return String.format(
                "请确认预约信息：\n姓名：%s\n年龄：%d岁\n预约科室：%s\n预约时间：%s\n联系电话：%s\n\n"
                        + "信息正确请回复“确认预约”，需要修改请直接告诉我要修改的内容，取消请回复“取消预约”。",
                draft.patientName,
                draft.age,
                draft.department,
                draft.appointmentTime.format(APPOINTMENT_TIME_FORMATTER),
                draft.phone
        );
    }

    private String buildMissingFieldsReply(List<String> missingFields, List<String> validationErrors) {
        StringBuilder reply = new StringBuilder();
        if (!validationErrors.isEmpty()) {
            reply.append("有些信息未能正确识别：").append(String.join("；", validationErrors)).append("。\n");
        }
        reply.append("目前还缺少：").append(String.join("、", missingFields)).append("。\n")
                .append("可预约科室：").append(String.join("、", DEPARTMENTS)).append("。\n")
                .append("您可以自然描述，也可以使用：姓名：利口，年龄：56，预约时间：2026-07-30 09:30，电话：17865387668，科室：泌尿外科。");
        return reply.toString();
    }

    private String buildExtractionFailureReply(AppointmentDraft draft) {
        return "暂时无法识别您提供的预约信息。目前还缺少："
                + String.join("、", draft.missingFields())
                + "。请使用明确格式重新填写，例如：姓名：利口，年龄：56，预约时间：2026-07-30 09:30，电话：17865387668，科室：泌尿外科。";
    }

    private LocalDateTime parseAppointmentTime(String value) {
        try {
            return LocalDateTime.parse(value.trim(), APPOINTMENT_TIME_FORMATTER);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value.trim());
            } catch (DateTimeParseException ignoredAgain) {
                return null;
            }
        }
    }

    private String normalizePhone(String value) {
        StringBuilder digits = new StringBuilder();
        for (char character : value.toCharArray()) {
            if (Character.isDigit(character)) {
                digits.append(character);
            } else if (!Character.isWhitespace(character)
                    && character != '-' && character != '+' && character != '(' && character != ')') {
                return null;
            }
        }
        return digits.length() >= 7 && digits.length() <= 20 ? digits.toString() : null;
    }

    private String normalizeDepartment(String value) {
        String department = value.trim();
        if (DEPARTMENTS.contains(department)) {
            return department;
        }
        return DEPARTMENT_ALIASES.get(department);
    }

    private boolean shouldStartAppointment(String message, Object conversationId) {
        return hasAppointmentIntent(message)
                || (offeredUsers.contains(conversationId) && isAffirmative(message))
                || hasLikelyAppointmentDetails(message);
    }

    private boolean hasLikelyAppointmentDetails(String message) {
        boolean containsDepartment = DEPARTMENTS.stream().anyMatch(message::contains)
                || DEPARTMENT_ALIASES.keySet().stream().anyMatch(message::contains);
        long digitCount = message.chars().filter(Character::isDigit).count();
        return containsDepartment && digitCount >= 5;
    }

    private boolean hasAppointmentIntent(String message) {
        return message.contains("预约") || message.contains("挂号") || message.contains("约医生") || message.contains("帮我约");
    }

    private boolean isAppointmentStartOnly(String message) {
        String compact = compactMessage(message);
        return Set.of("预约", "我要预约", "预约医生", "我要预约医生", "挂号", "我要挂号", "帮我预约").contains(compact);
    }

    private boolean isAffirmative(String message) {
        String compact = compactMessage(message);
        return Set.of("需要", "要", "是", "好的", "好", "可以", "帮我预约", "我要预约").contains(compact)
                || compact.startsWith("需要预约")
                || compact.startsWith("我要挂号");
    }

    private boolean isConfirmation(String message) {
        String compact = compactMessage(message);
        return Set.of("确认", "确认预约", "信息正确", "正确", "没问题", "提交预约").contains(compact);
    }

    private boolean isNegative(String message) {
        String compact = compactMessage(message);
        return Set.of("不需要", "不用", "暂时不用", "否", "不要", "不确认").contains(compact);
    }

    private boolean isCancellation(String message) {
        String compact = compactMessage(message);
        return compact.contains("取消预约") || compact.contains("不预约了") || compact.contains("停止填写");
    }

    private String compactMessage(String message) {
        StringBuilder compact = new StringBuilder();
        for (char character : message.toCharArray()) {
            if (!Character.isWhitespace(character) && "，。！？!?,；;：:".indexOf(character) < 0) {
                compact.append(character);
            }
        }
        return compact.toString();
    }

    private String cancelAppointment(Object conversationId) {
        drafts.remove(conversationId);
        offeredUsers.remove(conversationId);
        return "已取消本次预约填写。";
    }

    private Object conversationId(Long userId) {
        return userId == null ? "default" : userId;
    }

    private static Map<String, String> createDepartmentAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("心内科", "心血管内科");
        aliases.put("消化科", "消化内科");
        aliases.put("呼吸科", "呼吸内科");
        aliases.put("耳鼻喉", "耳鼻喉科");
        aliases.put("牙科", "口腔科");
        aliases.put("泌尿科", "泌尿外科");
        return aliases;
    }

    private static class AppointmentDraft {
        private String patientName;
        private Integer age;
        private LocalDateTime appointmentTime;
        private String phone;
        private String department;
        private boolean awaitingConfirmation;

        private List<String> missingFields() {
            List<String> missing = new ArrayList<>();
            if (patientName == null || patientName.isBlank()) {
                missing.add("姓名");
            }
            if (age == null) {
                missing.add("年龄");
            }
            if (appointmentTime == null) {
                missing.add("预约时间");
            }
            if (phone == null || phone.isBlank()) {
                missing.add("用户电话");
            }
            if (department == null || department.isBlank()) {
                missing.add("预约科室");
            }
            return missing;
        }

        private AppointmentExtractionContext toExtractionContext() {
            AppointmentExtractionContext context = new AppointmentExtractionContext();
            context.setPatientName(patientName);
            context.setAge(age);
            context.setAppointmentTime(appointmentTime == null ? null : appointmentTime.format(APPOINTMENT_TIME_FORMATTER));
            context.setPhone(phone);
            context.setDepartment(department);
            return context;
        }
    }
}
