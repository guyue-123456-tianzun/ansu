<template>
  <div class="phone">
    <div class="column">
      <header class="hero" :class="overview?.dorm?.status">
        <div class="hero-top">
          <div>
            <div class="room">{{ overview?.dorm?.room_no ? overview.dorm.room_no + ' 宿舍' : '学生端' }}</div>
            <div class="status">{{ statusText(overview?.dorm?.status) }}</div>
          </div>
          <button class="logout" @click="logout">退出</button>
        </div>
        <div class="power">{{ overview?.power_w ?? '--' }}<span>W</span></div>
        <div class="hero-meta">
          <span>今日 {{ overview?.today_kwh ?? 0 }} kWh</span>
          <span>信用分 {{ overview?.dorm?.credit_score ?? 100 }} / 120</span>
          <span>未读 {{ overview?.unread_notifications ?? 0 }}</span>
        </div>
      </header>

      <section v-if="overview?.active_alert" class="alert-card" @click="openAlert">
        <div class="alert-head">
          <span class="pulse" />
          <strong>{{ overview.active_alert.title }}</strong>
          <span class="state">{{ alertStatusText(overview.active_alert.status) }}</span>
        </div>
        <p>功率阶跃，已记录证据。点击查看发生了什么、为什么判定、现在可以做什么。</p>
        <div class="alert-foot">
          <span>{{ shortTime(overview.active_alert.created_at) }}</span>
          <span>查看证据 →</span>
        </div>
      </section>

      <section v-else class="empty-card">
        <strong>当前没有告警</strong>
        <p>宿舍用电状态正常。管理端注入 800W 后，这里会同步出现提醒。</p>
      </section>
    </div>

    <div class="column">
      <section class="card">
        <div class="card-title">今日概览</div>
        <div class="rows">
          <div class="row"><span>当前功率</span><b>{{ overview?.power_w ?? 0 }} W</b></div>
          <div class="row"><span>今日用电</span><b>{{ overview?.today_kwh ?? 0 }} kWh</b></div>
          <div class="row"><span>累计违规</span><b>{{ overview?.dorm?.violation_count ?? 0 }} 次</b></div>
          <div class="row"><span>宿舍信用</span><b>{{ overview?.dorm?.credit_score ?? 100 }} / 120</b></div>
        </div>
      </section>

      <section class="card">
        <div class="card-title">信用怎么算</div>
        <p class="hint">基础分 100；安全学习每次 +3（每月最多 +6）；整月无违规 +2；申诉通过撤销本次扣分。上限 120，所以会看到 100 以上。</p>
      </section>

      <section class="card">
        <div class="card-title">下一步</div>
        <p class="hint">点告警卡片进详情：可“已撤除”“提交申诉”“安全学习”；宿管在申诉工作台复核。</p>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import http from '../../api/http'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const overview = ref<any>(null)
let timer: number | undefined

const statusMap: Record<string, string> = {
  normal: '用电正常',
  warned: '提醒中',
  cut_off: '已模拟分闸',
  locked: '已锁定',
}

function statusText(status?: string) {
  return statusMap[status || ''] || '加载中'
}

function alertStatusText(status?: string) {
  const map: Record<string, string> = {
    open: '待通知', notified: '待处理', student_responded: '已响应',
    reviewing: '复核中', resolved: '已处理', closed: '已关闭',
  }
  return map[status || ''] || status || ''
}

function shortTime(value?: string) {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleTimeString('zh-CN', { hour12: false })
}

async function refresh() {
  try {
    const res: any = await http.get('/student/overview')
    overview.value = res.data
  } catch (error) {
    console.error(error)
  }
}

function openAlert() {
  const id = overview.value?.active_alert?.id
  if (id) router.push(`/m/alerts/${id}`)
}

function logout() {
  auth.logout()
  router.push('/login')
}

onMounted(() => {
  refresh()
  timer = window.setInterval(refresh, 1000)
})

onUnmounted(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<style scoped>
.phone {
  max-width: 430px;
  margin: 0 auto;
  min-height: 100%;
  padding: 16px 14px 32px;
}
.column {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.column > * {
  margin-top: 0;
}
@media (min-width: 900px) {
  .phone {
    max-width: 980px;
    display: grid;
    grid-template-columns: 1.1fr 0.9fr;
    gap: 18px;
    padding: 24px;
    align-items: start;
  }
}
.hero {
  background: #0b2545;
  color: #fff;
  border-radius: 20px;
  padding: 20px;
  box-shadow: 0 16px 40px rgba(11, 37, 69, 0.2);
  transition: background 0.3s ease;
}
.hero.warned { background: #7c3a0b; }
.hero.cut_off { background: #7f1d1d; }
.hero.locked { background: #334155; }
.hero-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}
.room {
  font-size: 14px;
  opacity: 0.8;
}
.status {
  margin-top: 4px;
  font-size: 13px;
  color: #9fd3ff;
}
.logout {
  border: 0;
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
  border-radius: 10px;
  padding: 7px 12px;
  font-size: 12px;
}
.power {
  margin-top: 16px;
  font-size: 42px;
  font-weight: 700;
}
.power span {
  font-size: 16px;
  margin-left: 4px;
  opacity: 0.75;
}
.hero-meta {
  display: flex;
  gap: 14px;
  margin-top: 8px;
  font-size: 12px;
  color: #b7c6d9;
}
.alert-card {
  margin-top: 14px;
  padding: 16px;
  border-radius: 16px;
  background: #fff8e8;
  border: 1px solid #fde3a7;
  cursor: pointer;
}
.alert-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}
.pulse {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #f59e0b;
  box-shadow: 0 0 0 5px rgba(245, 158, 11, 0.15);
}
.state {
  margin-left: auto;
  font-size: 11px;
  color: #b45309;
}
.alert-card p {
  margin: 8px 0 0;
  font-size: 13px;
  color: #5b4a2e;
  line-height: 1.6;
}
.alert-foot {
  display: flex;
  justify-content: space-between;
  margin-top: 10px;
  font-size: 12px;
  color: #b45309;
}
.empty-card,
.card {
  margin-top: 14px;
  background: #fff;
  border-radius: 16px;
  padding: 16px;
  box-shadow: 0 8px 24px rgba(11, 37, 69, 0.06);
}
.empty-card strong {
  font-size: 14px;
}
.empty-card p,
.hint {
  margin: 8px 0 0;
  font-size: 13px;
  color: #78879b;
  line-height: 1.6;
}
.card-title {
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 10px;
}
.rows {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #43536a;
}
.row b {
  color: #142033;
}
</style>
