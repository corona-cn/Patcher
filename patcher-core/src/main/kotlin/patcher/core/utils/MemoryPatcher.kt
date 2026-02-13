@file:Suppress("Unused", "Nothing_to_inline")
package patcher.core.utils
import java.lang.foreign.*
import java.lang.invoke.*

/**
 * Memory patching utility for Windows processes.
 * Provides read/write operations to process memory space via Windows API.
 *
 * @param processHandle Handle to the target process opened with appropriate access rights
 */
@JvmInline
value class MemoryPatcher(private val processHandle: MemorySegment) {
    /* === COMPANION === */
    companion object {
        /* === INTERNAL PROCESS MEMORY HANDLES === */
        private val readProcessMemoryMH: MethodHandle = NativeBinder.bindKernel32("ReadProcessMemory") {
            params(address, address, address, long, address)
            returns(int)
        }

        private val writeProcessMemoryMH: MethodHandle = NativeBinder.bindKernel32("WriteProcessMemory") {
            params(address, address, address, long, address)
            returns(int)
        }


        /* === INTERNAL CONSTANTS === */
        private const val PAGE_READWRITE = 0x04

        private const val PAGE_EXECUTE_READWRITE = 0x40

        private const val MEM_COMMIT = 0x00001000

        private const val MEM_RESERVE = 0x00002000


        /* === PUBLIC CONSTRUCTOR FUNCTIONS === */
        /**
         * Creates MemoryPatcher instance from validated process handle
         *
         * @param handle Valid process handle with VM operation access
         *
         * @return MemoryPatcher instance bound to the process handle
         *
         * @throws IllegalStateException if handle is NULL
         */
        fun fromHandle(handle: MemorySegment): MemoryPatcher {
            if (handle == MemorySegment.NULL) {
                throw IllegalStateException("Invalid process handle")
            }

            return MemoryPatcher(handle)
        }
    }


    /* === PUBLIC MEMORY OPERATIONS === */
    /**
     * Reads byte array from target process memory
     *
     * @param address Virtual memory address in target process to read from
     * @param size Number of bytes to read
     *
     * @return Byte array containing read data, empty array on failure or size < 1
     */
    fun read(address: Long, size: Int): ByteArray {
        if (size < 1) {
            return byteArrayOf()
        }

        val buffer = NativeBinder.sharedArena.allocate(size.toLong())
        val bytesRead = NativeBinder.sharedArena.allocate(ValueLayout.JAVA_INT)
        val result = readProcessMemoryMH.invokeExact(
            processHandle,
            MemorySegment.ofAddress(address),
            buffer,
            size.toLong(),
            bytesRead
        ) as Int

        return if (result != 0) {
            val actualRead = bytesRead.get(ValueLayout.JAVA_INT, 0L)
            buffer.asSlice(0L, actualRead.toLong()).toArray(ValueLayout.JAVA_BYTE)
        } else {
            byteArrayOf()
        }
    }

    /**
     * Writes byte array to target process memory
     *
     * @param address Virtual memory address in target process to write to
     * @param data Byte array containing data to write
     *
     * @return true if write operation succeeded, false otherwise
     */
    fun write(address: Long, data: ByteArray): Boolean {
        if (data.isEmpty()) {
            return true
        }

        val buffer = NativeBinder.sharedArena.allocate(data.size.toLong())
        buffer.copyFrom(MemorySegment.ofArray(data))

        val bytesWritten = NativeBinder.sharedArena.allocate(ValueLayout.JAVA_INT)
        val result = writeProcessMemoryMH.invokeExact(
            processHandle,
            MemorySegment.ofAddress(address),
            buffer,
            data.size.toLong(),
            bytesWritten
        ) as Int

        return result != 0
    }

    /**
     * Reads memory region and formats as hexadecimal string
     *
     * @param address Virtual memory address in target process to read from
     * @param size Number of bytes to read
     *
     * @return Space-separated hexadecimal representation (e.g. "4D 5A 90 00"), empty string on failure
     */
    fun readAsHex(address: Long, size: Int): String {
        val bytes = read(address, size)
        return bytes.joinToString(" ") { "%02X".format(it) }
    }

    /**
     * Writes hexadecimal string to target process memory
     *
     * @param address Virtual memory address in target process to write to
     * @param hexString Hexadecimal string, may contain spaces (e.g. "4D5A90" or "4D 5A 90")
     *
     * @return true if write operation succeeded, false on invalid hex string or write failure
     */
    fun writeHex(address: Long, hexString: String): Boolean {
        val cleanHex = hexString.replace(" ", "")
        if (cleanHex.isEmpty() || cleanHex.length % 2 != 0) return false

        val bytes = cleanHex.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()

        return write(address, bytes)
    }

    /**
     * Closes the process handle associated with this MemoryPatcher
     *
     * This should be called when patching operations are complete
     * to avoid handle leaks. After closing, the MemoryPatcher instance
     * should be discarded.
     */
    fun close() {
        Process.closeHandle(processHandle)
    }
}