package com.zc.agent.core.modules.conversation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.conversation.model.TaskPlanRecord;
import com.zc.agent.core.modules.conversation.model.TaskPlanStepRecord;
import com.zc.agent.core.modules.conversation.repository.ConversationRepository;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.runtime.protocol.service.PlanRuntimeEventService;
import com.zc.agent.core.modules.runtime.service.ExecutionPlannerService;
import com.zc.agent.core.modules.runtime.service.PlanQualityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** 任务计划版本持久化、执行反馈和历史模板的独立生命周期边界。 */
@Service
public class TaskPlanLifecycleService {
    private final ConversationRepository repository;
    private final PlanQualityService planQualityService;
    private final ObjectMapper objectMapper;
    private final PlanRuntimeEventService planRuntimeEventService;

    public TaskPlanLifecycleService(ConversationRepository repository,
                                    PlanQualityService planQualityService,
                                    ObjectMapper objectMapper,
                                    PlanRuntimeEventService planRuntimeEventService) {
        this.repository = repository;
        this.planQualityService = planQualityService;
        this.objectMapper = objectMapper;
        this.planRuntimeEventService = planRuntimeEventService;
    }

    /**
     * 持久化不可变计划版本，并在同一事务发布对应 Plan Runtime Event。
     *
     * @param taskId Task ID
     * @param context 当前执行上下文
     * @param plan 待持久化计划
     * @param source 计划生成来源
     * @param operatorId 操作人 ID
     */
    @Transactional
    public void persistSnapshot(Long taskId, ExecutionContext context, ExecutionPlan plan, String source, Long operatorId) {
        int version = repository.nextTaskPlanVersion(taskId);
        Map<String, Object> metadata = new LinkedHashMap<>(planQualityService.evaluate(plan, context).toMetadata());
        metadata.put("planVersion", version);
        metadata.put("source", source);
        metadata.put("taskId", taskId);
        TaskPlanRecord previous = repository.findTaskPlansByTaskId(taskId).stream()
                .max(Comparator.comparing(TaskPlanRecord::planVersion))
                .orElse(null);
        Map<String, Object> changeSummary = Map.of();
        if (previous != null) {
            changeSummary = summarizeDiff(previous, plan);
            metadata.put("fromPlanVersion", previous.planVersion());
            metadata.put("diffSummary", changeSummary);
        }
        Long planRecordId = repository.insertTaskPlan(
                taskId, version, plan, promptId(context), promptName(context), source,
                json(plan.assumptions()), json(plan.constraints()), json(plan.risks()), json(metadata), operatorId);
        for (ExecutionPlan.ExecutionPlanStep step : plan.steps()) {
            repository.insertTaskPlanStep(planRecordId, taskId, version, step,
                    json(step.dependsOnStepIds()), json(step.inputs()), operatorId);
        }
        repository.updateTaskCurrentPlanVersion(taskId, version, operatorId);
        PlanRuntimeEventService.PlanEventCommand command = new PlanRuntimeEventService.PlanEventCommand(
                planRecordId, plan.planId(), taskId, version, plan.planType(), plan.status(),
                plan.steps().size(), source, integer(metadata.get("qualityScore")),
                previous == null ? null : previous.planId(),
                previous == null ? null : previous.planVersion(),
                changeSummary,
                context.taskContext() == null ? null : context.taskContext().traceId());
        if (previous == null) {
            planRuntimeEventService.created(command);
        } else {
            planRuntimeEventService.replanned(command);
        }
    }

    public List<ExecutionPlannerService.PlanTemplateHint> resolveTemplateHints(Long userId,Long agentId){
        return repository.findRecentOutcomePlans(userId,agentId,6).stream().map(plan->{
            Map<String,Object> metadata=object(plan.metadataJson());Map<String,Object> feedback=asMap(metadata.get("executionFeedback"));
            List<TaskPlanStepRecord> steps=repository.findTaskPlanStepsByTaskPlanId(plan.taskPlanId());
            return new ExecutionPlannerService.PlanTemplateHint(plan.planId(),plan.planVersion(),plan.planType(),plan.goal(),integer(metadata.get("qualityScore")),
                    text(metadata.get("qualitySummary")),text(feedback.get("finalStatus")),text(feedback.get("errorMessage")),
                    steps.stream().map(step->step.stepNo()+":"+step.stepType()+":"+step.stepName()).toList());
        }).toList();
    }

