<template>
  <div class="page">
    <header class="topbar">
      <div>
        <strong>安宿 · 管理端</strong>
        <span class="muted"> / 安全大屏骨架</span>
      </div>
      <div class="actions">
        <el-tag :type="healthStatus === 'ok' ? 'success' : 'warning'">
          {{ healthStatus }}
        </el-tag>
        <el-button size="small" @click="logout">退出</el-button>
      </div>
    </header>

    <main class="grid">
      <section class="card">
        <h2>健康检查</h2>
        <pre>{{ healthText }}</pre>
      </section>
      <section class="card">
        <h2>阈值版本</h2>
        <pre>{{ thresholdText }}</pre>
      </section>
      <section class="card wide">
        <h2>下一批接入</h2>
        <p>大屏三区、20 寝状态灯、注入 800W、告警流、学生端证据。</p>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import http from '../../api/http'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const health = ref<any>(null)
const thresholds = ref<any>(null)

const healthStatus = computed(() => health.value?.data?.status || 'loading')
const healthText = computed(() => JSON.stringify(health.value, null, 2))
const thresholdText = computed(() => JSON.stringify(thresholds.value, null, 2))

onMounted(async () => {
  try {
    health.value = await http.get('/health')
    thresholds.value = await http.get('/safety/thresholds')
  } catch {
    health.value = { code: -1, message: '后端未启动或未登录' }
  }
})

function logout() {
  auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.page {
  min-height: 100%;
  padding: 24px;
}
.topbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 18px;
}
.muted {
  color: #78879b;
}
.actions {
  display: flex;
  gap: 10px;
  align-items: center;
}
.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}
.card {
  background: #fff;
  border-radius: 16px;
  padding: 20px;
  box-shadow: 0 8px 30px rgba(11, 37, 69, 0.06);
}
.card.wide {
  grid-column: 1 / -1;
}
h2 {
  margin: 0 0 12px;
  font-size: 16px;
}
pre {
  margin: 0;
  max-height: 320px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.6;
  color: #43536a;
}
p {
  margin: 0;
  color: #43536a;
}
</style>
