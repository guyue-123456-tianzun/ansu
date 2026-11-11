import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', component: () => import('../views/LoginView.vue') },
    { path: '/admin/dashboard', component: () => import('../views/admin/DashboardView.vue') },
    { path: '/m/home', component: () => import('../views/student/HomeView.vue') },
  ],
})

router.beforeEach((to) => {
  if (to.path === '/login') return true
  const token = localStorage.getItem('ansu_token')
  if (!token) return '/login'
  return true
})

export default router
