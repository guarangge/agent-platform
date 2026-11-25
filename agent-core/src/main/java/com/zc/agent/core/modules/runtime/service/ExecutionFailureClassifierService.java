package com.zc.agent.core.modules.runtime.service;

import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.tool.model.ToolExecutionException;
import com.zc.agent.core.modules.workflow.service.WorkflowExecutionException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/** 将所有执行器异常归一化为统一错误语义。 */
@Service
public class ExecutionFailureClassifierService {

    public FailureSemantics classify(ExecutionPlan.ExecutionPlanStep step, Throwable error) {
        Throwable cause = root(error);
        boolean stepReplannable = step != null && Boolean.TRUE.equals(step.replannable());
        if (cause instanceof ToolExecutionException tool) {
            String code = text(tool.errorCode(), "TOOL_EXECUTION_FAILED");
            boolean approval = code.contains("UNCERTAIN") || code.contains("IRREVERSIBLE");
            boolean compensatable = code.contains("COMPENSATION") || "side_effect".equals(tool.errorType());
            return new FailureSemantics(code, "tool", tool.retryable(), stepReplannable && !approval,
                    compensatable, approval, !tool.retryable() && !stepReplannable && !approval);
        }
        if (cause instanceof LlmProviderException llm) {
            return new FailureSemantics(text(llm.errorCode(), "LLM_PROVIDER_FAILED"), "llm",
                    llm.retryable(), stepReplannable, false, false, !llm.retryable() && !stepReplannable);
        }
        if (cause instanceof PolicyViolationException) {
            return new FailureSemantics("GUARDRAIL_BLOCKED", "policy", false, stepReplannable,
                    false, false, !stepReplannable);
        }
        if (cause instanceof PlanControlPauseException) {
            return new FailureSemantics("HUMAN_CONTROL_PAUSED", "human_control", false, false,
                    false, true, false);
        }
        if (cause instanceof ResultTrustException trust) {
            return new FailureSemantics("RESULT_TRUST_FAILED", "trust", false, trust.replannable(),
                    false, false, !trust.replannable());
        }
        if (cause instanceof WorkflowExecutionException) {
            return new FailureSemantics("WORKFLOW_EXECUTION_FAILED", "workflow", false,
                    stepReplannable, false, false, !stepReplannable);
        }
        String message = cause.getMessage() == null ? "" : cause.getMessage().toLowerCase();
        boolean retryable = message.contains("timeout") || message.contains("超时") || message.contains("temporar");
        return new FailureSemantics("STEP_EXECUTION_FAILED", step == null ? "runtime" : step.stepType(),
                retryable, stepReplannable, false, false, !retryable && !stepReplannable);
    }

    private Throwable root(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current;
    }

    private String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record FailureSemantics(String errorCode,
                                   String category,
                                   boolean retryable,
                                   boolean replannable,
                                   boolean compensatable,
                                   boolean approvalRequired,
                                   boolean fatal) {
        public String disposition() {
            if (approvalRequired) return "approval_required";
            if (retryable) return "retryable";
            if (replannable) return "replannable";
            if (compensatable) return "compensatable";
            return "fatal";
        }

        public Map<String, Object> toMetadata() {
            Map<String,Object> value=new LinkedHashMap<>();value.put("errorCode",errorCode);value.put("category",category);
            value.put("disposition",disposition());value.put("retryable",retryable);value.put("replannable",replannable);
            value.put("compensatable",compensatable);value.put("approvalRequired",approvalRequired);value.put("fatal",fatal);return value;
        }
    }
}
