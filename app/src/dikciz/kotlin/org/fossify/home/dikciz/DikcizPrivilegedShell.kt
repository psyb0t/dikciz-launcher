package org.fossify.home.dikciz

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import org.json.JSONObject

internal data class DikcizShellResult(
    val exitCode: Int,
    val output: String,
    val isOutputTruncated: Boolean,
)

internal class DikcizPrivilegedShell {
    fun isRootAvailable(): Boolean {
        val probe = try {
            run(ROOT_SHELL_PROBE_COMMAND, useRoot = true, timeoutMilliseconds = ROOT_PROBE_TIMEOUT_MILLISECONDS)
        } catch (_: DikcizAutomationRequestException) {
            return false
        } catch (_: IOException) {
            return false
        } catch (_: SecurityException) {
            return false
        }
        return probe.exitCode == SHELL_EXIT_CODE_SUCCESS && probe.output.trim() == ROOT_SHELL_USER_ID
    }

    fun rootUnavailable(): DikcizAutomationRequestException {
        return DikcizAutomationRequestException(
            DikcizAutomationControlPlane.ERROR_ROOT_UNAVAILABLE,
            MESSAGE_ROOT_SHELL_UNAVAILABLE,
        )
    }

    fun run(
        command: String,
        useRoot: Boolean,
        timeoutMilliseconds: Long = DEFAULT_SHELL_TIMEOUT_MILLISECONDS,
        maxOutputBytes: Int = DEFAULT_SHELL_OUTPUT_BYTES,
    ): DikcizShellResult {
        val shellPath = if (useRoot) ROOT_SHELL_PATH else ANDROID_SHELL_PATH
        val process = ProcessBuilder(listOf(shellPath, SHELL_COMMAND_ARGUMENT, command))
            .redirectErrorStream(true)
            .start()
        val outputCollector = ShellOutputCollector(maxOutputBytes)
        val reader = Thread(
            { collectShellOutput(process.inputStream, outputCollector) },
            SHELL_OUTPUT_READER_THREAD_NAME,
        )
        reader.isDaemon = true
        reader.start()
        if (!process.waitFor(timeoutMilliseconds, TimeUnit.MILLISECONDS)) {
            terminateShellProcess(process)
            // Cleanup read failures must not replace the command timeout.
            reader.join(SHELL_OUTPUT_READER_JOIN_MILLISECONDS)
            if (reader.isAlive) reader.interrupt()
            throw DikcizAutomationRequestException(
                DikcizAutomationControlPlane.ERROR_TIMEOUT,
                MESSAGE_SHELL_TIMEOUT,
            )
        }
        awaitShellOutput(reader, outputCollector)
        return DikcizShellResult(
            exitCode = process.exitValue(),
            output = outputCollector.text(),
            isOutputTruncated = outputCollector.isTruncated,
        )
    }

    private fun collectShellOutput(input: InputStream, collector: ShellOutputCollector) {
        try {
            input.use { stream ->
                val buffer = ByteArray(SHELL_OUTPUT_READ_BUFFER_BYTES)
                while (true) {
                    val bytesRead = stream.read(buffer)
                    if (bytesRead < NO_BYTES_READ) {
                        return
                    }
                    collector.append(buffer, bytesRead)
                }
            }
        } catch (exception: Exception) {
            collector.failure = exception
        }
    }

    private fun terminateShellProcess(process: Process) {
        process.destroy()
        if (process.waitFor(SHELL_COMMAND_TERMINATION_WAIT_MILLISECONDS, TimeUnit.MILLISECONDS)) {
            return
        }
        process.destroyForcibly()
        process.waitFor(SHELL_COMMAND_TERMINATION_WAIT_MILLISECONDS, TimeUnit.MILLISECONDS)
    }

    private fun awaitShellOutput(reader: Thread, collector: ShellOutputCollector) {
        reader.join(SHELL_OUTPUT_READER_JOIN_MILLISECONDS)
        if (reader.isAlive) {
            reader.interrupt()
            throw shellFailure(MESSAGE_SHELL_OUTPUT_TIMEOUT)
        }
        collector.failure?.let { exception ->
            throw shellFailure(exception.message ?: MESSAGE_SHELL_OUTPUT_FAILED)
        }
    }

