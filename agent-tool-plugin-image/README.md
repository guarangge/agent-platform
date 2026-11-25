# 图片理解 Tool 插件

该插件通过 Runtime Plugin SPI 提供 `IMAGE_OCR` 和 `IMAGE_LAYOUT_ANALYZE`。

- Tool 参数只保存 `attachmentId`，插件在调用期读取当前消息附件并校验 SHA-256。
- `IMAGE_LAYOUT_ANALYZE` 使用 JDK `ImageIO` 本地分析尺寸、方向和宽高比。
- `IMAGE_OCR` 对接独立 OpenAI-compatible Vision endpoint，不绑定具体供应商。

配置示例：

```yaml
agent:
  plugin:
    image:
      ocr-endpoint: https://example.com/v1/chat/completions
      ocr-model: vision-model
      ocr-api-key: ${IMAGE_OCR_API_KEY:}
      ocr-timeout-seconds: 60
```

构建：

```bash
mvn -pl agent-tool-plugin-image -am package
```
