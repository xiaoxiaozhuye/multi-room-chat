<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { adminApi } from '@/api/admin'
import TablePagination from '@/components/TablePagination.vue'
import type { AuditItem } from '@/types/api'

const rows = ref<AuditItem[]>([])
const loading = ref(false)
const detail = ref<AuditItem | null>(null) as any
const filter = reactive({ actorId: '', roomId: '', messageId: '', action: '', from: '', to: '' })
const page = ref(1)
const pageSize = 20
const hasNext = ref(false)
const time = (value?: string) => value ? new Date(value).toLocaleString() : '—'

async function load(): Promise<void> {
  loading.value = true
  try {
    const response = await adminApi.audits({ ...filter, page: page.value, size: pageSize })
    rows.value = response.items
    hasNext.value = response.hasNext
  } finally { loading.value = false }
}
function search(): void { page.value = 1; void load() }
function changePage(nextPage: number): void { page.value = nextPage; void load() }
onMounted(load)
</script>

<template>
  <section class="admin-page">
    <div class="page-heading"><div><h2>审计日志</h2><p>审计记录只读，包含所有管理操作的请求追踪信息。</p></div></div>
    <el-card shadow="never"><el-form :inline="true" class="filter-bar">
      <el-form-item label="操作者"><el-input v-model="filter.actorId" placeholder="用户 ID"/></el-form-item>
      <el-form-item label="房间"><el-input v-model="filter.roomId" placeholder="房间 ID"/></el-form-item>
      <el-form-item label="消息"><el-input v-model="filter.messageId" placeholder="消息 ID"/></el-form-item>
      <el-form-item label="行为"><el-input v-model="filter.action" placeholder="例如 ROOM_UPDATE"/></el-form-item>
      <el-form-item label="时间"><el-date-picker v-model="filter.from" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss[Z]" placeholder="开始"/><span class="date-divider">至</span><el-date-picker v-model="filter.to" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss[Z]" placeholder="结束"/></el-form-item>
      <el-button @click="search">查询</el-button>
    </el-form></el-card>
    <el-card shadow="never" class="table-card">
      <el-table :data="rows" v-loading="loading">
        <el-table-column label="发生时间" width="180"><template #default="{row}">{{time(row.createdAt)}}</template></el-table-column>
        <el-table-column prop="actorId" label="操作者" min-width="230"/><el-table-column prop="action" label="行为" min-width="160"/>
        <el-table-column label="资源" min-width="230"><template #default="{row}">{{row.resourceType}} · {{row.resourceId}}</template></el-table-column>
        <el-table-column label="状态变化" min-width="140"><template #default="{row}">{{Object.keys(row.beforeState ?? {}).length ? '有记录' : '—'}} → {{Object.keys(row.afterState ?? {}).length ? '有记录' : '—'}}</template></el-table-column>
        <el-table-column prop="requestId" label="请求 ID" min-width="220"/><el-table-column width="70"><template #default="{row}"><el-button link @click="detail=row">详情</el-button></template></el-table-column>
      </el-table>
      <TablePagination :page="page" :page-size="pageSize" :item-count="rows.length" :has-next="hasNext" @change="changePage" />
    </el-card>
    <el-drawer v-model="detail" :title="detail ? '审计详情' : ''" size="520px"><pre v-if="detail">{{JSON.stringify(detail, null, 2)}}</pre></el-drawer>
  </section>
</template>

<style scoped>.page-heading{margin-bottom:18px}.page-heading h2{margin:0 0 6px}.page-heading p{margin:0;color:var(--el-text-color-secondary)}.filter-bar{margin-bottom:-18px}.table-card{margin-top:16px}.date-divider{margin:0 8px}pre{overflow:auto;white-space:pre-wrap;word-break:break-all}</style>
