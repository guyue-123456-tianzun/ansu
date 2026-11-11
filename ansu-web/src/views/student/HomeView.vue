<template>
  <div class="phone">
    <header class="hero">
      <div class="room">{{ auth.user?.display_name || '学生端' }}</div>
      <div class="status">演示身份已登录</div>
      <div class="power">-- W</div>
      <div class="hint">下一批：宿舍卡、告警详情、证据、申诉、学习</div>
    </header>

    <section class="card">
      <h2>当前身份</h2>
      <pre>{{ userText }}</pre>
    </section>

    <section class="card">
      <h2>后端健康</h2>
      <pre>{{ healthText }}</pre>
    </section>

    <button class="logout" @click="logout">退出演示</button>
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

const userText = computed(() => JSON.stringify(auth.user, null, 2))
const healthText = computed(() => JSON.stringify(health.value, null, 2))

onMounted(async () => {
  try {
    health.value = await http.get('/health')
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
.phone {
  max-width: 430px;
  margin: 0 auto;
  min-height: 100%;
  padding: 16px 14px 32px;
}
.hero {
  background: #0b2545;
  color: #fff;
  border-radius: 20px;
  padding: 22px;
  box-shadow: 0 16px 40px rgba(11, 37, 69, 0.2);
}
.room {
  font-size: 14px;
  opacity: 0.8;
}
.status {
  margin-top: 6px;
  font-size: 13px;
  color: #9fd3ff;
}
.power {
  margin-top: 18px;
  font-size: 40px;
  font-weight: 700;
}
.hint {
  margin-top: 10px;
  font-size: 12px;
  color: #b7c6d9;
}
.card {
  margin-top: 14px;
  background: #fff;
  border-radius: 16px;
  padding: 16px;
  box-shadow: 0 8px 24px rgba(11, 37, 69, 0.06);
}
h2 {
  margin: 0 0 8px;
  font-size: 14px;
}
pre {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: #43536a;
  white-space: pre-wrap;
  word-break: break-all;
}
.logout {
  width: 100%;
  margin-top: 18px;
  padding: 12px;
  border: 0;
  border-radius: 12px;
  background: #eaf6fd;
  color: #0b2545;
  font-size: 14px;
}
</style>
