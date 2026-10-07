package org.fossify.home.dikciz

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

internal data class DikcizShellResult(
    val exitCode: Int,
    val output: String,
    val isOutputTruncated: Boolean,
)

/**
 * The single bounded shell facility. Every privileged Dikciz command shares one UID-zero
 * probe, one timeout, and one output ceiling, so no caller can widen that boundary.
 */
internal class DikcizPrivilegedShell {
    fun isRootAvailable(): Boolean {
        val probe = try {
            run(ROOT_SHELL_PROBE_COMMAND, useRoot = true)
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

    fun run(command: String, useRoot: Boolean): DikcizShellResult {
        val shellPath = if (useRoot) ROOT_SHELL_PATH else ANDROID_SHELL_PATH
        val process = ProcessBuilder(listOf(shellPath, SHELL_COMMAND_ARGUMENT, command))
            .redirectErrorStream(true)
            .start()
        val outputCollector = ShellOutputCollector()
        val reader = Thread(
            { collectShellOutput(process.inputStream, outputCollector) },
            SHELL_OUTPUT_READER_THREAD_NAME,
        )
        reader.isDaemon = true
        reader.start()
        if (!process.waitFor(SHELL_COMMAND_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)) {
            terminateShellProcess(process)
            awaitShellOutput(reader, outputCollector)
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

    private class ShellOutputCollector {
        private val output = ByteArrayOutputStream()

        @Volatile
        var failure: Exception? = null

        @Volatile
        var isTruncated = false

        fun append(buffer: ByteArray, bytesRead: Int) {
            val remainingBytes = MAXIMUM_SHELL_OUTPUT_BYTES - output.size()
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
        const val MAXIMUM_SHELL_OUTPUT_BYTES = 64 * 1024
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
        const val SHELL_COMMAND_TIMEOUT_MILLISECONDS = 5_000L
        const val SHELL_EXIT_CODE_SUCCESS = 0
        const val SHELL_OUTPUT_READER_JOIN_MILLISECONDS = 1_000L
        const val SHELL_OUTPUT_READER_THREAD_NAME = "dikciz-shell-output"
        const val SHELL_OUTPUT_READ_BUFFER_BYTES = 4_096
        const val VALUE_APP_SANDBOX = "app_sandbox"
        const val VALUE_ROOT = "root"
    }
}
