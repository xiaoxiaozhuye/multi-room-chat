<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api/admin'
import type { AdminRoom } from '@/types/api'

type RoomSetting = AdminRoom & { moderationEnabled: boolean; loading?: boolean }
const rows = ref<RoomSetting[]>([])
const loading = ref(false)

async function load(): Promise<void> {
  loading.value = true
  try {
    const rooms = (await adminApi.rooms({ page: 1, size: 100 })).items
    rows.value = rooms.map((room) => ({ ...room, moderationEnabled: true, loading: true }))
    await Promise.all(rows.value.map(async (row) => {
      row.moderationEnabled = (await adminApi.roomModerationSettings(row.id)).enabled
      row.loading = false
    }))
  } finally {
    loading.value = false
  }
}

async function update(row: RoomSetting, value: boolean): Promise<void> {
  if (!value) {
    try {
      await ElMessageBox.confirm(`关闭后，${row.name} 的新普通消息将跳过审核并直接进入发布流程。确定关闭吗？`, '关闭聊天室审核', { type: 'warning' })
    } catch {
      row.moderationEnabled = true
      return
    }
  }
  row.loading = true
  try {
    row.moderationEnabled = (await adminApi.updateRoomModerationSettings(row.id, value)).enabled
    ElMessage.success(`${row.name} 的内容审核已${row.moderationEnabled ? '开启' : '关闭'}`)
  } finally {
    row.loading = false
  }
}

onMounted(load)
</script>

<template>
  <section class="admin-page">
    <div class="page-heading"><div><h2>聊天室审核设置</h2><p>逐个聊天室控制新提交普通消息是否进入内容审核；未单独设置的聊天室使用系统默认值。</p></div><el-button :loading="loading" @click="load">刷新</el-button></div>
    <el-card shadow="never"><el-table :data="rows" v-loading="loading">
      <el-table-column prop="name" label="聊天室" min-width="220" />
      <el-table-column prop="status" label="状态" width="120" />
      <el-table-column label="内容审核" width="220"><template #default="{ row }"><el-switch :model-value="row.moderationEnabled" :loading="row.loading" active-text="已开启" inactive-text="已关闭" @update:model-value="update(row, $event)" /></template></el-table-column>
    </el-table><el-empty v-if="!loading && rows.length === 0" description="暂无可管理的聊天室" /></el-card>
  </section>
</template>

<style scoped>
.page-heading { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px }
.page-heading h2 { margin: 0 0 6px }
.page-heading p { margin: 0; color: var(--el-text-color-secondary) }
</style>
