<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api/admin'
import type { AdminRoom, JoinRequest } from '@/types/api'

const rows = ref<JoinRequest[]>([]); const rooms = ref<AdminRoom[]>([]); const loading = ref(false)
const filter = reactive({ roomId: '', memberStatus: 'PENDING', userId: '' })
const time = (value?: string) => value ? new Date(value).toLocaleString() : '—'
const roomName = (id: string) => rooms.value.find((room) => room.id === id)?.name ?? id
async function load(): Promise<void> { loading.value = true; try { rows.value = (await adminApi.joinRequests({ ...filter, page: 1, size: 100 })).items } finally { loading.value = false } }
async function loadRooms(): Promise<void> { rooms.value = (await adminApi.rooms({ roomStatus: '', page: 1, size: 100 })).items }
async function process(row: JoinRequest, action: 'approve' | 'reject'): Promise<void> {
  if (action === 'reject') await ElMessageBox.confirm('拒绝后申请人可重新申请，是否继续？', '拒绝入群申请', { type: 'warning' })
  try { await (action === 'approve' ? adminApi.approveJoin(row.id) : adminApi.rejectJoin(row.id)); rows.value = rows.value.filter((item) => item.id !== row.id); ElMessage.success(action === 'approve' ? '已批准入群' : '已拒绝申请') }
  catch (error) { await load(); throw error }
}
onMounted(async () => { await Promise.all([loadRooms(), load()]) })
</script>
<template><section class="admin-page"><div class="page-heading"><div><h2>入群审批</h2><p>默认按申请时间从早到晚处理；房间范围由服务端按管理员授权限制。</p></div></div>
  <el-card shadow="never"><el-form :inline="true" class="filter-bar"><el-form-item label="房间"><el-select v-model="filter.roomId" clearable filterable placeholder="全部授权房间"><el-option v-for="room in rooms" :key="room.id" :label="room.name" :value="room.id" /></el-select></el-form-item><el-form-item label="状态"><el-select v-model="filter.memberStatus"><el-option label="待审批" value="PENDING"/><el-option label="已批准" value="ACTIVE"/><el-option label="已拒绝" value="REJECTED"/></el-select></el-form-item><el-form-item label="申请人 ID"><el-input v-model="filter.userId" clearable /></el-form-item><el-button @click="load">查询</el-button></el-form></el-card>
  <el-card shadow="never" class="table-card"><el-table :data="rows" v-loading="loading"><el-table-column label="申请人" min-width="260"><template #default="{row}">{{ row.userId }}</template></el-table-column><el-table-column label="房间" min-width="160"><template #default="{row}">{{ roomName(row.roomId) }}</template></el-table-column><el-table-column label="申请时间" min-width="180"><template #default="{row}">{{ time(row.requestedAt) }}</template></el-table-column><el-table-column prop="status" label="状态" width="110"><template #default="{row}"><el-tag :type="row.status === 'PENDING' ? 'warning' : 'info'">{{ row.status }}</el-tag></template></el-table-column><el-table-column label="操作" width="150"><template #default="{row}"><el-space v-if="row.status === 'PENDING'"><el-button type="primary" link @click="process(row, 'approve')">批准</el-button><el-button type="danger" link @click="process(row, 'reject')">拒绝</el-button></el-space><span v-else>—</span></template></el-table-column></el-table></el-card>
</section></template><style scoped>.page-heading{margin-bottom:18px}.page-heading h2{margin:0 0 6px}.page-heading p{margin:0;color:var(--el-text-color-secondary)}.filter-bar{margin-bottom:-18px}.table-card{margin-top:16px}</style>
