<template>
  <div class="page">
    <header class="topbar">
      <div class="brand">
        <span class="mark">安</span>
        <div>
          <strong>安宿 · 安全大屏</strong>
          <span class="muted">1 号楼 · 20 间宿舍</span>
        </div>
      </div>
      <div class="actions">
        <el-tag v-if="summary?.run_id" type="info">run {{ summary.run_id }}</el-tag>
        <el-button type="danger" :loading="injecting" @click="inject">注入 800W 阻性</el-button>
        <el-button :loading="resetting" @click="resetDemo">重置演示</el-button>
        <el-button @click="router.push('/admin/appeals')">申诉工作台</el-button>
        <el-button @click="logout">退出</el-button>
      </div>
    </header>

    <main class="layout">
      <aside class="panel dorm-panel">
        <div class="panel-title">寝室状态</div>
        <button
          v-for="d in dorms"
          :key="d.id"
          class="dorm-row"
          :class="[{ active: selectedDorm === d.id }, d.status]"
          @click="selectDorm(d.id)"
        >
          <span class="dot" :class="d.status" />
          <span class="room">{{ d.room_no }}</span>
          <span class="power">{{ d.power_w }}W</span>
          <span class="state">{{ statusText(d.status) }}</span>
        </button>
      </aside>

      <section class="center">
        <div class="metrics">
          <div class="metric">
            <span>正常</span><b>{{ summary?.counts?.normal ?? 0 }}</b>
          </div>
          <div class="metric warning">
            <span>提醒</span><b>{{ summary?.counts?.warned ?? 0 }}</b>
          </div>
          <div class="metric danger">
            <span>分闸</span><b>{{ summary?.counts?.cut_off ?? 0 }}</b>
          </div>
          <div class="metric">
            <span>闭环率</span><b>{{ Math.round((summary?.closure_rate ?? 0) * 100) }}%</b>
          </div>
          <div class="metric">
            <span>总功率</span><b>{{ summary?.total_power_w ?? 0 }}W</b>
          </div>
        </div>

        <div class="panel chart-panel">
          <div class="panel-head">
            <div>
              <div class="panel-title">{{ selectedDorm }} 宿舍 · 最近 60 分钟</div>
              <div class="muted small">当前功率 {{ selectedDormData?.power_w ?? 0 }}W · 信用分 {{ selectedDormData?.credit_score ?? 100 }}</div>
            </div>
            <el-tag :type="tagType(selectedDormData?.status)">{{ statusText(selectedDormData?.status) }}</el-tag>
          </div>
          <div ref="chartRef" class="chart" />
        </div>
      </section>

      <aside class="panel alert-panel">
        <div class="panel-head">
          <div class="panel-title">告警流</div>
          <span class="muted small">{{ alerts.length }} 条</span>
        </div>
        <div v-if="alerts.length === 0" class="empty">暂无告警</div>
        <div v-for="a in alerts" :key="a.id" class="alert-item">
          <div class="alert-top">
            <el-tag :type="a.level === 'urgent' ? 'danger' : 'warning'" size="small">{{ levelText(a.level) }}</el-tag>
            <strong>{{ a.room_no }}</strong>
            <span class="muted">{{ a.type }}</span>
          </div>
          <div class="alert-meta">
            {{ a.trigger_value }}W · 阈值 {{ a.threshold }}W
          </div>
          <div class="alert-meta">
            <span :class="['state-text', a.status]">{{ alertStatusText(a.status) }}</span>
            · {{ shortTime(a.created_at) }}
          </div>
        </div>
      </aside>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import http from '../../api/http'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const summary = ref<any>(null)
const dorms = ref<any[]>([])
const loadPoints = ref<any[]>([])
const alerts = ref<any[]>([])
const selectedDorm = ref(314)
const injecting = ref(false)
const resetting = ref(false)
const chartRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null
let timer: number | undefined

const selectedDormData = computed(() => dorms.value.find((d) => d.id === selectedDorm.value))

const statusMap: Record<string, string> = {
  normal: '正常',
  warned: '提醒',
  cut_off: '分闸',
  locked: '锁定',
}

function statusText(status?: string) {
  return statusMap[status || ''] || '未知'
}

function levelText(level?: string) {
  return level === 'urgent' ? '紧急' : level === 'warn' ? '提醒' : '提示'
}

function alertStatusText(status?: string) {
  const map: Record<string, string> = {
    open: '待通知', notified: '待处理', student_responded: '已响应',
    reviewing: '复核中', resolved: '已处理', closed: '已关闭',
  }
  return map[status || ''] || status || ''
}

function tagType(status?: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'cut_off') return 'danger'
  if (status === 'warned') return 'warning'
  if (status === 'locked') return 'info'
  return 'success'
}

function shortTime(value?: string) {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleTimeString('zh-CN', { hour12: false })
}

