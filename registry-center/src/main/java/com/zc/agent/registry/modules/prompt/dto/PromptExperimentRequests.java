package com.zc.agent.registry.modules.prompt.dto;

import jakarta.validation.Valid;import jakarta.validation.constraints.*;import java.util.List;
/** Prompt 实验定义与运行时解析请求。 */
public final class PromptExperimentRequests{private PromptExperimentRequests(){}
 public record VariantRequest(@NotBlank String variantCode,@NotBlank String variantName,@NotNull Long promptId,@NotNull @Min(1) Integer weight,@NotNull Boolean control,@NotBlank String status){}
 public record ExperimentRequest(@NotBlank String experimentCode,@NotBlank String experimentName,@NotNull Long basePromptId,Long agentId,@NotBlank String stage,@NotBlank String allocationKey,@NotNull @Min(1) @Max(100) Integer rolloutPercent,@NotBlank String status,@NotEmpty List<@Valid VariantRequest> variants){}
 public record ResolveRequest(@NotNull Long basePromptId,@NotNull Long userId,@NotNull Long conversationId,Long agentId,@NotBlank String stage){}
 public record CompleteRequest(@NotNull Long winnerPromptId){}
}
