package com.health.memory;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;

@Repository
public class CognitiveMemoryRepository {
    private final JdbcTemplate jdbc;
    public CognitiveMemoryRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public record Profile(double encodingThreshold,double relevanceWeight,double decayMultiplier,int contextBudget,int feedbackCount,LocalDateTime lastConsolidatedAt) {}
    public record Job(String id,Long userId,long revision,long turn,String type,String input,String state,int attempts,String leaseToken,LocalDateTime createdAt) {}
    public record Episode(String id,Long userId,String jobId,long turn,long revision,String factKey,String category,String content,String evidence,
                          String evidenceHash,String operation,String subject,String temporalType,boolean explicitChange,String goal,
                          double importance,double attention,String state,LocalDateTime occurredAt,String timeEvidence,LocalDateTime recordedAt,
                          LocalDateTime expiresAt,boolean consolidated) {}
    public record Semantic(Long id,String factKey,String category,String content,String sourceText,String status,String pendingContent,
                           long turn,int version,double importance,boolean pinned,String temporalType,LocalDateTime lastAccessedAt,
                           int usefulCount,LocalDateTime createdAt,LocalDateTime updatedAt,LocalDateTime expiresAt) {}
    public record Version(String id,Long memoryId,int version,String content,String sourceText,String status,String pendingContent,LocalDateTime validFrom,LocalDateTime validTo) {}
    public record Vector(String type,String id,int version,String hash,String model,int dimension,String json) {}
    public record RecallItem(String type,String id,int version,double score,String reason) {}
    public record Retrieval(String id,long turn,String feedback,LocalDateTime createdAt,List<RecallItem> items) {}

    private static LocalDateTime time(ResultSet rs,String field) throws SQLException { var value=rs.getTimestamp(field);return value==null?null:value.toLocalDateTime(); }
    private static final RowMapper<Job> JOB=(r,n)->new Job(r.getString("id"),r.getLong("user_id"),r.getLong("revision"),r.getLong("turn_no"),r.getString("task_type"),r.getString("input_text"),r.getString("state"),r.getInt("attempts"),r.getString("lease_token"),time(r,"created_at"));
    private static final RowMapper<Episode> EPISODE=(r,n)->new Episode(r.getString("id"),r.getLong("user_id"),r.getString("job_id"),r.getLong("turn_no"),r.getLong("revision"),r.getString("fact_key"),r.getString("category"),r.getString("content"),r.getString("evidence"),r.getString("evidence_hash"),r.getString("operation"),r.getString("subject"),r.getString("temporal_type"),r.getBoolean("explicit_change"),r.getString("goal"),r.getDouble("importance"),r.getDouble("attention"),r.getString("state"),time(r,"occurred_at"),r.getString("time_evidence"),time(r,"recorded_at"),time(r,"expires_at"),r.getBoolean("consolidated"));
    private static final RowMapper<Semantic> SEMANTIC=(r,n)->new Semantic(r.getLong("id"),r.getString("fact_key"),r.getString("category"),r.getString("content"),r.getString("source_text"),r.getString("status"),r.getString("pending_content"),r.getLong("last_turn"),r.getInt("semantic_version"),r.getDouble("importance"),r.getBoolean("pinned"),r.getString("temporal_type"),time(r,"last_accessed_at"),r.getInt("useful_count"),time(r,"created_at"),time(r,"updated_at"),time(r,"expires_at"));

    public Profile profile(Long user) {
        return jdbc.queryForObject("SELECT * FROM user_memory_settings WHERE user_id=?",(r,n)->new Profile(r.getDouble("encoding_threshold"),r.getDouble("relevance_weight"),r.getDouble("decay_multiplier"),r.getInt("context_budget"),r.getInt("feedback_count"),time(r,"last_consolidated_at")),user);
    }
    public void adapt(Long user,double threshold,double relevance,double decay,int budget) {
        jdbc.update("UPDATE user_memory_settings SET encoding_threshold=?,relevance_weight=?,decay_multiplier=?,context_budget=?,feedback_count=feedback_count+1 WHERE user_id=?",threshold,relevance,decay,budget,user);
    }
    public void consolidated(Long user,LocalDateTime now) { jdbc.update("UPDATE user_memory_settings SET last_consolidated_at=? WHERE user_id=?",now,user); }

