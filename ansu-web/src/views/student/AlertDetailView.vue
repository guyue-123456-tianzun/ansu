<template>
  <div class="page">
    <header class="topbar">
      <button class="back" @click="router.back()">← 返回</button>
      <strong>告警证据</strong>
      <span class="spacer" />
    </header>

    <div v-if="!detail" class="loading">加载中…</div>

    <template v-else>
      <section class="status-card" :class="detail.alert.status">
        <div class="status-row">
          <span class="tag">{{ levelText(detail.alert.level) }}</span>
          <span class="room">{{ detail.alert.room_no }} 宿舍</span>
          <span class="state">{{ alertStatusText(detail.alert.status) }}</span>
        </div>
        <div class="big">{{ detail.alert.trigger_value }}<span>W</span></div>
        <div class="sub">阈值 {{ detail.alert.threshold }}W · {{ shortTime(detail.alert.created_at) }}</div>
      </section>

      <section class="card">
        <div class="card-title">证据曲线</div>
        <div ref="chartRef" class="chart" />
        <div class="evidence-line">
          PF 0.998 · 阶跃 780W · 持续 5.0s · 规则 resistive_ge_cutoff_for_3s
        </div>
      </section>

      <section class="card">
        <div class="card-title">三段式解释</div>
        <div class="explain">
          <div class="explain-item">
            <span class="label">发生了什么</span>
            <p>{{ detail.explain.what }}</p>
          </div>
          <div class="explain-item">
            <span class="label">为什么判定</span>
            <p>{{ detail.explain.why }}</p>
          </div>
          <div class="explain-item">
            <span class="label">现在可以做什么</span>
            <p>{{ detail.explain.action }}</p>
          </div>
        </div>
      </section>

      <section class="card">
        <div class="card-title">处置时间线</div>
        <div v-for="item in detail.alert.timeline" :key="item.event" class="timeline-row">
          <span class="dot" />
          <span class="time">{{ shortTime(item.at) }}</span>
          <span>{{ item.event }}</span>
        </div>
      </section>

      <div class="actions">
        <el-button :loading="removing" @click="removeDevice">已撤除</el-button>
        <el-button type="primary" @click="appealVisible = true">提交申诉</el-button>
        <el-button @click="goLearn">安全学习</el-button>
      </div>

      <el-dialog v-model="appealVisible" title="提交申诉" width="92%">
        <el-form label-position="top">
          <el-form-item label="原因">
            <el-select v-model="appealReason" style="width: 100%">
              <el-option label="误判" value="misjudge" />
              <el-option label="设备故障" value="device_fault" />
              <el-option label="特殊情况" value="special" />
            </el-select>
          </el-form-item>
          <el-form-item label="说明">
            <el-input v-model="appealText" type="textarea" :rows="4" placeholder="例如：这是台式机电源，不是热得快" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="appealVisible = false">取消</el-button>
          <el-button type="primary" :loading="appealing" @click="submitAppeal">提交</el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import http from '../../api/http'

const route = useRoute()
const router = useRouter()
const detail = ref<any>(null)
const removing = ref(false)
const appealing = ref(false)
const appealVisible = ref(false)
const appealReason = ref('misjudge')
const appealText = ref('')
const chartRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

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

function shortTime(value?: string) {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleTimeString('zh-CN', { hour12: false })
}

async function load() {
  try {
    const res: any = await http.get(`/student/alerts/${route.params.id}`)
    detail.value = res.data
    await nextTick()
    renderChart()
  } catch (error) {
    console.error(error)
  }
}

function renderChart() {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  const points = detail.value?.evidence?.points || []
  chart.setOption({
    grid: { left: 40, right: 16, top: 16, bottom: 28 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: points.map((p: any) => shortTime(p.ts)),
      axisLabel: { color: '#78879b', fontSize: 10 },
    },
    yAxis: {
      type: 'value',
      name: 'W',
      axisLabel: { color: '#78879b' },
      splitLine: { lineStyle: { color: '#eef3f8' } },
    },
    series: [
      {
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 4,
        data: points.map((p: any) => p.p_w),
        lineStyle: { color: '#ef4444', width: 2 },
        areaStyle: { color: 'rgba(239,68,68,0.12)' },
        markLine: {
          silent: true,
          symbol: 'none',
          data: [{ yAxis: detail.value?.alert?.threshold || 500, lineStyle: { color: '#f59e0b' } }],
        },
      },
    ],
  })
}

async function removeDevice() {
  removing.value = true
  try {
    const res: any = await http.post(`/student/alerts/${route.params.id}/respond`, { action: 'removed' })
    ElMessage.success(res.data?.message || '已撤除')
    setTimeout(load, 1200)
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || '操作失败')
  } finally {
    removing.value = false
  }
}

async function submitAppeal() {
  if (!appealText.value.trim()) {
    ElMessage.warning('请填写申诉说明')
    return
  }
  appealing.value = true
  try {
    await http.post('/student/appeals', {
      alertId: Number(route.params.id),
      reasonType: appealReason.value,
      statement: appealText.value.trim(),
    })
    ElMessage.success('申诉已提交，等待宿管复核')
    appealVisible.value = false
    await load()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || '提交失败')
  } finally {
    appealing.value = false
  }
}

function goLearn() {
  router.push(`/m/learning/${route.params.id}`)
}

onMounted(load)
onUnmounted(() => chart?.dispose())
</script>

<style scoped>
.page {
  max-width: 430px;
  margin: 0 auto;
  min-height: 100%;
  padding: 16px 14px 32px;
}
.topbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}
.back {
  border: 0;
  background: #fff;
  border-radius: 10px;
  padding: 8px 10px;
  color: #0b2545;
}
.spacer { flex: 1; }
.loading {
  padding: 40px 0;
  text-align: center;
  color: #78879b;
}
.status-card {
  background: #0b2545;
  color: #fff;
  border-radius: 18px;
  padding: 18px;
}
.status-card.warned { background: #7c3a0b; }
.status-card.cut_off { background: #7f1d1d; }
.status-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.tag {
  padding: 2px 7px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.16);
  font-size: 11px;
}
.state { margin-left: auto; opacity: 0.8; }
.big {
  margin-top: 12px;
  font-size: 40px;
  font-weight: 700;
}
.big span {
  font-size: 15px;
  margin-left: 4px;
  opacity: 0.75;
}
.sub {
  margin-top: 4px;
  font-size: 12px;
  opacity: 0.75;
}
.card {
  margin-top: 14px;
  background: #fff;
  border-radius: 16px;
  padding: 16px;
  box-shadow: 0 8px 24px rgba(11, 37, 69, 0.06);
}
.card-title {
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 10px;
}
.chart {
  height: 190px;
}
.evidence-line {
  margin-top: 8px;
  font-size: 11px;
  color: #78879b;
  line-height: 1.5;
}
.explain {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.label {
  font-size: 11px;
  color: #0ea5e9;
  font-weight: 700;
}
.explain p {
  margin: 4px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: #43536a;
}
.timeline-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  font-size: 13px;
  color: #43536a;
}
.timeline-row .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #0ea5e9;
}
.timeline-row .time {
  width: 58px;
  color: #78879b;
  font-size: 12px;
}
.actions {
  display: flex;
  gap: 8px;
  margin-top: 16px;
}
</style>
