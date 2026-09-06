<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api/admin'
import type { AdminRoom, RoomAuthorization } from '@/types/api'

const rooms = ref<AdminRoom[]>([]); const roomId = ref(''); const rows = ref<RoomAuthorization[]>([]); const userId = ref(''); const loading = ref(false)
const time = (value?: string) => value ? new Date(value).toLocaleString() : '—'
async function load(): Promise<void> { if (!roomId.value) return; loading.value = true; try { rows.value = await adminApi.authorizations(roomId.value) } finally { loading.value = false } }
async function grant(): Promise<void> { if (!roomId.value || !userId.value.trim()) return; await adminApi.grant(roomId.value, userId.value.trim()); userId.value = ''; await load(); ElMessage.success('授权已生效') }
async function revoke(row: RoomAuthorization): Promise<void> { await ElMessageBox.confirm('将立即失去此房间管理与读取权限。', '确认撤销', { type: 'warning' }); await adminApi.revoke(row.roomId, row.adminUserId); await load() }
onMounted(async () => { rooms.value = (await adminApi.rooms({ page: 1, size: 100 })).items })
</script>
<template><section class="admin-page"><div class="page-heading"><div><h2>管理员授权</h2><p>仅 SYSTEM_ADMIN 可管理。授权不会提升用户角色，只赋予单一房间的运营权限。</p></div></div><el-card shadow="never"><el-form :inline="true"><el-form-item label="聊天室"><el-select v-model="roomId" filterable placeholder="请选择房间" @change="load"><el-option v-for="room in rooms" :key="room.id" :label="room.name" :value="room.id"/></el-select></el-form-item><el-form-item label="管理员用户 ID"><el-input v-model="userId" placeholder="UUID"/></el-form-item><el-button type="primary" :disabled="!roomId || !userId.trim()" @click="grant">添加授权</el-button></el-form></el-card><el-card shadow="never" class="table-card"><el-empty v-if="!roomId" description="请选择一个房间"/><el-table v-else :data="rows" v-loading="loading"><el-table-column prop="adminUserId" label="管理员 ID" min-width="300"/><el-table-column prop="grantedByUserId" label="授权人 ID" min-width="280"/><el-table-column label="授权时间" min-width="180"><template #default="{row}">{{time(row.createdAt)}}</template></el-table-column><el-table-column width="80"><template #default="{row}"><el-button link type="danger" @click="revoke(row)">撤销</el-button></template></el-table-column></el-table></el-card></section></template><style scoped>.page-heading{margin-bottom:18px}.page-heading h2{margin:0 0 6px}.page-heading p{margin:0;color:var(--el-text-color-secondary)}.table-card{margin-top:16px}</style>
