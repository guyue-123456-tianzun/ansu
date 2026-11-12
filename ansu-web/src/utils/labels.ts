export const dormStatusText = (status?: string) => {
  const map: Record<string, string> = {
    normal: '正常',
    warned: '提醒',
    cut_off: '分闸',
    locked: '锁定',
  }
  return map[status || ''] || '未知'
}

export const dormStatusLongText = (status?: string) => {
  const map: Record<string, string> = {
    normal: '用电正常',
    warned: '提醒中',
    cut_off: '已模拟分闸',
    locked: '已锁定',
  }
  return map[status || ''] || '加载中'
}

export const alertLevelText = (level?: string) =>
  level === 'urgent' ? '紧急' : level === 'warn' ? '提醒' : '提示'

export const alertStatusText = (status?: string) => {
  const map: Record<string, string> = {
    open: '待通知',
    notified: '待处理',
    student_responded: '已响应',
    reviewing: '复核中',
    resolved: '已处理',
    closed: '已关闭',
  }
  return map[status || ''] || status || ''
}

export const alertTypeText = (type?: string) => {
  const map: Record<string, string> = {
    resistive: '阻性',
    overload: '总功率',
    leakage: '漏电',
    temp: '温度',
    night_anomaly: '夜间异常',
  }
  return map[type || ''] || type || ''
}

export const appealStatusText = (status?: string) => {
  const map: Record<string, string> = {
    submitted: '待复核',
    reviewing: '复核中',
    approved: '已通过',
    rejected: '已驳回',
    withdrawn: '已撤回',
  }
  return map[status || ''] || status || ''
}

export const appealReasonText = (reason?: string) => {
  const map: Record<string, string> = {
    misjudge: '误判',
    device_fault: '设备故障',
    special: '特殊情况',
  }
  return map[reason || ''] || reason || ''
}