    public String enqueue(Long user,long revision,long turn,String type,String input) {
        return enqueue(user,revision,turn,type,input,LocalDateTime.now());
    }
    public String enqueue(Long user,long revision,long turn,String type,String input,LocalDateTime observedAt) {
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO memory_outbox(id,user_id,revision,turn_no,task_type,input_text,created_at) VALUES(?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id",id,user,revision,turn,type,input,observedAt);
        return jdbc.queryForObject("SELECT id FROM memory_outbox WHERE user_id=? AND turn_no=? AND task_type=?",String.class,user,turn,type);
    }
    public List<Job> due(LocalDateTime now) {
        return jdbc.query("SELECT * FROM memory_outbox WHERE next_publish_at<=? AND ((state='PENDING' AND available_at<=?) OR (state='PROCESSING' AND lease_until<=?)) ORDER BY created_at LIMIT 32",JOB,now,now,now);
    }
    public boolean reservePublish(String id,LocalDateTime now) {
        return jdbc.update("UPDATE memory_outbox SET next_publish_at=? WHERE id=? AND next_publish_at<=? AND state IN ('PENDING','PROCESSING')",now.plusSeconds(30),id,now)==1;
    }
    public Job job(String id) { return jdbc.query("SELECT * FROM memory_outbox WHERE id=?",JOB,id).stream().findFirst().orElse(null); }
    public String claim(String id,LocalDateTime now,int leaseSeconds,int maxAttempts) {
        String token=UUID.randomUUID().toString();
        int updated=jdbc.update("UPDATE memory_outbox SET state='PROCESSING',attempts=attempts+1,lease_token=?,lease_until=?,next_publish_at=? WHERE id=? AND attempts<? AND ((state='PENDING' AND available_at<=?) OR (state='PROCESSING' AND lease_until<=?))",
                token,now.plusSeconds(leaseSeconds),now.plusSeconds(leaseSeconds),id,maxAttempts,now,now);
        return updated==1?token:null;
    }
    public boolean owns(String id,String token) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM memory_outbox WHERE id=? AND state='PROCESSING' AND lease_token=?",Integer.class,id,token)>0;
    }
    public void done(String id,String token,String state) {
        jdbc.update("UPDATE memory_outbox SET state=?,input_text='',completed_at=CURRENT_TIMESTAMP,lease_until=NULL,lease_token=NULL WHERE id=? AND lease_token=?",state,id,token);
    }
    public void retry(String id,String token,String code,int maxAttempts,LocalDateTime now) {
        Job job=job(id);
        if(job==null||!Objects.equals(job.leaseToken(),token)) return;
        boolean exhausted=job.attempts()>=maxAttempts;
        jdbc.update("UPDATE memory_outbox SET state=?,available_at=?,next_publish_at=?,lease_until=NULL,lease_token=NULL,last_error=?,input_text=CASE WHEN ? THEN '' ELSE input_text END WHERE id=? AND lease_token=?",
                exhausted?"FAILED":"PENDING",now.plusSeconds(15L*job.attempts()),now.plusSeconds(15L*job.attempts()),code,exhausted,id,token);
    }
    public void expireJobs(LocalDateTime now,int maxAttempts) {
        jdbc.update("UPDATE memory_outbox SET state='FAILED',input_text='',lease_token=NULL,last_error='LEASE_EXHAUSTED' WHERE state='PROCESSING' AND lease_until<=? AND attempts>=?",now,maxAttempts);
        jdbc.update("DELETE FROM memory_outbox WHERE state IN ('DONE','CANCELLED','FAILED') AND created_at<?",now.minusDays(7));
    }
    public void cancelJobs(Long user) { jdbc.update("UPDATE memory_outbox SET state='CANCELLED',input_text='',lease_token=NULL WHERE user_id=? AND state IN ('PENDING','PROCESSING')",user); }
    public long countJobs(Long user,String state) { return jdbc.queryForObject("SELECT COUNT(*) FROM memory_outbox WHERE user_id=? AND state=?",Long.class,user,state); }
    public boolean hasPendingWork(Long user) { return jdbc.queryForObject("SELECT COUNT(*) FROM memory_outbox WHERE user_id=? AND state IN ('PENDING','PROCESSING')",Integer.class,user)>0; }

