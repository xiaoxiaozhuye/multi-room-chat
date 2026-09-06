import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import { createPinia } from 'pinia'
import App from '@/App.vue'
import router from '@/router'
import { setUnauthorizedHandler } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { chatWebSocket } from '@/websocket/client'

import '@/styles/main.css'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(router)

setUnauthorizedHandler(() => {
  const auth = useAuthStore(pinia)
  auth.clearLocalSession()
  void router.replace({ name: 'login' })
})

const auth = useAuthStore(pinia)
if (auth.accessToken) chatWebSocket.connect(auth.accessToken)
chatWebSocket.on((event) => {
  if (event.type === 'AUTH_EXPIRED') {
    auth.clearLocalSession()
    void router.replace({ name: 'login' })
  }
})

app.mount('#app')
