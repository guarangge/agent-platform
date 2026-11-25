#!/usr/bin/env python3
"""用于平台端到端测试的 OpenAI Chat Completions 兼容服务。"""

import argparse
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


def build_content(payload: dict) -> str:
    messages = payload.get("messages") or []
    system_prompt = next((item.get("content", "") for item in messages if item.get("role") == "system"), "")
    user_prompt = next((item.get("content", "") for item in messages if item.get("role") == "user"), "")
    if isinstance(user_prompt, list):
        has_image = any(item.get("type") == "image_url" for item in user_prompt if isinstance(item, dict))
        user_prompt = "\n".join(item.get("text", "") for item in user_prompt
                                if isinstance(item, dict) and item.get("type") == "text")
        if has_image:
            return "已通过多模态协议接收并分析图片。E2E_IMAGE_OK"

    if "Agent 执行决策器" in system_prompt:
        return json.dumps({
            "decisionType": "direct_reply",
            "reason": "E2E Mock 决策",
            "selectedTools": [],
            "selectedWorkflows": [],
        }, ensure_ascii=False)
    if "Agent 任务拆解 Planner" in system_prompt:
        return json.dumps({
            "planId": "PLAN_E2E_MOCK",
            "planType": "direct_reply",
            "goal": "验证通用 Agent 主链",
            "status": "draft",
            "assumptions": [],
            "constraints": [],
            "risks": [],
            "steps": [{
                "stepNo": 1,
                "stepId": "step_1",
                "stepType": "reply",
                "stepName": "生成验证回复",
                "objective": "基于会话上下文生成回复",
                "dependsOnStepIds": [],
                "targetToolCode": None,
                "targetWorkflowId": None,
                "inputs": {},
                "expectedOutput": "可验证的回复",
                "failurePolicy": "fail_fast",
                "replannable": False,
                "status": "draft",
                "description": "E2E reply step",
            }],
        }, ensure_ascii=False)
    if "异步链路" in user_prompt:
        return "E2E_ASYNC_OK"
    if "刚才给出的项目代号" in user_prompt:
        return "ORBIT-42 E2E_HISTORY_OK" if "ORBIT-42" in user_prompt else "E2E_HISTORY_MISSING"
    if "长期记忆校验" in user_prompt:
        return "ORBIT-42 E2E_LONG_CONTEXT_OK" if "ORBIT-42" in user_prompt else "E2E_LONG_CONTEXT_MISSING"
    if "ORBIT-42" in user_prompt:
        return "已记住项目代号 ORBIT-42。E2E_FIRST_OK"
    return "E2E_REPLY_OK"


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/health":
            self.respond(200, {"status": "UP"})
            return
        self.respond(404, {"error": "not_found"})

    def do_POST(self):
        if self.path != "/v1/chat/completions":
            self.respond(404, {"error": "not_found"})
            return
        length = int(self.headers.get("Content-Length", "0"))
        payload = json.loads(self.rfile.read(length) or b"{}")
        content = build_content(payload)
        self.respond(200, {
            "id": "chatcmpl-e2e",
            "object": "chat.completion",
            "model": payload.get("model", "e2e-model"),
            "choices": [{"index": 0, "message": {"role": "assistant", "content": content}, "finish_reason": "stop"}],
            "usage": {"prompt_tokens": 128, "completion_tokens": 32, "total_tokens": 160},
        })

    def log_message(self, message, *args):
        return

    def respond(self, status: int, body: dict):
        encoded = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, required=True)
    args = parser.parse_args()
    ThreadingHTTPServer(("127.0.0.1", args.port), Handler).serve_forever()


if __name__ == "__main__":
    main()
