package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.security.SecurityUser;
import com.zc.agent.core.security.TenantScopeContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** timer/event/webhook 持久化等待、信号唤醒和 Run 重新入队服务。 */
@Service
public class DurableWaitService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final byte[] webhookSecret;
    private final long webhookClockSkewSeconds;

    @Autowired
    public DurableWaitService(JdbcTemplate jdbc,
                              ObjectMapper objectMapper,
                              @Value("${core.runtime.wait.webhook-secret}") String webhookSecret,
                              @Value("${core.runtime.wait.webhook-clock-skew-seconds:300}") long webhookClockSkewSeconds) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        if (webhookSecret == null || webhookSecret.length() < 24) {
            throw new IllegalStateException("core.runtime.wait.webhook-secret 长度不能少于24字符");
        }
        this.webhookSecret = webhookSecret.getBytes(StandardCharsets.UTF_8);
        this.webhookClockSkewSeconds = Math.max(30, webhookClockSkewSeconds);
    }

    /** 单元测试兼容构造器。 */
    DurableWaitService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this(jdbc, objectMapper, "agent-wait-webhook-test-secret", 300);
    }

    public WakeResult await(ExecutionContext context, ExecutionPlan.ExecutionPlanStep step, Long waitMillis, Long operatorId) {
        Map<String,Object> inputs=step.inputs()==null?Map.of():step.inputs();
        String waitType=String.valueOf(inputs.getOrDefault("waitType",waitMillis>0?"timer":"event"));
        if(!List.of("timer","event","webhook").contains(waitType))throw new BusinessException("waitType 仅支持 timer/event/webhook");
        Long taskId=context.taskContext().taskId();
        List<Map<String,Object>> existing=jdbc.queryForList("SELECT event_key,status,event_payload::text FROM task_wait_subscriptions WHERE task_id=? AND step_no=?",taskId,step.stepNo());
        if(!existing.isEmpty()){
            Map<String,Object> record=existing.get(0);
            if("signaled".equals(record.get("status")))return new WakeResult(String.valueOf(record.get("event_key")),String.valueOf(record.get("event_payload")));
            throw new ExecutionSuspendedException("任务仍在等待事件: "+record.get("event_key"),String.valueOf(record.get("event_key")));
        }
        String configured=String.valueOf(inputs.getOrDefault("eventKey",""));
        String eventKey=configured.isBlank()?"WAIT_"+UUID.randomUUID().toString().replace("-","").toUpperCase():configured;
        LocalDateTime wakeAt="timer".equals(waitType)?LocalDateTime.now().plusNanos(Math.max(1L,waitMillis)*1_000_000L):null;
        TenantScopeContext.Scope scope=TenantScopeContext.current();
        long tenantId=scope==null||scope.tenantId()==null?1L:scope.tenantId();long projectId=scope==null||scope.projectId()==null?1L:scope.projectId();
        jdbc.update("INSERT INTO task_wait_subscriptions(tenant_id,project_id,task_id,step_no,wait_type,event_key,wake_at,created_by) VALUES(?,?,?,?,?,?,?,?)",
                tenantId,projectId,taskId,step.stepNo(),waitType,eventKey,wakeAt,operatorId);
        ensureResumableRun(context, eventKey, operatorId);
        throw new ExecutionSuspendedException("任务已挂起，等待 "+waitType+" 信号: "+eventKey,eventKey);
    }

    public void signal(String eventKey,Map<String,Object> payload,SecurityUser user){
        int updated=jdbc.update("""
                UPDATE task_wait_subscriptions w SET status='signaled',event_payload=CAST(? AS jsonb),signaled_by=?,signaled_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP
                FROM tasks t JOIN conversations c ON c.id=t.conversation_id AND c.is_deleted=FALSE
                WHERE w.task_id=t.id AND w.event_key=? AND w.status='waiting' AND c.user_id=?
                """,json(payload),user.userId(),eventKey,user.userId());
        if(updated==0)throw new BusinessException("等待事件不存在、已处理或无权访问");requeue(eventKey);
    }

    /** 校验外部 Webhook 签名、防重放信息后唤醒任务。 */
    public void signalWebhook(String eventKey,
                              String timestamp,
                              String nonce,
                              String signature,
                              String rawPayload) {
        long epochSeconds;
        try {
            epochSeconds = Long.parseLong(timestamp);
        } catch (Exception exception) {
            throw new BusinessException("Webhook timestamp 非法");
        }
        if (nonce == null || nonce.isBlank() || nonce.length() > 128) {
            throw new BusinessException("Webhook nonce 非法");
        }
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - epochSeconds) > webhookClockSkewSeconds) {
            throw new BusinessException("Webhook 请求已过期");
        }
        String body = rawPayload == null ? "{}" : rawPayload;
        String expected = hmac(timestamp + "." + nonce + "." + eventKey + "." + body);
        if (signature == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException("Webhook 签名校验失败");
        }
        try {
            jdbc.update("""
                    INSERT INTO task_wait_webhook_nonces(event_key,nonce,expires_at)
                    VALUES(?,?,CURRENT_TIMESTAMP + (? * INTERVAL '1 second'))
                    """, eventKey, nonce, webhookClockSkewSeconds);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("Webhook nonce 已使用，拒绝重放");
        }
        int updated = jdbc.update("""
                UPDATE task_wait_subscriptions SET status='signaled',event_payload=CAST(? AS jsonb),
                    signaled_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP
                WHERE event_key=? AND wait_type='webhook' AND status='waiting'
                """, normalizePayload(body), eventKey);
        if (updated == 0) {
            throw new BusinessException("Webhook 等待事件不存在或已处理");
        }
        requeue(eventKey);
    }

    @Scheduled(fixedDelayString="${core.runtime.wait.scan-delay-ms:1000}")
    public void signalDueTimers(){
        List<String> keys=jdbc.queryForList("UPDATE task_wait_subscriptions SET status='signaled',signaled_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE status='waiting' AND wait_type='timer' AND wake_at<=CURRENT_TIMESTAMP RETURNING event_key",String.class);
        keys.forEach(this::requeue);
    }

    @Scheduled(fixedDelayString="${core.runtime.wait.nonce-cleanup-delay-ms:60000}")
    public void cleanupExpiredWebhookNonces() {
        jdbc.update("DELETE FROM task_wait_webhook_nonces WHERE expires_at<CURRENT_TIMESTAMP");
    }

    /** 同步消息执行遇到 wait 时自动补建 waiting_event Run，确保信号到达后存在可领取载体。 */
    private void ensureResumableRun(ExecutionContext context, String eventKey, Long operatorId) {
        Long taskId = context.taskContext().taskId();
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM agent_runs WHERE task_id=? AND is_deleted=FALSE", Integer.class, taskId);
        if (existing != null && existing > 0) {
            return;
        }
        TenantScopeContext.Scope scope = TenantScopeContext.current();
        ExecutionContext.CurrentUserContext current = context.currentUser();
        SecurityUser user = new SecurityUser(current.userId(), current.username(), current.displayName(),
                current.roleCodes(), current.menuScopes(), scope == null ? 1L : scope.tenantId(),
                scope == null ? 1L : scope.organizationId(), scope == null ? 1L : scope.workspaceId(),
                scope == null ? 1L : scope.projectId());
        String runCode = "RUN_WAIT_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
        jdbc.update("""
                INSERT INTO agent_runs(run_code,conversation_id,user_id,agent_id,input_text,user_context,idempotency_key,
                    status,max_attempts,priority,task_id,available_at,worker_group_code,required_capabilities,created_at,updated_at)
                VALUES(?,?,?,?,?,CAST(? AS jsonb),?,'waiting_event',2,50,?,CURRENT_TIMESTAMP,'default','[]'::jsonb,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, runCode, context.conversationContext().conversationId(), current.userId(), context.agentContext().agentId(),
                context.userMessage(), json(user), "durable-wait-task-" + taskId, taskId);
        jdbc.update("UPDATE tasks SET run_code=?,updated_at=CURRENT_TIMESTAMP,updated_by=? WHERE id=? AND run_code IS NULL",
                runCode, operatorId, taskId);
        jdbc.update("UPDATE task_wait_subscriptions SET promoted_run_code=? WHERE event_key=?", runCode, eventKey);
    }

    private void requeue(String eventKey){
        jdbc.update("UPDATE task_steps SET status='failed',error_message='等待事件已到达，准备续跑',updated_at=CURRENT_TIMESTAMP WHERE task_id=(SELECT task_id FROM task_wait_subscriptions WHERE event_key=?) AND status='waiting_event'",eventKey);
        jdbc.update("UPDATE tasks SET status='failed',error_message='等待事件已到达',updated_at=CURRENT_TIMESTAMP WHERE id=(SELECT task_id FROM task_wait_subscriptions WHERE event_key=?)",eventKey);
        jdbc.update("UPDATE agent_runs SET status='queued',available_at=CURRENT_TIMESTAMP,worker_id=NULL,lease_until=NULL,max_attempts=GREATEST(max_attempts,attempt_count+1),updated_at=CURRENT_TIMESTAMP WHERE task_id=(SELECT task_id FROM task_wait_subscriptions WHERE event_key=?) AND status='waiting_event'",eventKey);
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret, "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Webhook HMAC 初始化失败", exception);
        }
    }

    private String normalizePayload(String rawPayload) {
        try {
            return objectMapper.writeValueAsString(objectMapper.readTree(rawPayload));
        } catch (Exception exception) {
            throw new BusinessException("Webhook payload 不是合法 JSON");
        }
    }

    private String json(Object value){try{return objectMapper.writeValueAsString(value==null?Map.of():value);}catch(Exception e){return "{}";}}
    public record WakeResult(String eventKey,String payloadJson){}
}