async function refresh() {
  try {
    const [summaryRes, dormsRes, loadRes, alertsRes]: any[] = await Promise.all([
      http.get('/dashboard/summary'),
      http.get('/dorms'),
      http.get('/load', { params: { dormId: selectedDorm.value, minutes: 60 } }),
      http.get('/alerts', { params: { limit: 20 } }),
    ])
    summary.value = summaryRes.data
    dorms.value = dormsRes.data
    loadPoints.value = loadRes.data
    alerts.value = alertsRes.data
    await nextTick()
    renderChart()
  } catch (error) {
    console.error(error)
  }
}

function renderChart() {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  const points = loadPoints.value
  chart.setOption({
    grid: { left: 48, right: 20, top: 20, bottom: 32 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: points.map((p) => shortTime(p.ts)),
      axisLabel: { color: 'var(--muted)', fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      name: 'W',
      axisLabel: { color: 'var(--muted)' },
      splitLine: { lineStyle: { color: '#eef3f8' } },
    },
    series: [
      {
        type: 'line',
        smooth: true,
        symbol: 'none',
        data: points.map((p) => p.p_w),
        lineStyle: { color: '#0284c7', width: 2 },
        areaStyle: { color: 'rgba(14,165,233,0.15)' },
        markLine: {
          silent: true,
          symbol: 'none',
          data: [{ yAxis: 500, label: { formatter: '处置线 500W', color: '#b45309' }, lineStyle: { color: '#f59e0b' } }],
        },
      },
    ],
  })
}

function selectDorm(id: number) {
  selectedDorm.value = id
  refresh()
}

async function inject() {
  injecting.value = true
  try {
    await http.post('/demo/inject', {
      dormId: 314,
      device: 'kettle_800',
      durationS: 8,
      scene: 'violation',
    })
    ElMessage.success('已注入 800W，5 秒后模拟分闸')
    await refresh()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || '注入失败')
  } finally {
    injecting.value = false
  }
}

async function resetDemo() {
  resetting.value = true
  try {
    await http.post('/demo/reset', { confirm: 'RESET-DEMO' })
    ElMessage.success('演示已重置')
    await refresh()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || '重置失败')
  } finally {
    resetting.value = false
  }
}

function logout() {
  auth.logout()
  router.push('/login')
}

onMounted(() => {
  refresh()
  timer = window.setInterval(refresh, 2000)
  window.addEventListener('resize', resizeChart)
})

onUnmounted(() => {
  if (timer) window.clearInterval(timer)
  window.removeEventListener('resize', resizeChart)
  chart?.dispose()
})

function resizeChart() {
  chart?.resize()
}

</script>

<style scoped>
.page {
  min-height: 100%;
  padding: 18px 20px 24px;
  background: var(--bg);
}
.topbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
}
.mark {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  background: var(--navy);
  color: #fff;
  font-weight: 700;
}
.brand strong {
  display: block;
  font-size: 18px;
}
.muted {
  color: var(--muted);
}
.small {
  font-size: 12px;
}
.actions {
  display: flex;
  gap: 10px;
  align-items: center;
}
.layout {
  display: grid;
  grid-template-columns: 250px minmax(0, 1fr) 310px;
  gap: 14px;
  height: calc(100vh - 92px);
}
.panel {
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 8px 30px rgba(11, 37, 69, 0.06);
  padding: 14px;
  overflow: hidden;
}
.dorm-panel {
  overflow-y: auto;
}
.panel-title {
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 10px;
}
.dorm-row {
  width: 100%;
  border: 0;
  background: transparent;
  display: grid;
  grid-template-columns: 10px 1fr auto;
  grid-template-rows: auto auto;
  gap: 2px 8px;
  align-items: center;
  padding: 9px 8px;
  border-radius: 10px;
  cursor: pointer;
  text-align: left;
  color: var(--ink);
}
.dorm-row:hover,
.dorm-row.active {
  background: var(--accent-soft);
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #22c55e;
  grid-row: 1 / 3;
}
.dot.warned { background: var(--warning-2); }
.dot.cut_off { background: #ef4444; }
.dot.locked { background: #94a3b8; }
.room { font-weight: 700; }
.power { font-size: 12px; color: var(--ink-2); }
.state { font-size: 11px; color: var(--muted); grid-column: 2 / 4; }
.center {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}
.metrics {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}
.metric {
  background: #fff;
  border-radius: 14px;
  padding: 12px 14px;
  box-shadow: 0 8px 30px rgba(11, 37, 69, 0.06);
}
.metric span {
  display: block;
  font-size: 12px;
  color: var(--muted);
  margin-bottom: 4px;
}
.metric b { font-size: 22px; }
.metric.warning b { color: var(--warning); }
.metric.danger b { color: var(--danger); }
.chart-panel {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
}
.chart {
  flex: 1;
  min-height: 0;
  margin-top: 8px;
}
.alert-panel {
  overflow-y: auto;
}
.alert-item {
  padding: 10px 0;
  border-bottom: 1px solid #eef3f8;
}
.alert-item:last-child { border-bottom: 0; }
.alert-top {
  display: flex;
  align-items: center;
  gap: 6px;
}
.alert-meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--muted);
}
.state-text.resolved { color: var(--success); }
.state-text.notified { color: var(--warning); }
.empty {
  color: var(--muted);
  font-size: 13px;
  padding: 20px 0;
}
</style>
