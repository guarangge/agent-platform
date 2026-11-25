package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.eval.service.EvalFeedbackService;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.repository.ToolHealthCheckRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolSelectionServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldKeepExactToolFirstAndRejectIncompatibleCategoryFallback() throws Exception {
        ToolHealthCheckRepository healthRepository = mock(ToolHealthCheckRepository.class);
        EvalFeedbackService evalFeedbackService = mock(EvalFeedbackService.class);
        when(healthRepository.findLatestByToolId(anyLong())).thenReturn(Optional.empty());
        ToolSelectionService service = new ToolSelectionService(healthRepository, evalFeedbackService);
        RegistryAgentTool exact = tool(111L, "MCP_FS_WRITE_FILE", "write_file", schema("path", "content"));
        RegistryAgentTool compatible = tool(211L, "ALT_WRITE_FILE", "write_file", schema("path", "content"));
        RegistryAgentTool incompatible = tool(82L, "DOCUMENT_PARSE", "parse_document", schema("contentBase64"));
        ExecutionContext context = mock(ExecutionContext.class);
        when(context.agentContext()).thenReturn(new ExecutionContext.AgentContext(
                7L, "GENERAL", "通用Agent", "MODEL", "enabled", objectMapper.createObjectNode()));
        when(context.availableTools()).thenReturn(List.of(incompatible, compatible, exact));

        List<RegistryAgentTool> candidates = service.selectCandidates(context, "MCP_FS_WRITE_FILE");

        assertThat(candidates).extracting(RegistryAgentTool::toolCode)
                .containsExactly("MCP_FS_WRITE_FILE", "ALT_WRITE_FILE");
    }

    private RegistryAgentTool tool(Long id, String code, String operationName, JsonNode inputSchema) {
        return new RegistryAgentTool(
                id, code, code, "file", "document", inputSchema, objectMapper.createObjectNode(),
                "mcp", "stdio", "provider", "provider", "instance", "instance", "local",
                null, "none", objectMapper.createObjectNode(), objectMapper.createObjectNode(), 30, 0,
                operationName, "mcp_tool", objectMapper.createObjectNode(), objectMapper.createObjectNode(),
                objectMapper.createObjectNode(), "restricted", 30, "enabled", "", objectMapper.createObjectNode());
    }

    private JsonNode schema(String... required) throws Exception {
        var root = objectMapper.createObjectNode().put("type", "object");
        var requiredNode = root.putArray("required");
        var properties = root.putObject("properties");
        for (String field : required) {
            requiredNode.add(field);
            properties.putObject(field).put("type", "string");
        }
        return root;
    }
}
