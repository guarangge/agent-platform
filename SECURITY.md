# 安全说明

## 配置与凭据

1. 不要提交 `.env`、本地配置、私钥、数据库备份或真实 API Key。
2. 双服务 JWT、内部 Token、Webhook 密钥和凭据加密密钥均由部署环境显式提供；不使用共享开发默认值。
3. `REGISTRY_TOOL_ENCRYPTION_KEY` 用于已有数据库凭据，升级时必须保留。轮换应通过单独迁移完成，不能仅改配置。
4. 原始演示管理员通过新增迁移锁定，再使用一次性初始密码初始化；正常账号不会因重启而重置。
5. 若真实凭据曾公开，应先在其提供方撤销或替换，再清理所有已发布副本与 Git 历史。

## 提交前检查

在项目根目录执行 `python scripts/security/check_secrets.py`。Git 初始化并暂存后，再执行 `python scripts/security/check_secrets.py --staged`；Linux / macOS 使用 `python3`。

扫描结果只包含文件、行号和规则，不输出凭据。扫描为启发式检测，不覆盖全部未知格式、二进制附件、数据库或服务器环境；还需人工核对发布清单。

## 报告安全问题

请勿在公开 Issue、日志或截图中附带 Token、密码、用户数据或可直接利用的部署细节。若仓库启用 GitHub Private vulnerability reporting，可从 Security 页面私密提交；否则先通过维护者公布的私密渠道联系，并提供脱敏复现信息。
