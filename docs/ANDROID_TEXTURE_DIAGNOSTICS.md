# Android texture-integrity diagnostics

This checklist is for the Android/ARM64 `CUSA01740 v01.02` path. It records runtime evidence
without copying `eboot.bin`, game assets, or private device data into the repository.

## Launch record

For every run, retain the device-side `out/texture_integrity.json` and the surrounding launch log.
The record must include:

- title ID, APP_VER, and eboot SHA-256;
- patch profile status (`validated`, `unsupported-title`, `unsupported-app-version`,
  `eboot-hash-mismatch`, `patch-site-byte-mismatch`, `insufficient-memory-budget`,
  `patch-conflict`, or `missing-or-unreadable-eboot`);
- Vulkan/Turnip initialization status (`ok` or `failed` — a failure aborts the launch before
  any texture-integrity claim is made);
- Turnip ICD/variant and GPU chip ID;
- DMEM, VBlank, internal/output resolution, upscaler and linear/deferred/stale readback modes;
- shader-cache state (`cold` or `warm`) and an externally stored capture ID (with its capture
  directory, always outside the repository/APK).

## A/B scene matrix

Run the same save and camera route with the prior profile and the conservative profile
(`GOW3_UPSCALER=none`, `GOW3_READBACK_LINEAR=1`). Record whether small block artifacts remain.

1. Static scene: stand still for at least 30 seconds and capture the same view.
2. Camera movement: rotate and move through textured scenery; check for block changes or trails.
3. Transparent/effect scene: fire, fog, particles, glow and other alpha-heavy effects.
4. Cold shader cache: clear only the runtime shader cache, launch, and repeat the three scenes.
5. Warm shader cache: relaunch without clearing it and repeat the same scenes.
6. Output modes: repeat for each Android-supported resolution/profile, noting any mode that is
   not available on the device.

## Interpretation

- If only the cold run is affected, investigate shader compilation/cache behavior first.
- If disabling temporal upscaling or enabling linear readback removes the blocks, retain the
  conservative profile while investigating the GPU readback/layout path.
- If the issue follows higher DMEM/resolution settings or the process is killed, investigate
  Video Arena/direct-memory pressure separately from texture sampling.
- If the issue varies by Turnip variant/GPU while the guest inputs match, keep the result
  device-specific and do not enable a game patch globally.

No real-device run is a pass by itself: host builds and static tests can validate the launch
contract, but only the scene matrix can establish whether Android gameplay artifacts improved.
