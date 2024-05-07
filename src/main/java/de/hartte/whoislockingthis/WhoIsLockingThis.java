package de.hartte.whoislockingthis;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public class WhoIsLockingThis {
    public static void main(String[] args) throws Throwable {


        // STEP 1: FIND FOREIGN FUNCTION

        Linker linker = Linker.nativeLinker();
        try (Arena arena = Arena.ofConfined()) {
            SymbolLookup rstrtmgr = SymbolLookup.libraryLookup("rstrtmgr.dll", arena);
            var rmStartSessionAddr = rstrtmgr.find("RmStartSession").orElseThrow();
            var rmEndSessionAddr = rstrtmgr.find("RmEndSession").orElseThrow();
            var rmRegisterResourcesAddr = rstrtmgr.find("RmRegisterResources").orElseThrow();
            var rmGetListAddr = rstrtmgr.find("RmGetList").orElseThrow();

            /*
             * DWORD RmStartSession(
             *   [out] DWORD    *pSessionHandle,
             *         DWORD    dwSessionFlags,
             *   [out] WCHAR [] strSessionKey
             * );
             */
            FunctionDescriptor rmStartSessionSig = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS
            );

            /*
             * DWORD RmEndSession(
             *   [in] DWORD dwSessionHandle
             * );
             */
            FunctionDescriptor rmEndSessionSig = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT
            );

            /*
             * DWORD RmRegisterResources(
             *   [in]           DWORD                dwSessionHandle,
             *   [in]           UINT                 nFiles,
             *   [in, optional] LPCWSTR []           rgsFileNames,
             *   [in]           UINT                 nApplications,
             *   [in, optional] RM_UNIQUE_PROCESS [] rgApplications,
             *   [in]           UINT                 nServices,
             *   [in, optional] LPCWSTR []           rgsServiceNames
             * );
             */
            FunctionDescriptor rmRegisterResourcesSig = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS
            );

            /*
             * DWORD RmGetList(
             *   [in]                DWORD              dwSessionHandle,
             *   [out]               UINT               *pnProcInfoNeeded,
             *   [in, out]           UINT               *pnProcInfo,
             *   [in, out, optional] RM_PROCESS_INFO [] rgAffectedApps,
             *   [out]               LPDWORD            lpdwRebootReasons
             * );
             */
            FunctionDescriptor rmGetListSig = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS
            );

            // Create a downcall handle for the C function
            MethodHandle rmStartSession = linker.downcallHandle(rmStartSessionAddr, rmStartSessionSig);
            MethodHandle rmEndSession = linker.downcallHandle(rmEndSessionAddr, rmEndSessionSig);
            MethodHandle rmRegisterResources = linker.downcallHandle(rmRegisterResourcesAddr, rmRegisterResourcesSig);
            MethodHandle rmGetList = linker.downcallHandle(rmGetListAddr, rmGetListSig);

            // Allocate the array for
            MemorySegment rgsFileNames = arena.allocate(ValueLayout.ADDRESS, 0);
            MemorySegment rgApplications = arena.allocate(ValueLayout.ADDRESS, 0);
            MemorySegment rgsServiceNames = arena.allocate(ValueLayout.ADDRESS, 0);

            var sessionName = arena.allocateFrom("WhoIsLockingThis." + UUID.randomUUID(), StandardCharsets.UTF_16LE);
            var pSessionHandle = arena.allocate(ValueLayout.JAVA_INT); // Will receive session handle
            int sessionHandle = pSessionHandle.get(ValueLayout.JAVA_INT, 0);

            int err = (int) rmStartSession.invokeExact(pSessionHandle, 0, sessionName);
            if (err != 0) {
                throw new IllegalStateException("failed to open Rm session: " + err);
            }
            try {
                // Allocate an array that contains a pointer to the path
                var pathToCheck = "C:\\pagefile.sys";
                var pPaths = arena.allocate(ValueLayout.ADDRESS, 1);
                pPaths.set(ValueLayout.ADDRESS, 0, arena.allocateFrom(pathToCheck, StandardCharsets.UTF_16LE));

                var result = (int) rmRegisterResources.invokeExact(0, 1, pPaths, 0, MemorySegment.NULL, 0, MemorySegment.NULL);
                java.lang.System.out.println(result);

            } finally {
                err = (int) rmEndSession.invokeExact(sessionHandle);
            }

        }
    }
}