    public void insertEpisode(Episode e) {
        jdbc.update("INSERT INTO memory_episode(id,user_id,job_id,turn_no,revision,fact_key,category,content,evidence,evidence_hash,operation,subject,temporal_type,explicit_change,goal,importance,attention,state,occurred_at,time_evidence,recorded_at,expires_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id",
                e.id(),e.userId(),e.jobId(),e.turn(),e.revision(),e.factKey(),e.category(),e.content(),e.evidence(),e.evidenceHash(),e.operation(),e.subject(),e.temporalType(),e.explicitChange(),e.goal(),e.importance(),e.attention(),e.state(),e.occurredAt(),e.timeEvidence(),e.recordedAt(),e.expiresAt());
    }
    public List<Episode> episodes(Long user,int limit,boolean archived) {
        return jdbc.query("SELECT * FROM memory_episode WHERE user_id=? AND state "+(archived?"IN ('ACTIVE','ARCHIVED')":"='ACTIVE'")+" ORDER BY turn_no DESC,id LIMIT ?",EPISODE,user,limit);
    }
    public List<Episode> unconsolidated(Long user,int limit) {
        return jdbc.query("SELECT * FROM memory_episode WHERE user_id=? AND state='ACTIVE' AND consolidated=FALSE ORDER BY turn_no,id LIMIT ?",EPISODE,user,limit);
    }
    public List<Episode> factEpisodes(Long user,String key) { return jdbc.query("SELECT * FROM memory_episode WHERE user_id=? AND fact_key=? ORDER BY turn_no,id",EPISODE,user,key); }
    public Episode episode(Long user,String id) { return jdbc.query("SELECT * FROM memory_episode WHERE user_id=? AND id=?",EPISODE,user,id).stream().findFirst().orElse(null); }
    public void markConsolidated(Long user,String id) { jdbc.update("UPDATE memory_episode SET consolidated=TRUE WHERE user_id=? AND id=?",user,id); }
    public void archiveEpisode(Long user,String id) { jdbc.update("UPDATE memory_episode SET state='ARCHIVED' WHERE user_id=? AND id=?",user,id);dropVector(user,"EPISODE",id); }
    public void deleteEpisode(Long user,String id) { dropIndex(user,"EPISODE",id);jdbc.update("DELETE FROM memory_episode WHERE user_id=? AND id=?",user,id); }
    public void reopenFactEpisodes(Long user,String key) { jdbc.update("UPDATE memory_episode SET consolidated=FALSE WHERE user_id=? AND fact_key=? AND state='ACTIVE'",user,key); }
    public boolean canPruneEpisode(Long user,String id) {
        // Keep at least one source per dependent version/relation. Compression never increases confidence.
        return jdbc.queryForObject("SELECT COUNT(*) FROM memory_evidence e WHERE e.user_id=? AND e.episode_id=? AND NOT EXISTS (SELECT 1 FROM memory_evidence other WHERE other.user_id=e.user_id AND other.memory_id=e.memory_id AND other.version_no=e.version_no AND other.relation=e.relation AND other.episode_id<>e.episode_id)",Integer.class,user,id)==0;
    }

