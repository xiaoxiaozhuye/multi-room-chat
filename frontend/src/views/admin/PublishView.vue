<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api/admin'
import type { AdminRoom, DeliveryResult } from '@/types/api'

const rooms = ref<AdminRoom[]>([]); const targetIds = ref<string[]>([]); const content = ref(''); const tab = ref<'broadcast' | 'emergency'>('broadcast'); const sending = ref(false); const results = ref<DeliveryResult[]>([])
const available = computed(() => rooms.value.filter((room) => room.status !== 'DELETED'))
const selectable = computed(() => available.value.map((room) => room.id)); const roomName = (id: string) => rooms.value.find((room) => room.id === id)?.name ?? id
const statusText = (row: DeliveryResult) => row.deliveryStatus === 'PENDING_COMPENSATION' ? '已保存，实时投递待补偿' : row.deliveryStatus
async function send(): Promise<void> {
  const emergency = tab.value === 'emergency'; if (!targetIds.value.length || !content.value.trim()) return
  if (emergency) await ElMessageBox.confirm(`将立即向 ${targetIds.value.length} 个房间置顶推送，是否继续？`, '发送紧急通知', { type: 'warning', confirmButtonText: '发送紧急通知' })
  sending.value = true; try { const response = await adminApi.publish(emergency, targetIds.value, content.value.trim()); results.value = response.results; ElMessage.success('请求已处理，请查看逐房间结果') } finally { sending.value = false }
}
onMounted(async () => { rooms.value = (await adminApi.rooms({ page: 1, size: 100 })).items })
</script>
<template><section class="admin-page"><div class="page-heading"><div><h2>广播与紧急通知</h2><p>可选择一个或多个已授权房间。每个房间的执行状态会在下方单独展示。</p></div></div>
<el-card shadow="never"><el-tabs v-model="tab"><el-tab-pane label="管理员广播" name="broadcast"><el-alert title="广播不需要审核，但仍会按房间普通消息顺序发布，可能暂时等待前序消息。" type="info" :closable="false" /></el-tab-pane><el-tab-pane label="紧急通知" name="emergency"><el-alert title="将立即置顶推送给所选房间成员，不受审核队列影响。" type="error" :closable="false" /></el-tab-pane></el-tabs>
<el-form label-position="top" class="publish-form"><el-form-item label="目标房间"><el-select v-model="targetIds" multiple filterable collapse-tags placeholder="选择房间"><el-option v-for="room in available" :key="room.id" :label="`${room.name} · ${room.status} · 上限 ${room.maxMembers}`" :value="room.id" /></el-select><el-button link type="primary" @click="targetIds = selectable">全选我有权限的房间</el-button></el-form-item><el-form-item label="内容"><el-input v-model="content" type="textarea" :rows="5" maxlength="320" show-word-limit placeholder="请输入 1–320 个字符" /></el-form-item><el-button :type="tab === 'emergency' ? 'danger' : 'primary'" :loading="sending" :disabled="!targetIds.length || !content.trim()" @click="send">{{ tab === 'emergency' ? '发送紧急通知' : '发送广播' }}</el-button></el-form></el-card>
<el-card v-if="results.length" shadow="never" class="result-card"><template #header>逐房间执行结果</template><el-table :data="results"><el-table-column label="房间" min-width="200"><template #default="{row}">{{roomName(row.roomId)}}</template></el-table-column><el-table-column label="发送结果" min-width="180"><template #default="{row}"><el-tag :type="row.deliveryStatus === 'SUCCESS' ? 'success' : row.deliveryStatus === 'PENDING_COMPENSATION' ? 'warning' : 'danger'">{{statusText(row)}}</el-tag></template></el-table-column><el-table-column label="消息 / 通知序列" min-width="160"><template #default="{row}">{{row.roomSeq ?? row.notificationSeq ?? '—'}}</template></el-table-column><el-table-column prop="messageStatus" label="后续状态" width="150"/><el-table-column prop="errorCode" label="失败原因" min-width="180"/></el-table></el-card>
</section></template><style scoped>.page-heading{margin-bottom:18px}.page-heading h2{margin:0 0 6px}.page-heading p{margin:0;color:var(--el-text-color-secondary)}.publish-form{max-width:760px;margin-top:20px}.publish-form :deep(.el-select){width:100%}.result-card{margin-top:16px}</style>
