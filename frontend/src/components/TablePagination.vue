<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  page: number
  pageSize: number
  itemCount: number
  hasNext?: boolean
}>(), { hasNext: false })

const emit = defineEmits<{ change: [page: number] }>()
const visible = computed(() => props.page > 1 || props.hasNext)
const total = computed(() => props.hasNext
  ? props.page * props.pageSize + 1
  : (props.page - 1) * props.pageSize + props.itemCount)
</script>

<template>
  <el-pagination
    v-if="visible"
    class="table-pagination"
    background
    layout="prev, pager, next"
    :current-page="page"
    :page-size="pageSize"
    :total="total"
    @current-change="emit('change', $event)"
  />
</template>

<style scoped>
.table-pagination { justify-content: flex-end; margin-top: 16px; }
</style>
