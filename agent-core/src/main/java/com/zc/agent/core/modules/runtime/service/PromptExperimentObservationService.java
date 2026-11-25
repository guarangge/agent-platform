package com.zc.agent.core.modules.runtime.service;import com.zc.agent.core.modules.conversation.client.RegistryAgentClient.PromptExperimentSelection;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Service;import java.util.*;
/** Prompt 实验曝光记录和按真实 Task/Cost 聚合的结果指标。 */@Service public class PromptExperimentObservationService{private final JdbcTemplate jdbc;public PromptExperimentObservationService(JdbcTemplate jdbc){this.jdbc=jdbc;}public void expose(PromptExperimentSelection s,Long user,Long agent,Long conversation,Long task){jdbc.update("""
 INSERT INTO prompt_experiment_exposures(experiment_id,experiment_code,variant_id,variant_code,base_prompt_id,selected_prompt_id,stage,user_id,agent_id,conversation_id,task_id)
 VALUES(?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(task_id,stage) DO NOTHING
 """,s.experimentId(),s.experimentCode(),s.variantId(),s.variantCode(),s.basePromptId(),s.selectedPromptId(),s.stage(),user,agent,conversation,task);}
 public List<Map<String,Object>> metrics(Long experimentId){return jdbc.queryForList("""
 SELECT e.experiment_id,e.experiment_code,e.variant_id,e.variant_code,COUNT(DISTINCT e.id) exposures,
  COUNT(DISTINCT e.id) FILTER(WHERE t.status='success') successes,ROUND(100.0*COUNT(DISTINCT e.id) FILTER(WHERE t.status='success')/GREATEST(COUNT(DISTINCT e.id),1),2) success_rate,
  COUNT(DISTINCT e.id) FILTER(WHERE t.status='failed') failures,ROUND(AVG(DISTINCT EXTRACT(EPOCH FROM(COALESCE(t.updated_at,CURRENT_TIMESTAMP)-t.created_at))*1000),2) avg_duration_ms,
  COALESCE(SUM(u.amount),0) total_cost
 FROM prompt_experiment_exposures e LEFT JOIN tasks t ON t.id=e.task_id LEFT JOIN usage_events u ON u.task_id=e.task_id
 WHERE (CAST(? AS BIGINT) IS NULL OR e.experiment_id=?) GROUP BY e.experiment_id,e.experiment_code,e.variant_id,e.variant_code ORDER BY e.experiment_id,e.variant_id
 """,experimentId,experimentId);}}
