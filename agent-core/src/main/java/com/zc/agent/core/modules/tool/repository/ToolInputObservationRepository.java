package com.zc.agent.core.modules.tool.repository;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Repository;import java.util.*;
/** Tool 输入契约观测持久层。 */
@Repository public class ToolInputObservationRepository{private final JdbcTemplate jdbc;public ToolInputObservationRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public void insert(String tool,String contract,String provider,String transport,Long user,Long agent,Long conversation,Long task,int raw,int fin,String hash,boolean valid,boolean clamped,String masked,String status,String error,long duration,String metadata){jdbc.update("""
  INSERT INTO tool_input_observations(tool_code,contract_version,provider_type,transport_type,user_id,agent_id,conversation_id,task_id,raw_input_chars,final_input_chars,payload_hash,schema_valid,clamped,sensitive_paths_masked,status,error_code,duration_ms,metadata)
  VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,CAST(? AS JSONB),?,?,?,CAST(? AS JSONB))
  """,tool,contract,provider,transport,user,agent,conversation,task,raw,fin,hash,valid,clamped,masked,status,error,duration,metadata);}
 public List<Map<String,Object>> list(String tool,int limit){return tool==null||tool.isBlank()?jdbc.queryForList("SELECT * FROM tool_input_observations ORDER BY observed_at DESC LIMIT ?",limit):jdbc.queryForList("SELECT * FROM tool_input_observations WHERE tool_code=? ORDER BY observed_at DESC LIMIT ?",tool,limit);}
 public List<Map<String,Object>> metrics(){return jdbc.queryForList("""
  SELECT tool_code,COUNT(*) calls,COUNT(*) FILTER(WHERE status='failed') failures,ROUND(100.0*COUNT(*) FILTER(WHERE status='failed')/GREATEST(COUNT(*),1),2) failure_rate,
   COUNT(*) FILTER(WHERE clamped) clamped_count,ROUND(AVG(final_input_chars),2) avg_input_chars,ROUND(AVG(duration_ms),2) avg_duration_ms,MAX(observed_at) last_observed_at
  FROM tool_input_observations GROUP BY tool_code ORDER BY calls DESC
  """);}}
