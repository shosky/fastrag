import { ref, computed } from 'vue'

export function usePagination(defaultPageSize = 10) {
  const currentPage = ref(1)
  const pageSize = ref(defaultPageSize)
  const total = ref(0)

  const totalPages = computed(() => Math.ceil(total.value / pageSize.value))

  function handleCurrentChange(page: number) {
    currentPage.value = page
  }

  function handleSizeChange(size: number) {
    pageSize.value = size
    currentPage.value = 1
  }

  function reset() {
    currentPage.value = 1
    total.value = 0
  }

  return {
    currentPage,
    pageSize,
    total,
    totalPages,
    handleCurrentChange,
    handleSizeChange,
    reset,
  }
}
