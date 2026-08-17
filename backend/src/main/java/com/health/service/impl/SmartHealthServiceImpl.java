package com.health.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.domain.dto.ExercisePlanDTO;
import com.health.domain.dto.HealthRiskAssessmentDTO;
import com.health.domain.dto.NutritionAdviceDTO;
import com.health.domain.dto.SleepInsightDTO;
import com.health.domain.dto.SmartHealthOverviewDTO;
import com.health.domain.dto.StressInsightDTO;
import com.health.domain.entity.HealthRecord;
import com.health.domain.entity.SportRecord;
import com.health.domain.entity.User;
import com.health.mapper.HealthRecordMapper;
import com.health.mapper.SportRecordMapper;
import com.health.mapper.UserMapper;
import com.health.service.HealthKnowledgeRagService;
import com.health.service.SmartHealthService;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class SmartHealthServiceImpl implements SmartHealthService {

    private static final Logger log = LoggerFactory.getLogger(SmartHealthServiceImpl.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final HealthRecordMapper healthRecordMapper;
    private final SportRecordMapper sportRecordMapper;
    private final UserMapper userMapper;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final HealthKnowledgeRagService healthKnowledgeRagService;

    @Value("${langchain4j.fallback.enabled:true}")
    private boolean fallbackEnabled;

    @Value("${langchain4j.fallback.ai-timeout-ms:3000}")
    private long aiTimeoutMs;

    /**
     * 智能健康报告缓存时长（天）：数据指纹不变时命中缓存，避免重复调用 AI
     */
    @Value("${smart-health.cache-ttl-days:7}")
    private long cacheTtlDays;

    private static final String OVERVIEW_CACHE_KEY_PREFIX = "smart:overview:";

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    public SmartHealthServiceImpl(
            HealthRecordMapper healthRecordMapper,
            SportRecordMapper sportRecordMapper,
            UserMapper userMapper,
            ChatModel chatModel,
            ObjectMapper objectMapper
    ) {
        this(healthRecordMapper, sportRecordMapper, userMapper, chatModel, objectMapper,
                (user, healthRecords, sportRecords, bmi, weeklyExerciseMinutes) -> List.of());
    }

    @Autowired
    public SmartHealthServiceImpl(
            HealthRecordMapper healthRecordMapper,
            SportRecordMapper sportRecordMapper,
            UserMapper userMapper,
            ChatModel chatModel,
            ObjectMapper objectMapper,
            HealthKnowledgeRagService healthKnowledgeRagService
    ) {
        this.healthRecordMapper = healthRecordMapper;
        this.sportRecordMapper = sportRecordMapper;
        this.userMapper = userMapper;
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.healthKnowledgeRagService = healthKnowledgeRagService;
    }

    @Override
    public SmartHealthOverviewDTO generateOverview(Long userId) {
        User user = userMapper.selectById(userId);
        List<HealthRecord> healthRecords = loadHealthRecords(userId);
        List<SportRecord> sportRecords = loadSportRecords(userId);

        // 基于健康/运动数据计算指纹：数据不变则指纹不变，直接返回缓存，节省 AI 调用
        String fingerprint = computeDataFingerprint(user, healthRecords, sportRecords);
        String cacheKey = OVERVIEW_CACHE_KEY_PREFIX + userId + ":" + fingerprint;
        SmartHealthOverviewDTO cached = readOverviewFromCache(cacheKey, userId);
        if (cached != null) {
            return cached;
        }

        HealthRecord latestHealthRecord = healthRecords.isEmpty() ? null : healthRecords.get(0);

        Double bmi = calculateBmi(user, latestHealthRecord);
        Integer weeklyExerciseMinutes = calculateWeeklyExerciseMinutes(sportRecords);
        Double avgHeartRate = calculateAverageHeartRate(healthRecords);
        Double latestBloodSugar = latestHealthRecord != null && latestHealthRecord.getBloodSugar() != null
                ? latestHealthRecord.getBloodSugar().doubleValue()
                : null;
        List<String> retrievedKnowledge = healthKnowledgeRagService.retrieveRelevantKnowledge(
                user, healthRecords, sportRecords, bmi, weeklyExerciseMinutes);

        // 优先尝试 AI 生成健康报告
        SmartHealthOverviewDTO aiResult = tryGenerateWithAI(user, healthRecords, sportRecords,
                bmi, weeklyExerciseMinutes, avgHeartRate, latestBloodSugar, latestHealthRecord, retrievedKnowledge);
        if (aiResult != null) {
            writeOverviewToCache(cacheKey, aiResult, userId);
            return aiResult;
        }

        // AI 不可用，回退到规则引擎
        log.info("使用规则引擎生成健康报告, userId={}", userId);
        SmartHealthOverviewDTO overview = new SmartHealthOverviewDTO();
        overview.setUserId(userId);
        overview.setGeneratedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        overview.setBmi(bmi != null ? round(bmi) : null);
        overview.setRiskAssessments(buildRiskAssessments(bmi, latestHealthRecord, latestBloodSugar, avgHeartRate, weeklyExerciseMinutes));
        overview.setNutritionAdvice(buildNutritionAdvice(user, bmi, latestBloodSugar, weeklyExerciseMinutes));
        overview.setExercisePlan(buildExercisePlan(weeklyExerciseMinutes, bmi));
        overview.setSleepInsight(buildSleepInsight(latestHealthRecord, sportRecords));
        overview.setStressInsight(buildStressInsight(avgHeartRate, weeklyExerciseMinutes, latestHealthRecord));
        overview.setOverallStatus(buildOverallStatus(overview.getRiskAssessments()));
        overview.setQuickTips(buildQuickTips(overview));
        writeOverviewToCache(cacheKey, overview, userId);
        return overview;
    }

    // ==================== Redis 缓存与数据指纹 ====================

    /**
     * 计算数据指纹：用户体征 + 全部健康记录 + 全部运动记录。
     * 任何一条记录的新增/修改/删除都会导致指纹变化，从而触发重新生成。
     */
    private String computeDataFingerprint(User user, List<HealthRecord> healthRecords, List<SportRecord> sportRecords) {
        StringBuilder sb = new StringBuilder();
        if (user != null) {
            sb.append(user.getAge()).append('|')
                    .append(user.getGender()).append('|')
                    .append(user.getHeight()).append('|')
                    .append(user.getWeight()).append(';');
        }
        sb.append('H').append(healthRecords.size()).append('#');
        for (HealthRecord r : healthRecords) {
            sb.append(r.getId()).append(':')
                    .append(r.getRecordDate()).append(':')
                    .append(r.getBloodPressureSystolic()).append(':')
                    .append(r.getBloodPressureDiastolic()).append(':')
                    .append(r.getHeartRate()).append(':')
                    .append(r.getBloodSugar()).append(':')
                    .append(r.getWeight()).append(',');
        }
        sb.append('S').append(sportRecords.size()).append('#');
        for (SportRecord r : sportRecords) {
            sb.append(r.getId()).append(':')
                    .append(r.getRecordDate()).append(':')
                    .append(r.getSportType()).append(':')
                    .append(r.getDuration()).append(':')
                    .append(r.getIntensity()).append(',');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            // 极端情况下退化为直接用字符串 hash
            return Integer.toHexString(sb.toString().hashCode());
        }
    }

    private SmartHealthOverviewDTO readOverviewFromCache(String cacheKey, Long userId) {
        if (stringRedisTemplate == null) {
            return null;
        }
        try {
            String json = stringRedisTemplate.opsForValue().get(cacheKey);
            if (json == null || json.isBlank()) {
                return null;
            }
            SmartHealthOverviewDTO cached = objectMapper.readValue(json, SmartHealthOverviewDTO.class);
            cached.setUserId(userId);
            log.info("智能健康报告命中缓存，跳过 AI 调用, userId={}", userId);
            return cached;
        } catch (Exception e) {
            log.warn("读取智能健康报告缓存失败，重新生成, userId={}: {}", userId, e.getMessage());
            return null;
        }
    }

    private void writeOverviewToCache(String cacheKey, SmartHealthOverviewDTO overview, Long userId) {
        if (stringRedisTemplate == null || overview == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(overview);
            stringRedisTemplate.opsForValue().set(cacheKey, json, Duration.ofDays(cacheTtlDays));
            log.info("智能健康报告已写入缓存, userId={}, ttl={}天", userId, cacheTtlDays);
        } catch (Exception e) {
            log.warn("写入智能健康报告缓存失败, userId={}: {}", userId, e.getMessage());
        }
    }

    private List<HealthRecord> loadHealthRecords(Long userId) {
        QueryWrapper<HealthRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
                .eq("deleted", 0)
                .orderByDesc("record_date")
                .orderByDesc("create_time");
        return healthRecordMapper.selectList(wrapper);
    }

    private List<SportRecord> loadSportRecords(Long userId) {
        QueryWrapper<SportRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
                .eq("deleted", 0)
                .orderByDesc("record_date")
                .orderByDesc("create_time");
        return sportRecordMapper.selectList(wrapper);
    }

    private List<HealthRiskAssessmentDTO> buildRiskAssessments(
            Double bmi,
            HealthRecord latestHealthRecord,
            Double latestBloodSugar,
            Double avgHeartRate,
            Integer weeklyExerciseMinutes
    ) {
        List<HealthRiskAssessmentDTO> assessments = new ArrayList<>();
        assessments.add(buildBmiRisk(bmi));
        assessments.add(buildBloodPressureRisk(latestHealthRecord));
        assessments.add(buildDiabetesRisk(latestBloodSugar, bmi));
        assessments.add(buildCardiovascularRisk(latestHealthRecord, avgHeartRate, weeklyExerciseMinutes, bmi));
        return assessments;
    }

    private HealthRiskAssessmentDTO buildBmiRisk(Double bmi) {
        HealthRiskAssessmentDTO dto = new HealthRiskAssessmentDTO();
        dto.setAssessmentType("BMI");

        if (bmi == null) {
            dto.setRiskScore(35.0);
            dto.setRiskLevel("MEDIUM");
            dto.setSummary("缺少身高或体重数据，无法完成精确 BMI 评估。");
            dto.setRecommendations(List.of("补充身高和最近体重记录", "保持每周至少一次体重追踪"));
            return dto;
        }

        if (bmi < 18.5) {
            dto.setRiskScore(62.0);
            dto.setRiskLevel("MEDIUM");
            dto.setSummary(String.format(Locale.US, "当前 BMI %.1f，偏低，存在营养摄入不足风险。", bmi));
            dto.setRecommendations(List.of("增加优质蛋白和复合碳水摄入", "每周进行 2-3 次力量训练"));
            return dto;
        }

        if (bmi < 24.0) {
            dto.setRiskScore(18.0);
            dto.setRiskLevel("LOW");
            dto.setSummary(String.format(Locale.US, "当前 BMI %.1f，处于正常范围。", bmi));
            dto.setRecommendations(List.of("继续保持规律作息与运动", "每月复核一次体重变化"));
            return dto;
        }

        if (bmi < 28.0) {
            dto.setRiskScore(58.0);
            dto.setRiskLevel("MEDIUM");
            dto.setSummary(String.format(Locale.US, "当前 BMI %.1f，已进入超重区间。", bmi));
            dto.setRecommendations(List.of("晚餐控制精制碳水比例", "每周累计中高强度运动提升到 180 分钟"));
            return dto;
        }

        dto.setRiskScore(82.0);
        dto.setRiskLevel("HIGH");
        dto.setSummary(String.format(Locale.US, "当前 BMI %.1f，肥胖风险较高。", bmi));
        dto.setRecommendations(List.of("优先建立减重饮食计划", "建议结合医生或营养师进行干预"));
        return dto;
    }

    private HealthRiskAssessmentDTO buildBloodPressureRisk(HealthRecord latestHealthRecord) {
        HealthRiskAssessmentDTO dto = new HealthRiskAssessmentDTO();
        dto.setAssessmentType("BLOOD_PRESSURE");
        Double systolic = latestHealthRecord != null ? latestHealthRecord.getBloodPressureSystolic() : null;
        Double diastolic = latestHealthRecord != null ? latestHealthRecord.getBloodPressureDiastolic() : null;

        if (systolic == null || diastolic == null) {
            dto.setRiskScore(30.0);
            dto.setRiskLevel("MEDIUM");
            dto.setSummary("最近缺少完整血压记录，无法完成稳定性分析。");
            dto.setRecommendations(List.of("补充晨起静息血压记录", "连续 7 天在固定时段监测"));
            return dto;
        }

        if (systolic >= 140 || diastolic >= 90) {
            dto.setRiskScore(88.0);
            dto.setRiskLevel("HIGH");
            dto.setSummary(String.format(Locale.US, "最近血压 %.0f/%.0f mmHg，已达到高血压风险区间。", systolic, diastolic));
            dto.setRecommendations(List.of("减少高盐加工食品摄入", "优先安排有氧运动和放松训练"));
            return dto;
        }

        if (systolic >= 130 || diastolic >= 85) {
            dto.setRiskScore(60.0);
            dto.setRiskLevel("MEDIUM");
            dto.setSummary(String.format(Locale.US, "最近血压 %.0f/%.0f mmHg，略高于理想范围。", systolic, diastolic));
            dto.setRecommendations(List.of("控制晚间饮食和饮酒", "保持每周至少 5 天轻中强度活动"));
            return dto;
        }

        dto.setRiskScore(20.0);
        dto.setRiskLevel("LOW");
        dto.setSummary(String.format(Locale.US, "最近血压 %.0f/%.0f mmHg，整体平稳。", systolic, diastolic));
        dto.setRecommendations(List.of("维持规律复测", "持续控制钠盐摄入"));
        return dto;
    }

    private HealthRiskAssessmentDTO buildDiabetesRisk(Double bloodSugar, Double bmi) {
        HealthRiskAssessmentDTO dto = new HealthRiskAssessmentDTO();
        dto.setAssessmentType("DIABETES");

        if (bloodSugar == null) {
            dto.setRiskScore(28.0);
            dto.setRiskLevel("MEDIUM");
            dto.setSummary("最近没有血糖数据，本次采用体重和生活方式进行粗略估计。");
            dto.setRecommendations(List.of("补充空腹血糖或餐后血糖", "减少含糖饮料和夜宵频率"));
            return dto;
        }

        double score = 20.0;
        if (bloodSugar >= 7.0) {
            score += 55.0;
        } else if (bloodSugar >= 6.1) {
            score += 30.0;
        } else if (bloodSugar >= 5.6) {
            score += 15.0;
        }

        if (bmi != null && bmi >= 24.0) {
            score += 10.0;
        }

        dto.setRiskScore(round(score));
        if (score >= 75.0) {
            dto.setRiskLevel("HIGH");
            dto.setSummary(String.format(Locale.US, "最近血糖 %.1f mmol/L，代谢风险偏高。", bloodSugar));
            dto.setRecommendations(List.of("主食优先粗粮和低 GI 组合", "尽快形成固定复测周期"));
        } else if (score >= 45.0) {
            dto.setRiskLevel("MEDIUM");
            dto.setSummary(String.format(Locale.US, "最近血糖 %.1f mmol/L，需继续关注。", bloodSugar));
            dto.setRecommendations(List.of("控制精制糖和高油点心", "餐后增加 15-20 分钟步行"));
        } else {
            dto.setRiskLevel("LOW");
            dto.setSummary(String.format(Locale.US, "最近血糖 %.1f mmol/L，当前处于相对稳态。", bloodSugar));
            dto.setRecommendations(List.of("继续保持规律进餐", "每月补充一次血糖记录"));
        }
        return dto;
    }

    private HealthRiskAssessmentDTO buildCardiovascularRisk(
            HealthRecord latestHealthRecord,
            Double avgHeartRate,
            Integer weeklyExerciseMinutes,
            Double bmi
    ) {
        HealthRiskAssessmentDTO dto = new HealthRiskAssessmentDTO();
        dto.setAssessmentType("CARDIO");

        double score = 20.0;
        if (avgHeartRate != null && !avgHeartRate.isNaN() && avgHeartRate > 95) {
            score += 25.0;
        } else if (avgHeartRate != null && !avgHeartRate.isNaN() && avgHeartRate > 85) {
            score += 12.0;
        }

        if (latestHealthRecord != null) {
            Double systolic = latestHealthRecord.getBloodPressureSystolic();
            Double diastolic = latestHealthRecord.getBloodPressureDiastolic();
            if ((systolic != null && systolic >= 140) || (diastolic != null && diastolic >= 90)) {
                score += 30.0;
            } else if ((systolic != null && systolic >= 130) || (diastolic != null && diastolic >= 85)) {
                score += 15.0;
            }
        }

        if (weeklyExerciseMinutes < 150) {
            score += 18.0;
        }
        if (bmi != null && bmi >= 28.0) {
            score += 12.0;
        }

        dto.setRiskScore(round(score));
        if (score >= 70.0) {
            dto.setRiskLevel("HIGH");
            dto.setSummary("心血管相关综合风险较高，主要受血压、静息心率和运动量影响。");
            dto.setRecommendations(List.of("每周至少 5 天低到中强度有氧活动", "减少熬夜并控制高盐高脂饮食"));
        } else if (score >= 45.0) {
            dto.setRiskLevel("MEDIUM");
            dto.setSummary("心血管风险处于可干预阶段，提升活动量会有明显收益。");
            dto.setRecommendations(List.of("逐步将每周活动时间提升到 150-180 分钟", "每周复核血压和心率趋势"));
        } else {
            dto.setRiskLevel("LOW");
            dto.setSummary("当前心血管风险较低，建议继续维持现有习惯。");
            dto.setRecommendations(List.of("保留稳定运动频率", "注意连续久坐时间不要过长"));
        }
        return dto;
    }

    private NutritionAdviceDTO buildNutritionAdvice(User user, Double bmi, Double bloodSugar, Integer weeklyExerciseMinutes) {
        NutritionAdviceDTO dto = new NutritionAdviceDTO();
        dto.setTitle("个性化饮食建议");

        double weight = user != null && user.getWeight() != null ? user.getWeight() : 60.0;
        double height = user != null && user.getHeight() != null ? user.getHeight() : 170.0;
        int age = user != null && user.getAge() != null ? user.getAge() : 30;
        double bmr = 10 * weight + 6.25 * height - 5 * age + 5;
        double activityFactor = weeklyExerciseMinutes >= 180 ? 1.55 : weeklyExerciseMinutes >= 90 ? 1.4 : 1.25;
        int dailyCalories = (int) Math.round(bmr * activityFactor);

        List<String> recommendations = new ArrayList<>();
        recommendations.add("碳水建议控制在总热量的 45%-55%，优先全谷物和杂豆。");
        recommendations.add("蛋白质建议占比 20%-30%，优先鱼类、蛋类、奶制品和豆制品。");
        recommendations.add("每天至少安排 500g 蔬菜和 1-2 份低糖水果。");

        if (bmi != null && bmi >= 24.0) {
            dailyCalories -= 250;
            recommendations.add("当前以温和减脂为目标，建议晚餐减少油炸和高糖零食。");
        }
        if (bloodSugar != null && bloodSugar >= 6.1) {
            recommendations.add("血糖偏高时，主食分配到三餐，避免集中摄入精制甜品。");
        }

        dto.setDailyCalories(Math.max(dailyCalories, 1200));
        dto.setSummary("基于基础代谢、活动水平和体重状态生成的日常饮食建议。");
        dto.setRecommendations(recommendations);
        return dto;
    }

    private ExercisePlanDTO buildExercisePlan(Integer weeklyExerciseMinutes, Double bmi) {
        ExercisePlanDTO dto = new ExercisePlanDTO();
        dto.setGoal(bmi != null && bmi >= 24.0 ? "减脂与心肺提升" : "体能维持与代谢优化");
        dto.setIntensity(weeklyExerciseMinutes >= 150 ? "中等强度" : "从低到中强度逐步进阶");
        dto.setWeeklyMinutesTarget(weeklyExerciseMinutes >= 150 ? 180 : 150);

        List<String> plan = new ArrayList<>();
        plan.add("周一：30 分钟快走或慢跑 + 10 分钟拉伸。");
        plan.add("周三：20 分钟力量训练，覆盖深蹲、推举、核心训练。");
        plan.add("周五：30-40 分钟中等强度有氧，如骑行或椭圆机。");
        plan.add("周末：选择 1 天进行 45 分钟低强度长时活动，另 1 天作为恢复日。");
        if (weeklyExerciseMinutes < 90) {
            plan.add("起步阶段每次先达到 20 分钟，连续 2 周后再逐步加量。");
        }
        dto.setWeeklyPlan(plan);
        return dto;
    }

    private SleepInsightDTO buildSleepInsight(HealthRecord latestHealthRecord, List<SportRecord> sportRecords) {
        SleepInsightDTO dto = new SleepInsightDTO();
        double score = 78.0;
        List<String> recommendations = new ArrayList<>();

        if (latestHealthRecord != null && latestHealthRecord.getHeartRate() != null && latestHealthRecord.getHeartRate() > 90) {
            score -= 10;
            recommendations.add("静息心率偏高，建议睡前 2 小时避免高强度运动和刺激性饮品。");
        }

        if (!sportRecords.isEmpty()) {
            SportRecord latestSport = sportRecords.get(0);
            LocalDate sportDate = parseDate(latestSport.getRecordDate());
            if (sportDate != null && sportDate.isEqual(LocalDate.now())) {
                recommendations.add("若当天有训练，优先把训练结束时间控制在睡前 3 小时前。");
            }
        }

        recommendations.add("建议固定上床和起床时间，连续两周保持一致。");
        recommendations.add("睡前减少电子屏暴露，并保持卧室温度舒适。");

        dto.setScore(round(score));
        dto.setSummary(score >= 75 ? "当前睡眠恢复条件较好，但仍需保持规律作息。" : "近期恢复质量一般，建议优先稳定作息。");
        dto.setRecommendations(recommendations);
        return dto;
    }

    private StressInsightDTO buildStressInsight(Double avgHeartRate, Integer weeklyExerciseMinutes, HealthRecord latestHealthRecord) {
        StressInsightDTO dto = new StressInsightDTO();
        double score = 35.0;
        if (avgHeartRate != null && !avgHeartRate.isNaN() && avgHeartRate > 90) {
            score += 20.0;
        }
        if (weeklyExerciseMinutes < 90) {
            score += 12.0;
        }
        if (latestHealthRecord != null && latestHealthRecord.getBloodPressureSystolic() != null
                && latestHealthRecord.getBloodPressureSystolic() >= 140) {
            score += 15.0;
        }

        dto.setScore(round(score));
        if (score >= 65.0) {
            dto.setLevel("HIGH");
            dto.setSummary("压力指数偏高，建议优先调整恢复节奏。");
            dto.setRecommendations(List.of("每天安排 10 分钟呼吸训练", "减少连续工作时长并增加走动休息"));
        } else if (score >= 45.0) {
            dto.setLevel("MEDIUM");
            dto.setSummary("存在一定压力累积，恢复行为需要更规律。");
            dto.setRecommendations(List.of("保持轻中强度活动以稳定情绪", "晚间避免摄入过量咖啡因"));
        } else {
            dto.setLevel("LOW");
            dto.setSummary("当前压力状态相对可控。");
            dto.setRecommendations(List.of("维持规律运动和睡眠", "继续关注工作日久坐与疲劳积累"));
        }
        return dto;
    }

    private String buildOverallStatus(List<HealthRiskAssessmentDTO> assessments) {
        double averageScore = assessments.stream()
                .filter(Objects::nonNull)
                .map(HealthRiskAssessmentDTO::getRiskScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(35.0);

        if (averageScore >= 70.0) {
            return "需要重点干预";
        }
        if (averageScore >= 45.0) {
            return "建议持续改善";
        }
        return "整体状态稳定";
    }

    private List<String> buildQuickTips(SmartHealthOverviewDTO overview) {
        List<String> tips = new ArrayList<>();
        HealthRiskAssessmentDTO highestRisk = overview.getRiskAssessments().stream()
                .max(Comparator.comparing(item -> item.getRiskScore() == null ? 0 : item.getRiskScore()))
                .orElse(null);

        if (highestRisk != null) {
            tips.add("当前最需要优先关注的是 " + highestRisk.getAssessmentType() + " 风险。");
        }
        if (overview.getExercisePlan() != null) {
            tips.add("本周运动目标：" + overview.getExercisePlan().getWeeklyMinutesTarget() + " 分钟。");
        }
        if (overview.getNutritionAdvice() != null) {
            tips.add("推荐日热量摄入约 " + overview.getNutritionAdvice().getDailyCalories() + " kcal。");
        }
        if (overview.getStressInsight() != null) {
            tips.add("压力水平：" + overview.getStressInsight().getLevel() + "。");
        }
        return tips;
    }

    private Double calculateBmi(User user, HealthRecord latestHealthRecord) {
        Double height = user != null ? user.getHeight() : null;
        Double weight = latestHealthRecord != null && latestHealthRecord.getWeight() != null
                ? latestHealthRecord.getWeight()
                : user != null ? user.getWeight() : null;

        if (height == null || height <= 0 || weight == null || weight <= 0) {
            return null;
        }
        return weight / Math.pow(height / 100.0, 2);
    }

    private Integer calculateWeeklyExerciseMinutes(List<SportRecord> sportRecords) {
        LocalDate weekAgo = LocalDate.now().minusDays(6);
        return sportRecords.stream()
                .filter(record -> {
                    LocalDate date = parseDate(record.getRecordDate());
                    return date != null && !date.isBefore(weekAgo);
                })
                .map(SportRecord::getDuration)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private Double calculateAverageHeartRate(List<HealthRecord> healthRecords) {
        double average = healthRecords.stream()
                .limit(7)
                .map(HealthRecord::getHeartRate)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(Double.NaN);
        return Double.isNaN(average) ? null : average;
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    // ==================== AI 增强方法 ====================

    /**
     * 尝试使用 AI 生成健康报告，失败时返回 null 触发规则引擎回退
     */
    private SmartHealthOverviewDTO tryGenerateWithAI(
            User user,
            List<HealthRecord> healthRecords,
            List<SportRecord> sportRecords,
            Double bmi,
            Integer weeklyExerciseMinutes,
            Double avgHeartRate,
            Double latestBloodSugar,
            HealthRecord latestHealthRecord,
            List<String> retrievedKnowledge
    ) {
        if (chatModel == null) {
            log.debug("ChatModel 未配置，跳过 AI 生成");
            return null;
        }

        if (!fallbackEnabled) {
            log.debug("AI 回退开关已关闭，直接执行 AI 生成");
            try {
                return generateOverviewWithAI(user, healthRecords, sportRecords,
                        bmi, weeklyExerciseMinutes, avgHeartRate, latestBloodSugar, latestHealthRecord, retrievedKnowledge);
            } catch (Exception e) {
                log.warn("AI 生成健康报告失败，将回退到规则引擎: {}", e.getMessage());
                return null;
            }
        }

        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return generateOverviewWithAI(user, healthRecords, sportRecords,
                            bmi, weeklyExerciseMinutes, avgHeartRate, latestBloodSugar, latestHealthRecord, retrievedKnowledge);
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }).orTimeout(aiTimeoutMs, TimeUnit.MILLISECONDS).exceptionally(e -> {
                Throwable cause = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
                log.warn("AI 生成健康报告失败，将回退到规则引擎: {}", cause.getMessage());
                return null;
            }).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.warn("AI 生成健康报告失败，将回退到规则引擎: {}", cause.getMessage());
            return null;
        }
    }

    private SmartHealthOverviewDTO generateOverviewWithAI(
            User user,
            List<HealthRecord> healthRecords,
            List<SportRecord> sportRecords,
            Double bmi,
            Integer weeklyExerciseMinutes,
            Double avgHeartRate,
            Double latestBloodSugar,
            HealthRecord latestHealthRecord,
            List<String> retrievedKnowledge
    ) throws JsonProcessingException {
        log.info("开始 AI 生成健康报告, userId={}", user.getId());
        String prompt = buildAIPrompt(user, healthRecords, sportRecords,
                bmi, weeklyExerciseMinutes, avgHeartRate, latestBloodSugar, latestHealthRecord, retrievedKnowledge);

        String aiResponse = chatModel.chat(prompt);
        log.debug("AI 原始响应长度: {}", aiResponse != null ? aiResponse.length() : 0);

        SmartHealthOverviewDTO result = parseAIResponse(aiResponse, user.getId());
        log.info("AI 健康报告生成成功, userId={}", user.getId());
        return result;
    }

    /**
     * 构建发送给 AI 的详细 Prompt
     */
    private String buildAIPrompt(
            User user,
            List<HealthRecord> healthRecords,
            List<SportRecord> sportRecords,
            Double bmi,
            Integer weeklyExerciseMinutes,
            Double avgHeartRate,
            Double latestBloodSugar,
            HealthRecord latestHealthRecord,
            List<String> retrievedKnowledge
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一位资深健康管理顾问和全科医生。请根据以下用户的健康数据，生成一份专业、个性化的健康评估报告。\n\n");

        // 用户基本信息
        sb.append("## 用户基本信息\n");
        sb.append(String.format("- 年龄: %d 岁\n", user.getAge() != null ? user.getAge() : 0));
        sb.append(String.format("- 性别: %s\n", user.getGender() != null && user.getGender() == 1 ? "男" : "女"));
        sb.append(String.format("- 身高: %.1f cm\n", user.getHeight() != null ? user.getHeight() : 0));
        sb.append(String.format("- 体重: %.1f kg\n", user.getWeight() != null ? user.getWeight() : 0));
        sb.append(String.format("- BMI: %s\n", bmi != null ? String.format(Locale.US, "%.1f", bmi) : "数据不足"));
        sb.append("\n");

        // 最新健康指标
        sb.append("## 最新健康指标\n");
        if (latestHealthRecord != null) {
            sb.append(String.format("- 收缩压: %s mmHg\n",
                    latestHealthRecord.getBloodPressureSystolic() != null
                            ? String.format(Locale.US, "%.0f", latestHealthRecord.getBloodPressureSystolic()) : "未记录"));
            sb.append(String.format("- 舒张压: %s mmHg\n",
                    latestHealthRecord.getBloodPressureDiastolic() != null
                            ? String.format(Locale.US, "%.0f", latestHealthRecord.getBloodPressureDiastolic()) : "未记录"));
            sb.append(String.format("- 静息心率: %s bpm\n",
                    latestHealthRecord.getHeartRate() != null ? latestHealthRecord.getHeartRate().toString() : "未记录"));
            sb.append(String.format("- 血糖: %s mmol/L\n",
                    latestBloodSugar != null ? String.format(Locale.US, "%.1f", latestBloodSugar) : "未记录"));
        } else {
            sb.append("暂无健康指标记录\n");
        }
        if (avgHeartRate != null && !avgHeartRate.isNaN()) {
            sb.append(String.format("- 近期平均心率: %.1f bpm\n", avgHeartRate));
        }
        sb.append("\n");

        // 运动数据
        sb.append("## 运动数据\n");
        sb.append(String.format("- 本周运动总时长: %d 分钟\n", weeklyExerciseMinutes != null ? weeklyExerciseMinutes : 0));
        sb.append(String.format("- 运动记录数(近一周): %d 条\n", sportRecords.size()));
        if (!sportRecords.isEmpty()) {
            sb.append("- 近期运动详情:\n");
            sportRecords.stream().limit(5).forEach(r ->
                    sb.append(String.format("  · %s: %s %d分钟 %s强度\n",
                            r.getRecordDate(), r.getSportType(),
                            r.getDuration() != null ? r.getDuration() : 0,
                            r.getIntensity() != null ? r.getIntensity() : "未知"))
            );
        }
        sb.append("\n");

        // 历史健康趋势
        sb.append("## 历史健康数据趋势\n");
        if (!healthRecords.isEmpty()) {
            sb.append(String.format("共 %d 条历史健康记录\n", healthRecords.size()));
            healthRecords.stream().limit(7).forEach(r ->
                    sb.append(String.format("  · %s: 血压%.0f/%.0f 心率%d 血糖%s\n",
                            r.getRecordDate(),
                            r.getBloodPressureSystolic() != null ? r.getBloodPressureSystolic() : 0,
                            r.getBloodPressureDiastolic() != null ? r.getBloodPressureDiastolic() : 0,
                            r.getHeartRate() != null ? r.getHeartRate() : 0,
                            r.getBloodSugar() != null ? r.getBloodSugar().toString() : "-"))
            );
        } else {
            sb.append("暂无历史健康记录\n");
        }
        sb.append("\n");

        if (retrievedKnowledge != null && !retrievedKnowledge.isEmpty()) {
            sb.append("## 检索到的健康知识依据\n");
            for (int i = 0; i < retrievedKnowledge.size(); i++) {
                sb.append(i + 1).append(". ").append(retrievedKnowledge.get(i)).append("\n\n");
            }
            sb.append("请优先结合以上知识依据和用户真实健康数据生成建议；如果知识依据与用户数据不匹配，以用户真实数据为准。\n\n");
        }

        // 输出格式要求
        sb.append("## 输出要求\n");
        sb.append("请严格返回以下 JSON 格式（不要包含 markdown 代码块标记），所有字段均为必填：\n\n");
        sb.append("{\n");
        sb.append("  \"bmi\": 数值(保留1位小数),\n");
        sb.append("  \"overallStatus\": \"整体状态稳定\" 或 \"建议持续改善\" 或 \"需要重点干预\",\n");
        sb.append("  \"riskAssessments\": [\n");
        sb.append("    {\n");
        sb.append("      \"assessmentType\": \"BMI\",\n");
        sb.append("      \"riskScore\": 0-100的风险分数,\n");
        sb.append("      \"riskLevel\": \"LOW\" 或 \"MEDIUM\" 或 \"HIGH\",\n");
        sb.append("      \"summary\": \"该维度评估总结(1-2句话)\",\n");
        sb.append("      \"recommendations\": [\"建议1\", \"建议2\"]\n");
        sb.append("    }\n");
        sb.append("  ],\n");
        sb.append("  \"nutritionAdvice\": {\n");
        sb.append("    \"title\": \"个性化饮食建议\",\n");
        sb.append("    \"summary\": \"饮食建议总结\",\n");
        sb.append("    \"dailyCalories\": 推荐每日热量(整数),\n");
        sb.append("    \"recommendations\": [\"饮食建议1\", \"饮食建议2\"]\n");
        sb.append("  },\n");
        sb.append("  \"exercisePlan\": {\n");
        sb.append("    \"goal\": \"运动目标描述\",\n");
        sb.append("    \"intensity\": \"运动强度描述\",\n");
        sb.append("    \"weeklyMinutesTarget\": 每周目标分钟数(整数),\n");
        sb.append("    \"weeklyPlan\": [\"周一计划\", \"周三计划\", \"周五计划\", \"周末计划\"]\n");
        sb.append("  },\n");
        sb.append("  \"sleepInsight\": {\n");
        sb.append("    \"score\": 0-100的睡眠质量分数,\n");
        sb.append("    \"summary\": \"睡眠质量总结\",\n");
        sb.append("    \"recommendations\": [\"睡眠建议1\", \"睡眠建议2\"]\n");
        sb.append("  },\n");
        sb.append("  \"stressInsight\": {\n");
        sb.append("    \"score\": 0-100的压力分数,\n");
        sb.append("    \"level\": \"LOW\" 或 \"MEDIUM\" 或 \"HIGH\",\n");
        sb.append("    \"summary\": \"压力评估总结\",\n");
        sb.append("    \"recommendations\": [\"减压建议1\", \"减压建议2\"]\n");
        sb.append("  },\n");
        sb.append("  \"quickTips\": [\"快速提示1\", \"快速提示2\", \"快速提示3\"]\n");
        sb.append("}\n\n");

        sb.append("## 重要约束\n");
        sb.append("1. riskAssessments 必须包含四个维度: BMI、BLOOD_PRESSURE、DIABETES、CARDIO\n");
        sb.append("2. 所有建议必须具体、可执行，结合用户的实际数据\n");
        sb.append("3. 用中文输出所有文本内容\n");
        sb.append("4. 如果某项数据缺失，基于现有数据做合理推断，并在 summary 中说明\n");

        return sb.toString();
    }

    /**
     * 解析 AI 返回的 JSON 并填充系统字段
     */
    private SmartHealthOverviewDTO parseAIResponse(String aiResponse, Long userId) throws JsonProcessingException {
        // 清理可能的 markdown 代码块标记
        String json = aiResponse.trim();
        if (json.startsWith("```json")) {
            json = json.substring(7);
        } else if (json.startsWith("```")) {
            json = json.substring(3);
        }
        if (json.endsWith("```")) {
            json = json.substring(0, json.length() - 3);
        }
        json = json.trim();

        SmartHealthOverviewDTO result = objectMapper.readValue(json, SmartHealthOverviewDTO.class);
        result.setUserId(userId);
        result.setGeneratedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        // 校验并补齐缺失字段
        if (result.getRiskAssessments() == null) {
            result.setRiskAssessments(new ArrayList<>());
        }
        if (result.getQuickTips() == null) {
            result.setQuickTips(new ArrayList<>());
        }

        return result;
    }
}
