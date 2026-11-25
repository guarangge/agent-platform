package com.zc.agent.core.modules.conversation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.conversation.model.TaskPlanRecord;
import com.zc.agent.core.modules.conversation.repository.ConversationRepository;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.runtime.protocol.service.PlanRuntimeEventService;
import com.zc.agent.core.modules.runtime.service.PlanQualityService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskPlanLifecycleServiceTest {
    @Test
    void shouldPersistFailureFeedbackWithoutAssistantContent() {
        ConversationRepository repository=mock(ConversationRepository.class);
        PlanRuntimeEventService planRuntimeEventService=mock(PlanRuntimeEventService.class);
        TaskPlanRecord plan=new TaskPlanRecord(1L,2L,1,"P1","agentic","goal","draft","[]","[]","[]",null,null,"initial","{}", LocalDateTime.now());
        when(repository.findTaskPlansByTaskId(2L)).thenReturn(List.of(plan));
        TaskPlanLifecycleService service=new TaskPlanLifecycleService(
                repository,new PlanQualityService(),new ObjectMapper(),planRuntimeEventService);

        service.updateFeedback(2L,"failed",null,"timeout",9L);

        verify(repository).updateTaskPlanMetadata(eq(1L),contains("timeout"),eq(9L));
    }

    @Test
    void shouldPublishReplannedEventForSubsequentPlanVersion() {
        ConversationRepository repository=mock(ConversationRepository.class);
        PlanQualityService planQualityService=mock(PlanQualityService.class);
        PlanRuntimeEventService planRuntimeEventService=mock(PlanRuntimeEventService.class);
        ExecutionContext context=mock(ExecutionContext.class);
        when(context.taskContext()).thenReturn(new ExecutionContext.TaskContext(2L,"TASK_2","TRACE_2"));
        TaskPlanRecord previous=new TaskPlanRecord(1L,2L,1,"PLAN_1","agentic","goal","ready","[]","[]","[]",null,null,"initial","{}",LocalDateTime.now());
        when(repository.nextTaskPlanVersion(2L)).thenReturn(2);
        when(repository.findTaskPlansByTaskId(2L)).thenReturn(List.of(previous));
        when(repository.findTaskPlanStepsByTaskPlanId(1L)).thenReturn(List.of());
        ExecutionPlan plan=new ExecutionPlan("PLAN_2","agentic","goal","ready",List.of(),List.of(),List.of(),List.of(
                new ExecutionPlan.ExecutionPlanStep(1,"s1","reply","回复","输出答案",List.of(),null,null,
                        Map.of(),"答案","fail_fast",false,"ready","")
        ));
        when(planQualityService.evaluate(plan,context)).thenReturn(
                new PlanQualityService.PlanQualityResult(95,List.of(),"计划质量良好"));
        TaskPlanLifecycleService service=new TaskPlanLifecycleService(
                repository,planQualityService,new ObjectMapper(),planRuntimeEventService);

        service.persistSnapshot(2L,context,plan,"auto_replan",9L);

        verify(planRuntimeEventService).replanned(org.mockito.ArgumentMatchers.argThat(command ->
                command.planId().equals("PLAN_2")
                        && command.planVersion().equals(2)
                        && command.previousPlanId().equals("PLAN_1")
                        && command.source().equals("auto_replan")
                        && command.qualityScore().equals(95)));
    }

    @Test
    void shouldPublishCreatedEventForFirstPlanVersion() {
        ConversationRepository repository=mock(ConversationRepository.class);
        PlanQualityService planQualityService=mock(PlanQualityService.class);
        PlanRuntimeEventService planRuntimeEventService=mock(PlanRuntimeEventService.class);
        ExecutionContext context=mock(ExecutionContext.class);
        when(context.taskContext()).thenReturn(new ExecutionContext.TaskContext(2L,"TASK_2","TRACE_2"));
        when(repository.nextTaskPlanVersion(2L)).thenReturn(1);
        when(repository.findTaskPlansByTaskId(2L)).thenReturn(List.of());
        ExecutionPlan plan=new ExecutionPlan("PLAN_1","direct_reply","goal","ready",List.of(),List.of(),List.of(),List.of(
                new ExecutionPlan.ExecutionPlanStep(1,"s1","reply","回复","输出答案",List.of(),null,null,
                        Map.of(),"答案","fail_fast",false,"ready","")
        ));
        when(planQualityService.evaluate(plan,context)).thenReturn(
                new PlanQualityService.PlanQualityResult(100,List.of(),"计划质量良好"));
        TaskPlanLifecycleService service=new TaskPlanLifecycleService(
                repository,planQualityService,new ObjectMapper(),planRuntimeEventService);

        service.persistSnapshot(2L,context,plan,"initial",9L);

        verify(planRuntimeEventService).created(org.mockito.ArgumentMatchers.argThat(command ->
                command.planId().equals("PLAN_1")
                        && command.planVersion().equals(1)
                        && command.previousPlanId()==null
                        && command.source().equals("initial")));
    }
}
