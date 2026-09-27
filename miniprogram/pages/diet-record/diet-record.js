const { post, del } = require('../../utils/request')

// 常见食物快速添加库
const QUICK_FOODS = [
  { name: '糙米饭', emoji: '', amount: '1碗(150g)', calories: 174 },
  { name: '鸡胸肉', emoji: '🍗', amount: '100g', calories: 165 },
  { name: '西兰花', emoji: '🥦', amount: '100g', calories: 35 },
  { name: '苹果', emoji: '🍎', amount: '1个(200g)', calories: 112 },
  { name: '鸡蛋', emoji: '🥚', amount: '1个(50g)', calories: 72 },
  { name: '牛奶', emoji: '🥛', amount: '1杯(250ml)', calories: 135 },
  { name: '全麦面包', emoji: '🍞', amount: '2片(60g)', calories: 150 },
  { name: '香蕉', emoji: '🍌', amount: '1根(120g)', calories: 107 },
  { name: '番茄', emoji: '🍅', amount: '1个(150g)', calories: 27 },
  { name: '黄瓜', emoji: '🥒', amount: '1根(200g)', calories: 30 },
  { name: '酸奶', emoji: '', amount: '1杯(200g)', calories: 120 },
  { name: '燕麦', emoji: '🥣', amount: '1碗(40g)', calories: 150 }
]

Page({
  data: {
    selectedDate: '',
    meals: ['早餐', '午餐', '晚餐', '加餐'],
    currentMeal: '午餐',
    totalCalories: 0,
    carbPercent: 45,
    proteinPercent: 30,
    fatPercent: 25,
    // 按餐次分组的食物
    foodsByMeal: {
      '早餐': [],
      '午餐': [],
      '晚餐': [],
      '加餐': []
    },
    currentFoods: [],
    quickFoods: QUICK_FOODS,
    showFoodForm: false,
    savingFood: false,
    foodForm: {
      name: '',
      amount: '',
      calories: ''
    }
  },

  onLoad() {
    const today = new Date().toISOString().slice(0, 10)
    this.setData({
      selectedDate: today,
      currentFoods: []
    })
  },

  onShow() {
    this.loadFoods()
  },

  onDateChange(e) {
    this.setData({ selectedDate: e.detail.value })
    this.loadFoods()
  },

  switchMeal(e) {
    const meal = e.currentTarget.dataset.meal
    this.setData({
      currentMeal: meal,
      currentFoods: this.data.foodsByMeal[meal] || []
    })
  },

  async loadFoods() {
    // 饮食记录暂时使用本地存储，因为后端没有专门的饮食记录表
    // 后续可以扩展后端接口
    try {
      const app = getApp()
      const userId = app.globalData.userInfo?.id
      const key = `diet_${userId}_${this.data.selectedDate}`
      const stored = wx.getStorageSync(key)
      if (stored) {
        const foodsByMeal = stored
        const allFoods = Object.values(foodsByMeal).flat()
        const totalCalories = allFoods.reduce((sum, f) => sum + Number(f.calories || 0), 0)
        this.setData({
          foodsByMeal,
          currentFoods: foodsByMeal[this.data.currentMeal] || [],
          totalCalories
        })
      }
    } catch (err) {
      console.error('加载饮食记录失败:', err)
    }
  },

  saveFoods() {
    try {
      const app = getApp()
      const userId = app.globalData.userInfo?.id
      const key = `diet_${userId}_${this.data.selectedDate}`
      wx.setStorageSync(key, this.data.foodsByMeal)
    } catch (err) {
      console.error('保存饮食记录失败:', err)
    }
  },

  // 快速添加食物
  quickAddFood(e) {
    const food = e.currentTarget.dataset.food
    const meal = this.data.currentMeal
    const foodsByMeal = { ...this.data.foodsByMeal }
    const newFood = {
      id: Date.now(),
      name: food.name,
      emoji: food.emoji,
      amount: food.amount,
      calories: food.calories,
      meal: meal
    }
    foodsByMeal[meal] = [...(foodsByMeal[meal] || []), newFood]

    const allFoods = Object.values(foodsByMeal).flat()
    const totalCalories = allFoods.reduce((sum, f) => sum + Number(f.calories || 0), 0)

    this.setData({
      foodsByMeal,
      currentFoods: foodsByMeal[meal],
      totalCalories
    })
    this.saveFoods()
    wx.showToast({ title: '已添加', icon: 'success' })
  },

  deleteFood(e) {
    const id = e.currentTarget.dataset.id
    const meal = this.data.currentMeal
    const foodsByMeal = { ...this.data.foodsByMeal }
    foodsByMeal[meal] = (foodsByMeal[meal] || []).filter(f => f.id !== id)

    const allFoods = Object.values(foodsByMeal).flat()
    const totalCalories = allFoods.reduce((sum, f) => sum + Number(f.calories || 0), 0)

    this.setData({
      foodsByMeal,
      currentFoods: foodsByMeal[meal],
      totalCalories
    })
    this.saveFoods()
  },

  openFoodForm() {
    this.setData({
      showFoodForm: true,
      foodForm: { name: '', amount: '', calories: '' }
    })
  },

  closeFoodForm() {
    this.setData({ showFoodForm: false })
  },

  onFoodInput(e) {
    const field = e.currentTarget.dataset.field
    this.setData({ [`foodForm.${field}`]: e.detail.value })
  },

  submitFood() {
    const { foodForm, currentMeal } = this.data
    if (!foodForm.name) {
      wx.showToast({ title: '请输入食物名称', icon: 'none' })
      return
    }
    if (!foodForm.calories) {
      wx.showToast({ title: '请输入热量', icon: 'none' })
      return
    }

    const foodsByMeal = { ...this.data.foodsByMeal }
    const newFood = {
      id: Date.now(),
      name: foodForm.name,
      emoji: '',
      amount: foodForm.amount || '适量',
      calories: Number(foodForm.calories),
      meal: currentMeal
    }
    foodsByMeal[currentMeal] = [...(foodsByMeal[currentMeal] || []), newFood]

    const allFoods = Object.values(foodsByMeal).flat()
    const totalCalories = allFoods.reduce((sum, f) => sum + Number(f.calories || 0), 0)

    this.setData({
      foodsByMeal,
      currentFoods: foodsByMeal[currentMeal],
      totalCalories,
      showFoodForm: false
    })
    this.saveFoods()
    wx.showToast({ title: '添加成功', icon: 'success' })
  }
})
