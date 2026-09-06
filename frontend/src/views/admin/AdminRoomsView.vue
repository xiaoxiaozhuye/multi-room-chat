<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api/admin'
import { useAuthStore } from '@/stores/auth'
import type { AdminRoom, RoomAuthorization } from '@/types/api'

const auth = useAuthStore()
const systemAdmin = computed(() => auth.user?.roles.includes('SYSTEM_ADMIN') ?? false)
const rooms = ref<AdminRoom[]>([])
const loading = ref(false)
const drawer = ref(false)
const editing = ref<AdminRoom | null>(null)
const dialog = ref(false)
const authorizations = ref<RoomAuthorization[]>([])
const grantUserId = ref('')
const filters = reactive({ name: '', status: '', joinMode: '' })
const form = reactive({ name: '', description: '', maxMembers: 100, joinMode: 'OPEN' as 'OPEN' | 'APPROVAL' })

const tagType = (status: string) => ({ ACTIVE: 'success', PAUSED: 'warning', CLOSED: 'info', DELETED: 'danger' }[status] ?? 'info') as 'success' | 'warning' | 'info' | 'danger'
const time = (value?: string) => value ? new Date(value).toLocaleString() : '—'

async function load(): Promise<void> {
  loading.value = true
  try { rooms.value = (await adminApi.rooms({ ...filters, page: 1, size: 100 })).items } finally { loading.value = false }
}
function newRoom(): void { Object.assign(form, { name: '', description: '', maxMembers: 100, joinMode: 'OPEN' }); dialog.value = true }
async function create(): Promise<void> { await adminApi.createRoom(form); ElMessage.success('聊天室已创建'); dialog.value = false; await load() }
async function open(room: AdminRoom): Promise<void> {
  editing.value = { ...room }; drawer.value = true
  if (systemAdmin.value) authorizations.value = await adminApi.authorizations(room.id)
}
async function save(): Promise<void> {
  if (!editing.value) return
  const { id, name, description, maxMembers, joinMode } = editing.value
  editing.value = await adminApi.updateRoom(id, { name, description, maxMembers, joinMode })
  ElMessage.success('聊天室信息已保存'); await load()
}
async function transition(status: 'ACTIVE' | 'PAUSED' | 'CLOSED'): Promise<void> {
  if (!editing.value) return
  const updated = await adminApi.updateRoom(editing.value.id, { status })
  editing.value = updated
  const index = rooms.value.findIndex((room) => room.id === updated.id)
  if (index >= 0) rooms.value[index] = updated
  await load()
  ElMessage.success(status === 'ACTIVE' ? '聊天室已恢复' : status === 'PAUSED' ? '聊天室已暂停' : '聊天室已关闭')
}
async function togglePause(): Promise<void> {
  if (!editing.value || editing.value.status === 'CLOSED') return
  await transition(editing.value.status === 'PAUSED' ? 'ACTIVE' : 'PAUSED')
}
async function confirmClose(): Promise<void> {
  if (!editing.value || editing.value.status === 'CLOSED') return
  await ElMessageBox.confirm(
    '关闭后成员不能加入或发送普通消息，且无法重新开启。是否继续？',
    '确认关闭聊天室',
    { type: 'warning', confirmButtonText: '确认关闭', cancelButtonText: '取消' },
  )
  await transition('CLOSED')
}
async function remove(): Promise<void> {
  if (!editing.value) return
  const name = editing.value.name
  const { value } = await ElMessageBox.prompt('所有未发布普通消息将被取消；此操作不能恢复。请输入完整聊天室名称确认。', '删除聊天室', { inputPattern: new RegExp(`^${name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`), inputErrorMessage: '请输入完整聊天室名称', type: 'error' })
  if (value !== name) return
  await adminApi.deleteRoom(editing.value.id); drawer.value = false; ElMessage.success('聊天室已逻辑删除'); await load()
}
async function grant(): Promise<void> {
  if (!editing.value || !grantUserId.value.trim()) return
  await adminApi.grant(editing.value.id, grantUserId.value.trim()); grantUserId.value = ''
  authorizations.value = await adminApi.authorizations(editing.value.id); ElMessage.success('授权已生效')
}
async function revoke(item: RoomAuthorization): Promise<void> {
  if (!editing.value) return
  await ElMessageBox.confirm('撤销后该管理员将立即失去此聊天室管理与读取权限。', '确认撤销', { type: 'warning' })
  await adminApi.revoke(editing.value.id, item.adminUserId); authorizations.value = await adminApi.authorizations(editing.value.id)
}
onMounted(load)
</script>

