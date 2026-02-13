@file:Suppress("Unused", "Nothing_to_inline")
package patcher.core.utils
import java.lang.foreign.*
import java.lang.invoke.*

object ProcessHandleUtils {
    /* === INTERNAL PROCESS HANDLES === */
    /* Process Snapshot */
    private val createToolhelp32SnapshotMH: MethodHandle = NativeBinder.bindKernel32("CreateToolhelp32Snapshot") {
        params(long, long)
        returns(address)
    }

    private val process32FirstWMH: MethodHandle = NativeBinder.bindKernel32("Process32FirstW") {
        params(address, address)
        returns(int)
    }

    private val process32NextWMH: MethodHandle = NativeBinder.bindKernel32("Process32NextW") {
        params(address, address)
        returns(int)
    }

    /* Handle Management */
    private val closeHandleMH: MethodHandle = NativeBinder.bindKernel32("CloseHandle") {
        params(address)
        returns(int)
    }

    private val openProcessMH: MethodHandle = NativeBinder.bindKernel32("OpenProcess") {
        params(int, int, long)
        returns(address)
    }


    /* === PUBLIC PROCESS FUNCTION WRAPPERS === */
    /**
     * Creates a snapshot of the specified processes, heaps, modules, and threads.
     *
     * @param flags The portions of the system to be included in the snapshot
     * @param processId The process identifier to be included in the snapshot
     * @return Handle to the snapshot on success, null on failure
     */
    fun createToolhelp32Snapshot(flags: Long, processId: Long): MemorySegment? {
        return try {
            (createToolhelp32SnapshotMH.invokeExact(flags, processId) as MemorySegment)
                .takeIf { it != MemorySegment.NULL }
        } catch (e: Throwable) {
            throw RuntimeException("Failed to create toolhelp32 snapshot", e)
        }
    }

    /**
     * Retrieves information about the first process encountered in a system snapshot.
     *
     * @param snapshotHandle Handle to the snapshot
     * @param processEntry Pointer to a PROCESSENTRY32 structure
     * @return Non-zero if successful, zero otherwise
     */
    fun process32FirstW(snapshotHandle: MemorySegment, processEntry: MemorySegment): Int {
        return try {
            process32FirstWMH.invokeExact(snapshotHandle, processEntry) as Int
        } catch (e: Throwable) {
            throw RuntimeException("Failed to get first process", e)
        }
    }

    /**
     * Retrieves information about the next process recorded in a system snapshot.
     *
     * @param snapshotHandle Handle to the snapshot
     * @param processEntry Pointer to a PROCESSENTRY32 structure
     * @return Non-zero if successful, zero otherwise
     */
    fun process32NextW(snapshotHandle: MemorySegment, processEntry: MemorySegment): Int {
        return try {
            process32NextWMH.invokeExact(snapshotHandle, processEntry) as Int
        } catch (e: Throwable) {
            throw RuntimeException("Failed to get next process", e)
        }
    }

    /**
     * Closes an open object handle.
     *
     * @param handle Handle to close
     * @return Non-zero if successful, zero otherwise
     */
    fun closeHandle(handle: MemorySegment): Int {
        return try {
            closeHandleMH.invokeExact(handle) as Int
        } catch (e: Throwable) {
            throw RuntimeException("Failed to close handle", e)
        }
    }

    /**
     * Opens an existing local process object.
     *
     * @param desiredAccess The access to the process object
     * @param inheritHandle Whether the handle can be inherited by child processes
     * @param processId The identifier of the local process to be opened
     * @return Handle to the process on success, null on failure
     */
    fun openProcess(desiredAccess: Int, inheritHandle: Boolean, processId: Long): MemorySegment? {
        val inheritFlag = if (inheritHandle) 1 else 0
        return try {
            (openProcessMH.invokeExact(desiredAccess, inheritFlag, processId) as MemorySegment)
                .takeIf { it != MemorySegment.NULL }
        } catch (e: Throwable) {
            throw RuntimeException("Failed to open process", e)
        }
    }
}