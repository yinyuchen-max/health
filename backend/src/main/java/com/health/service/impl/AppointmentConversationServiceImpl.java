package com.health.service.impl;

import com.health.domain.dto.AppointmentExtractionContext;
import com.health.domain.dto.AppointmentExtractionResult;
import com.health.domain.entity.DoctorAppointment;
import com.health.domain.vo.DoctorVO;
import com.health.domain.vo.UserVO;
import com.health.service.AppointmentConversationService;
import com.health.service.AppointmentInformationExtractor;
import com.health.service.DoctorAppointmentService;
import com.health.service.DoctorService;
import com.health.service.UserService;
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
    private final DoctorService doctorService;
    private final UserService userService;
    private final ConcurrentHashMap<Object, AppointmentDraft> drafts = new ConcurrentHashMap<>();
    private final Set<Object> offeredUsers = ConcurrentHashMap.newKeySet();

    public AppointmentConversationServiceImpl(DoctorAppointmentService doctorAppointmentService,
                                              AppointmentInformationExtractor appointmentInformationExtractor,
                                              DoctorService doctorService,
                                              UserService userService) {
        this.doctorAppointmentService = doctorAppointmentService;
        this.appointmentInformationExtractor = appointmentInformationExtractor;
        this.doctorService = doctorService;
        this.userService = userService;
    }

    @Override
    public String handleMessage(String userMessage, Long userId) {
        Object conversationId = conversationId(userId);
        String message = userMessage == null ? "" : userMessage.trim();
        AppointmentDraft draft = drafts.get(conversationId);

        if (draft != null && isCancellation(message)) {
            return cancelAppointment(conversationId);
        }
        if (draft != null && draft.awaitingDoctorSelection) {
            return handleDoctorSelection(draft, message);
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
            prefillFromUserProfile(draft, userId);
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

        // 信息完整：若尚未选择医生且该科室有可约医生，先引导用户选择医生
        if (draft.doctorId == null && !draft.doctorDeclined) {
            String selectionReply = tryAskDoctorSelection(draft);
            if (selectionReply != null) {
                return selectionReply;
            }
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
                if (!department.equals(draft.department)) {
                    // 科室变更后需重新选择医生
                    draft.doctorId = null;
                    draft.doctorName = null;
                    draft.doctorDeclined = false;
                    draft.awaitingDoctorSelection = false;
                    draft.recommendedDoctors.clear();
                }
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
        appointment.setDoctorId(draft.doctorId);
        // 患者已在对话中确认预约，直接置为已确认
        appointment.setStatus("confirmed");

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
                "预约成功！姓名：%s，年龄：%d岁，预约科室：%s，预约医生：%s，预约时间：%s，联系电话：%s。",
                draft.patientName,
                draft.age,
                draft.department,
                draft.doctorName != null ? draft.doctorName : "不指定（由医院安排）",
                draft.appointmentTime.format(APPOINTMENT_TIME_FORMATTER),
                draft.phone
        );
    }

    /**
     * 预约信息完整后，列出该科室医生并引导用户选择
     * 返回 null 表示无需选择（无可约医生），直接进入确认环节
     */
    private String tryAskDoctorSelection(AppointmentDraft draft) {
        try {
            List<DoctorVO> doctors = doctorService.getDoctorsForRecommendation(draft.department);
            if (doctors == null || doctors.isEmpty()) {
                return null;
            }
            draft.recommendedDoctors = new ArrayList<>(doctors.subList(0, Math.min(doctors.size(), 5)));
            draft.awaitingDoctorSelection = true;
            draft.awaitingConfirmation = false;

            StringBuilder reply = new StringBuilder();
            reply.append("预约信息已完整！该科室可预约医生：\n");
            reply.append(buildDoctorListText(draft));
            reply.append("\n请回复序号或医生姓名选择医生；不指定医生请回复“不指定”。");
            return reply.toString();
        } catch (Exception exception) {
            log.warn("获取可预约医生列表失败，跳过医生选择 - department: {}", draft.department, exception);
            return null;
        }
    }

    /**
     * 处理用户的医生选择消息（序号 / 医生姓名 / 不指定）
     */
    private String handleDoctorSelection(AppointmentDraft draft, String message) {
        String compact = compactMessage(message);

        // 不指定医生
        if (compact.contains("不指定") || compact.contains("随便") || compact.contains("都可以")
                || compact.contains("不用选") || compact.contains("不选择")) {
            draft.doctorDeclined = true;
            draft.awaitingDoctorSelection = false;
            draft.awaitingConfirmation = true;
            return buildConfirmationReply(draft);
        }

        // 按序号选择
        String digits = compact.replaceAll("[^0-9]", "");
        if (!digits.isEmpty()) {
            try {
                int index = Integer.parseInt(digits);
                if (index >= 1 && index <= draft.recommendedDoctors.size()) {
                    return selectDoctor(draft, draft.recommendedDoctors.get(index - 1));
                }
            } catch (NumberFormatException ignored) {
                // 继续尝试按姓名匹配
            }
        }

        // 按医生姓名匹配
        for (DoctorVO doctor : draft.recommendedDoctors) {
            if (compact.contains(doctor.getRealName())) {
                return selectDoctor(draft, doctor);
            }
        }

        // 未识别，重新提示
        return "未能识别您选择的医生。该科室可预约医生：\n" + buildDoctorListText(draft)
                + "\n请回复序号或医生姓名选择医生；不指定医生请回复“不指定”。";
    }

    private String selectDoctor(AppointmentDraft draft, DoctorVO doctor) {
        draft.doctorId = doctor.getId();
        draft.doctorName = doctor.getRealName();
        draft.awaitingDoctorSelection = false;
        draft.awaitingConfirmation = true;
        return "已为您选择医生：" + doctor.getRealName()
                + (doctor.getTitle() != null ? "（" + doctor.getTitle() + "）" : "") + "。\n\n"
                + buildConfirmationReply(draft);
    }

    private String buildDoctorListText(AppointmentDraft draft) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < draft.recommendedDoctors.size(); i++) {
            DoctorVO d = draft.recommendedDoctors.get(i);
            text.append(String.format("  %d. %s（%s）%s - %s\n",
                    i + 1,
                    d.getRealName(),
                    d.getTitle() != null ? d.getTitle() : "医生",
                    d.getHospital(),
                    d.getSpecialization() != null ? d.getSpecialization() : ""
            ));
        }
        return text.toString();
    }

    private String buildConfirmationReply(AppointmentDraft draft) {
        StringBuilder reply = new StringBuilder();
        reply.append(String.format(
                "请确认预约信息：\n姓名：%s\n年龄：%d岁\n预约科室：%s\n预约医生：%s\n预约时间：%s\n联系电话：%s\n",
                draft.patientName,
                draft.age,
                draft.department,
                draft.doctorName != null ? draft.doctorName : "不指定（由医院安排）",
                draft.appointmentTime.format(APPOINTMENT_TIME_FORMATTER),
                draft.phone
        ));

        reply.append("\n信息正确请回复“确认预约”，需要修改请直接告诉我要修改的内容，取消请回复“取消预约”。");
        return reply.toString();
    }

    private String buildMissingFieldsReply(List<String> missingFields, List<String> validationErrors) {
        StringBuilder reply = new StringBuilder();
        if (!validationErrors.isEmpty()) {
            reply.append("有些信息未能正确识别：").append(String.join("；", validationErrors)).append("。\n");
        }
        reply.append("已从您的账号自动填入个人信息。")
                .append("目前还缺少：").append(String.join("、", missingFields)).append("。\n")
                .append("可预约科室：").append(String.join("、", DEPARTMENTS)).append("。\n")
                .append("您只需提供缺少的信息，例如：预约时间：2026-07-30 09:30，科室：泌尿外科。");
        return reply.toString();
    }

    private String buildExtractionFailureReply(AppointmentDraft draft) {
        return "暂时无法识别您提供的预约信息。目前还缺少："
                + String.join("、", draft.missingFields())
                + "。个人信息已从账号自动填入，您只需提供缺少的内容，例如：预约时间：2026-07-30 09:30，科室：泌尿外科。";
    }

    /**
     * 从用户账号资料自动填充预约信息（姓名/年龄/电话），免去手动填写
     */
    private void prefillFromUserProfile(AppointmentDraft draft, Long userId) {
        if (userId == null) {
            return;
        }
        try {
            UserVO userInfo = userService.getUserInfo(userId);
            if (userInfo == null) {
                return;
            }
            if (userInfo.getUsername() != null && !userInfo.getUsername().isBlank()) {
                draft.patientName = userInfo.getUsername().trim();
            }
            if (userInfo.getAge() != null && userInfo.getAge() >= 1 && userInfo.getAge() <= 120) {
                draft.age = userInfo.getAge();
            }
            if (userInfo.getPhone() != null && !userInfo.getPhone().isBlank()) {
                String phone = normalizePhone(userInfo.getPhone());
                if (phone != null) {
                    draft.phone = phone;
                }
            }
        } catch (Exception exception) {
            log.warn("获取用户资料失败，需要用户手动填写 - userId: {}", userId, exception);
        }
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
        // 医生选择相关
        private Long doctorId;
        private String doctorName;
        private boolean doctorDeclined;
        private boolean awaitingDoctorSelection;
        private List<DoctorVO> recommendedDoctors = new ArrayList<>();

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
