@file:Suppress("Unused", "Nothing_to_inline")
package patcher.core.utils
import java.lang.foreign.*

@JvmInline
value class Process(val memorySegment: MemorySegment) {
    /* === COMPANION === */
    companion object {
        /* === INTERNAL CONSTANTS === */
        /* Toolhelp32 Snapshot */
        private const val TH32CS_SNAPPROCESS = 0x00000002L

        private const val MAX_PATH = 260

        private const val PROCESS_ENTRY_SIZE_LONG = 568L

        private const val PROCESS_ENTRY_SIZE_INT = 568

        /* Process Access Rights */
        private const val PROCESS_VM_WRITE = 0x0020

        private const val PROCESS_VM_READ = 0x0010

        private const val PROCESS_VM_OPERATION = 0x0008

        private const val PROCESS_QUERY_INFORMATION = 0x0400

        /* Process Access Combination */
        private const val PROCESS_ALL_ACCESS = 0x1F0FFF

        private const val PROCESS_STANDARD_RIGHTS = PROCESS_VM_READ or PROCESS_VM_WRITE or PROCESS_VM_OPERATION or PROCESS_QUERY_INFORMATION

        /* Process Entry Offset */
        private const val OFFSET_DW_SIZE = 0L

        private const val OFFSET_TH32_PROCESS_ID = 8L

        private const val OFFSET_SZ_EXE_FILE = 44L


        /* === PUBLIC FUNCTIONS === */
        /**
         * Retrieves all running processes in the system
         *
         * Creates a snapshot of system processes and enumerates each process entry.
         * Automatically manages snapshot handle lifecycle.
         *
         * @return List of Process objects representing all running processes,
         *         empty list if snapshot creation fails
         */
        fun getAllProcesses(): List<Process> {
            val processes = mutableListOf<Process>()
            val snapshot = ProcessHandleUtils.createToolhelp32Snapshot(TH32CS_SNAPPROCESS, OFFSET_DW_SIZE) ?: return emptyList()

            try {
                val firstEntry = NativeBinder.sharedArena.allocate(PROCESS_ENTRY_SIZE_LONG).apply {
                    set(ValueLayout.JAVA_INT, OFFSET_DW_SIZE, PROCESS_ENTRY_SIZE_INT)
                }

                if (ProcessHandleUtils.process32FirstW(snapshot, firstEntry) != 0) {
                    processes.add(Process(firstEntry))
                    do {
                        val nextEntry = NativeBinder.sharedArena.allocate(PROCESS_ENTRY_SIZE_LONG).apply {
                            set(ValueLayout.JAVA_INT, OFFSET_DW_SIZE, PROCESS_ENTRY_SIZE_INT)
                        }
                        processes.add(Process(nextEntry))
                    } while (ProcessHandleUtils.process32NextW(snapshot, nextEntry) != 0)
                }
            } finally {
                ProcessHandleUtils.closeHandle(snapshot)
            }

            return processes
        }

        /**
         * Opens a handle to a process with specified access rights
         *
         * @param pid Process identifier of the target process
         * @param desiredAccess Access mask specifying the desired access rights
         *                      Defaults to PROCESS_ALL_ACCESS
         *
         * @return MemorySegment representing the process handle, or null if opening fails
         */
        fun openHandle(pid: Long, desiredAccess: Int = PROCESS_ALL_ACCESS): MemorySegment? {
            return ProcessHandleUtils.openProcess(desiredAccess, false, pid)
        }

        /**
         * Closes an open process handle
         *
         * @param handle Process handle to close
         *
         * @return true if handle was successfully closed, false otherwise
         */
        fun closeHandle(handle: MemorySegment): Boolean {
            return (ProcessHandleUtils.closeHandle(handle)) != 0
        }
    }


    /* === IDENTITIES === */
    override fun toString(): String {
        val name = getName()
        val pid = getPid()
        val parentPid = getParentPid()
        val priorityClass = getPriorityClass()
        return "Process(name=$name, pid=$pid, parentPid=$parentPid, priorityClass=$priorityClass)"
    }


    /* === PUBLIC STATE FUNCTIONS === */
    /**
     * Returns the process ID (PID)
     */
    inline fun getPid(): Long {
        return memorySegment.get(ValueLayout.JAVA_INT, 8L).toLong()
    }

    /**
     * Returns the parent process ID (PPID)
     */
    inline fun getParentPid(): Long {
        return memorySegment.get(ValueLayout.JAVA_INT, 28L).toLong()
    }

    /**
     * Returns the process executable name (e.g. "calc.exe")
     */
    inline fun getName(): String {
        return readProcessName()
    }

    /**
     * Returns the process base priority class
     */
    inline fun getPriorityClass(): Int {
        return memorySegment.get(ValueLayout.JAVA_INT, 48L)
    }


    /* === INTERNAL FUNCTIONS === */
    /**
     * Reads the process executable name from szExeFile field
     *
     * Parses null-terminated wide character array starting at OFFSET_SZ_EXE_FILE.
     * Maximum length is limited to MAX_PATH characters.
     *
     * @return Process executable name as UTF-16 string
     */
    @PublishedApi
    internal fun readProcessName(): String {
        val buffer = CharArray(MAX_PATH)
        var length = 0

        for (i in 0 until MAX_PATH) {
            val charOffset = OFFSET_SZ_EXE_FILE + (i * ValueLayout.JAVA_CHAR.byteSize())
            val char = memorySegment.get(ValueLayout.JAVA_CHAR, charOffset)

            if (char == '\u0000') {
                break
            }

            buffer[i] = char
            length++
        }

        return String(buffer, 0, length)
    }
}