package com.zc.agent.core.modules.conversation.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.conversation.dto.AgentRunControlRequests.RoutingRuleRequest;
import com.zc.agent.core.modules.conversation.dto.AgentRunControlRequests.WorkerGroupRequest;
import com.zc.agent.core.modules.conversation.model.AgentRunControlModels.RoutingDecision;
import com.zc.agent.core.modules.conversation.model.AgentRunControlModels.RoutingRule;
import com.zc.agent.core.modules.conversation.model.AgentRunControlModels.WorkerGroup;
import com.zc.agent.core.modules.conversation.model.AgentRunControlModels.WorkerNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Agent Run Worker Group、节点与路由规则持久层。 */
@Repository
public class AgentRunControlPlaneRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    public AgentRunControlPlaneRepository(JdbcTemplate jdbcTemplate,ObjectMapper objectMapper){this.jdbcTemplate=jdbcTemplate;this.objectMapper=objectMapper;}

    public void ensureGroup(String code,String name,int concurrency,int capacity,List<String> capabilities){jdbcTemplate.update("""
            INSERT INTO agent_worker_groups(group_code,group_name,max_concurrency,queue_capacity,required_capabilities,status)
            VALUES(?,?,?,?,CAST(? AS JSONB),'enabled') ON CONFLICT(tenant_id,project_id,group_code) DO NOTHING
            """,code,name,concurrency,capacity,json(capabilities));}

    public Optional<RoutingDecision> route(Long agentId,int priority,String fallbackGroup){
        List<RoutingDecision> rules=jdbcTemplate.query("""
                SELECT g.group_code,r.required_capabilities,g.queue_capacity,g.max_concurrency,r.rule_code
                FROM agent_run_routing_rules r JOIN agent_worker_groups g ON g.group_code=r.target_group_code
                 AND g.tenant_id=r.tenant_id AND g.project_id=r.project_id
                WHERE r.status='enabled' AND g.status='enabled' AND (r.agent_id IS NULL OR r.agent_id=?)
                  AND (r.min_priority IS NULL OR r.min_priority<=?) AND (r.max_priority IS NULL OR r.max_priority>=?)
                ORDER BY r.priority,r.id LIMIT 1
                """,(rs,n)->new RoutingDecision(rs.getString(1),list(rs.getString(2)),rs.getInt(3),rs.getInt(4),rs.getString(5)),agentId,priority,priority);
        if(!rules.isEmpty())return Optional.of(rules.get(0));
        return jdbcTemplate.query("SELECT group_code,required_capabilities,queue_capacity,max_concurrency FROM agent_worker_groups WHERE group_code=? AND status='enabled' LIMIT 1",
                (rs,n)->new RoutingDecision(rs.getString(1),list(rs.getString(2)),rs.getInt(3),rs.getInt(4),"fallback"),fallbackGroup).stream().findFirst();
    }

    public List<WorkerGroup> groups(){return jdbcTemplate.query("""
            SELECT g.*,COUNT(DISTINCT n.id) FILTER(WHERE n.status='online' AND n.last_heartbeat_at>CURRENT_TIMESTAMP-INTERVAL '90 seconds') online_workers,
              COUNT(DISTINCT r.id) FILTER(WHERE r.status='queued') queued_count,COUNT(DISTINCT r.id) FILTER(WHERE r.status='running') running_count,
              MIN(r.created_at) FILTER(WHERE r.status='queued') oldest_queued_at
            FROM agent_worker_groups g LEFT JOIN agent_worker_nodes n ON n.group_code=g.group_code
             LEFT JOIN agent_runs r ON r.worker_group_code=g.group_code AND r.is_deleted=FALSE
            GROUP BY g.id ORDER BY g.group_code
            """,(rs,n)->new WorkerGroup(rs.getLong("id"),rs.getString("group_code"),rs.getString("group_name"),rs.getInt("max_concurrency"),rs.getInt("queue_capacity"),list(rs.getString("required_capabilities")),rs.getString("status"),rs.getLong("queued_count"),rs.getLong("running_count"),rs.getLong("online_workers"),rs.getTimestamp("oldest_queued_at")==null?null:rs.getTimestamp("oldest_queued_at").toLocalDateTime()));}

    public List<WorkerNode> nodes(){return jdbcTemplate.query("SELECT * FROM agent_worker_nodes ORDER BY last_heartbeat_at DESC",(rs,n)->new WorkerNode(rs.getLong("id"),rs.getString("worker_id"),rs.getString("group_code"),rs.getString("instance_address"),list(rs.getString("capabilities")),rs.getInt("max_concurrency"),rs.getInt("current_load"),rs.getString("status"),rs.getTimestamp("started_at").toLocalDateTime(),rs.getTimestamp("last_heartbeat_at").toLocalDateTime(),rs.getTimestamp("stopped_at")==null?null:rs.getTimestamp("stopped_at").toLocalDateTime()));}

    public List<RoutingRule> rules(){return jdbcTemplate.query("SELECT * FROM agent_run_routing_rules ORDER BY priority,id",(rs,n)->new RoutingRule(rs.getLong("id"),rs.getString("rule_code"),rs.getString("rule_name"),(Long)rs.getObject("agent_id"),(Integer)rs.getObject("min_priority"),(Integer)rs.getObject("max_priority"),list(rs.getString("required_capabilities")),rs.getString("target_group_code"),rs.getInt("priority"),rs.getString("status")));}

    public void saveGroup(Long id,WorkerGroupRequest r,Long user){if(id==null)jdbcTemplate.update("INSERT INTO agent_worker_groups(group_code,group_name,max_concurrency,queue_capacity,required_capabilities,status,created_by,updated_by) VALUES(?,?,?,?,CAST(? AS JSONB),?,?,?)",r.groupCode(),r.groupName(),r.maxConcurrency(),r.queueCapacity(),json(r.requiredCapabilities()),r.status(),user,user);else jdbcTemplate.update("UPDATE agent_worker_groups SET group_name=?,max_concurrency=?,queue_capacity=?,required_capabilities=CAST(? AS JSONB),status=?,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",r.groupName(),r.maxConcurrency(),r.queueCapacity(),json(r.requiredCapabilities()),r.status(),user,id);}
    public void saveRule(Long id,RoutingRuleRequest r,Long user){if(id==null)jdbcTemplate.update("INSERT INTO agent_run_routing_rules(rule_code,rule_name,agent_id,min_priority,max_priority,required_capabilities,target_group_code,priority,status,created_by,updated_by) VALUES(?,?,?,?,?,CAST(? AS JSONB),?,?,?,?,?)",r.ruleCode(),r.ruleName(),r.agentId(),r.minPriority(),r.maxPriority(),json(r.requiredCapabilities()),r.targetGroupCode(),r.priority(),r.status(),user,user);else jdbcTemplate.update("UPDATE agent_run_routing_rules SET rule_name=?,agent_id=?,min_priority=?,max_priority=?,required_capabilities=CAST(? AS JSONB),target_group_code=?,priority=?,status=?,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",r.ruleName(),r.agentId(),r.minPriority(),r.maxPriority(),json(r.requiredCapabilities()),r.targetGroupCode(),r.priority(),r.status(),user,id);}

    public void heartbeat(String workerId,String group,String address,List<String> capabilities,int maxConcurrency,int load){jdbcTemplate.update("""
            INSERT INTO agent_worker_nodes(worker_id,group_code,instance_address,capabilities,max_concurrency,current_load,status)
            VALUES(?,?,?,CAST(? AS JSONB),?,?,'online') ON CONFLICT(tenant_id,project_id,worker_id) DO UPDATE SET group_code=EXCLUDED.group_code,
             instance_address=EXCLUDED.instance_address,capabilities=EXCLUDED.capabilities,max_concurrency=EXCLUDED.max_concurrency,
             current_load=EXCLUDED.current_load,status=CASE WHEN agent_worker_nodes.status='draining' THEN 'draining' ELSE 'online' END,
             last_heartbeat_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP
            """,workerId,group,address,json(capabilities),maxConcurrency,load);}
    public boolean workerAccepting(String workerId){
        List<Boolean> values=jdbcTemplate.query("""
                SELECT status='online' FROM agent_worker_nodes
                WHERE worker_id=? ORDER BY last_heartbeat_at DESC,id DESC LIMIT 1
                """,(rs,n)->rs.getBoolean(1),workerId);
        return values.isEmpty() || Boolean.TRUE.equals(values.get(0));
    }
    public void nodeStatus(String workerId,String status){jdbcTemplate.update("UPDATE agent_worker_nodes SET status=?,stopped_at=CASE WHEN ?='offline' THEN CURRENT_TIMESTAMP ELSE NULL END,updated_at=CURRENT_TIMESTAMP WHERE worker_id=?",status,status,workerId);}
    public int markStaleNodes(int timeoutSeconds){return jdbcTemplate.update("UPDATE agent_worker_nodes SET status='unhealthy',updated_at=CURRENT_TIMESTAMP WHERE status='online' AND last_heartbeat_at<CURRENT_TIMESTAMP-(?*INTERVAL '1 second')",timeoutSeconds);}

    private String json(List<String> values){try{return objectMapper.writeValueAsString(values==null?List.of():values.stream().filter(v->v!=null&&!v.isBlank()).map(String::trim).distinct().toList());}catch(Exception e){return "[]";}}
    private List<String> list(String value){try{return objectMapper.readValue(value,new TypeReference<>(){});}catch(Exception e){return List.of();}}
}
