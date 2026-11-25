<template>
  <section class="login-page">
    <div class="login-panel">
      <div class="brand-panel">
        <p class="brand-tag">Registry Center</p>
        <h1>通用 Agent 平台</h1>
        <p class="brand-text">
          统一管理 Agent 资源、工具与工作流，查看会话和任务执行情况。
        </p>
        <div class="brand-card">
          <div>请使用管理员分配的账号登录。</div>
          <div>首次部署请按 README 完成管理员初始化。</div>
        </div>
      </div>
      <el-card shadow="never" class="form-card">
        <template #header>
          <div class="form-title">登录系统</div>
        </template>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" placeholder="请输入用户名" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
          </el-form-item>
          <el-form-item>
            <el-checkbox v-model="form.rememberMe">记住登录状态</el-checkbox>
          </el-form-item>
          <div class="actions">
            <el-button @click="resetForm">重置</el-button>
            <el-button type="primary" :loading="submitting" @click="submitForm">登录</el-button>
          </div>
        </el-form>
      </el-card>
    </div>
  </section>
</template>

<script setup lang="ts">
import { nextTick, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, type FormInstance, type FormRules } from "element-plus";
import { useAuthStore } from "@/stores/auth";
import { consumePostLoginRedirect } from "@/utils/authExpiration";

interface LoginForm {
  username: string;
  password: string;
  rememberMe: boolean;
}

const router = useRouter();
const authStore = useAuthStore();
const formRef = ref<FormInstance>();
const submitting = ref(false);
const form = reactive<LoginForm>({
  username: "",
  password: "",
  rememberMe: true
});

const rules: FormRules<LoginForm> = {
  username: [{ required: true, message: "请输入用户名", trigger: "blur" }],
  password: [{ required: true, message: "请输入密码", trigger: "blur" }]
};

async function submitForm(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }

  submitting.value = true;
  try {
    await authStore.login(form);
    ElMessage.success("登录成功");
    const redirect = consumePostLoginRedirect();
    await router.replace(redirect);
    await nextTick();
    if (window.location.pathname === "/login") {
      window.location.replace(redirect);
    }
  } catch (error) {
    ElMessage.error("登录失败，请检查用户名或密码");
  } finally {
    submitting.value = false;
  }
}

function resetForm(): void {
  formRef.value?.resetFields();
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 32px;
  background:
    radial-gradient(circle at top left, rgba(18, 89, 134, 0.18), transparent 34%),
    linear-gradient(135deg, #eef5fb 0%, #f7f9fc 55%, #e8f0f7 100%);
}

.login-panel {
  width: min(1080px, 100%);
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  gap: 24px;
}

.brand-panel {
  padding: 36px;
  border-radius: 28px;
  background: linear-gradient(155deg, #163a59 0%, #1b5b82 100%);
  color: #f7fbff;
  box-shadow: 0 24px 50px rgba(18, 58, 89, 0.2);
}

.brand-tag {
  margin: 0 0 12px;
  font-size: 13px;
  text-transform: uppercase;
  letter-spacing: 0.18em;
  color: rgba(247, 251, 255, 0.78);
}

.brand-panel h1 {
  margin: 0;
  font-size: 42px;
  line-height: 1.1;
}

.brand-text {
  margin: 18px 0 0;
  line-height: 1.8;
  color: rgba(247, 251, 255, 0.82);
}

.brand-card {
  margin-top: 28px;
  padding: 18px 20px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.08);
  line-height: 1.8;
}

.form-card {
  border-radius: 28px;
  border: 1px solid #dce7f1;
  background: rgba(255, 255, 255, 0.94);
}

.form-title {
  font-size: 22px;
  font-weight: 700;
  color: #17324d;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

@media (max-width: 960px) {
  .login-panel {
    grid-template-columns: 1fr;
  }
}
</style>
