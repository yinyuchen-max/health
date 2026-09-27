<template>
  <div class="doctor-register-page">
    <section class="register-shell">
      <div class="hero-panel">
        <div class="brand-copy">
          <p class="brand-tag">Health System</p>
          <h1>加入医生团队</h1>
          <p class="brand-desc">
            注册成为平台认证医生，接收患者预约和在线咨询，查看患者健康报告。
          </p>
        </div>

        <!-- 装饰插图区 -->
        <div class="hero-illustration">
          <svg viewBox="0 0 400 220" fill="none" xmlns="http://www.w3.org/2000/svg">
            <!-- 背景圆 -->
            <circle cx="200" cy="110" r="90" fill="#e8edf5" opacity="0.6"/>
            <circle cx="200" cy="110" r="65" fill="#f0f4fa" opacity="0.8"/>
            <!-- 医生图标 - 十字 -->
            <rect x="185" y="80" width="30" height="60" rx="6" fill="#1f2430" opacity="0.7"/>
            <rect x="170" y="95" width="60" height="30" rx="6" fill="#1f2430" opacity="0.7"/>
            <!-- 听诊器曲线 -->
            <path d="M130 140 C130 160, 160 170, 170 155" stroke="#1f2430" stroke-width="2.5" fill="none" stroke-linecap="round"/>
            <circle cx="130" cy="140" r="6" fill="#1f2430" opacity="0.5"/>
            <!-- 心率线 -->
            <polyline points="240,110 260,110 270,90 280,130 290,105 300,115 320,110"
              stroke="#1f2430" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round" opacity="0.6"/>
            <!-- 浮动装饰 -->
            <circle cx="320" cy="60" r="5" fill="#e74c3c" opacity="0.3"/>
            <circle cx="100" cy="80" r="4" fill="#1f2430" opacity="0.15"/>
            <circle cx="330" cy="160" r="6" fill="#1f2430" opacity="0.1"/>
            <circle cx="80" cy="150" r="3" fill="#e74c3c" opacity="0.2"/>
          </svg>
        </div>

        <!-- 数据统计 -->
        <div class="hero-stats">
          <div class="stat-item">
            <span class="stat-number">认证</span>
            <span class="stat-label">资质审核</span>
          </div>
          <div class="stat-divider"></div>
          <div class="stat-item">
            <span class="stat-number">在线</span>
            <span class="stat-label">患者咨询</span>
          </div>
          <div class="stat-divider"></div>
          <div class="stat-item">
            <span class="stat-number">报告</span>
            <span class="stat-label">健康查看</span>
          </div>
        </div>

        <div class="hero-footer">
          <div class="feature-item">
            <el-icon :size="20"><FirstAidKit /></el-icon>
            <span>接收患者预约</span>
          </div>
          <div class="feature-item">
            <el-icon :size="20"><ChatDotRound /></el-icon>
            <span>在线医患沟通</span>
          </div>
          <div class="feature-item">
            <el-icon :size="20"><DataLine /></el-icon>
            <span>查看健康报告</span>
          </div>
        </div>
      </div>

      <div class="form-panel">
        <div class="form-card">
          <div class="card-header">
            <p>Doctor Register</p>
            <h2>医生注册</h2>
            <span>填写账号信息和执业资质，审核通过后即可使用医生功能</span>
          </div>

          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            label-position="top"
            class="register-form"
          >
            <!-- 账号信息 -->
            <div class="section-title">账号信息</div>

            <el-form-item label="用户名" prop="username">
              <el-input v-model="form.username" placeholder="请输入用户名（3-20个字符）" :prefix-icon="User" size="large" clearable />
            </el-form-item>

            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="密码" prop="password">
                  <el-input v-model="form.password" type="password" placeholder="至少6个字符" :prefix-icon="Lock" size="large" show-password />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="确认密码" prop="confirmPassword">
                  <el-input v-model="form.confirmPassword" type="password" placeholder="再次输入密码" :prefix-icon="Lock" size="large" show-password />
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="邮箱" prop="email">
                  <el-input v-model="form.email" placeholder="用于接收审核结果通知" :prefix-icon="Message" size="large" clearable />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="手机号" prop="phone">
                  <el-input v-model="form.phone" placeholder="选填" :prefix-icon="Phone" size="large" clearable />
                </el-form-item>
              </el-col>
            </el-row>

            <!-- 医生信息 -->
            <div class="section-title">执业信息</div>

            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="真实姓名" prop="realName">
                  <el-input v-model="form.realName" placeholder="执业证书上的姓名" size="large" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="所属医院" prop="hospital">
                  <el-input v-model="form.hospital" placeholder="例：北京协和医院" size="large" />
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="科室" prop="department">
                  <el-select v-model="form.department" placeholder="选择科室" size="large" style="width: 100%">
                    <el-option v-for="dept in departments" :key="dept" :label="dept" :value="dept" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="职称" prop="title">
                  <el-select v-model="form.title" placeholder="选择职称（选填）" size="large" clearable style="width: 100%">
                    <el-option label="主任医师" value="主任医师" />
                    <el-option label="副主任医师" value="副主任医师" />
                    <el-option label="主治医师" value="主治医师" />
                    <el-option label="住院医师" value="住院医师" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>

            <el-form-item label="执业证书编号" prop="licenseNumber">
              <el-input v-model="form.licenseNumber" placeholder="医师执业证书编号" size="large" />
            </el-form-item>

            <el-form-item label="擅长领域" prop="specialization">
              <el-input v-model="form.specialization" placeholder="例：高血压、糖尿病、心脑血管疾病" size="large" />
            </el-form-item>

            <el-form-item label="个人简介（选填）" prop="introduction">
              <el-input v-model="form.introduction" type="textarea" :rows="2" placeholder="介绍从业经历和专业特长" />
            </el-form-item>

            <div class="actions">
              <el-button type="primary" class="submit-button" :loading="loading" @click="handleSubmit">
                提交申请
              </el-button>
              <el-button class="back-button" @click="router.push('/')">
                返回登录
              </el-button>
            </div>
          </el-form>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, User, Message, Phone, FirstAidKit, ChatDotRound, DataLine } from '@element-plus/icons-vue'
