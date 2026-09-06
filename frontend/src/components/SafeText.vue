<script setup lang="ts">
defineProps<{ content: string }>()
</script>

<template><p class="safe-text">{{ content }}</p></template>

<style scoped>
.safe-text { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; word-break: break-word; }
</style>
