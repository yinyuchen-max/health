package com.health.memory;

import com.health.ai.AiRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/** Authoritative per-user original log and append-only checkpoint history. Call writes under user state lock. */
@Repository
public class ChatTranscriptRepository {
    private final JdbcTemplate jdbc;
    public ChatTranscriptRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public record State(Long userId,long revision,long version,long coveredThrough,String checkpoint) {}
    public record Message(long seq,AiRequest.Message value) {}
    private static final RowMapper<State> STATE=(rs,n)->new State(rs.getLong("user_id"),rs.getLong("revision"),
            rs.getLong("version"),rs.getLong("covered_through"),rs.getString("checkpoint_json"));
    private static final RowMapper<Message> MESSAGE=(rs,n)->new Message(rs.getLong("seq"),new AiRequest.Message(
            rs.getString("role"),rs.getString("content"),rs.getString("message_id"),
            rs.getTimestamp("recorded_at")==null?null:rs.getTimestamp("recorded_at").toLocalDateTime().toString()));
    public State state(Long user) {
        return jdbc.query("SELECT * FROM chat_context_state WHERE user_id=?",STATE,user).stream().findFirst().orElse(null);
    }
    public void create(Long user,long revision) {
        jdbc.update("INSERT INTO chat_context_state(user_id,revision) VALUES(?,?)",user,revision);
    }
    public void rebase(Long user,long revision) {
        jdbc.update("UPDATE chat_context_state SET revision=?,lease_token=NULL,lease_until=NULL WHERE user_id=?",revision,user);
    }
    public Message append(Long user,long revision,String turn,String role,String text,LocalDateTime time) {
        String id=UUID.randomUUID().toString();
        try {
            jdbc.update("INSERT INTO chat_message_archive(message_id,turn_key,user_id,revision,role,content,recorded_at) VALUES(?,?,?,?,?,?,?)",
                    id,turn,user,revision,role,text,time==null?null:Timestamp.valueOf(time));
        } catch(DuplicateKeyException duplicate) {
            // Completion callbacks are idempotent; conflicting duplicate content is never accepted.
            var existing=jdbc.query("SELECT * FROM chat_message_archive WHERE user_id=? AND turn_key=? AND role=?",MESSAGE,user,turn,role);
            if(existing.isEmpty() || !existing.get(0).value().content().equals(text)) throw duplicate;
            return existing.get(0);
        }
        return jdbc.queryForObject("SELECT * FROM chat_message_archive WHERE user_id=? AND message_id=?",MESSAGE,user,id);
    }
    public List<Message> active(Long user,long covered,long before) {
        var result=jdbc.query("SELECT * FROM chat_message_archive WHERE user_id=? AND include_in_context=TRUE AND seq>? AND seq<? ORDER BY seq LIMIT 40001",MESSAGE,user,covered,before);
        if(result.size()>40000) throw new IllegalStateException("未压缩上下文超过处理上限");
        return result;
    }
    public List<Message> recall(Long user,long covered,String query) {
        var terms=new LinkedHashSet<String>();
        String normalized=query.toLowerCase(Locale.ROOT);
        if(normalized.matches("(?s).*(药|剂量|过敏|medic|dose|allerg).*")) terms.addAll(List.of("药","过敏","剂量"));
        var words=java.util.regex.Pattern.compile("[a-z0-9]{2,}|[\\p{IsHan}]{2,}").matcher(normalized);
        while(words.find() && terms.size()<10) {
            String word=words.group();
            if(word.matches("[\\p{IsHan}]+")) {
                for(int i=0;i<word.length()-1 && terms.size()<10;i++) terms.add(word.substring(i,i+2));
            } else terms.add(word);
        }
        if(covered==0 || terms.isEmpty()) return List.of();
        String predicates=String.join(" OR ",Collections.nCopies(terms.size(),"content LIKE ? ESCAPE '!'"));
        List<Object> args=new ArrayList<>(List.of(user,covered));
        terms.forEach(term->args.add("%"+term.replace("!","!!").replace("%","!%").replace("_","!_")+"%"));
        return jdbc.query("SELECT * FROM chat_message_archive WHERE user_id=? AND include_in_context=TRUE AND seq<=? AND role='user' AND ("+predicates+") ORDER BY seq DESC LIMIT 16",
                MESSAGE,args.toArray());
    }
    public void excludeOversizedTurn(Long user,String turn) {
        jdbc.update("UPDATE chat_message_archive SET include_in_context=FALSE WHERE user_id=? AND turn_key=?",user,turn);
    }
    public boolean acquire(Long user,long revision,String token,LocalDateTime now) {
        return jdbc.update("UPDATE chat_context_state SET lease_token=?,lease_until=? WHERE user_id=? AND revision=? AND (lease_token IS NULL OR lease_until<?)",
                token,Timestamp.valueOf(now.plusSeconds(45)),user,revision,Timestamp.valueOf(now))==1;
    }
    public void release(Long user,String token) {
        jdbc.update("UPDATE chat_context_state SET lease_token=NULL,lease_until=NULL WHERE user_id=? AND lease_token=?",user,token);
    }
    public boolean checkpoint(State previous,long covered,String payload,long before,long after,String model,String token) {
        int changed=jdbc.update("UPDATE chat_context_state SET version=version+1,covered_through=?,checkpoint_json=? WHERE user_id=? AND revision=? AND version=? AND lease_token=? AND lease_until>?",
                covered,payload,previous.userId(),previous.revision(),previous.version(),token,Timestamp.valueOf(LocalDateTime.now()));
        if(changed==0) return false;
        jdbc.update("INSERT INTO chat_compaction_checkpoint(user_id,revision,version,covered_through,checkpoint_json,before_tokens,after_tokens,model) VALUES(?,?,?,?,?,?,?,?)",
                previous.userId(),previous.revision(),previous.version()+1,covered,payload,before,after,model);
        return true;
    }
    public void clear(Long user) {
        jdbc.update("DELETE FROM chat_compaction_checkpoint WHERE user_id=?",user);
        jdbc.update("DELETE FROM chat_message_archive WHERE user_id=?",user);
        jdbc.update("DELETE FROM chat_context_state WHERE user_id=?",user);
    }
}
