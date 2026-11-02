package org.fossify.home.dikciz

internal enum class DikcizSimNumberStatus(val value: String) {
    Available("available"),
    Unavailable("unavailable"),
    PermissionRequired("permission_required"),
    AccessDenied("access_denied"),
    Unsupported("unsupported"),
}

internal data class DikcizSimPhoneNumber(
    val number: String?,
    val status: DikcizSimNumberStatus,
    val failure: RuntimeException? = null,
)

internal fun readSimPhoneNumber(permitted: Boolean, lookup: () -> String?): DikcizSimPhoneNumber {
    if (!permitted) return DikcizSimPhoneNumber(null, DikcizSimNumberStatus.PermissionRequired)
    return try {
        val number = lookup()?.trim().orEmpty()
        if (number.isEmpty()) {
            DikcizSimPhoneNumber(null, DikcizSimNumberStatus.Unavailable)
        } else {
            DikcizSimPhoneNumber(number, DikcizSimNumberStatus.Available)
        }
    } catch (exception: SecurityException) {
        DikcizSimPhoneNumber(null, DikcizSimNumberStatus.AccessDenied, exception)
    } catch (exception: IllegalStateException) {
        DikcizSimPhoneNumber(null, DikcizSimNumberStatus.Unavailable, exception)
    } catch (exception: UnsupportedOperationException) {
        DikcizSimPhoneNumber(null, DikcizSimNumberStatus.Unsupported, exception)
    }
}
