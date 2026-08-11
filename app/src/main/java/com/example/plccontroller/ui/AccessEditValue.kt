package com.example.plccontroller.ui

/** Pure validation and normalization for editable access settings. */
internal object AccessEditValue {
    private val rawNetworkPath = Regex("^[0-9a-zA-Z.:/-]+$")

    fun isUrlTarget(title: String): Boolean = title == ORDER_API_TITLE || title == MANAGEMENT_BACKEND_TITLE

    fun validate(
        title: String,
        value: String,
        isUrl: Boolean,
    ): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return title + "不能为空"
        if (isUrl && !isSupportedUrl(trimmed)) return "地址格式不正确，应为合法的网络路径"
        return null
    }

    fun shouldShowSchemeHint(
        value: String,
        isUrl: Boolean,
        errorText: String?,
    ): Boolean = isUrl && errorText == null && !hasHttpScheme(value.trim())

    fun normalizeForSave(
        value: String,
        isUrl: Boolean,
    ): String {
        val trimmed = value.trim()
        return if (isUrl && !hasHttpScheme(trimmed)) "http://$trimmed" else trimmed
    }

    private fun isSupportedUrl(value: String): Boolean = hasHttpScheme(value) || rawNetworkPath.matches(value)

    private fun hasHttpScheme(value: String): Boolean = value.startsWith("http://") || value.startsWith("https://")

    private const val ORDER_API_TITLE = "业务接口"
    private const val MANAGEMENT_BACKEND_TITLE = "管理后台"
}
