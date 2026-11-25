package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionException;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import com.zc.agent.core.modules.tool.model.ToolInvocationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.util.Map;
import java.util.List;
import java.util.Optional;
import com.zc.agent.core.modules.conversation.dto.ResolveCheckpointAmbiguityRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolSideEffectServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ToolRouterService toolRouterService = mock(ToolRouterService.class);
    private final ToolSideEffectService service = new ToolSideEffectService(jdbc, new ObjectMapper(), toolRouterService);

    @Test
    void shouldRequireCustomIdempotencyKeyWhenConfigured() {
        assertThatThrownBy(() -> service.begin(context(), tool("idempotent", "required_custom", null),
                new ToolInvocationRequest("创建订单", Map.of())))
                .isInstanceOf(ToolExecutionException.class)
                .extracting("errorCode")
                .isEqualTo("TOOL_IDEMPOTENCY_KEY_REQUIRED");
    }

    @Test
    void shouldRejectCompensatableToolWithoutCompensationTool() {
        assertThatThrownBy(() -> service.begin(context(), tool("compensatable", "generated", null),
                new ToolInvocationRequest("创建订单", Map.of())))
                .isInstanceOf(ToolExecutionException.class)
                .extracting("errorCode")
                .isEqualTo("TOOL_COMPENSATION_MISSING");
    }

    @Test
    void shouldUseTopLevelSideEffectDeclarationAsAuthority() {
        RegistryAgentTool metadataIdempotent = tool("idempotent", "required_custom", null);
        RegistryAgentTool declaredNone = new RegistryAgentTool(
                metadataIdempotent.toolId(), metadataIdempotent.toolCode(), metadataIdempotent.toolName(),
                metadataIdempotent.toolType(), metadataIdempotent.toolCategory(), List.of(), "none",
                metadataIdempotent.inputSchema(), metadataIdempotent.outputSchema(), metadataIdempotent.providerType(),
                metadataIdempotent.transportType(), metadataIdempotent.providerCode(), metadataIdempotent.providerName(),
                metadataIdempotent.providerInstanceCode(), metadataIdempotent.providerInstanceName(),
                metadataIdempotent.environmentCode(), metadataIdempotent.endpoint(), metadataIdempotent.authType(),
                metadataIdempotent.authConfig(), metadataIdempotent.headers(),
                metadataIdempotent.providerTimeoutSeconds(), metadataIdempotent.retryTimes(),
                metadataIdempotent.operationName(), metadataIdempotent.operationType(),
                metadataIdempotent.invokeConfig(), metadataIdempotent.argumentMapping(),
                metadataIdempotent.outputMapping(), metadataIdempotent.permissionLevel(),
                metadataIdempotent.timeoutSeconds(), metadataIdempotent.status(), metadataIdempotent.description(),
                metadataIdempotent.metadata());

        ToolSideEffectService.Lease lease = service.begin(
                context(), declaredNone, new ToolInvocationRequest("创建订单", Map.of()));

        assertThat(lease.id()).isNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldOccupyIdempotencyKeyAndPersistSuccessSnapshot() {
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), any(Object[].class))).thenReturn(99L);
        ToolSideEffectService.Lease lease = service.begin(context(), tool("idempotent", "generated", null),
                new ToolInvocationRequest("创建订单", Map.of("amount", 10)));

        assertThat(lease.id()).isEqualTo(99L);
        assertThat(lease.cached()).isFalse();

        service.success(lease, new ToolExecutionResult("ORDER_CREATE", "创建订单", "成功", "订单已创建", "{}"));

        verify(jdbc).update(anyString(), anyString(), any(Long.class));
    }

    @Test
    void shouldRequirePerExecutionResolutionForMultipleSuccessConfirmations() {
        when(jdbc.queryForList(anyString(), org.mockito.ArgumentMatchers.eq(8L))).thenReturn(List.of(
                Map.of("id", 1L, "tool_code", "ORDER_CREATE", "side_effect_level", "idempotent", "status", "uncertain"),
                Map.of("id", 2L, "tool_code", "PAY_CREATE", "side_effect_level", "idempotent", "status", "uncertain")
        ));

        assertThatThrownBy(() -> service.resolveRecoveryAmbiguity(
                context(), 8L, "confirm_success", Map.of("result", "ok"), List.of(), 1L))
                .isInstanceOf(ToolExecutionException.class)
                .extracting("errorCode")
                .isEqualTo("TOOL_SIDE_EFFECT_RESOLUTION_INCOMPLETE");
    }

    @Test
    void shouldExecuteConfiguredCompensationTool() {
        when(jdbc.queryForList(anyString(), org.mockito.ArgumentMatchers.eq(8L))).thenReturn(List.of(Map.of(
                "id", 1L, "tool_code", "ORDER_CREATE", "side_effect_level", "compensatable",
                "status", "uncertain", "compensation_tool_code", "ORDER_CANCEL", "error_message", "timeout")));
        when(toolRouterService.execute(any(), org.mockito.ArgumentMatchers.eq("ORDER_CANCEL"), anyString(), any()))
                .thenReturn(Optional.of(new ToolExecutionResult("ORDER_CANCEL", "取消订单", "已补偿", "已取消", "{}")));

        service.resolveRecoveryAmbiguity(context(), 8L, null, null, List.of(
                new ResolveCheckpointAmbiguityRequest.SideEffectResolution(1L, "compensate", Map.of())), 1L);

        verify(toolRouterService).execute(any(), org.mockito.ArgumentMatchers.eq("ORDER_CANCEL"), anyString(), any());
    }

    private ExecutionContext context() {
        ExecutionContext context = mock(ExecutionContext.class);
        ExecutionContext.TaskContext taskContext = mock(ExecutionContext.TaskContext.class);
        when(context.taskContext()).thenReturn(taskContext);
        when(taskContext.taskId()).thenReturn(8L);
        return context;
    }

    private RegistryAgentTool tool(String level, String idempotencyMode, String compensationToolCode) {
        ObjectNode metadata = new ObjectMapper().createObjectNode();
        ObjectNode policy = metadata.putObject("sideEffectPolicy");
        policy.put("level", level);
        policy.put("idempotencyMode", idempotencyMode);
        if (compensationToolCode != null) {
            policy.put("compensationToolCode", compensationToolCode);
        }
        return new RegistryAgentTool(1L, "ORDER_CREATE", "创建订单", "function", "business", List.of(), level,
                null, null,
                "internal", "in_process", "internal", "内置", "default", "默认", "default", null,
                "none", null, null, 30, 0, "create", "internal_method", null, null, null,
                "default", 30, "enabled", "", metadata);
    }
}
