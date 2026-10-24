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
        if(appointment!=null) { result.setReply(appointment); return result; }
        try {
            var snapshot=longTermMemory.beginTurn(userId,message);
            var turn=invocation(message,userId,snapshot);
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
        if(appointment!=null) { callback.onToken(appointment); callback.onComplete(appointment); return; }
        try { streamPreparation.execute(()->streamReply(message,userId,callback)); }
        catch(RejectedExecutionException e) { callback.onError(new AiServiceException("AI 请求较多，请稍后重试")); }
    }
    private void streamReply(String message,Long userId,StreamCallback callback) {
        try {
            var snapshot=longTermMemory.beginTurn(userId,message);
            var turn=invocation(message,userId,snapshot);
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
    private ChatContextService.Turn invocation(String message,Long userId,LongTermMemoryService.Snapshot snapshot) {
        var variables=new HashMap<>(context(userId));
        variables.put("longTermMemories",longTermMemory.context(snapshot));
        return contexts.prepare(userId,message,snapshot,variables);
    }
    @jakarta.annotation.PreDestroy public void close() { streamPreparation.shutdownNow(); }
    private Map<String,String> context(Long userId) {
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
        
        return Map.of("healthRecords", healthText.isEmpty() ? "暂无记录" : healthText.toString(),
                "sportRecords", sportText.isEmpty() ? "暂无记录" : sportText.toString());
    }
}