    public List<Semantic> semantics(Long user,boolean archived,int limit) {
        return jdbc.query("SELECT * FROM user_memory WHERE user_id=? "+(archived?"":"AND status IN ('ACTIVE','CONFLICT') ")+"ORDER BY pinned DESC,importance DESC,updated_at DESC,id DESC LIMIT ?",SEMANTIC,user,limit);
    }
    public Semantic semantic(Long user,Long id) { return jdbc.query("SELECT * FROM user_memory WHERE user_id=? AND id=?",SEMANTIC,user,id).stream().findFirst().orElse(null); }
    public Semantic fact(Long user,String key) { return jdbc.query("SELECT * FROM user_memory WHERE user_id=? AND fact_key=?",SEMANTIC,user,key).stream().findFirst().orElse(null); }
    public void metadata(Long user,Long id,int version,double importance,String temporalType) {
        jdbc.update("UPDATE user_memory SET semantic_version=?,importance=?,temporal_type=? WHERE user_id=? AND id=?",version,importance,temporalType,user,id);
    }
    public void pinned(Long user,Long id,boolean pinned) { jdbc.update("UPDATE user_memory SET pinned=? WHERE user_id=? AND id=?",pinned,user,id); }
    public void status(Long user,Long id,String status) { jdbc.update("UPDATE user_memory SET status=?,updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND id=?",status,user,id); if("ARCHIVED".equals(status)) dropVector(user,"SEMANTIC",id.toString());else if(!"ACTIVE".equals(status)) dropIndex(user,"SEMANTIC",id.toString()); }
    public void snapshotVersion(Long user,Semantic s) {
        jdbc.update("UPDATE memory_semantic_version SET valid_to=CURRENT_TIMESTAMP WHERE user_id=? AND memory_id=? AND valid_to IS NULL AND version_no<?",user,s.id(),s.version());
        jdbc.update("INSERT INTO memory_semantic_version(id,user_id,memory_id,version_no,content,source_text,status,pending_content,valid_from) VALUES(?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id",
                UUID.randomUUID().toString(),user,s.id(),s.version(),s.content(),s.sourceText(),s.status(),s.pendingContent(),s.updatedAt());
    }
    public void evidence(Long user,Long memory,int version,String episode,String relation) {
        jdbc.update("INSERT INTO memory_evidence(user_id,memory_id,version_no,episode_id,relation) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE relation=VALUES(relation)",user,memory,version,episode,relation);
    }
    public List<String> evidenceIds(Long user,Long memory) { return jdbc.queryForList("SELECT DISTINCT episode_id FROM memory_evidence WHERE user_id=? AND memory_id=?",String.class,user,memory); }
    public List<Long> dependents(Long user,String episode) { return jdbc.queryForList("SELECT DISTINCT memory_id FROM memory_evidence WHERE user_id=? AND episode_id=?",Long.class,user,episode); }
    public List<Version> versions(Long user,Long memory) {
        return jdbc.query("SELECT * FROM memory_semantic_version WHERE user_id=? AND memory_id=? ORDER BY version_no DESC",(r,n)->new Version(r.getString("id"),r.getLong("memory_id"),r.getInt("version_no"),r.getString("content"),r.getString("source_text"),r.getString("status"),r.getString("pending_content"),time(r,"valid_from"),time(r,"valid_to")),user,memory);
    }
    public void removeFactDependencies(Long user,Long memory) {
        dropIndex(user,"SEMANTIC",memory.toString());
        jdbc.update("DELETE FROM memory_retrieval_item WHERE memory_type='SEMANTIC' AND memory_id=? AND retrieval_id IN (SELECT id FROM memory_retrieval WHERE user_id=?)",memory.toString(),user);
    }

    public void entity(Long user,String entity,String type,String id) {
        jdbc.update("INSERT INTO memory_entity(user_id,entity,memory_type,memory_id) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE entity=VALUES(entity)",user,entity,type,id);
    }
    public Set<String> entities(Long user,String type,String id) { return new LinkedHashSet<>(jdbc.queryForList("SELECT entity FROM memory_entity WHERE user_id=? AND memory_type=? AND memory_id=?",String.class,user,type,id)); }
    public Map<String,Set<String>> entityIndex(Long user) {
        Map<String,Set<String>> result=new HashMap<>();
        jdbc.query("SELECT entity,memory_type,memory_id FROM memory_entity WHERE user_id=?",r->{
            result.computeIfAbsent(r.getString("memory_type")+":"+r.getString("memory_id"),k->new LinkedHashSet<>()).add(r.getString("entity"));
        },user);
        return result;
    }
    public void dropIndex(Long user,String type,String id) {
        jdbc.update("DELETE FROM memory_entity WHERE user_id=? AND memory_type=? AND memory_id=?",user,type,id);
        jdbc.update("DELETE FROM memory_vector WHERE user_id=? AND memory_type=? AND memory_id=?",user,type,id);
    }
    public void dropVector(Long user,String type,String id) { jdbc.update("DELETE FROM memory_vector WHERE user_id=? AND memory_type=? AND memory_id=?",user,type,id); }
    public void vector(Long user,Vector v) {
        jdbc.update("INSERT INTO memory_vector(user_id,memory_type,memory_id,version_no,content_hash,model,dimension_no,vector_json) VALUES(?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE version_no=VALUES(version_no),content_hash=VALUES(content_hash),model=VALUES(model),dimension_no=VALUES(dimension_no),vector_json=VALUES(vector_json)",user,v.type(),v.id(),v.version(),v.hash(),v.model(),v.dimension(),v.json());
    }
    public List<Vector> vectors(Long user) { return jdbc.query("SELECT * FROM memory_vector WHERE user_id=?",(r,n)->new Vector(r.getString("memory_type"),r.getString("memory_id"),r.getInt("version_no"),r.getString("content_hash"),r.getString("model"),r.getInt("dimension_no"),r.getString("vector_json")),user); }

