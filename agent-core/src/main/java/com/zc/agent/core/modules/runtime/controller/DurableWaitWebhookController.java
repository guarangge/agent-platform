package com.zc.agent.core.modules.runtime.controller;

import com.zc.agent.core.common.response.ApiResponse;
import com.zc.agent.core.modules.runtime.service.DurableWaitService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 外部系统签名唤醒持久化等待任务的专用入口。 */
@RestController
@RequestMapping("/api/core/webhooks/waits")
public class DurableWaitWebhookController {
    private final DurableWaitService durableWaitService;

    public DurableWaitWebhookController(DurableWaitService durableWaitService) {
        this.durableWaitService = durableWaitService;
    }

    /** 验证 HMAC、时间戳和 nonce 后发送一次性唤醒信号。 */
    @PostMapping("/{eventKey}")
    public ApiResponse<Void> signal(@PathVariable String eventKey,
                                    @RequestHeader("X-Agent-Timestamp") String timestamp,
                                    @RequestHeader("X-Agent-Nonce") String nonce,
                                    @RequestHeader("X-Agent-Signature") String signature,
                                    @RequestBody(required = false) String payload) {
        durableWaitService.signalWebhook(eventKey, timestamp, nonce, signature, payload);
        return ApiResponse.success(null);
    }
}
