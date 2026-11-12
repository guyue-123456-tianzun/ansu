<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <div class="brand-mark">安</div>
        <div>
          <h1>安宿</h1>
          <p>宿舍用电安全共治平台 · 演示身份</p>
        </div>
      </div>

      <el-form label-position="top" @submit.prevent>
        <el-form-item label="角色">
          <el-select v-model="role" style="width: 100%">
            <el-option label="学生" value="student" />
            <el-option label="宿管" value="manager" />
            <el-option label="管理员" value="admin" />
            <el-option label="辅导员（只读）" value="counselor" />
            <el-option label="维修工" value="repair" />
            <el-option label="评委 / 访客" value="guest" />
          </el-select>
        </el-form-item>

        <el-form-item v-if="role === 'student'" label="宿舍号">
          <el-input v-model.number="dormId" placeholder="例如 314" />
        </el-form-item>

        <el-button type="primary" :loading="loading" style="width: 100%" @click="submit">
          进入演示
        </el-button>
      </el-form>

      <p class="hint">
        演示环境：选择角色进入；仅使用演示数据，正式版将接入学校统一身份认证。
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const role = ref('student')
const dormId = ref<number | undefined>(314)
const loading = ref(false)

async function submit() {
  loading.value = true
  try {
    const user = await auth.loginDemo(role.value, role.value === 'student' ? dormId.value : undefined)
    ElMessage.success(`已进入：${user.display_name}`)
    if (user.role === 'student') {
      await router.push('/m/home')
    } else {
      await router.push('/admin/dashboard')
    }
  } catch (error: any) {
    const message = error?.response?.data?.message || '演示身份切换失败'
    ElMessage.error(message)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100%;
  display: grid;
  place-items: center;
  padding: 24px;
  background: radial-gradient(circle at 20% 10%, var(--accent-soft), var(--bg) 55%);
}
.login-card {
  width: min(420px, 100%);
  padding: 32px;
  border-radius: 20px;
  background: #fff;
  box-shadow: 0 18px 50px rgba(11, 37, 69, 0.12);
}
.brand {
  display: flex;
  gap: 14px;
  align-items: center;
  margin-bottom: 24px;
}
.brand-mark {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  background: var(--navy);
  color: #fff;
  font-size: 22px;
  font-weight: 700;
}
h1 {
  margin: 0;
  font-size: 22px;
}
.brand p {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 13px;
}
.hint {
  margin: 18px 0 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.6;
}
</style>