    public String retrieval(Long user,long revision,long turn,List<RecallItem> items) {
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO memory_retrieval(id,user_id,revision,turn_no) VALUES(?,?,?,?)",id,user,revision,turn);
        for(var item:items) jdbc.update("INSERT INTO memory_retrieval_item(retrieval_id,memory_type,memory_id,version_no,score,reason) VALUES(?,?,?,?,?,?)",id,item.type(),item.id(),item.version(),item.score(),item.reason());
        return id;
    }
    public Retrieval retrieval(Long user,String id) {
        return jdbc.query("SELECT * FROM memory_retrieval WHERE user_id=? AND id=? AND completed=TRUE",(r,n)->new Retrieval(id,r.getLong("turn_no"),r.getString("feedback"),time(r,"created_at"),jdbc.query("SELECT * FROM memory_retrieval_item WHERE retrieval_id=?",(i,j)->new RecallItem(i.getString("memory_type"),i.getString("memory_id"),i.getInt("version_no"),i.getDouble("score"),i.getString("reason")),id)),user,id).stream().findFirst().orElse(null);
    }
    public List<Retrieval> retrievals(Long user) { return jdbc.queryForList("SELECT id FROM memory_retrieval WHERE user_id=? AND completed=TRUE ORDER BY turn_no DESC LIMIT 10",String.class,user).stream().map(id->retrieval(user,id)).toList(); }
    public boolean completeRetrieval(Long user,long turn) { return jdbc.update("UPDATE memory_retrieval SET completed=TRUE WHERE user_id=? AND turn_no=? AND completed=FALSE",user,turn)==1; }
    public void feedback(Long user,String id,String feedback) { jdbc.update("UPDATE memory_retrieval SET feedback=? WHERE user_id=? AND id=? AND feedback IS NULL",feedback,user,id); }
    public void useful(Long user,Long id,int version,LocalDateTime now) { jdbc.update("UPDATE user_memory SET useful_count=useful_count+1,last_accessed_at=?,status='ACTIVE' WHERE user_id=? AND id=? AND semantic_version=? AND status IN ('ACTIVE','ARCHIVED') AND (expires_at IS NULL OR expires_at>?)",now,user,id,version,now); }
    public void exposure(Long user,Long id,LocalDateTime time) {
        jdbc.update("UPDATE user_memory SET last_accessed_at=CASE WHEN last_accessed_at IS NULL OR last_accessed_at<? THEN ? ELSE last_accessed_at END WHERE user_id=? AND id=?",time,time,user,id);
    }
    public void pruneTraces(LocalDateTime before) { jdbc.update("DELETE FROM memory_retrieval WHERE created_at<?",before); }
    public void clearRetrievals(Long user) { jdbc.update("DELETE FROM memory_retrieval WHERE user_id=?",user); }
    public List<Long> users() { return jdbc.queryForList("SELECT user_id FROM user_memory_settings WHERE enabled=TRUE ORDER BY user_id",Long.class); }
    public void clearDerived(Long user) {
        jdbc.update("DELETE FROM memory_retrieval WHERE user_id=?",user);
        jdbc.update("DELETE FROM memory_vector WHERE user_id=?",user);
        jdbc.update("DELETE FROM memory_entity WHERE user_id=?",user);
        jdbc.update("DELETE FROM memory_episode WHERE user_id=?",user);
    }
}