    private fun shellFailure(message: String): DikcizAutomationRequestException {
        return DikcizAutomationRequestException(
            DikcizAutomationControlPlane.ERROR_VALIDATION_FAILED,
            message,
        )
    }

    private class ShellOutputCollector(private val maxOutputBytes: Int) {
        private val output = ByteArrayOutputStream()

        @Volatile
        var failure: Exception? = null

        @Volatile
        var isTruncated = false

        fun append(buffer: ByteArray, bytesRead: Int) {
            val remainingBytes = maxOutputBytes - output.size()
            val bytesToWrite = minOf(remainingBytes.coerceAtLeast(NO_BYTES_READ), bytesRead)
            if (bytesToWrite > NO_BYTES_READ) {
                output.write(buffer, NO_BYTES_READ, bytesToWrite)
            }
            if (bytesToWrite < bytesRead) {
                isTruncated = true
            }
        }

        fun text(): String = String(output.toByteArray(), StandardCharsets.UTF_8)
    }

    companion object {
        const val ANDROID_SHELL_PATH = "/system/bin/sh"
        const val DEFAULT_SHELL_OUTPUT_BYTES = 64 * 1024
        const val MAXIMUM_SHELL_OUTPUT_BYTES = 1024 * 1024
        const val DEFAULT_SHELL_TIMEOUT_MILLISECONDS = 30_000L
        const val MAXIMUM_SHELL_TIMEOUT_MILLISECONDS = Int.MAX_VALUE
        const val KEY_MAX_OUTPUT_BYTES = "maxOutputBytes"
        private const val MINIMUM_OPTION_VALUE = 1
        private const val MESSAGE_INVALID_OPTIONS = "shell timeout and output size must be positive integers within the advertised limits"
        private const val ROOT_PROBE_TIMEOUT_MILLISECONDS = 5_000L
        const val MESSAGE_ROOT_SHELL_UNAVAILABLE = "root shell is unavailable"
        const val MESSAGE_SHELL_OUTPUT_FAILED = "launcher shell output could not be read"
        const val MESSAGE_SHELL_OUTPUT_TIMEOUT = "launcher shell output did not finish in time"
        const val MESSAGE_SHELL_TIMEOUT = "launcher shell command timed out"
        const val NO_BYTES_READ = 0
        const val ROOT_SHELL_PATH = "su"
        const val ROOT_SHELL_PROBE_COMMAND = "id -u"
        const val ROOT_SHELL_USER_ID = "0"
        const val SHELL_COMMAND_ARGUMENT = "-c"
        const val SHELL_COMMAND_TERMINATION_WAIT_MILLISECONDS = 500L
        const val SHELL_EXIT_CODE_SUCCESS = 0
        const val SHELL_OUTPUT_READER_JOIN_MILLISECONDS = 1_000L
        const val SHELL_OUTPUT_READER_THREAD_NAME = "dikciz-shell-output"
        const val SHELL_OUTPUT_READ_BUFFER_BYTES = 4_096
        const val VALUE_APP_SANDBOX = "app_sandbox"
        const val VALUE_ROOT = "root"

        fun validateOptions(request: JSONObject) {
            val limits = mapOf(
                DikcizAutomationControlPlane.KEY_TIMEOUT_MILLISECONDS to MAXIMUM_SHELL_TIMEOUT_MILLISECONDS,
                KEY_MAX_OUTPUT_BYTES to MAXIMUM_SHELL_OUTPUT_BYTES,
            )
            for ((key, maximum) in limits) {
                if (!request.has(key)) continue
                val value = request.opt(key)
                if ((value !is Int && value !is Long) ||
                    (value as Number).toLong() !in MINIMUM_OPTION_VALUE.toLong()..maximum.toLong()
                ) {
                    throw DikcizAutomationRequestException(
                        DikcizAutomationControlPlane.ERROR_VALIDATION_FAILED,
                        MESSAGE_INVALID_OPTIONS,
                    )
                }
            }
        }
    }
}
