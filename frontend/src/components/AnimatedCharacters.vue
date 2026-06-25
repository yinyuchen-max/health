<template>
  <div
    ref="stageRef"
    class="characters-stage"
    @pointermove="handlePointerMove"
    @pointerleave="resetPointer"
  >
    <div class="stage-glow"></div>
    <div class="stage-floor"></div>

    <div
      v-for="character in characters"
      :key="character.id"
      class="character"
      :class="[
        `character-${character.id}`,
        {
          'guard-mode': isPasswordGuardMode,
          'is-looking': shouldLookAtEachOther && character.lookPartner
        }
      ]"
      :style="getCharacterStyle(character)"
    >
      <div v-if="character.capStyle" class="cap" :style="getCapStyle(character)"></div>
      <div class="highlight"></div>
      <div class="face" :style="getFaceStyle(character)">
        <div
          v-for="eyeIndex in 2"
          :key="`${character.id}-${eyeIndex}`"
          class="eye"
          :class="{ blinking: blinkState[character.id] }"
          :style="getEyeStyle(character)"
        >
          <span class="pupil" :style="getPupilStyle(character, eyeIndex - 1)"></span>
        </div>
      </div>

      <div
        v-if="character.hasMouth"
        class="mouth"
        :class="`mouth-${character.id}`"
        :style="getMouthStyle(character)"
      ></div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'

const props = defineProps({
  isTyping: {
    type: Boolean,
    default: false
  },
  isPasswordGuardMode: {
    type: Boolean,
    default: false
  }
})

const stageRef = ref(null)
const pointer = ref({ x: 0, y: 0 })
const blinkState = reactive({
  purple: false,
  black: false
})
const timers = {
  purple: null,
  black: null
}

const characters = [
  {
    id: 'orange',
    left: 6,
    width: 246,
    height: 214,
    radius: '128px 128px 0 0',
    color: '#ff9f70',
    zIndex: 3,
    faceLeft: 88,
    faceTop: 96,
    gap: 38,
    eyeWidth: 14,
    eyeHeight: 14,
    pupilSize: 9,
    maxDistance: 4.6,
    faceTrackX: 11,
    faceTrackY: 8,
    skewFactor: 5.5,
    mouthLeft: 86,
    mouthTop: 142,
    hasMouth: true,
    mouthType: 'smile',
    lookPartner: false,
    capStyle: null
  },
  {
    id: 'purple',
    left: 78,
    width: 184,
    height: 412,
    radius: '14px 14px 0 0',
    color: '#6947ef',
    zIndex: 1,
    faceLeft: 50,
    faceTop: 48,
    gap: 30,
    eyeWidth: 20,
    eyeHeight: 20,
    pupilSize: 7,
    maxDistance: 4.2,
    faceTrackX: 8,
    faceTrackY: 10,
    skewFactor: 8.5,
    mouthLeft: null,
    mouthTop: null,
    hasMouth: false,
    lookPartner: true,
    capStyle: null
  },
  {
    id: 'black',
    left: 250,
    width: 122,
    height: 314,
    radius: '10px 10px 0 0',
    color: '#2b2c31',
    zIndex: 2,
    faceLeft: 30,
    faceTop: 36,
    gap: 24,
    eyeWidth: 16,
    eyeHeight: 16,
    pupilSize: 6,
    maxDistance: 3.6,
    faceTrackX: 7,
    faceTrackY: 8,
    skewFactor: 7.5,
    mouthLeft: null,
    mouthTop: null,
    hasMouth: false,
    lookPartner: true,
    capStyle: {
      width: 58,
      height: 16,
      top: 0,
      left: 32,
      radius: '0 0 10px 10px'
    }
  },
  {
    id: 'yellow',
    left: 318,
    width: 146,
    height: 240,
    radius: '74px 74px 0 0',
    color: '#e6d64c',
    zIndex: 4,
    faceLeft: 54,
    faceTop: 46,
    gap: 26,
    eyeWidth: 12,
    eyeHeight: 12,
    pupilSize: 9,
    maxDistance: 4.2,
    faceTrackX: 9,
    faceTrackY: 8,
    skewFactor: 5.2,
    mouthLeft: 45,
    mouthTop: 96,
    hasMouth: true,
    mouthType: 'flat',
    lookPartner: false,
    capStyle: null
  }
]

const shouldLookAtEachOther = computed(() => props.isTyping && !props.isPasswordGuardMode)

const getNormalizedPointer = (character) => {
  if (props.isPasswordGuardMode) {
    if (character.id === 'orange') {
      return { x: -0.9, y: -0.7 }
    }
    if (character.id === 'yellow') {
      return { x: -0.85, y: -0.72 }
    }
    if (character.id === 'purple') {
      return { x: -1, y: -0.32 }
    }
    return { x: -1, y: -0.24 }
  }

  if (shouldLookAtEachOther.value && character.lookPartner) {
    return character.id === 'purple'
      ? { x: 0.95, y: -0.06 }
      : { x: -0.95, y: -0.05 }
  }

  return pointer.value
}

const getCharacterStyle = (character) => {
  const direction = getNormalizedPointer(character)
  const skew = direction.x * character.skewFactor

  return {
    left: `${character.left}px`,
    width: `${character.width}px`,
    height: `${character.height}px`,
    borderRadius: character.radius,
    backgroundColor: character.color,
    zIndex: character.zIndex,
    transform: `skewX(${skew}deg)`,
    transformOrigin: 'bottom center'
  }
}

