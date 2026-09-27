Page({
  data: {
    height: '',
    weight: '',
    bmiResult: '',
    bmiLevel: '',
    bmiLevelClass: '',
    scalePosition: 50
  },

  onHeightInput(e) {
    this.setData({ height: e.detail.value })
  },

  onWeightInput(e) {
    this.setData({ weight: e.detail.value })
  },

  calcBMI() {
    const { height, weight } = this.data
    if (!height || !weight) {
      wx.showToast({ title: '请输入身高和体重', icon: 'none' })
      return
    }

    const h = Number(height) / 100 // cm -> m
    const w = Number(weight)
    const bmi = (w / (h * h)).toFixed(1)

    let level, levelClass, position
    if (bmi < 18.5) {
      level = '偏瘦'
      levelClass = 'result-underweight'
      position = 10
    } else if (bmi < 24) {
      level = '正常'
      levelClass = 'result-normal'
      position = 30 + ((bmi - 18.5) / 5.5) * 30
    } else if (bmi < 28) {
      level = '偏胖'
      levelClass = 'result-overweight'
      position = 60 + ((bmi - 24) / 4) * 20
    } else {
      level = '肥胖'
      levelClass = 'result-obese'
      position = 85
    }

    this.setData({
      bmiResult: bmi,
      bmiLevel: level,
      bmiLevelClass,
      scalePosition: Math.min(Math.max(position, 0), 100)
    })
  }
})
