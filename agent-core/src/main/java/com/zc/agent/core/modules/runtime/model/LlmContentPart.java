package com.zc.agent.core.modules.runtime.model;

/** 供应商无关的多模态 LLM 内容片段。 */
public sealed interface LlmContentPart permits LlmTextContentPart, LlmImageContentPart {
}
