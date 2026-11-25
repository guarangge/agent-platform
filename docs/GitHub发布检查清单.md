# GitHub 发布检查清单

适用于当前源码快照。检查不自动创建远程仓库、不上传代码，也不恢复已经移除的 Git 历史。

## 本地准备

1. 配置文件、Java 配置类和部署脚本不得保留真实或共享默认密码、JWT、服务 Token、Webhook 密钥和加密密钥。
2. `.env.example` 仅提供字段；`.env`、私钥、本地配置、`output/`、`backups/`、依赖和构建产物均排除发布。`.dockerignore` 同时限制 Docker 构建上下文。
3. 已有数据库的密钥不能直接更换，先按 README 迁移说明保留原值；发布源码不需要附带数据库或部署凭据。
4. 原始演示管理员锁定及初始化由新增 Flyway 迁移和初始化器处理；历史迁移 checksum 保持不变。
5. README 使用仓库相对链接、区分 PowerShell 与 Bash，说明依赖、环境变量、首次登录和升级方法。
6. 检查 Word、PDF、表格、图片、截图等附件中的个人或业务信息，确认有权公开；脚本不解析这些附件正文。
7. 检查 LICENSE / NOTICE 与实际版权归属、第三方依赖授权一致；项目采用 Apache-2.0。

## 验证

Windows PowerShell，在项目根目录执行：

```powershell
python scripts/security/check_secrets.py
python -m unittest discover -s scripts/test -p test_publication_security.py
mvn test
npm.cmd --prefix frontend-web ci
npm.cmd --prefix frontend-web run build
```

Linux / macOS 使用 `python3` 和 `npm`；完整环境可执行 `bash scripts/ci-verify.sh`。完整平台 E2E 需要 Docker 与 Chromium，不应将未执行的检查记录为通过。

## 首次暂存

仅在准备创建新 Git 仓库时，于项目根目录执行：

```powershell
git init -b main
git add .
python scripts/security/check_secrets.py --staged
git diff --cached --stat
git diff --cached --check
```

扫描通过后仍要人工确认暂存文件，尤其是附件和配置。之后再按选定的 GitHub 仓库地址配置 remote、提交和推送；提交说明使用中文。当前未创建远程仓库，`.github` 不是上传必需目录。

如果旧远程仓库曾包含真实密钥，新建本地 Git 不会删除旧远程历史。先撤销相关凭据，再处理旧仓库历史、分支、缓存及其他副本。

## 发布后的保护

在 GitHub 账号或仓库可用时启用 secret scanning / push protection，并按项目情况恢复自动化 CI。功能可用范围以 GitHub 当前计划与设置为准。

参考：[GitHub 密钥泄漏风险](https://docs.github.com/en/code-security/concepts/secret-security/secret-leakage-risks)、[Apache-2.0 官方条款](https://www.apache.org/licenses/LICENSE-2.0)。

## 本轮源码验证记录（2026-11-25）

已通过普通文件凭据扫描、6 项发布工具回归、Registry 50 项测试、Java SDK 6 项测试、前端构建、系统 Chrome 无头登录页验证、Bash/YAML 检查、新迁移 4 类账号场景与历史迁移 checksum 校验。Git 暂存读取和忽略规则在隔离测试仓库验证，当前项目本身仍未初始化 Git；首次真实暂存后仍需执行 `--staged` 检查。

Windows 全量 Core 测试仍有 3 项 Sandbox 平台差异失败；默认 Playwright Chromium 缓存缺失，页面验证使用系统 Chrome。当前没有 Docker，未执行完整平台 E2E；完整结果及限制已记录在 `MVP开发计划.md`，不据此宣称生产验收通过。
