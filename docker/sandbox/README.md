# Sandbox Runtime Image

在项目根目录执行：

```bash
docker build -t agent-sandbox-runtime:1.0.0 docker/sandbox
```

镜像只提供 `python3`、`node`、`jq` 三类基础运行命令。实际允许命令由 `sandbox_profiles.allowed_commands` 再次限制；运行容器默认关闭网络、只读根文件系统并使用非 root 用户。