<template>
  <section class="admin-page">
    <div class="page-heading"><div><h2>聊天室管理</h2><p>仅展示当前账号有权运营的聊天室；权限以服务端校验为准。</p></div><el-button v-if="systemAdmin" type="primary" @click="newRoom">新建聊天室</el-button></div>
    <el-card shadow="never"><el-form :inline="true" class="filter-bar">
      <el-form-item label="名称"><el-input v-model="filters.name" clearable placeholder="名称前缀" @keyup.enter="load" /></el-form-item>
      <el-form-item label="状态"><el-select v-model="filters.status" clearable placeholder="全部"><el-option v-for="item in ['ACTIVE','PAUSED','CLOSED']" :key="item" :label="item" :value="item" /></el-select></el-form-item>
      <el-form-item label="加入方式"><el-select v-model="filters.joinMode" clearable placeholder="全部"><el-option label="开放加入" value="OPEN" /><el-option label="审批加入" value="APPROVAL" /></el-select></el-form-item>
      <el-button @click="load">查询</el-button>
    </el-form></el-card>
    <el-card shadow="never" class="table-card"><el-table :data="rooms" v-loading="loading" @row-click="open">
      <el-table-column prop="name" label="聊天室名称" min-width="180" /><el-table-column label="状态" width="105"><template #default="{ row }"><el-tag :type="tagType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
      <el-table-column label="加入方式" width="120"><template #default="{ row }">{{ row.joinMode === 'OPEN' ? '开放加入' : '审批加入' }}</template></el-table-column><el-table-column label="成员上限" width="100"><template #default="{ row }">{{ row.maxMembers }}</template></el-table-column>
      <el-table-column label="最后更新" min-width="170"><template #default="{ row }">{{ time(row.updatedAt) }}</template></el-table-column><el-table-column label="操作" width="105"><template #default="{ row }"><el-button link type="primary" @click.stop="open(row)">查看管理</el-button></template></el-table-column>
    </el-table></el-card>

    <el-dialog v-model="dialog" title="新建聊天室" width="500px"><el-form label-width="90px"><el-form-item label="名称" required><el-input v-model="form.name" maxlength="120" /></el-form-item><el-form-item label="描述"><el-input v-model="form.description" type="textarea" /></el-form-item><el-form-item label="最大人数"><el-input-number v-model="form.maxMembers" :min="1" /></el-form-item><el-form-item label="加入方式"><el-radio-group v-model="form.joinMode"><el-radio value="OPEN">开放加入</el-radio><el-radio value="APPROVAL">审批加入</el-radio></el-radio-group></el-form-item></el-form><template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" :disabled="!form.name.trim()" @click="create">创建</el-button></template></el-dialog>
    <el-drawer v-model="drawer" size="600px" :title="editing?.name ?? '聊天室详情'"><template v-if="editing"><el-tabs><el-tab-pane label="基本信息"><el-form label-width="90px"><el-form-item label="名称"><el-input v-model="editing.name" /></el-form-item><el-form-item label="描述"><el-input v-model="editing.description" type="textarea" /></el-form-item><el-form-item label="最大人数"><el-input-number v-model="editing.maxMembers" :min="1" /></el-form-item><el-form-item label="加入方式"><el-select v-model="editing.joinMode"><el-option label="开放加入" value="OPEN" /><el-option label="审批加入" value="APPROVAL" /></el-select></el-form-item><el-button type="primary" @click="save">保存修改</el-button></el-form><el-divider /><p v-if="editing.status === 'CLOSED'">已关闭的聊天室不能重新开启。</p><el-space><el-button v-if="editing.status !== 'CLOSED'" :type="editing.status === 'PAUSED' ? 'success' : undefined" @click="togglePause">{{ editing.status === 'PAUSED' ? '恢复' : '暂停' }}</el-button><el-button v-if="editing.status !== 'CLOSED'" type="warning" @click="confirmClose">关闭</el-button><el-button v-if="systemAdmin" type="danger" plain @click="remove">逻辑删除</el-button></el-space></el-tab-pane>
      <el-tab-pane v-if="systemAdmin" label="管理员授权"><el-alert title="只能授权已具有 ROOM_ADMIN 角色的用户。" type="info" :closable="false" /><div class="grant-line"><el-input v-model="grantUserId" placeholder="管理员用户 ID" /><el-button type="primary" @click="grant">添加授权</el-button></div><el-table :data="authorizations"><el-table-column prop="adminUserId" label="管理员 ID" min-width="250"/><el-table-column label="授权时间" min-width="160"><template #default="{ row }">{{ time(row.createdAt) }}</template></el-table-column><el-table-column width="70"><template #default="{ row }"><el-button link type="danger" @click="revoke(row)">撤销</el-button></template></el-table-column></el-table></el-tab-pane>
    </el-tabs></template></el-drawer>
  </section>
</template>

<style scoped>.page-heading{display:flex;justify-content:space-between;align-items:center;margin-bottom:18px}.page-heading h2{margin:0 0 6px}.page-heading p{margin:0;color:var(--el-text-color-secondary)}.filter-bar{margin-bottom:-18px}.table-card{margin-top:16px}.grant-line{display:flex;gap:8px;margin:16px 0}</style>
