<script setup lang="ts">
import { useRoute } from 'vue-router'

const route = useRoute()
</script>

<template>
  <el-card shadow="never">
    <template #header>{{ route.meta.title }}</template>
    <el-empty description="业务页面将在后续任务中接入。此页面已继承统一认证、权限路由和实时连接能力。" />
  </el-card>
</template>
