package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import org.springframework.stereotype.Service;

/** 解析压缩策略作用域和默认值，不参与实际文本压缩。 */
@Service
public class ContextCompactionPolicyResolver {
    public ContextCompactionPolicy resolve(ExecutionContext context) {
        JsonNode conversation = context == null || context.conversationContext() == null ? null : context.conversationContext().metadata();
        JsonNode agent = context == null || context.agentContext() == null ? null : context.agentContext().metadata();
        ContextCompactionPolicy policy = read(conversation);
        if (policy != null) return policy;
        policy = read(agent);
        return policy == null ? ContextCompactionPolicy.defaults() : policy;
    }
    private ContextCompactionPolicy read(JsonNode metadata) {
        JsonNode direct = metadata == null ? null : metadata.get("compactionPolicy");
        JsonNode nested = metadata == null ? null : metadata.path("contextPolicy").path("compactionPolicy");
        JsonNode node = direct != null && !direct.isNull() ? direct : nested;
        if (node == null || !node.isObject()) return null;
        return new ContextCompactionPolicy(bool(node,"enabled",true),bool(node,"preferSummaryOverRawHistory",true),
                positive(node,"maxRecentMessages",6),positive(node,"maxRecentUserMessages",4),
                bool(node,"preferStateFirst",true),positive(node,"historySummaryThresholdMessages",6),
                ratio(node,"minRetentionRatio",0.18D,0.9D,0.18D),positive(node,"qualityWarningThreshold",75));
    }
    private boolean bool(JsonNode n,String f,boolean d){JsonNode v=n.get(f);return v!=null&&v.isBoolean()?v.booleanValue():d;}
    private int positive(JsonNode n,String f,int d){JsonNode v=n.get(f);return v!=null&&v.canConvertToInt()&&v.intValue()>0?v.intValue():d;}
    private double ratio(JsonNode n,String f,double min,double max,double d){JsonNode v=n.get(f);if(v==null||!v.isNumber())return d;double x=v.doubleValue();return x>=min&&x<=max?x:d;}
}
