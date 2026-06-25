package com.health.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import com.health.service.SmartHealthService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class SmartHealthServiceImpl implements SmartHealthService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final HealthRecordMapper healthRecordMapper;
    private final SportRecordMapper sportRecordMapper;
    private final UserMapper userMapper;

    public SmartHealthServiceImpl(
            HealthRecordMapper healthRecordMapper,
            SportRecordMapper sportRecordMapper,
            UserMapper userMapper
    ) {
        this.healthRecordMapper = healthRecordMapper;
        this.sportRecordMapper = sportRecordMapper;
        this.userMapper = userMapper;
    }

    @Override
    public SmartHealthOverviewDTO generateOverview(Long userId) {
        User user = userMapper.selectById(userId);
        List<HealthRecord> healthRecords = loadHealthRecords(userId);
        List<SportRecord> sportRecords = loadSportRecords(userId);
        HealthRecord latestHealthRecord = healthRecords.isEmpty() ? null : healthRecords.get(0);

        Double bmi = calculateBmi(user, latestHealthRecord);
        Integer weeklyExerciseMinutes = calculateWeeklyExerciseMinutes(sportRecords);
        Double avgHeartRate = calculateAverageHeartRate(healthRecords);
        Double latestBloodSugar = latestHealthRecord != null && latestHealthRecord.getBloodSugar() != null
                ? latestHealthRecord.getBloodSugar().doubleValue()
                : null;

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
        return overview;
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
}
