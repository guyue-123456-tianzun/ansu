import { defineStore } from 'pinia'
import http from '../api/http'

export interface DemoUser {
  id: number
  role: string
  display_name: string
  dorm_id: number | null
}

interface AuthState {
  token: string
  user: DemoUser | null
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: localStorage.getItem('ansu_token') || '',
    user: JSON.parse(localStorage.getItem('ansu_user') || 'null') as DemoUser | null,
  }),
  actions: {
    async loginDemo(role: string, dormId?: number) {
      const response: any = await http.post('/auth/demo', { role, dormId })
      const data = response.data
      this.token = data.token
      this.user = data.user
      localStorage.setItem('ansu_token', data.token)
      localStorage.setItem('ansu_user', JSON.stringify(data.user))
      return data.user as DemoUser
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('ansu_token')
      localStorage.removeItem('ansu_user')
    },
  },
})
