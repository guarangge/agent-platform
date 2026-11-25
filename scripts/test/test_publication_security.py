"""发布准备工具的安全边界回归，不访问真实数据库或修改项目 .env。"""

import importlib.util
import os
from pathlib import Path
import tempfile
import shutil
import subprocess
import stat
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]


def load_module(name, relative_path):
    spec = importlib.util.spec_from_file_location(name, str(ROOT / relative_path))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


local_env = load_module("local_env", "scripts/setup/local_env.py")
scanner = load_module("check_secrets", "scripts/security/check_secrets.py")


class PublicationSecurityTest(unittest.TestCase):
    def test_generation_keeps_shared_credentials_consistent_and_refuses_overwrite(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / ".env.example").write_bytes((ROOT / ".env.example").read_bytes())
            path = local_env.initialize(root)
            values = local_env.parse_env(path)
            self.assertEqual(values["REGISTRY_JWT_SECRET"], values["CORE_JWT_SECRET"])
            self.assertEqual(values["POSTGRES_PASSWORD"], values["REGISTRY_DB_PASSWORD"])
            self.assertEqual(values["POSTGRES_PASSWORD"], values["AGENT_CORE_DB_PASSWORD"])
            self.assertNotEqual(values["REGISTRY_TOOL_ENCRYPTION_KEY"], values["REGISTRY_JWT_SECRET"])
            self.assertGreaterEqual(len(values["REGISTRY_BOOTSTRAP_ADMIN_PASSWORD"]), 12)
            previous = path.read_bytes()
            with self.assertRaises(FileExistsError):
                local_env.initialize(root)
            self.assertEqual(previous, path.read_bytes())

    def test_environment_parser_does_not_execute_or_expand_secret(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / ".env"
            path.write_text("VALUE=$(never_execute)\nSECOND='literal$value'\n", encoding="utf-8")
            self.assertEqual(local_env.parse_env(path), {"VALUE": "$(never_execute)", "SECOND": "literal$value"})

    def test_process_environment_overrides_file(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / ".env").write_text("\n".join(key + "=from-file" for key in local_env.REQUIRED["agent-core"]), encoding="utf-8")
            with patch.dict(os.environ, {"CORE_JWT_SECRET": "from-process"}, clear=True):
                self.assertEqual(local_env.service_environment("agent-core", root)["CORE_JWT_SECRET"], "from-process")

    def test_scanner_detects_tokens_without_returning_their_content(self):
        token = "sk-proj-" + "A1b2" * 12
        findings = scanner.scan_text("config.json", '{"apiKey": "' + token + '"}')
        self.assertIn((1, "provider-token"), findings)
        self.assertNotIn(token, str(findings))

    @unittest.skipUnless(shutil.which("git"), "Git not installed")
    def test_staged_scan_reads_index_instead_of_modified_working_file(self):
        with tempfile.TemporaryDirectory(prefix="agent-staged-scan-") as temporary:
            root = Path(temporary)
            try:
                token = "sk-proj-" + "B2c3" * 12
                subprocess.run(["git", "init", "--quiet", str(root)], env=os.environ.copy(), check=True)
                (root / "config.txt").write_text(token, encoding="utf-8")
                (root / ".env").write_text("LOCAL_ONLY=value", encoding="utf-8")
                subprocess.run(["git", "-C", str(root), "add", "config.txt", ".env"], env=os.environ.copy(), check=True)
                (root / "config.txt").write_text("clean working tree", encoding="utf-8")
                (root / ".gitignore").write_bytes((ROOT / ".gitignore").read_bytes())
                ignored = subprocess.run(
                    ["git", "-C", str(root), "check-ignore", "--no-index", "-z", "--stdin"],
                    input=b".env\0.env.example\0nested/application-local.yml\0server.key\0output/report.pdf\0node_modules/package/index.js\0",
                    stdout=subprocess.PIPE, env=os.environ.copy(), check=True,
                ).stdout.decode("utf-8").rstrip("\0").split("\0")
                self.assertIn(".env", ignored)
                self.assertIn("nested/application-local.yml", ignored)
                self.assertIn("server.key", ignored)
                self.assertIn("output/report.pdf", ignored)
                self.assertNotIn(".env.example", ignored)
                with patch.object(scanner, "ROOT", root):
                    staged = dict(scanner.candidate_files(True))
                self.assertTrue(scanner.private_file(".env"))
                self.assertIn((1, "provider-token"), scanner.scan_text("config.txt", staged["config.txt"].decode("utf-8")))
            finally:
                # Python 3.7 的临时目录清理不能删除 Windows 只读 Git 对象，仅调整本测试目录。
                for path in (root / ".git").rglob("*"):
                    if path.is_file():
                        path.chmod(stat.S_IREAD | stat.S_IWRITE)

    def test_scanner_blocks_private_files_and_secret_defaults(self):
        self.assertTrue(scanner.private_file(".env"))
        self.assertTrue(scanner.private_file("server.key"))
        self.assertFalse(scanner.private_file(".env.example"))
        findings = scanner.scan_text("application.yml", "jwt-secret: ${APP_JWT_SECRET:unsafe-default}")
        self.assertIn((1, "nonempty-secret-fallback"), findings)


if __name__ == "__main__":
    unittest.main()
