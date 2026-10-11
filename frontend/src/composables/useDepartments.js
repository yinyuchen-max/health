import { ref, onMounted } from 'vue'
import request from '../utils/request'

const departments = ref([])
const departmentsLoading = ref(false)
const departmentsError = ref('')
let pending

async function reloadDepartments() {
  if (pending) return pending
  departmentsLoading.value = true
  departmentsError.value = ''
  pending = request.get('/doctor/departments').then(response => {
    const names = response?.data
    if (!Array.isArray(names) || !names.length || names.some(name => typeof name !== 'string')) {
      throw new Error('科室列表格式不正确')
    }
    departments.value = names
  }).catch(() => {
    departmentsError.value = '科室列表加载失败，请重试'
  }).finally(() => {
    departmentsLoading.value = false
    pending = undefined
  })
  return pending
}

export function useDepartments() {
  onMounted(() => { if (!departments.value.length) reloadDepartments() })
  return { departments, departmentsLoading, departmentsError, reloadDepartments }
}
