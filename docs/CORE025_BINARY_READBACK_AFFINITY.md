# BACHATA core 025 ARM64 binary audit — readback and host affinity

Date: 2026-10-15. Analysis performed on the **actual executables** extracted from APKs in the user's Library, not solely on public GitHub source.

## Reproducible binary identity

Nested path: `assets/runtime/runtime.zip!host/shadps4-arm64-025`.

| APK | Core size | SHA-256 |
| --- | ---: | --- |
| `Bachata-S4-Plus-v0.2.6-R15-CORE025-UNICO-SPIDERMAN-CUSA-ELDEN024-P36.apk` | 75,323,608 B | `b12599ecf2ebc0820fe67e47bad3c8a6d9e7ce12fd0d4feca5fe232b24ad35a4` |
| `Bachata-S4-Plus-v0.2.6-R27-FSR31-CORE025-GLIBC-N055-P36.apk` | 75,323,608 B | `c1cf8986c2a5f6f9f33d32a073eff797e2717b4848cd475c4d19f26dc7042bb2` |

Both are AArch64 ELF64 position-independent executables with matching `.text` size. Direct byte diff across these executables: **23,898 changed bytes** in the files overall, but only **38 changed bytes across 10 patches in executable `.text`**. Most other differences relate to a relocated `.dynstr` table in the newer ELF's reserved `.rodata` area. This does **not** establish that any specific 10 changed instructions are readback fixes.

## What is present in the binary (not merely the source)

The latest executable contains the following embedded strings/feature indicators:

- `BACHATA_READBACK_METRICS` at ELF file offset `0x2928b41`.
- `READBACK_METRICS interval_ms={} requests={} requested_bytes={} prefetch_window_bytes={} batches={} downloaded_bytes={} copies={} allocations={} reuses={} retained_bytes={} queue_ms={:.3f} prepare_ms={:.3f} allocate_ms={:.3f} record_ms={:.3f} wait_ms={:.3f} invalidate_ms={:.3f} restore_ms={:.3f} tracking_ms={:.3f}` at ~`0x2928c11`.
- `READBACK_PAGE page={:#x} faults={} gpu_bytes={} buffer={:#x}+{:#x}` at ~`0x2928d7b`.
- `big_core_affinity` at `0x291cfe0`, plus `Big-core affinity target=GameMain requested={} enabled={} cpu={} paired_cpu={}` and the analogous `GpuComm` message.
- `Adaptive affinity: probing cpu {}` and `ApplyCurrentThreadAffinity` from `src/common/thread.cpp`.
- A real dynamic import `pthread_setaffinity_np@GLIBC_2.34`, via PLT at `0x6ca560`. Static disassembly confirms six `BL` callsites to that PLT between `0x78ebac` and `0x78f6c0`.

The public `zenithblue-oss/shadps4-arm64` tree is older and **does not expose every internal detail** evident in the delivered core 025 binary. However, embedding telemetry format strings alone does not prove that telemetry was enabled or how often its code executed.

## Runtime evidence (two user-provided diagnostics)

In both `bachata-diagnostic-BS4-20261015-852B84AB.zip` and `...-91F991B9.zip`, the `shadps4.log` reports:

The log's launcher command explicitly runs `host/shadps4-arm64-025` (N055 in the first diagnostic and N054 in the second), confirming these are actual core 025 sessions rather than another selected host. **Both logs are from CUSA03173, not God of War III CUSA01715**: the settings evidence is real but must not be represented as a matched game FPS comparison.

```
GPU readbacksMode: 0
GPU readbackLinearImages: false
Big-core affinity target=GpuComm requested=false enabled=false cpu=-1 paired_cpu=-1
Big-core affinity target=GameMain requested=false enabled=false cpu=-1 paired_cpu=-1
```

In the public ARM64 source, `src/core/emulator_settings.h` declares `GpuReadbacksMode { Disabled=0, Relaxed=1, Precise=2 }`. Therefore **automatic image-readback mode was disabled in these BACHATA sessions**. This does not imply that all mandatory buffer-cache readbacks or fences were absent.

Host big-core affinity is **supported in the binary** but was **not requested or enabled** in the logged sessions. This refines an earlier source-only conclusion: PS4 `Pthread::SetAffinity` is a separate semantic API whose host-mask application is commented out in the published source; the binary also has its own optional adaptive `GameMain`/`GpuComm` host-affinity route.

## Relevant GoW III differences

- GoW III Port `gpu/shim/core/emulator_settings.h` defaults to `GOW3_READBACKS=Relaxed` (value 1), explicitly because the game's `FaceGen` GPU-written vertices explode with stale reads when fully disabled. Do **not** copy BACHATA's disabled mode indiscriminately.
- The Port's Android `scripts/launch_env.py` defaults `GOW3_READBACK_LINEAR=1`, whereas the inspected BACHATA sessions had linear image readbacks **false**. This is a **confirmed configuration difference** and a plausible extra GPU-to-CPU copy cost. A matched A/B with `GOW3_READBACK_LINEAR=0` is a reasonable further performance test, after checking GoW III rendering, exposure and CPU read correctness. In contrast, leave `GOW3_READBACKS=Relaxed` enabled due FaceGen correctness.
- Port already has deferred/stale texture readbacks, prefetch windows for GPU-modified buffer ranges, staging reuse, page watchers, partial-write protection, and demand waits. Binary strings from BACHATA show telemetry for these concepts, not enough by themselves to justify overwriting the Port's working implementation.
- The separate source-audited Vulkan scheduler optimization in this branch limits Android to eight in-flight submissions and avoids a per-submit timeline counter query; this is congruent with the public ARM64 BACHATA scheduler policy. The embedded binary comparison alone does **not** establish exactly which scheduler instructions executed in the tested session.
- The additional zero-download fast path in the Port avoids `WaitHostCopies()` when no image transfer is queued, but preserves all mandatory waits for actual image transfers.

## Guardrails

Do not force physical guest-thread affinity: in the actual user logs, BACHATA's host-target affinity setting was off. Optional adaptive affinity is a separate feature and requires device A/B timing before enabling.

Do not switch `GOW3_READBACKS` from Relaxed to Disabled by default: the Port documents a game-specific FaceGen correctness dependency.

Do not claim a proven FPS increase from binary inspection alone. First compare matched GoW III scenes with `GOW3_READBACK_LINEAR=0` vs 1; measure actual GPU wait/readback behavior without enabling verbose logs continuously.
