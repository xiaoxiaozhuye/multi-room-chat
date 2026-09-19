<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const isAdmin = computed(() => auth.hasAnyRole(['ROOM_ADMIN', 'SYSTEM_ADMIN']))
async function signOut(): Promise<void> {
  try {
    await ElMessageBox.confirm('确定要退出当前账号吗？', '退出登录', {
      confirmButtonText: '退出登录',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  await auth.signOut()
  await router.replace({ name: 'login' })
}
</script>

<template>
  <section class="my-page">
    <div class="my-entry-list">
      <el-card shadow="never" class="my-entry-card" @click="$router.push({ name: 'profile' })">
        <div><strong>个人资料</strong><p>编辑头像、昵称和个人介绍</p></div>
        <span aria-hidden="true">›</span>
      </el-card>
      <el-card v-if="isAdmin" shadow="never" class="my-entry-card" @click="$router.push({ name: 'admin-workbench' })">
        <div><strong>管理工作台</strong><p>处理聊天室运营、入群审批和内容审核</p></div>
        <span aria-hidden="true">›</span>
      </el-card>
      <el-card shadow="never" class="my-entry-card" @click="signOut">
        <div><strong>退出登录</strong><p>退出当前账号</p></div>
        <span aria-hidden="true">›</span>
      </el-card>
    </div>
  </section>
</template>

<style scoped>
.my-page { display: grid; gap: 20px; max-width: 760px; margin: 0 auto; }
.my-entry-list { display: grid; gap: 12px; }
.my-entry-card { cursor: pointer; }
.my-entry-card :deep(.el-card__body) { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 20px; }
.my-entry-card strong { font-size: 16px; }
.my-entry-card p { margin: 6px 0 0; color: #667085; font-size: 13px; }
.my-entry-card > span { color: #98a2b3; font-size: 26px; }
</style>
