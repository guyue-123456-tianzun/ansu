<template>
  <div class="page">
    <header class="topbar">
      <button class="back" @click="router.push('/admin/dashboard')">← 大屏</button>
      <strong>申诉工作台</strong>
      <span class="spacer" />
      <el-button size="small" @click="load">刷新</el-button>
    </header>

    <div v-if="appeals.length === 0" class="empty">暂无待办申诉</div>

    <div v-for="a in appeals" :key="a.id" class="card">
      <div class="row">
        <strong>{{ a.room_no }} 宿舍</strong>
        <el-tag size="small" :type="a.status === 'submitted' ? 'warning' : 'info'">{{ a.status }}</el-tag>
      </div>
      <div class="meta">{{ a.reason_type }} · {{ a.submitter }} · {{ formatTime(a.deadline_at) }} 截止</div>
      <p class="statement">{{ a.statement }}</p>
      <div class="actions">
        <el-button size="small" type="success" :loading="busy === a.id" @click="approve(a)">通过</el-button>
        <el-button size="small" type="danger" :loading="busy === a.id" @click="reject(a)">驳回</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../../api/http'

const router = useRouter()
const appeals = ref<any[]>([])
const busy = ref<number | null>(null)

function formatTime(value?: string) {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}

async function load() {
  try {
    const res: any = await http.get('/manager/appeals')
    appeals.value = (res.data || []).filter((a: any) => ['submitted', 'reviewing'].includes(a.status))
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || '加载失败')
  }
}

async function approve(a: any) {
  busy.value = a.id
  try {
    await http.post(`/manager/appeals/${a.id}/review`, {
      decision: 'approved',
      note: '证据核实，撤销本次处置。',
    })
    ElMessage.success('已通过，学生用电恢复、扣分撤销')
    await load()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || '操作失败')
  } finally {
    busy.value = null
  }
}

async function reject(a: any) {
  try {
    const { value } = await ElMessageBox.prompt('请填写驳回理由', '驳回申诉', {
      inputPlaceholder: '例如：设备特征与阻性负载一致',
      inputValidator: (v) => (v && v.trim().length >= 2 ? true : '理由至少 2 个字'),
    })
    busy.value = a.id
    await http.post(`/manager/appeals/${a.id}/review`, {
      decision: 'rejected',
      note: value.trim(),
    })
    ElMessage.success('已驳回')
    await load()
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error?.response?.data?.message || '操作失败')
    }
  } finally {
    busy.value = null
  }
}

onMounted(load)
</script>

<style scoped>
.page {
  width: 100%;
  max-width: none;
  margin: 0;
  min-height: 100%;
  padding: 20px 18px 40px;
}
@media (min-width: 900px) {
  .page { padding: 24px 32px 40px; }
}
.topbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}
.back {
  border: 0;
  background: #fff;
  border-radius: 10px;
  padding: 8px 12px;
  color: var(--navy);
  cursor: pointer;
}
.spacer { flex: 1; }
.empty {
  padding: 40px;
  text-align: center;
  color: var(--muted);
  background: #fff;
  border-radius: 16px;
}
.card {
  background: #fff;
  border-radius: 16px;
  padding: 16px;
  margin-bottom: 12px;
  box-shadow: 0 8px 24px rgba(11, 37, 69, 0.06);
}
.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.meta {
  margin-top: 6px;
  font-size: 12px;
  color: var(--muted);
}
.statement {
  margin: 10px 0;
  font-size: 13px;
  color: var(--ink-2);
  line-height: 1.6;
}
.actions {
  display: flex;
  gap: 8px;
}
</style>
