<template>
  <div class="phone">
    <div class="column">
      <header class="hero" :class="overview?.dorm?.status">
        <div class="hero-top">
          <div>
            <div class="room">{{ overview?.dorm?.room_no ? overview.dorm.room_no + ' 宿舍' : '学生端' }}</div>
            <div class="status">{{ dormStatusLongText(overview?.dorm?.status) }}</div>
          </div>
          <button class="logout" @click="logout">退出</button>
        </div>
        <div class="power">{{ overview?.power_w ?? '--' }}<span>W</span></div>
        <div class="hero-meta">
          <span>今日 {{ overview?.today_kwh ?? 0 }} kWh</span>
          <span>信用分 {{ overview?.dorm?.credit_score ?? 100 }} / 100</span>
          <span>未读 {{ overview?.unread_notifications ?? 0 }}</span>
        </div>
      </header>

      <div v-if="error" class="state-banner error" role="alert">
        <span>数据加载失败：{{ error }}</span>
        <button class="retry" @click="refresh">重试</button>
      </div>
      <div v-else-if="loading && !overview" class="state-banner loading" aria-live="polite">正在加载宿舍状态…</div>

      <section v-if="overview?.active_alert" class="alert-card" role="button" tabindex="0" :aria-label="`查看 ${overview.active_alert.title} 证据`" @click="openAlert" @keydown.enter="openAlert" @keydown.space.prevent="openAlert">
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
          <div class="row"><span>宿舍信用</span><b>{{ overview?.dorm?.credit_score ?? 100 }} / 100</b></div>
        </div>
      </section>

      <section class="card">
        <div class="card-title">信用怎么算</div>
        <p class="hint">基础分 100；安全学习每次 +3（每月最多 +6）；整月无违规 +2；申诉通过撤销本次扣分。上限 100；学习/无违规加分到 100 为止。</p>
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
import { alertStatusText, dormStatusLongText } from '../../utils/labels'

const router = useRouter()
const auth = useAuthStore()
const overview = ref<any>(null)
const loading = ref(true)
const error = ref('')
let timer: number | undefined

function shortTime(value?: string) {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleTimeString('zh-CN', { hour12: false })
}

async function refresh() {
  if (!overview.value) loading.value = true
  try {
    const res: any = await http.get('/student/overview')
    overview.value = res.data
    error.value = ''
  } catch (e: any) {
    error.value = e?.response?.data?.message || '网络异常，请检查后端是否启动'
    console.error(e)
  } finally {
    loading.value = false
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
.state-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  font-size: var(--text-sm);
}
.state-banner.error {
  background: var(--danger-soft);
  color: #8f1111;
  border: 1px solid #f5c2c2;
}
.state-banner.loading {
  background: var(--accent-soft);
  color: var(--ink-2);
}
.retry {
  border: 0;
  background: var(--surface);
  color: var(--danger);
  border-radius: var(--radius-sm);
  min-height: 36px;
  padding: 0 12px;
  font-size: var(--text-sm);
}
.phone {
  width: 100%;
  max-width: none;
  margin: 0;
  min-height: 100%;
  padding: 16px;
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
    display: grid;
    grid-template-columns: minmax(0, 1.2fr) minmax(0, 0.8fr);
    gap: 18px;
    padding: 24px 32px;
    align-items: start;
  }
}
.hero {
  background: var(--navy);
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
  background: rgba(0, 0, 0, 0.24);
  color: #fff;
  border-radius: var(--radius-md);
  min-height: 40px;
  padding: 8px 14px;
  font-size: var(--text-sm);
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
  width: 100%;
  text-align: left;
  padding: 16px;
  border-radius: 16px;
  background: var(--warning-soft);
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
  background: var(--warning-2);
  box-shadow: 0 0 0 5px rgba(245, 158, 11, 0.15);
}
.state {
  margin-left: auto;
  font-size: 11px;
  color: var(--warning);
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
  color: var(--warning);
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
  color: var(--muted);
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
  color: var(--ink-2);
}
.row b {
  color: var(--ink);
}
</style>
