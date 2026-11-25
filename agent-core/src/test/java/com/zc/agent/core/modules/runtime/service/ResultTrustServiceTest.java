package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.zc.agent.core.modules.tool.service.ToolRouterService;

class ResultTrustServiceTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private final ResultTrustService service=new ResultTrustService(new StepOutputContractService(mapper));

    @Test
    void shouldRequestReplanWhenEvidenceAndConfidenceAreInsufficient(){
        ExecutionContext context=mock(ExecutionContext.class);ExecutionContext.AgentContext agent=mock(ExecutionContext.AgentContext.class);
        ObjectNode metadata=mapper.createObjectNode();metadata.putObject("trustPolicy").put("enabled",true)
                .put("minimumConfidence",0.8).put("minimumEvidenceSources",2).put("requireCitation",true).put("failureAction","replan");
        when(context.agentContext()).thenReturn(agent);when(agent.metadata()).thenReturn(metadata);
        String snapshot="{\"resultType\":\"reply\",\"responseText\":\"answer\",\"confidence\":0.5,\"evidence\":[],\"structuredData\":{}}";
        var result=service.evaluate(context,step(),snapshot);
        assertThat(result.passed()).isFalse();assertThat(result.issues()).hasSize(3);
        assertThatThrownBy(()->service.enforce(result,step())).isInstanceOf(ResultTrustException.class);
    }

    @Test
    void shouldDetectClaimEvidenceConflictAndAuthorityFailure() {
        ExecutionContext context=context(Map.of(
                "enabled",true,"minimumConfidence",0.5,"minimumEvidenceSources",2,
                "requireClaimEvidence",true,"detectContradictions",true,"minimumAuthorityScore",0.9,"failureAction","block"));
        String snapshot="""
                {"resultType":"reply","responseText":"结论","confidence":0.9,
                 "structuredData":{"claims":[{"id":"c1","text":"系统可用"}]},
                 "evidence":[
                   {"claimId":"c1","stance":"supports","source":"https://official.test/a","sourceType":"official"},
                   {"claimId":"c1","stance":"refutes","source":"https://blog.test/b","sourceType":"web"}
                 ]}
                """;

        var result=service.evaluate(context,step(),snapshot);

        assertThat(result.claimCount()).isEqualTo(1);
        assertThat(result.supportedClaimCount()).isEqualTo(1);
        assertThat(result.conflicts()).containsExactly("c1");
        assertThat(result.issues()).anyMatch(item->item.contains("冲突证据"));
        assertThat(result.issues()).anyMatch(item->item.contains("权威度"));
    }

    @Test
    void shouldUseIndependentLlmVerifier() throws Exception {
        RuntimeLlmGatewayService gateway=mock(RuntimeLlmGatewayService.class);
        ResultTrustService verifierService=new ResultTrustService(new StepOutputContractService(mapper),gateway,
                mock(ToolRouterService.class),mapper);
        ExecutionContext context=context(Map.of("enabled",true,"minimumConfidence",0.5,"minimumEvidenceSources",0,
                "failureAction","block","verifier",Map.of("enabled",true,"mode","llm")));
        when(gateway.chatCompletion(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any())).thenReturn("{\"passed\":false,\"confidence\":0.2,\"issues\":[\"事实不一致\"]}");
        when(gateway.sanitizeJsonText(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation->invocation.getArgument(0));

        var result=verifierService.evaluate(context,step(),
                "{\"resultType\":\"reply\",\"responseText\":\"answer\",\"confidence\":0.9,\"evidence\":[],\"structuredData\":{}}");

        assertThat(result.verifier().passed()).isFalse();
        assertThat(result.issues()).contains("事实不一致");
    }

    private ExecutionContext context(Map<String,Object> policy) {
        ExecutionContext context=mock(ExecutionContext.class);ExecutionContext.AgentContext agent=mock(ExecutionContext.AgentContext.class);
        ObjectNode metadata=mapper.createObjectNode();metadata.set("trustPolicy",mapper.valueToTree(policy));
        when(context.agentContext()).thenReturn(agent);when(agent.metadata()).thenReturn(metadata);
        when(context.modelContext()).thenReturn(mock(ExecutionContext.ModelContext.class));
        return context;
    }

    private ExecutionPlan.ExecutionPlanStep step(){return new ExecutionPlan.ExecutionPlanStep(1,"s1","reply","reply","reply",List.of(),null,null,Map.of(),"", "replan",true,"draft","");}
}