const getFaceStyle = (character) => {
  const direction = getNormalizedPointer(character)
  const shiftX = direction.x * character.faceTrackX
  const shiftY = direction.y * character.faceTrackY
  const baseLeft = props.isPasswordGuardMode ? character.faceLeft - 6 : character.faceLeft
  const baseTop = props.isPasswordGuardMode ? character.faceTop - 6 : character.faceTop

  return {
    left: `${baseLeft}px`,
    top: `${baseTop}px`,
    gap: `${character.gap}px`,
    transform: `translate(${shiftX}px, ${shiftY}px)`
  }
}

const getEyeStyle = (character) => ({
  width: `${character.eyeWidth}px`,
  height: `${character.eyeHeight}px`
})

const getPupilStyle = (character, eyeIndex) => {
  const direction = getNormalizedPointer(character)
  const x = direction.x * character.maxDistance + (eyeIndex === 0 ? -0.25 : 0.25)
  const y = direction.y * character.maxDistance

  return {
    width: `${character.pupilSize}px`,
    height: `${character.pupilSize}px`,
    transform: `translate(${x}px, ${y}px)`
  }
}

const getMouthStyle = (character) => {
  const direction = getNormalizedPointer(character)
  return {
    left: `${character.mouthLeft}px`,
    top: `${character.mouthTop}px`,
    transform: `translate(${direction.x * 7}px, ${direction.y * 6}px)`
  }
}

const getCapStyle = (character) => ({
  width: `${character.capStyle.width}px`,
  height: `${character.capStyle.height}px`,
  top: `${character.capStyle.top}px`,
  left: `${character.capStyle.left}px`,
  borderRadius: character.capStyle.radius
})

const updatePointer = (event) => {
  if (!stageRef.value) {
    return
  }

  const rect = stageRef.value.getBoundingClientRect()
  const centerX = rect.left + rect.width / 2
  const centerY = rect.top + rect.height / 2 + 12
  const dx = event.clientX - centerX
  const dy = event.clientY - centerY
  const distance = Math.max(Math.hypot(dx, dy), 1)

  pointer.value = {
    x: Math.max(-1, Math.min(1, dx / distance)),
    y: Math.max(-1, Math.min(1, dy / distance))
  }
}

const handlePointerMove = (event) => {
  if (!props.isPasswordGuardMode) {
    updatePointer(event)
  }
}

const resetPointer = () => {
  pointer.value = { x: 0, y: 0 }
}

const queueBlink = (id) => {
  clearTimeout(timers[id])
  timers[id] = setTimeout(() => {
    blinkState[id] = true
    setTimeout(() => {
      blinkState[id] = false
      queueBlink(id)
    }, 145)
  }, 1600 + Math.random() * 2200)
}

watch(
  () => props.isPasswordGuardMode,
  (guarding) => {
    if (guarding) {
      resetPointer()
    }
  },
  { immediate: true }
)

queueBlink('purple')
queueBlink('black')

onBeforeUnmount(() => {
  clearTimeout(timers.purple)
  clearTimeout(timers.black)
})
</script>

<style scoped>
.characters-stage {
  position: relative;
  width: 560px;
  height: 430px;
  margin-top: 18px;
  align-self: center;
}

.stage-glow {
  position: absolute;
  inset: 56px 24px 46px;
  border-radius: 36px;
  background: radial-gradient(circle at center, rgba(255, 255, 255, 0.88), rgba(255, 255, 255, 0));
  pointer-events: none;
}

.stage-floor {
  position: absolute;
  left: 22px;
  right: 22px;
  bottom: 0;
  height: 28px;
  border-radius: 999px;
  background: rgba(119, 132, 152, 0.12);
  filter: blur(1px);
}

.character {
  position: absolute;
  bottom: 16px;
  overflow: hidden;
  transition: transform 240ms ease, height 240ms ease, left 240ms ease;
  box-shadow:
    inset -18px 0 0 rgba(255, 255, 255, 0.1),
    inset 0 -14px 0 rgba(0, 0, 0, 0.08);
}

.highlight {
  position: absolute;
  top: 18px;
  left: 22px;
  width: 22%;
  height: 58%;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.14);
  filter: blur(0.2px);
}

.cap {
  position: absolute;
  background: rgba(255, 255, 255, 0.1);
}

.face {
  position: absolute;
  display: flex;
  align-items: center;
  transition: transform 220ms ease, left 220ms ease, top 220ms ease;
}

.eye {
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 999px;
  background: #fff;
  box-shadow: inset 0 -1px 0 rgba(0, 0, 0, 0.08);
  transition: height 120ms ease, transform 120ms ease;
}

.eye.blinking {
  height: 3px !important;
}

.pupil {
  border-radius: 999px;
  background: #26272d;
  transition: transform 180ms ease;
}

.mouth {
  position: absolute;
  transition: transform 180ms ease;
}

.mouth-orange {
  width: 28px;
  height: 12px;
  border-bottom: 4px solid #2a2b2f;
  border-radius: 0 0 20px 20px;
}

.mouth-yellow {
  width: 54px;
  height: 4px;
  border-radius: 999px;
  background: #2a2b2f;
}

.character-orange.guard-mode,
.character-yellow.guard-mode {
  transform: skewX(0deg) !important;
}

.character-purple.is-looking {
  height: 440px !important;
}

.character-black.is-looking {
  left: 246px !important;
}

.character-purple.guard-mode,
.character-black.guard-mode {
  transform: skewX(-3deg) !important;
}

@media (max-width: 720px) {
  .characters-stage {
    width: 100%;
    height: 300px;
    transform: scale(0.76);
    transform-origin: center bottom;
  }
}
</style>
