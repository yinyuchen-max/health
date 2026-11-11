package com.health.service.impl;
import com.health.ai.*;
import com.health.domain.dto.ChatResponseDTO;
import com.health.domain.vo.HealthRecordVO;
import com.health.domain.vo.SportRecordVO;
import com.health.service.*;
import com.health.common.config.RuntimeSettings;
import com.health.memory.LongTermMemoryService;
import com.health.memory.ChatContextService;
import java.util.concurrent.*;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class ChatServiceImpl implements ChatService {
    private final AiClient ai;
    private final AppointmentConversationService appointments;
    private final HealthRecordService healthRecordService;
    private final SportRecordService sportRecordService;
    private final ChatContextService contexts;
    private final ExecutorService streamPreparation=new ThreadPoolExecutor(8,8,60,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),
            r->{var t=new Thread(r,"health-chat-context");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private final RuntimeSettings settings;
    private final LongTermMemoryService longTermMemory;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private LifestyleService lifestyle;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.health.memory.ChatTranscriptRepository transcripts;
    public ChatServiceImpl(AiClient ai, AppointmentConversationService appointments,
                           HealthRecordService healthRecordService, SportRecordService sportRecordService,
                           ChatContextService contexts, RuntimeSettings settings, LongTermMemoryService longTermMemory) {
        this.ai=ai; this.appointments=appointments; this.healthRecordService=healthRecordService;
        this.sportRecordService=sportRecordService; this.contexts=contexts; this.settings=settings;
        this.longTermMemory=longTermMemory;
    }
    @Override public ChatResponseDTO chat(String message,Long userId) {
        Objects.requireNonNull(userId,"对话必须有已认证用户");
        ai.requireAccess(userId);
        ChatResponseDTO result=new ChatResponseDTO();
        if(message==null || message.isBlank()) { result.setReply("请告诉我您想咨询的健康问题。"); return result; }
        String appointment=appointments.handleMessage(message,userId);
        if(appointment!=null) {
            result.setReply(appointment);
            result.setContextNote("这次在办理预约，预约内容不会写入长期记忆。");
            rememberAppointment(userId, message, appointment);
            return result;
        }
        try {
            var snapshot=longTermMemory.beginTurn(userId,message);
            var prepared=prepareContext(userId);
            result.setContextNote(prepared.note());
            var turn=invocation(message,userId,snapshot,prepared.variables());
            String reply=ai.complete(turn.request());
            contexts.complete(turn,reply);
            longTermMemory.completeTurn(snapshot,message,()->{});
            result.setReply(reply);
        } catch(AiServiceException e) { result.setReply(e.getMessage()); }
        catch(Exception e) { result.setReply("AI 服务暂时不可用，请稍后重试"); }
        return result;
    }
    @Override public void chatStream(String message,Long userId,StreamCallback callback) {
        Objects.requireNonNull(userId,"对话必须有已认证用户");
        ai.requireAccess(userId);
        if(message==null || message.isBlank()) { callback.onComplete("请告诉我您想咨询的健康问题。"); return; }
        String appointment=appointments.handleMessage(message,userId);
        if(appointment!=null) {
            callback.onContext("这次在办理预约，预约内容不会写入长期记忆。");
            rememberAppointment(userId, message, appointment);
            callback.onToken(appointment); callback.onComplete(appointment); return;
        }
        try { streamPreparation.execute(()->streamReply(message,userId,callback)); }
        catch(RejectedExecutionException e) { callback.onError(new AiServiceException("AI 请求较多，请稍后重试")); }
    }
    private void streamReply(String message,Long userId,StreamCallback callback) {
        try {
            var snapshot=longTermMemory.beginTurn(userId,message);
            var prepared=prepareContext(userId);
            callback.onContext(prepared.note());
            var turn=invocation(message,userId,snapshot,prepared.variables());
            ai.stream(turn.request(),new StreamCallback() {
                public void onThinking(String token) { callback.onThinking(token); }
                public void onToken(String token) { callback.onToken(token); }
                public void onComplete(String reply) {
                    contexts.complete(turn,reply);
                    longTermMemory.completeTurn(snapshot,message,()->{});
                    callback.onComplete(reply);
                }
                public void onError(Throwable error) { callback.onError(error); }
            });
        } catch(AiServiceException e) { callback.onError(e); }
        catch(Exception e) { callback.onError(new AiServiceException("AI 服务暂时不可用，请稍后重试")); }
    }
    /** Appointment text stays visible across devices but never enters model context or memory extraction. */
    private void rememberAppointment(Long userId, String userText, String assistantText) {
        if (transcripts == null || userText == null || userText.isBlank() || assistantText == null || assistantText.isBlank()) return;
        String turn = "visible-" + java.util.UUID.randomUUID();
        var now = java.time.LocalDateTime.now();
        transcripts.append(userId, 0L, turn, "user", clip(userText), now, false);
        transcripts.append(userId, 0L, turn, "assistant", clip(assistantText), now, false);
    }
    private static String clip(String text) { String value = text.trim(); return value.length() > 20000 ? value.substring(0, 20000) : value; }
    private record PreparedContext(Map<String,String> variables, String note) {}
    private PreparedContext prepareContext(Long userId) {
        LifestyleService.Capture life=readLifestyle(userId);
        Map<String,String> variables=context(userId, life);
        return new PreparedContext(variables, contextNote(variables, life));
    }
    private LifestyleService.Capture readLifestyle(Long userId) {
        if(lifestyle==null || userId==null) return null;
        try { return lifestyle.capture(userId); }
        catch(RuntimeException ignored) { return null; }
    }
    private ChatContextService.Turn invocation(String message,Long userId,LongTermMemoryService.Snapshot snapshot,Map<String,String> base) {
        var variables=new HashMap<>(base);
        variables.put("longTermMemories",longTermMemory.context(snapshot));
        return contexts.prepare(userId,message,snapshot,variables);
    }
    private String contextNote(Map<String,String> variables, LifestyleService.Capture life) {
        List<String> items=new ArrayList<>();
        if(variables.getOrDefault("healthRecords","").contains("日期:")) items.add("健康记录");
        if(variables.getOrDefault("sportRecords","").contains("日期:")) items.add("运动记录");
        if(life!=null) items.addAll(life.labels());
        else if(lifestyle!=null && variables.getOrDefault("dailyContext","").contains("读取失败")) items.add("部分日常记录暂时读不到");
        if(items.isEmpty()) return "这次没有读到个人记录，回答会是一般建议，不是你的实测结果。";
        return "这次参考了"+String.join("、", items)+"。没有列出的内容，表示当前没有记录。";
    }
    @jakarta.annotation.PreDestroy public void close() { streamPreparation.shutdownNow(); }
    private Map<String,String> context(Long userId, LifestyleService.Capture life) {
        StringBuilder healthText = new StringBuilder();
        StringBuilder sportText = new StringBuilder();
        
        if (userId != null) {
            // 查询用户最近的健康记录
            Map<String, Object> healthData = healthRecordService.getHealthRecordsByUserId(userId, 1, settings.getChatMemory().getContextRecordLimit());
            List<?> records = (List<?>) healthData.get("records");
            
            // 查询用户最近的运动记录
            Map<String, Object> sportData = sportRecordService.getSportRecordsByUserId(userId, 1, settings.getChatMemory().getContextRecordLimit());
            List<?> sportRecords = (List<?>) sportData.get("records");
            
            // 如果有健康数据，添加到系统提示中
            if (records != null && !records.isEmpty()) {
                for (Object record : records) {
                    if (record instanceof HealthRecordVO) {
                        HealthRecordVO hr = (HealthRecordVO) record;
                        healthText.append("- 日期: ").append(hr.getRecordDate()).append(", ");
                        if (hr.getBloodPressureSystolic() != null && hr.getBloodPressureDiastolic() != null) {
                            healthText.append("血压: ").append(hr.getBloodPressureSystolic())
                                .append("/").append(hr.getBloodPressureDiastolic()).append(" mmHg, ");
                        }
                        if (hr.getHeartRate() != null) {
                            healthText.append("心率: ").append(hr.getHeartRate()).append(" bpm, ");
                        }
                        if (hr.getBloodSugar() != null) {
                            healthText.append("血糖: ").append(hr.getBloodSugar()).append(" mmol/L, ");
                        }
                        if (hr.getWeight() != null) {
                            healthText.append("体重: ").append(hr.getWeight()).append(" kg, ");
                        }
                        if (hr.getNotes() != null && !hr.getNotes().isBlank()) {
                            String notes = hr.getNotes().trim();
                            healthText.append("备注: ").append(notes.length() > 80 ? notes.substring(0, 80) : notes).append(", ");
                        }
                        healthText.append("\n");
                    }
                }
            }
            
            // 如果有运动数据，添加到系统提示中
            if (sportRecords != null && !sportRecords.isEmpty()) {
                for (Object record : sportRecords) {
                    if (record instanceof SportRecordVO) {
                        SportRecordVO sr = (SportRecordVO) record;
                        sportText.append("- 日期: ").append(sr.getRecordDate())
                            .append(", 运动类型: ").append(sr.getSportType())
                            .append(", 时长: ").append(sr.getDuration()).append(" 分钟")
                            .append(", 消耗: ").append(sr.getCalories()).append(" kcal\n");
                    }
                }
            }
            
        }
        
        String daily=life!=null ? life.prompt() : lifestyle==null ? "暂无微信步数、睡眠记录、每日目标和提醒。" : "步数、睡眠、目标和提醒暂时读取失败。";
        return Map.of("healthRecords", healthText.isEmpty() ? "暂无记录" : healthText.toString(),
                "sportRecords", sportText.isEmpty() ? "暂无记录" : sportText.toString(),
                "dailyContext", daily);
    }
}
