package com.zc.agent.core.modules.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.workflow.model.RegistryWorkflowSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class WorkflowResultAssemblerServiceTest {

    @Test
    void shouldReturnCompleteLastResponseForUserReplyWorkflow() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        WorkflowResultAssemblerService service = new WorkflowResultAssemblerService(
                mock(WorkflowRecoveryService.class), objectMapper);
        RegistryWorkflowSnapshot workflow = new RegistryWorkflowSnapshot(
                48L, "general_reply", "通用回复", "chat", "general", "published",
                objectMapper.readTree("{\"nodes\":[],\"edges\":[]}"),
                objectMapper.readTree("{\"routing\":{\"outputMode\":\"user_reply\"}}"));
        String completeResponse = "# 完整回复\n\n" + "正文内容".repeat(200);
        List<Map<String, Object>> nodes = List.of(Map.of(
                "nodeType", "reply",
                "nodeName", "生成回复",
                "summary", "回复摘要",
                "responseText", completeResponse
        ));

        var result = service.assemble(workflow, nodes, "回复摘要", completeResponse, null);

        assertThat(result.responseText()).isEqualTo(completeResponse);
        assertThat(objectMapper.readTree(result.outputSnapshot()).path("assistantMessage").asText())
                .isEqualTo(completeResponse);
        assertThat(result.responseText()).doesNotContain("已执行 Workflow");
    }
}
