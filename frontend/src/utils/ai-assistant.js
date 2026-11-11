export function isEmergency(text) {
  return /胸痛|呼吸困难|喘不上气|严重出血|大出血|昏迷|抽搐|一侧无力|说话含糊/.test(text || '')
}

export function mentionsAppointment(text) {
  return /预约|挂号|看医生|门诊/.test(text || '')
}

export async function loadChatHistory(request) {
  const response = await request.get('/chat/history')
  if (response.code !== 200) throw new Error(response.message || '读取对话失败')
  return (response.data || []).map(item => ({
    role: item.role,
    content: item.content,
    time: String(item.recordedAt || '').replace('T', ' ').slice(11, 16)
  }))
}

export async function clearServerChat(request) {
  const response = await request.delete('/chat/history')
  if (response.code !== 200) throw new Error(response.message || '清空失败')
}

export async function sendChatFeedback(request, rating, excerpt) {
  const response = await request.post('/chat/feedback', {
    rating,
    excerpt: String(excerpt || '').slice(0, 500)
  })
  if (response.code !== 200) throw new Error(response.message || '评价失败')
}

export function reminderDraft(text) {
  const base = { frequency: 'daily', enabled: true, smartMode: false }
  if (/血压/.test(text)) return { ...base, type: 'bloodPressure', time: '08:00' }
  if (/血糖/.test(text)) return { ...base, type: 'bloodSugar', time: '08:00' }
  if (/体重/.test(text)) return { ...base, type: 'weight', time: '07:30' }
  if (/睡眠|入睡|作息/.test(text)) return { ...base, type: 'sleep', time: '22:30' }
  if (/喝水|饮水/.test(text)) return { ...base, type: 'water', time: '10:00' }
  if (/运动|锻炼|步行|步数/.test(text)) return { ...base, type: 'exercise', time: '18:00' }
  return null
}

export function sleepMinutes(bedTime, wakeTime) {
  const [bedHour, bedMinute] = String(bedTime || '').split(':').map(Number)
  const [wakeHour, wakeMinute] = String(wakeTime || '').split(':').map(Number)
  if ([bedHour, bedMinute, wakeHour, wakeMinute].some(Number.isNaN)) return 0
  let minutes = wakeHour * 60 + wakeMinute - (bedHour * 60 + bedMinute)
  if (minutes <= 0) minutes += 24 * 60
  return minutes
}
