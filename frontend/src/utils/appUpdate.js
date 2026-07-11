export function shouldShowUpdate(result) {
  return result?.status === 'AVAILABLE'
}

export function getUpdateCheckFailurePresentation(trigger) {
  return trigger === 'startup' ? 'notification' : 'dialog'
}

export function formatBytes(bytes) {
  if (!Number.isFinite(bytes) || bytes < 0) return '-'
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export function formatSpeed(bytesPerSecond) {
  const formatted = formatBytes(bytesPerSecond)
  return formatted === '-' ? '-' : `${formatted}/秒`
}

export function formatEta(seconds) {
  if (!Number.isFinite(seconds) || seconds < 0) return ''
  if (seconds < 60) return `预计还需 ${Math.ceil(seconds)} 秒`
  return `预计还需 ${Math.ceil(seconds / 60)} 分钟`
}

export function describeUpdateError(error) {
  const technicalDetail = String(error?.message || error || '')
  const normalized = technicalDetail.toLowerCase()

  if (/signature|verify/.test(normalized)) {
    return {
      title: '安装包未通过安全校验',
      message: '系统不会安装这个更新包，请稍后重试或联系维护人员。',
      technicalDetail
    }
  }
  if (/install/.test(normalized)) {
    return {
      title: '更新安装失败',
      message: '现有版本仍可继续使用，请稍后重试。',
      technicalDetail
    }
  }
  if (/error sending request|timeout|dns|connect/.test(normalized)) {
    return {
      title: '暂时无法检查更新',
      message: '当前网络无法连接 GitHub 更新服务器，请检查网络后重试。',
      technicalDetail
    }
  }
  if (/download|stream|body|interrupt/.test(normalized)) {
    return {
      title: '更新包下载未完成',
      message: '网络连接可能中断，请重新下载。',
      technicalDetail
    }
  }
  if (/json|manifest|latest|index/.test(normalized)) {
    return {
      title: '更新信息暂时不可用',
      message: '请稍后在系统设置中再次检查。',
      technicalDetail
    }
  }
  return {
    title: '更新未能完成',
    message: '现有版本仍可继续使用，请稍后重试。',
    technicalDetail
  }
}
