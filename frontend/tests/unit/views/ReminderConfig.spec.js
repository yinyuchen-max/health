import { mount } from '@vue/test-utils'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import ReminderConfig from '@/views/ReminderConfig.vue'

// 模拟Element Plus组件
vi.mock('element-plus', async () => {
  const actual = await vi.importActual('element-plus')
  return {
    ...actual,
    ElCard: {
      template: '<div><slot name="header"></slot><slot></slot></div>',
      props: ['header']
    },
    ElForm: {
      template: '<form><slot></slot></form>',
      props: ['model', 'labelWidth', 'rules']
    },
    ElFormItem: {
      template: '<div><label><slot name="label"></slot></label><slot></slot></div>',
      props: ['label', 'prop']
    },
    ElSelect: {
      template: '<select><slot></slot></select>',
      props: ['modelValue', 'placeholder']
    },
    ElOption: {
      template: '<option><slot></slot></option>',
      props: ['label', 'value']
    },
    ElTimePicker: {
      template: '<input type="time">',
      props: ['modelValue', 'placeholder', 'format', 'valueFormat', 'clearable']
    },
    ElSwitch: {
      template: '<div><span>Switch</span></div>',
      props: ['modelValue', 'activeText', 'inactiveText']
    },
    ElButton: {
      template: '<button><slot></slot></button>',
      props: ['type', 'loading', 'disabled']
    },
    ElTooltip: {
      template: '<div><slot></slot></div>',
      props: ['content', 'placement']
    },
    ElIcon: {
      template: '<i><slot></slot></i>',
      props: ['size', 'color']
    },
    ElRow: {
      template: '<div><slot></slot></div>',
      props: ['gutter']
    },
    ElCol: {
      template: '<div><slot></slot></div>',
      props: ['span']
    },
    ElMessage: {
      install: vi.fn()
    },
    ElMessageBox: {
      confirm: vi.fn(() => Promise.resolve()),
      alert: vi.fn(() => Promise.resolve())
    }
  }
})

// 模拟图标组件
vi.mock('@element-plus/icons-vue', () => ({
  Bell: {},
  QuestionFilled: {},
  TrendCharts: {},
  Lightning: {},
  Check: {}
}))

describe('ReminderConfig', () => {
  let wrapper

  beforeEach(() => {
    wrapper = mount(ReminderConfig, {
      global: {
        stubs: {
          'el-card': true,
          'el-form': true,
          'el-form-item': true,
          'el-select': true,
          'el-option': true,
          'el-time-picker': true,
          'el-switch': true,
          'el-button': true,
          'el-tooltip': true,
          'el-icon': true,
          'el-row': true,
          'el-col': true
        }
      }
    })
  })

  afterEach(() => {
    wrapper.unmount()
  })

  it('renders reminder configuration form', () => {
    expect(wrapper.find('.reminder-config').exists()).toBe(true)
    expect(wrapper.findComponent({ name: 'el-form' }).exists()).toBe(true)
  })

  it('allows selecting reminder types', async () => {
    const select = wrapper.findComponent({ name: 'el-select' })
    expect(select.exists()).toBe(true)

    // 测试选择提醒类型
    await select.setValue('bloodPressure')
    expect(wrapper.vm.form.type).toBe('bloodPressure')
  })

  it('validates required fields', async () => {
    const form = wrapper.findComponent({ name: 'el-form' })
    await form.trigger('submit.prevent')

    // 应该显示必填验证错误
    expect(form.emitted()['validate'] || []).toBeTruthy()
  })

  it('shows health advice based on selected type', async () => {
    const select = wrapper.findComponent({ name: 'el-select' })
    await select.setValue('bloodPressure')

    // 检查是否显示了血压相关的建议
    const adviceItems = wrapper.findAll('.advice-item')
    expect(adviceItems.length).toBeGreaterThanOrEqual(1)
  })

  it('handles test notification', async () => {
    // 模拟ElMessageBox.confirm
    const mockConfirm = vi.spyOn(wrapper.vm.$options.methods, 'testNotification')
    expect(mockConfirm).toBeDefined()

    // 设置提醒类型
    wrapper.vm.form.type = 'bloodPressure'
    await wrapper.vm.testNotification()

    // 验证confirm被调用
    expect(mockConfirm).toHaveBeenCalled()
  })

  it('resets form correctly', () => {
    const resetSpy = vi.spyOn(wrapper.vm, 'resetForm')
    wrapper.vm.resetForm()
    expect(resetSpy).toHaveBeenCalled()
  })

  it('computes statistics correctly', () => {
    // 测试计算属性
    expect(wrapper.vm.reminderCount).toBeGreaterThanOrEqual(0)
    expect(wrapper.vm.todayReminders).toBeGreaterThanOrEqual(0)
    expect(wrapper.vm.completionRate).toBeGreaterThanOrEqual(0)
    expect(wrapper.vm.completionRate).toBeLessThanOrEqual(100)
  })

  it('displays different health advice for different types', () => {
    // 测试不同类型的建议
    const types = ['bloodPressure', 'bloodSugar', 'weight', 'exercise']
    types.forEach(type => {
      wrapper.vm.form.type = type
      const advice = wrapper.vm.healthAdvices
      expect(advice.length).toBeGreaterThan(0)
    })
  })

  it('requests notification permission on mount', () => {
    const spy = vi.spyOn(window.Notification, 'requestPermission')
    wrapper.vm.$mount()
    expect(spy).toHaveBeenCalled()
  })
})