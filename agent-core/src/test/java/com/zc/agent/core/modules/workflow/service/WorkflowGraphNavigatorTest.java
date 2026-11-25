package com.zc.agent.core.modules.workflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowGraphNavigatorTest {
    private final WorkflowGraphNavigator navigator = new WorkflowGraphNavigator();

    @Test
    void shouldSelectExplicitConditionBranch() throws Exception {
        JsonNode definition = new ObjectMapper().readTree("""
                {"nodes":[{"id":"condition"},{"id":"yes"},{"id":"no"}],"edges":[
                  {"source":"condition","target":"yes","branch":"true"},
                  {"source":"condition","target":"no","branch":"false"}
                ]}
                """);

        assertThat(navigator.conditionNext(definition, "condition", true)).isEqualTo("yes");
        assertThat(navigator.conditionNext(definition, "condition", false)).isEqualTo("no");
        assertThat(navigator.node(definition, "yes")).isNotNull();
    }

    @Test
    void shouldNotFallbackToWrongExplicitConditionBranch() throws Exception {
        JsonNode definition = new ObjectMapper().readTree("""
                {"edges":[{"source":"condition","target":"yes","branch":"true"}]}
                """);

        assertThat(navigator.conditionNext(definition, "condition", false)).isNull();
    }

    @Test
    void shouldResolveCustomStartNodeId() throws Exception {
        JsonNode definition = new ObjectMapper().readTree("{\"nodes\":[{\"id\":\"start_1\",\"type\":\"start\"}]} ");
        assertThat(navigator.startNodeId(definition)).isEqualTo("start_1");
    }
}
