package com.zc.agent.registry.modules.prompt.service;

import com.zc.agent.registry.common.exception.BusinessException;import com.zc.agent.registry.modules.prompt.dto.PromptExperimentRequests.*;import com.zc.agent.registry.security.SecurityUser;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
import java.util.*;
/** Prompt 实验生命周期、稳定分流和胜出版本发布服务。 */
@Service public class PromptExperimentService{
 private final JdbcTemplate jdbc;public PromptExperimentService(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public List<Map<String,Object>> list(){return jdbc.queryForList("""
  SELECT e.*,COALESCE(jsonb_agg(jsonb_build_object('id',v.id,'variantCode',v.variant_code,'variantName',v.variant_name,'promptId',v.prompt_id,'weight',v.weight,'control',v.is_control,'status',v.status) ORDER BY v.id) FILTER(WHERE v.id IS NOT NULL),'[]') variants
  FROM prompt_experiments e LEFT JOIN prompt_experiment_variants v ON v.experiment_id=e.id GROUP BY e.id ORDER BY e.id DESC
  """);}
 @Transactional public void create(ExperimentRequest r,SecurityUser u){validate(r);Long id=jdbc.queryForObject("""
  INSERT INTO prompt_experiments(experiment_code,experiment_name,base_prompt_id,agent_id,stage,allocation_key,rollout_percent,status,created_by,updated_by,started_at)
  VALUES(?,?,?,?,?,?,?,?,?,?,CASE WHEN ?='running' THEN CURRENT_TIMESTAMP ELSE NULL END) RETURNING id
  """,Long.class,r.experimentCode(),r.experimentName(),r.basePromptId(),r.agentId(),r.stage(),r.allocationKey(),r.rolloutPercent(),r.status(),u.userId(),u.userId(),r.status());saveVariants(id,r.variants());}
 @Transactional public void update(Long id,ExperimentRequest r,SecurityUser u){validate(r);jdbc.update("""
  UPDATE prompt_experiments SET experiment_name=?,base_prompt_id=?,agent_id=?,stage=?,allocation_key=?,rollout_percent=?,status=?,updated_by=?,updated_at=CURRENT_TIMESTAMP,started_at=CASE WHEN ?='running' THEN COALESCE(started_at,CURRENT_TIMESTAMP) ELSE started_at END WHERE id=?
  """,r.experimentName(),r.basePromptId(),r.agentId(),r.stage(),r.allocationKey(),r.rolloutPercent(),r.status(),u.userId(),r.status(),id);jdbc.update("DELETE FROM prompt_experiment_variants WHERE experiment_id=?",id);saveVariants(id,r.variants());}
 @Transactional public void complete(Long id,Long winnerPromptId,SecurityUser u){Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM prompt_experiment_variants WHERE experiment_id=? AND prompt_id=?",Integer.class,id,winnerPromptId);if(count==null||count==0)throw new BusinessException("胜出 Prompt 不属于当前实验");jdbc.update("UPDATE prompt_experiments SET status='completed',winner_prompt_id=?,ended_at=CURRENT_TIMESTAMP,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",winnerPromptId,u.userId(),id);jdbc.update("""
  UPDATE prompts base SET content=winner.content,version=winner.version,updated_by=?,updated_at=CURRENT_TIMESTAMP FROM prompts winner,prompt_experiments e WHERE e.id=? AND base.id=e.base_prompt_id AND winner.id=?
  """,u.userId(),id,winnerPromptId);}
 public Map<String,Object> resolve(ResolveRequest r){List<Map<String,Object>> rows=jdbc.queryForList("""
  SELECT e.id experiment_id,e.experiment_code,e.base_prompt_id,e.allocation_key,e.rollout_percent,v.id variant_id,v.variant_code,v.prompt_id,v.weight,
   p.prompt_code,p.prompt_name,p.prompt_type,p.prompt_category,p.content
  FROM prompt_experiments e JOIN prompt_experiment_variants v ON v.experiment_id=e.id AND v.status='enabled' JOIN prompts p ON p.id=v.prompt_id
  WHERE e.base_prompt_id=? AND e.stage=? AND e.status='running' AND (e.agent_id IS NULL OR e.agent_id=?) AND p.status='enabled' ORDER BY e.id DESC,v.id
  """,r.basePromptId(),r.stage(),r.agentId());if(rows.isEmpty())return Map.of("matched",false);
  long experiment=((Number)rows.get(0).get("experiment_id")).longValue();String key="user".equals(rows.get(0).get("allocation_key"))?String.valueOf(r.userId()):String.valueOf(r.conversationId());int bucket=Math.floorMod(Objects.hash(experiment,key),10000);int rollout=((Number)rows.get(0).get("rollout_percent")).intValue()*100;if(bucket>=rollout)return Map.of("matched",false);
  int total=rows.stream().mapToInt(v->((Number)v.get("weight")).intValue()).sum();int selected=Math.floorMod(Objects.hash("variant",experiment,key),total),cursor=0;Map<String,Object> winner=rows.get(0);for(Map<String,Object> row:rows){cursor+=((Number)row.get("weight")).intValue();if(selected<cursor){winner=row;break;}}
  LinkedHashMap<String,Object> out=new LinkedHashMap<>(winner);out.put("matched",true);return out;}
 private void saveVariants(Long id,List<VariantRequest> values){for(VariantRequest v:values)jdbc.update("INSERT INTO prompt_experiment_variants(experiment_id,variant_code,variant_name,prompt_id,weight,is_control,status) VALUES(?,?,?,?,?,?,?)",id,v.variantCode(),v.variantName(),v.promptId(),v.weight(),v.control(),v.status());}
 private void validate(ExperimentRequest r){if(!Set.of("system","decision","planner").contains(r.stage()))throw new BusinessException("不支持的 Prompt 实验阶段");if(!Set.of("user","conversation").contains(r.allocationKey()))throw new BusinessException("不支持的稳定分流键");if(!Set.of("draft","running","paused","completed","cancelled").contains(r.status()))throw new BusinessException("不支持的实验状态");if(r.variants().stream().filter(v->v.control()).count()!=1)throw new BusinessException("实验必须且只能有一个 control Variant");}
}
