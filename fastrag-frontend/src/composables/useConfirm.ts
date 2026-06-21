import { ElMessageBox, ElMessage } from 'element-plus'

export function useConfirm() {
  async function confirm(
    message: string,
    title = '确认操作',
    type: 'warning' | 'info' | 'error' = 'warning'
  ): Promise<boolean> {
    try {
      await ElMessageBox.confirm(message, title, {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type,
      })
      return true
    } catch {
      return false
    }
  }

  function success(message: string) {
    ElMessage.success(message)
  }

  function error(message: string) {
    ElMessage.error(message)
  }

  function warning(message: string) {
    ElMessage.warning(message)
  }

  function info(message: string) {
    ElMessage.info(message)
  }

  return { confirm, success, error, warning, info }
}
