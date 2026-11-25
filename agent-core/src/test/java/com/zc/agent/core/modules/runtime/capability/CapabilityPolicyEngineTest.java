package com.zc.agent.core.modules.runtime.capability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zc.agent.core.modules.runtime.capability.model.CapabilityPolicyRequest;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolInvocationRequest;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CapabilityPolicyEngineTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CapabilityPolicyEngine engine = new CapabilityPolicyEngine(objectMapper, "prod");

    @Test
    void shouldApplySecureBaselineByDeclaredBoundary() {
        assertThat(decide(tool(List.of("network.read"), "none", objectMapper.createObjectNode()), Map.of()).action())
                .isEqualTo("allow");

        ObjectNode isolated = objectMapper.createObjectNode().put("workspaceScoped", true);
        assertThat(decide(tool(List.of("filesystem.read", "filesystem.write"), "none", isolated),
                Map.of("operation", "write", "path", "notes/a.md")).action())
                .isEqualTo("sandbox");

        assertThat(decide(tool(List.of("artifact.publish"), "irreversible", objectMapper.createObjectNode()),
                Map.of("path", "report.pdf")).action())
                .isEqualTo("approval");
    }

    @Test
    void shouldInferApprovalForLegacyMcpWriteOperation() {
        RegistryAgentTool legacyMcpWrite = new RegistryAgentTool(
                111L, "MCP_FS_WRITE_FILE", "写入文件", "mcp", "filesystem", List.of(), "none",
                null, null, "mcp", "stdio", "filesystem", "Filesystem", "local", "本地",
                "local", null, "none", null, null, 30, 0, "write_file", "mcp_tool",
                null, null, null, "admin", 30, "enabled", "", objectMapper.createObjectNode());

        var decision = decide(legacyMcpWrite, Map.of("path", "/workspace/report.txt", "content", "test"));

        assertThat(decision.action()).isEqualTo("approval");
        assertThat(decision.matchedCapabilities()).contains("filesystem.write");
        assertThat(decision.sideEffectLevel()).isEqualTo("irreversible");
    }

    @Test
    void shouldUseStableParameterHashAndConsumeApprovedNodeOnce() {
        RegistryAgentTool publishTool = tool(
                List.of("artifact.publish"), "irreversible", objectMapper.createObjectNode());
        LinkedHashMap<String, Object> firstArguments = new LinkedHashMap<>();
        firstArguments.put("path", "report.pdf");
        firstArguments.put("public", true);
        LinkedHashMap<String, Object> reorderedArguments = new LinkedHashMap<>();
        reorderedArguments.put("public", true);
        reorderedArguments.put("path", "report.pdf");

        var first = decide(publishTool, firstArguments);
        var reordered = decide(publishTool, reorderedArguments);

        assertThat(first.parameterHash()).isEqualTo(reordered.parameterHash());
        assertThat(first.approvalNodeId()).isEqualTo(reordered.approvalNodeId());
        try (CapabilityApprovalContext.Scope ignored = CapabilityApprovalContext.open(
                first.approvalNodeId(), "approved", "TOOL_CALL_11111111111111111111", first.parameterHash())) {
            var approved = decide(publishTool, reorderedArguments);
            assertThat(approved.action()).isEqualTo("allow");
            assertThat(approved.approvalGranted()).isTrue();
            assertThat(approved.metadata()).containsEntry("requestedAction", "approval");
            assertThat(CapabilityApprovalContext.approvedToolCallId(
                    approved.approvalNodeId(), approved.parameterHash()))
                    .isEqualTo("TOOL_CALL_11111111111111111111");
            assertThat(CapabilityApprovalContext.approvedToolCallId(
                    approved.approvalNodeId(), "changed-hash")).isNull();
        }
        assertThat(decide(publishTool, reorderedArguments).action()).isEqualTo("approval");
    }

    @Test
    void shouldMatchCapabilityParameterAndTargetWithoutPersistingRawValues() {
        ObjectNode metadata = objectMapper.createObjectNode();
        var rule = metadata.putObject("policyCenter").putArray("rules").addObject();
        rule.put("policyCode", "protected-file-block");
        rule.put("policyName", "保护目录禁止删除");
        rule.put("version", 3);
        rule.put("priority", 500);
        rule.put("enabled", true);
        rule.putObject("scope").putArray("envs").add("prod");
        rule.putObject("rollout").put("mode", "all");
        rule.putObject("action").put("type", "block").put("reason", "保护目录禁止删除");
        ObjectNode policyMetadata = rule.putObject("metadata");
        policyMetadata.put("policyType", "capability");
        ObjectNode policy = policyMetadata.putObject("capabilityPolicy");
        policy.putArray("capabilities").add("filesystem.write");
        policy.putArray("sideEffectLevels").add("irreversible");
        policy.putArray("targetPaths").add("/path");
        policy.putArray("targetPrefixes").add("/protected");
        ObjectNode condition = policy.putArray("parameterConditions").addObject();
        condition.put("path", "/operation");
        condition.put("operator", "in");
        condition.putArray("values").add("delete");

        var decision = decide(tool(List.of("filesystem.write"), "irreversible", metadata), Map.of(
                "operation", "delete", "path", "/protected/secret.txt"));

        assertThat(decision.action()).isEqualTo("block");
        assertThat(decision.policyCode()).isEqualTo("protected-file-block");
        assertThat(decision.targetHash()).isNotBlank();
        assertThat(decision.metadata().toString()).doesNotContain("/protected/secret.txt");
    }

    private com.zc.agent.core.modules.runtime.capability.model.CapabilityPolicyDecision decide(
            RegistryAgentTool tool, Map<String, Object> arguments) {
        return engine.decide(new CapabilityPolicyRequest(
                context(), tool, new ToolInvocationRequest("执行请求", arguments)));
    }

    private ExecutionContext context() {
        ExecutionContext context = mock(ExecutionContext.class);
        when(context.currentUser()).thenReturn(new ExecutionContext.CurrentUserContext(
                7L, "tester", "测试用户", List.of("ROLE_ADMIN"), List.of(), 1L, 1L, 1L, 9L));
        when(context.agentContext()).thenReturn(new ExecutionContext.AgentContext(
                3L, "ops-agent", "运维 Agent", null, "enabled", objectMapper.createObjectNode()));
        when(context.taskContext()).thenReturn(new ExecutionContext.TaskContext(11L, "TASK_11", "TRACE_11"));
        return context;
    }

    private RegistryAgentTool tool(List<String> capabilities, String sideEffectLevel, ObjectNode metadata) {
        return new RegistryAgentTool(
                1L, "TEST_TOOL", "测试 Tool", "function", "general", capabilities, sideEffectLevel,
                null, null, "internal", "in_process", "internal", "内置", "default", "默认",
                "prod", null, "none", null, null, 30, 0, "execute", "internal_method",
                null, null, null, "default", 30, "enabled", "", metadata);
    }
}
