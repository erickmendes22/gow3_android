# God of War III — Daedalus Fork

Daedalus Fork maintained by erickmendes22. Based on the Android work by zFitness and the upstream contributors.

**Version: 2.1.6-android2** · [YouTube: @daedalusemulator](https://www.youtube.com/@daedalusemulator) · [Base: zFitness/gow3_android](https://github.com/zFitness/gow3_android)


**简体中文** | [English](README.en.md) | [Português (Brasil)](README.pt-BR.md)

<p align="center">
  <img src="docs/a.jpg" alt="游戏截图 1" width="45%"/>
  <img src="docs/b.jpg" alt="游戏截图 2" width="45%"/>
</p>


《**战神 III 重制版**》（God of War III Remastered，PlayStation 4）的 **Android / ARM64 移植**（开发中，见下文）。

游戏的原始可执行文件是 x86-64 架构（PS4 使用的同样是 x86-64）。在 Android 设备上，游戏代码
由 **FEXCore** 动态翻译后在 ARM64 上执行；一套为本作专门编写的运行时替代了 PS4 的系统库，图形
部分则被翻译为 Vulkan（经 Turnip 驱动连接 Adreno GPU）。它不是通用模拟器：整个项目只服务
这一款游戏。本项目 fork 自 [R-V-L 移植版](https://github.com/R-V-L/gow3_pc)，
后者基于 [deadinside28 的 bloodborne_pc](https://github.com/deadinside28/bloodborne_pc)，
并合入了 [cuesta4 的 shadPS4 分支](https://github.com/cuesta4/shadPS4) 中针对战神 III 的修复
（见[致谢](#致谢)）。

> **本项目不包含任何游戏文件。** 你需要从自己的 PS4 导出的游戏文件（当前测试目标为
> CUSA01740，版本 01.02）。
> 本项目与 Sony Interactive Entertainment、Santa Monica Studio 无关。
> God of War 是 Sony Interactive Entertainment 的商标。


## 快速开始

从拿到项目到跑起 debug 的完整教程（Android 移植，macOS Apple Silicon 主机）见
**[docs/QUICKSTART.md](docs/QUICKSTART.md)**。

## 性能表现

Android 端仍在真机验证中，暂无稳定的性能数据。

## 已实现功能

- **运行链路**：x86-64 游戏代码由 FEXCore 在 ARM64 上执行（`cpu/`，`libgow3cpu.so`），图形
  经 shadPS4 图形核心的 ARM64 构建（`libgow3gpu.so`）翻译为 Vulkan，经 Turnip 连接 Adreno GPU。
- **Kotlin/Compose launcher**（`android-launcher/`）：安装 runtime（SHA-256 校验）、管理 Surface、
  输入、音频、设置与进程生命周期。

- 复用了桌面版同一套游戏运行时、PS4 系统库重实现、图形核心与补丁逻辑；Android 端当前在真机
  上验证中，分辨率、FPS 与修改器补丁尚未做字节级校验。

## 系统要求

- **ARM64（arm64-v8a）的 Android 设备**，无需 root。
- 支持 Vulkan 的 GPU，且驱动（Turnip）较新（Adreno 设备）。
- 你自己的游戏副本：**CUSA01740 并已应用 01.02 更新**（包含 `eboot.bin`、`sce_module`、
  `sce_sys` 的目录）。下载包中不包含任何游戏文件。

构建 Android 端需要一台宿主机（当前流程基于 macOS Apple Silicon），依赖与步骤见
[docs/QUICKSTART.md](docs/QUICKSTART.md)。


## 工作原理

| 模块     | 位置                                                             | 作用                                                                                              |
| -------- | ---------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| 准备     | `scripts/`                                                        | 读取 `eboot.bin`，应用重定位，并把游戏自带的 `libc` 与 `libSceFios2` 链接为单一镜像              |
| 加载器   | `src/probe.c`                                                     | 加载镜像、安装经过校验的 hook（修改器、倍率、血条），并启动游戏原始代码                           |
| 运行时   | `src/runtime_*.c`                                                 | 重新实现 PS4 系统函数：内存、线程、文件、音频、视频、手柄、存档与服务                             |
| GPU      | `gpu/shadps4/`                                                    | shadPS4 图形核心，将 PS4 GPU 指令翻译为 Vulkan，并包含本移植的性能优化（后台着色器缓存、延迟回读） |
| 游戏内菜单 | `gpu/shim/gow3_overlay.cpp`                                     | Dear ImGui 菜单与性能叠加层；设置位于 `gpu/shim/gow3_settings.cpp`                                 |
| 补丁     | `patches/God_of_War_III_Remastered.xml`、`scripts/patches.py`     | 启动时在内存中应用，不修改游戏文件                                                                 |
| 启动     | Android launcher（`android-launcher/`）、`run.sh`               | Android 端由 launcher 准备镜像、启动 `gow3-probe`；`run.sh` 为桌面版启动入口                     |

更多文档：[模组](docs/MODS.md)、[帧图与放大器](docs/upscaler.md)、[GPU 并行工作](docs/parallel_gpu.md)
与[路线图](docs/gow3/ROADMAP.md)。

## 致谢

本项目 fork 自 R-V-L 的 linux 移植版，后者又源自 FelipePrado0 的原创项目：

- **deadinside28**：[bloodborne_pc](https://github.com/deadinside28/bloodborne_pc)，最初的原生移植。
- **gow3_pc**（R-V-L）：[bloodborne_pc for Windows](https://github.com/R-V-L/gow3_pc)，
  本项目所基于的分支。
- **cuesta4**：[专注于 God of War III Remastered 的 shadPS4 分支](https://github.com/cuesta4/shadPS4)
  （分支 `eltutz`）。本移植使用了其中的 GNM 混合重写、深度/模板状态与图像别名修复。
- **shadPS4 团队**：[shadPS4](https://github.com/shadps4-emu/shadPS4) 渲染器。
- **fexdroid** [fexdroid](https://github.com/cobrabm12/fexdroid) 

补丁：kvicken 与 cuesta4（分辨率、贴图修复、120 FPS 与跳过视频，基于 illusion0001 的 PS3
补丁），发布于 shadPS4 的
[shadps4-emu/ps4_cheats#145](https://github.com/shadps4-emu/ps4_cheats/pull/145)，并针对本可执行
文件做了适配。240 FPS 补丁由 Felipe Prado 从 120 FPS 补丁改编而来。修改器改编自 Celogamez 的
[GoldHEN cheats](https://github.com/GoldHEN/GoldHEN_Cheat_Repository)。

此外还使用了：[LibAtrac9](https://github.com/Thealexbarney/LibAtrac9)、[FFmpeg](https://ffmpeg.org)、
[SDL3](https://github.com/libsdl-org/SDL)、[Dear ImGui](https://github.com/ocornut/imgui)、
[sirit](https://github.com/shadps4-emu/sirit)、[magic_enum](https://github.com/Neargye/magic_enum)、
[miniz](https://github.com/richgel999/miniz)、[xbyak](https://github.com/herumi/xbyak)、
[Vulkan Memory Allocator](https://github.com/GPUOpen-LibrariesAndSDKs/VulkanMemoryAllocator)、
[FSR-Vulkan](https://github.com/FireBurn/FSR-Vulkan) 与 AMD FidelityFX SDK、
[LLVM](https://llvm.org) 以及 [Pillow](https://python-pillow.org)。

## 许可证

GNU GPL v2 或更高版本（见 [LICENSE](LICENSE)）。第三方组件保留各自的许可证。