    @Transactional
    public void updateFeedback(Long taskId,String finalStatus,String assistantContent,String errorMessage,Long operatorId){
        TaskPlanRecord latest=repository.findTaskPlansByTaskId(taskId).stream().max(Comparator.comparing(TaskPlanRecord::planVersion)).orElse(null);
        if(latest==null)return;Map<String,Object> metadata=new LinkedHashMap<>(object(latest.metadataJson()));
        Map<String,Object> feedback=new LinkedHashMap<>();feedback.put("finalStatus",finalStatus);feedback.put("assistantPreview",preview(assistantContent));
        feedback.put("errorMessage",errorMessage==null?"":errorMessage);feedback.put("recordedAt",LocalDateTime.now().toString());
        metadata.put("executionFeedback",feedback);repository.updateTaskPlanMetadata(latest.taskPlanId(),json(metadata),operatorId);
    }

    private Map<String,Object> summarizeDiff(TaskPlanRecord previous,ExecutionPlan current){
        Map<String,String> before=new LinkedHashMap<>();for(TaskPlanStepRecord step:repository.findTaskPlanStepsByTaskPlanId(previous.taskPlanId()))before.put(key(step.stepId(),step.stepNo()),format(step));
        Map<String,String> after=new LinkedHashMap<>();for(ExecutionPlan.ExecutionPlanStep step:current.steps())after.put(key(step.stepId(),step.stepNo()),format(step));
        int added=0,removed=0,changed=0;LinkedHashSet<String> keys=new LinkedHashSet<>(before.keySet());keys.addAll(after.keySet());
        for(String key:keys){if(!before.containsKey(key))added++;else if(!after.containsKey(key))removed++;else if(!before.get(key).equals(after.get(key)))changed++;}
        return Map.of("fromPlanVersion",previous.planVersion(),"addedCount",added,"removedCount",removed,"changedCount",changed);
    }

    private String key(String id,Integer no){return id==null||id.isBlank()?"stepNo#"+no:id;}
    private String format(TaskPlanStepRecord s){return s.stepType()+"|"+s.stepName()+"|"+s.objective()+"|"+s.targetToolCode()+"|"+s.targetWorkflowId()+"|"+s.expectedOutput()+"|"+s.failurePolicy();}
    private String format(ExecutionPlan.ExecutionPlanStep s){return s.stepType()+"|"+s.stepName()+"|"+s.objective()+"|"+s.targetToolCode()+"|"+s.targetWorkflowId()+"|"+s.expectedOutput()+"|"+s.failurePolicy();}
    private Long promptId(ExecutionContext c){if(c.plannerPromptContext()!=null&&c.plannerPromptContext().promptId()!=null)return c.plannerPromptContext().promptId();if(c.decisionPromptContext()!=null&&c.decisionPromptContext().promptId()!=null)return c.decisionPromptContext().promptId();return c.promptContext()==null?null:c.promptContext().promptId();}
    private String promptName(ExecutionContext c){if(c.plannerPromptContext()!=null&&text(c.plannerPromptContext().promptName()).length()>0)return c.plannerPromptContext().promptName();if(c.decisionPromptContext()!=null&&text(c.decisionPromptContext().promptName()).length()>0)return c.decisionPromptContext().promptName();return c.promptContext()==null?null:c.promptContext().promptName();}
    private String preview(String value){if(value==null)return "";return value.length()<=120?value:value.substring(0,120)+"...";}
    private Integer integer(Object value){return value==null?null:Integer.valueOf(String.valueOf(value));}
    private String text(Object value){return value==null?"":String.valueOf(value);}
    @SuppressWarnings("unchecked") private Map<String,Object> asMap(Object value){return value instanceof Map<?,?> map?(Map<String,Object>)map:Map.of();}
    private Map<String,Object> object(String value){try{return objectMapper.readValue(value,new TypeReference<>(){});}catch(Exception e){return Map.of();}}
    private String json(Object value){try{return objectMapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("任务计划序列化失败",e);}}
}