import request from '../utils/request'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)

const departments = [
  '内科', '外科', '儿科', '妇科', '皮肤科', '眼科', '耳鼻喉科', '口腔科',
  '骨科', '神经内科', '心血管内科', '消化内科', '呼吸内科', '泌尿外科'
]

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  email: '',
  phone: '',
  realName: '',
  hospital: '',
  department: '',
  title: '',
  specialization: '',
  licenseNumber: '',
  introduction: ''
})

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度为3-20个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少6个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  hospital: [{ required: true, message: '请输入所属医院', trigger: 'blur' }],
  department: [{ required: true, message: '请选择科室', trigger: 'change' }],
  licenseNumber: [{ required: true, message: '请输入执业证书编号', trigger: 'blur' }]
}

const handleSubmit = () => {
  formRef.value.validate(async (valid) => {
    if (!valid) return

    loading.value = true
    try {
      const { confirmPassword, ...submitData } = form
      await request.post('/doctor/full-register', submitData)
      ElMessage.success({
        message: '注册成功！请等待管理员审核您的医生资质',
        duration: 3000,
        showClose: true
      })
      setTimeout(() => {
        router.push('/')
      }, 1000)
    } catch (error) {
      const message = error?.message || '注册失败，请稍后重试'
      if (message.includes('已存在')) {
        ElMessage.warning({
          message: '该用户名已被注册，请换一个',
          duration: 3000,
          showClose: true
        })
        form.username = ''
      } else {
        ElMessage.error({
          message,
          duration: 3000,
          showClose: true
        })
      }
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.doctor-register-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: linear-gradient(180deg, #EBF5FF 0%, #E8F0FE 100%);
}

.register-shell {
  width: min(1280px, 100%);
  min-height: min(820px, calc(100vh - 48px));
  display: grid;
  grid-template-columns: 0.9fr 1.1fr;
  background: #fff;
  border-radius: 40px;
  overflow: hidden;
  box-shadow: 0 28px 90px rgba(43, 52, 69, 0.14);
}

.hero-panel {
  padding: 58px 48px 34px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 24px;
  background:
    radial-gradient(circle at top right, rgba(255, 255, 255, 0.84), transparent 25%),
    linear-gradient(180deg, #f0f4fa 0%, #e8edf5 100%);
}

.brand-copy { max-width: 520px; }

.brand-tag {
  margin: 0 0 18px;
  color: #7d8797;
  font-size: 13px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  font-weight: 700;
}

.brand-copy h1 {
  margin: 0;
  color: #1f2430;
  font-size: clamp(34px, 4vw, 48px);
  line-height: 1.04;
  letter-spacing: -0.03em;
}

.brand-desc {
  margin: 18px 0 0;
  max-width: 480px;
  color: #5f6979;
  font-size: 16px;
  line-height: 1.85;
}

.hero-illustration {
  display: flex;
  justify-content: center;
  align-items: center;
  flex: 1;
  min-height: 0;
}

.hero-illustration svg {
  width: 100%;
  max-width: 360px;
  height: auto;
}

.hero-stats {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 16px 0;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.stat-number {
  font-size: 22px;
  font-weight: 800;
  color: #1f2430;
}

.stat-label {
  font-size: 12px;
  color: #7d8797;
  font-weight: 500;
}

.stat-divider {
  width: 1px;
  height: 32px;
  background: #d0d7e2;
}

.hero-footer {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #5f6979;
  font-size: 14px;
  font-weight: 500;
}

.feature-item .el-icon { color: #1f2430; }

.form-panel {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 32px;
  overflow-y: auto;
  background: linear-gradient(180deg, #ffffff 0%, #f9fbfd 100%);
}

.form-card {
  width: min(520px, 100%);
  padding: 8px 6px;
}

.card-header { margin-bottom: 24px; }

.card-header p {
  margin: 0;
  color: #828c9b;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.card-header h2 {
  margin: 12px 0 10px;
  color: #1f2430;
  font-size: 32px;
  line-height: 1.1;
}

.card-header span {
  color: #6e7787;
  font-size: 14px;
  line-height: 1.75;
}

.section-title {
  font-size: 13px;
  font-weight: 700;
  color: #7d8797;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  margin: 16px 0 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #eef1f6;
}

.actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-top: 18px;
}

.submit-button,
.back-button {
  height: 50px;
  border-radius: 14px;
  font-size: 15px;
  font-weight: 700;
}

.submit-button {
  border: none;
  background: #1f2430;
  box-shadow: 0 16px 28px rgba(31, 36, 48, 0.18);
}

.back-button {
  border: 1px solid #d8dee7;
  color: #33394a;
  background: #fff;
}

:deep(.el-form-item) { margin-bottom: 16px; }

:deep(.el-form-item__label) {
  color: #2f3545;
  font-weight: 700;
  padding-bottom: 6px;
}

:deep(.el-input__wrapper),
:deep(.el-select .el-input__wrapper) {
  min-height: 46px;
  padding-left: 14px;
  padding-right: 14px;
  border-radius: 12px;
  box-shadow: 0 0 0 1px #e1e6ee;
  background: #fff;
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #1f2430;
}

:deep(.el-input__inner) { font-size: 14px; }

:deep(.el-input__inner::placeholder) { color: #9aa3b3; }

:deep(.el-textarea__inner) {
  border-radius: 12px;
  font-size: 14px;
}

@media (max-width: 1080px) {
  .register-shell {
    grid-template-columns: 1fr;
  }

  .hero-panel {
    min-height: 260px;
    gap: 16px;
    padding: 36px 32px 24px;
  }

  .hero-illustration { display: none; }
}

@media (max-width: 720px) {
  .doctor-register-page { padding: 12px; }

  .register-shell {
    min-height: auto;
    border-radius: 24px;
  }

  .hero-panel {
    padding: 24px 18px 16px;
    min-height: 180px;
    gap: 12px;
  }

  .hero-stats { padding: 8px 0; }
  .stat-number { font-size: 18px; }

  .form-panel { padding: 20px 16px 24px; }

  .brand-copy h1,
  .card-header h2 { font-size: 26px; }

  .actions { grid-template-columns: 1fr; }
}
</style>
