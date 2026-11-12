<template>
  <div class="page">
    <header class="topbar">
      <button class="back" @click="router.back()">← 返回</button>
      <strong>安全学习</strong>
      <span class="spacer" />
    </header>

    <div v-if="!task" class="loading">加载中…</div>

    <template v-else>
      <section class="hero">
        <div class="eyebrow">安全学习 · {{ task.course_id }}</div>
        <h1>{{ task.title }}</h1>
        <p>阅读材料后完成 5 题测验，80 分通过。演示模式跳过视频，直接答题。</p>
      </section>

      <section class="card">
        <div class="card-title">3 分钟材料</div>
        <div v-for="section in task.material" :key="section.title" class="material">
          <h3>{{ section.title }}</h3>
          <p>{{ section.content }}</p>
        </div>
      </section>

      <section class="card">
        <div class="card-title">5 题测验 · 第 {{ task.attempt_no }} 次</div>
        <div v-for="q in task.questions" :key="q.no" class="question">
          <div class="stem">{{ q.no }}. {{ q.stem }}</div>
          <el-radio-group v-model="answers[q.no - 1]" class="options">
            <el-radio v-for="(option, index) in q.options" :key="index" :label="index" class="option">
              {{ String.fromCharCode(65 + index) }}. {{ option }}
            </el-radio>
          </el-radio-group>
        </div>
      </section>

      <div v-if="result" class="result" :class="{ pass: result.passed }">
        <div class="result-title">{{ result.passed ? '通过' : '未通过' }} · {{ result.score }} 分</div>
        <p v-if="result.passed">已恢复用电，信用分 {{ result.credit_after }}；证书 {{ result.certificate_id }}</p>
        <p v-else>再认真看一遍材料，还可以再试一次。</p>
        <div class="result-actions">
          <el-button v-if="!result.passed && result.next_action === 'retry'" type="primary" @click="retry">再试一次</el-button>
          <el-button @click="router.push('/m/home')">返回宿舍卡</el-button>
        </div>
      </div>

      <el-button v-else type="primary" class="submit" :loading="submitting" @click="submit">提交测验</el-button>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '../../api/http'

const route = useRoute()
const router = useRouter()
const task = ref<any>(null)
const answers = ref<number[]>([-1, -1, -1, -1, -1])
const submitting = ref(false)
const result = ref<any>(null)

async function load() {
  result.value = null
  answers.value = [-1, -1, -1, -1, -1]
  try {
    const res: any = await http.get('/student/learning/task', {
      params: { alert_id: route.params.id },
    })
    task.value = res.data
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || '学习任务加载失败')
  }
}

async function submit() {
  if (answers.value.some((a) => a < 0)) {
    ElMessage.warning('请完成全部 5 题')
    return
  }
  submitting.value = true
  try {
    const res: any = await http.post('/student/learning/complete', {
      alertId: Number(route.params.id),
      courseId: task.value.course_id,
      answers: answers.value,
      attemptNo: task.value.attempt_no,
    })
    result.value = res.data
    if (res.data.passed) {
      ElMessage.success('学习通过，已恢复用电')
    } else {
      ElMessage.warning('未通过，可再试一次')
    }
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || '提交失败')
  } finally {
    submitting.value = false
  }
}

async function retry() {
  await load()
}

onMounted(load)
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
.hero {
  background: #0b2545;
  color: #fff;
  border-radius: 18px;
  padding: 20px;
}
.eyebrow {
  font-size: 12px;
  color: #9fd3ff;
}
h1 {
  margin: 8px 0;
  font-size: 20px;
  line-height: 1.4;
}
.hero p {
  margin: 0;
  font-size: 12px;
  color: #b7c6d9;
  line-height: 1.6;
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
.material {
  padding: 8px 0;
  border-bottom: 1px solid #eef3f8;
}
.material:last-child { border-bottom: 0; }
.material h3 {
  margin: 0 0 4px;
  font-size: 13px;
  color: #0ea5e9;
}
.material p {
  margin: 0;
  font-size: 13px;
  color: #43536a;
  line-height: 1.7;
}
.question {
  padding: 12px 0;
  border-bottom: 1px solid #eef3f8;
}
.question:last-child { border-bottom: 0; }
.stem {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.6;
  margin-bottom: 8px;
}
.options {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.option {
  margin-right: 0;
  font-size: 13px;
  white-space: normal;
}
.submit {
  width: 100%;
  margin-top: 16px;
}
.result {
  margin-top: 16px;
  background: #fff8e8;
  border: 1px solid #fde3a7;
  border-radius: 16px;
  padding: 16px;
}
.result.pass {
  background: #eaf7ee;
  border-color: #bfe6cb;
}
.result-title {
  font-size: 16px;
  font-weight: 700;
}
.result p {
  margin: 8px 0 0;
  font-size: 13px;
  color: #43536a;
  line-height: 1.6;
}
.result-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}
</style>
