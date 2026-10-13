# 战神 III 重制版 PC 移植版（God of War III Remastered for PC）

**简体中文** | [Português (Brasil)](README.pt-BR.md)

> 本文件为中文翻译与整理版；上游项目原始 README 为英文版。

《**战神 III 重制版**》（God of War III Remastered，PlayStation 4）向 Windows 10/11 与
Linux（含 Steam Deck）的**原生移植**。

游戏原本的可执行文件可以直接跑在 PC 处理器上（PS4 使用的同样是 x86-64 架构）。一套为本作
专门编写的运行时替代了 PS4 的系统库，图形部分则被翻译为 Vulkan。它不是通用模拟器：整个
项目只服务这一款游戏。本项目 fork 自 [Supermedo 的 Windows 移植版](https://github.com/Supermedo/bloodborne_pc)，
后者基于 [deadinside28 的 bloodborne_pc](https://github.com/deadinside28/bloodborne_pc)，
并合入了 [cuesta4 的 shadPS4 分支](https://github.com/cuesta4/shadPS4) 中针对战神 III 的修复
（见[致谢](#致谢)）。

> **本项目不包含任何游戏文件。** 你需要从自己的 PS4 导出的 dump（CUSA01623，版本 01.02）。
> 本项目与 Sony Interactive Entertainment、Santa Monica Studio 无关。
> God of War 是 Sony Interactive Entertainment 的商标。

开发进度（以葡萄牙语记录）见 [docs/gow3/ROADMAP.md](docs/gow3/ROADMAP.md)。

## 性能表现

在测试机（AMD Radeon RX 6700 XT、Ryzen 5 5600X、32 GB 内存）上：

- 1080p、引擎帧率 240 FPS：平均 **144 FPS**（区间 55～240）。
- 4K、FSR 1：平均 **108 FPS**。

**已在原生 1080p、引擎 240 FPS 下从头到尾完整测试并可正常通关。** 在其他渲染分辨率下
部分贴图会出现异常，修复工作正在进行中。

## 已实现功能

- 启动流程、菜单、视频过场动画（H.264）以及实际游玩。
- 音频：音乐、语音与音效（ATRAC9 与 MP3）。
- 手柄（DualSense、Xbox 等，经由 SDL3）与键盘可同时使用，且两者均可自定义按键。
- 存档与读档：存档列表在画面上显示，与 PS4 上相同；自动存档同样可用。启动器会在每次启动
  前备份存档。
- 补丁：渲染分辨率 480p / 720p / 1440p / 1800p / 4K、损坏贴图修复、按 X 跳过视频（社区贡献）；
  引擎帧率 120 FPS（kvicken）或 240 FPS（由 Felipe Prado 适配）。
- **秒进：** 已保存的着色器缓存在后台加载，新着色器同样在后台编译，游戏不会停下来等待构建。
- **启动器：** 采用游戏本身的配色，英文界面并带部分翻译。首页提供快捷设置，另有显示、性能、
  游戏目录与存档备份、补丁、模组（排序、预设与冲突）、按键（可切换 PlayStation 或 Xbox 键位
  图标）、更新与诊断等页面。
- **游戏内菜单**（键盘 `Insert`，手柄 `R3 + L2`），四个标签页：
  - **Game：** 修改器（无限生命、魔力、道具槽、斯巴达之怒、红色魂珠最大化）；红色（EXP）、
    绿色（生命）、蓝色（魔力）、金色（斯巴达之怒）魂珠以及输出/承受伤害的倍率调节，范围
    0.1x～100x；敌人血条。
  - **Image：** 显示模式（窗口、无边框、全屏）、垂直同步、FSR 1 放大与 RCAS 锐化。
  - **Performance：** 帧率限制（30～不限制）、排队帧数、后台着色器编译开关；渲染分辨率、
    引擎帧率与 GPU 回读选项在点击一次「Apply and restart」后生效。
  - **Overlay：** 性能叠加层，显示 FPS、帧时间、放大器、GPU（型号与占用）、CPU、内存与显存，
    每项均可单独开关，并可调整字号、位置角落、背景不透明度与排版。数字取自游戏自身占用，
    而非整机占用。

## 系统要求

- Windows 10（1903 或更高版本）或 Windows 11，64 位。
- 支持 Vulkan 1.3 的显卡，且驱动为较新版本。
- 你自己的游戏副本：**CUSA01623 并已应用 01.02 更新**（包含 `eboot.bin`、`sce_module`、
  `sce_sys` 的目录）。下载包中不包含任何游戏文件。

### 补丁与游戏内功能的适用性

补丁只会应用在与其校验过的 `eboot.bin` 完全一致的文件上
（sha256 `d85c8135d330c3b601bf5dc3f1dd86bd6119fb8a9fce372bed2c9785f9a79299`）；游戏内 hook
（修改器、倍率、血条）也只会在被改写代码的字节符合预期时注入。否则游戏仍可启动，只是相关
功能不会出现，日志与菜单中会说明原因。

### 源码构建依赖

[MSYS2](https://www.msys2.org) 与 [Python 3](https://www.python.org)（需安装 Pillow：
`pip install pillow`，用于生成游戏图标与封面图）。

## 下载与游玩（Windows）

### 操作步骤

1. 从 [Releases 页面](https://github.com/FelipePrado0/gow3_pc/releases/latest) 下载
   `gow3-windows.zip`。
2. 解压到任意目录，例如 `C:\Games\gow3-windows`。不要在压缩包内直接运行。
3. 打开 **`God of War III.exe`**。如果 Windows 弹出「Windows 已保护你的电脑」，点击
   **更多信息**，再点 **仍要运行**：该程序没有数字签名。
4. 点击 **Choose the game folder**，进入 **Game** 页面。在 **Game folder** 一栏点击
   **Browse…**，选择包含 `eboot.bin` 的目录。
5. 回到 **Home** 页面，此时会显示封面图以及「CUSA01623 · version 01.02」。可按需调整
   **Quick settings**：显示模式、帧率限制、渲染分辨率与引擎帧率。
6. 点击 **PLAY**。首次启动需要稍长时间，用于准备游戏镜像；之后的启动会很快。

补丁与修复已内置并自动生效：贴图修复、按 X 跳过视频与 120 FPS 默认开启。分辨率与帧率可在
**Home** 或 **Performance** 页面调整。

### 游玩过程中

- 按键盘 **Insert** 或手柄 **R3 + L2** 打开移植版菜单：修改器、倍率、画面、性能与性能叠加层。
- 已保存的着色器在后台加载，角落会出现一条小提示「Loading shader cache」显示进度。

### 下次启动

- **`Play God of War III.exe`** 会直接按已保存的设置启动游戏。可以为它创建桌面快捷方式，或用
  **Add a Non-Steam Game** 添加到 Steam。
- 存档、着色器缓存与日志位于 `God of War III.exe` 同级的 `user` 目录。更新时请保留该目录。
- **Advanced → Check for updates** 会下载并安装新版本，存档与设置都会保留。

### 遇到问题时

- 启动黑屏：**Advanced → Clear shader cache**，然后重新启动。
- 游戏无法启动：查看 **Log** 页面，或在反馈问题时附上 `user\last_run.log`。

## Linux 与 Steam Deck

同样提供原生 Linux 构建 —— 使用相同的 probe 与 `run.sh`，但没有启动器图形界面。在 Steam Deck
上需从桌面模式运行。完整说明见 [docs/LINUX.md](docs/LINUX.md)。

**下载与游玩（发行版）**

1. 从 Releases 下载 `gow3-linux-x86_64.tar.gz` 并解压到任意位置。
2. 把游戏目录（包含 `eboot.bin` 的目录，例如 `CUSA01740`）放进解压后的目录中，与 `bin/` 同级。
3. 任意 Linux 上运行 `start-gow3.sh`；在 Steam Deck 上运行 `start-gow3-deck.sh`，或直接双击
   `God of War III.desktop`。

游戏目录会被自动识别；存档、着色器缓存与 `gow3.ini` 存放在 `~/.local/share/gow3`。首次启动会
编译着色器（约一两分钟）。本项目不会向系统全局安装任何内容，直接使用宿主机的 Vulkan 驱动。

**从源码构建：** 见 [docs/LINUX.md](docs/LINUX.md)。依赖位于本地 `.deps/`（无需 root 权限）；
执行 `bash build.sh`，然后 `GOW3_GAME_DIR=/path/to/CUSA01623 bash run.sh`。

## Android 移植（开发中）

本仓库同时包含一个 **Android / ARM64 移植**。游戏的 x86-64 代码运行在 **FEXCore** 之上
（`cpu/`，`libgow3cpu.so`），图形路径使用 shadPS4 图形核心的 ARM64 构建（`libgow3gpu.so`）。
Android 移植目前仍在真机上验证中。

上面记录的桌面版构建目标是 `CUSA01623 v01.02`。当前用于 Android 测试的 dump 是
`CUSA01740 v01.02`，其更新后的 eboot 以 `CUSA01740-patch` 的形式单独保存在手机上。ARM64
加载器无需游戏补丁即可准备并运行它；针对 `CUSA01740` 的分辨率、FPS 与修改器补丁尚未做字节级
校验。

架构说明与完整设计笔记见 [`docs/ANDROID_PORT.md`](docs/ANDROID_PORT.md)。

### Android 构建快速开始（macOS Apple Silicon）

在仓库根目录执行以下命令。这是标准流程；除非某个构建目标或依赖发生变化，否则不要重复执行
其中的 GPU / CPU 单独构建命令。

#### 1. 一次性准备依赖

仓库中必须已包含 FEX 与 ARM64 依赖。若 FEX 已放在 `third_party/FEX`，继续使用该 checkout：

```bash
cd /Users/zhengmiao/mygitworks/gow3_android
git submodule update --init --recursive
```

Android rootfs/sysroot 与头文件依赖也必须已存在于 `.local-deps/android` 与 `.local-deps/root`
下。如果缺失，参见 [`docs/ANDROID_PORT.md`](docs/ANDROID_PORT.md)。

#### 2. 只复制准备加载器所需的游戏文件

完整游戏留在手机上。电脑上只需要四个小文件：

```text
game/CUSA01740/eboot.bin                         # 来自 CUSA01740-patch
game/CUSA01740/sce_sys/param.sfo                 # 来自 CUSA01740-patch
game/CUSA01740/sce_module/libc.prx               # 来自 CUSA01740
game/CUSA01740/sce_module/libSceFios2.prx        # 来自 CUSA01740
```

测试期间手机上的目录结构如下：

```bash
mkdir -p game/CUSA01740/sce_module game/CUSA01740/sce_sys
adb pull "/sdcard/Download/games/ps4/GOW3 ROM/CUSA01740-patch/eboot.bin" \
  game/CUSA01740/eboot.bin
adb pull "/sdcard/Download/games/ps4/GOW3 ROM/CUSA01740-patch/sce_sys/param.sfo" \
  game/CUSA01740/sce_sys/param.sfo
adb pull "/sdcard/Download/games/ps4/GOW3 ROM/CUSA01740/sce_module/libc.prx" \
  game/CUSA01740/sce_module/libc.prx
adb pull "/sdcard/Download/games/ps4/GOW3 ROM/CUSA01740/sce_module/libSceFios2.prx" \
  game/CUSA01740/sce_module/libSceFios2.prx
```

#### 3. 准备游戏镜像

```bash
python3 scripts/prepare.py game/CUSA01740 --out out
python3 scripts/link_modules.py game/CUSA01740 --out out
python3 scripts/content_profile.py game/CUSA01740 --out out --sku full
```

预期产物包括 `out/eboot.elf`、`out/boot.bin`、`out/boot-linked.bin` 与 `out/content.bin`。
`prepare.py` 输出的 `Unavailable non-loadable metadata headers` 属于提示信息，并非失败。

#### 4. 构建 ARM64 原生组件与调试版 APK

在当前 shell 中一次性设置工具链路径：

```bash
export GOW3_LLVM_PREFIX=/opt/homebrew/opt/llvm
export PATH="$GOW3_LLVM_PREFIX/bin:/opt/homebrew/bin:$PATH"
export GOW3_AR="$GOW3_LLVM_PREFIX/bin/llvm-ar"
export GOW3_FEX_SOURCE="$PWD/third_party/FEX"
```

构建原生 CPU/GPU/probe 组件。由于当前 `CUSA01740` 测试的游戏专用补丁地址尚未通过校验，
必须设置 `GOW3_SKIP_GAME_PATCH_PARTS=1`：

```bash
GOW3_SKIP_GAME_PATCH_PARTS=1 \
bash tools/android/build/build_android_runtime.sh
```

该命令同时会构建 release 版 Turnip 与 LSFG，产出：

```text
out/arm64/gpu/libgow3gpu.so
out/arm64/fex/libgow3cpu.so
out/arm64/gow3-probe
out/arm64/bundle/
```

在原生组件已存在的前提下，使用 debug 汇编器构建调试版 APK。它会复用本地 Turnip/LSFG 产物，
不会重新构建它们：

```bash
GOW3_REBUILD_DEBUG_RUNTIME=1 \
GOW3_DEBIAN_MIRROR=https://mirrors.tuna.tsinghua.edu.cn/debian \
GOW3_KEYSTORE_PASSWORD=android \
GOW3_KEY_ALIAS=gow3 \
GOW3_KEY_PASSWORD=android \
bash tools/android/debug/build_debug_apk.sh
```

APK 输出在 `out/debug/` 下。若 `out/debug/runtime.tar.gz` 已存在且原生组件没有变化，可去掉
`GOW3_REBUILD_DEBUG_RUNTIME=1` 以直接复用。

#### 5. 安装、清理旧缓存与查看日志

```bash
adb install -r out/debug/God\ of\ War\ III-debug-*.apk
adb shell run-as com.gow3.android sh -c '
  rm -rf files/gow3port/user/cache
  rm -rf files/gow3port/user/shader
'
adb exec-out run-as com.gow3.android \
  sh -c 'tail -f files/gow3port/logs/android-game.log'
```

若设备已连接，也可直接按序列号读取日志（请把 `SERIAL` 换成 `adb devices` 显示的值）：

```bash
SERIAL=HA2EFNGR
APP=com.gow3.android

adb -s "$SERIAL" shell run-as "$APP" \
  cat /data/user/0/$APP/files/gow3port/logs/android-game.log
```

完整 `CUSA01740` 游戏保留在设备上，例如 `Download/games/ps4/GOW3 ROM/CUSA01740`。当前
Android 端的游戏选择器只接受一个游戏目录；如果设备上基础版游戏与 `CUSA01740-patch` 是分开
存放的，必须在运行时向其呈现一个合并后的游戏视图，才能正常完整启动。

#### 各构建脚本的作用

```text
build_arm64.sh                 仅构建 CPU + GPU + 原生 probe
build_android_runtime.sh       原生组件 + release Turnip/LSFG + release 运行时
build_debug_apk.sh             debug 运行时归档 + debug APK；复用本地 Turnip/LSFG
```

常规重新构建时不要分别执行三个原生命令。完整运行时用 `build_android_runtime.sh`；原生组件
已经是最新的情况下用 `build_debug_apk.sh`。

## 从源码构建（Windows）

开发在 `gow3` 分支上进行；`main` 分支接收经过测试的合并。

1. 安装 MSYS2（`winget install MSYS2.MSYS2`），并在 **MSYS2 CLANG64** shell 中安装
   [packaging/windows/README.md](packaging/windows/README.md) 列出的软件包。
2. 递归克隆仓库并构建：

   ```bash
   git clone --recursive https://github.com/FelipePrado0/gow3_pc
   cd gow3_pc
   git checkout gow3   # 最新进展在此分支；跳过则构建 main
   bash build.sh
   ```

3. 在项目目录中用 PowerShell 启动启动器：

   ```powershell
   python launcher/gow3_launcher_win.py
   ```

4. 在 **Game** 页面选择游戏目录。按需调整 **Display**、**Performance** 与 **Patches**，
   然后点击 **PLAY**。

若希望直接按已保存的设置启动、不弹出启动器窗口（用于快捷方式或 Steam）：

```powershell
python launcher/gow3_launcher_win.py --play
```

或者完全不使用启动器：

```powershell
$env:GOW3_GAME_DIR = 'D:\path\CUSA01623'
python run.py
```

设置保存在 `gow3.ini`（游戏与游戏内菜单使用）与 `%APPDATA%\gow3-launcher\settings.json`
（启动器使用）中。存档、着色器缓存与 `last_run.log` 位于项目的 `user` 目录。

### 键盘操作

以下为默认按键，可在启动器的 **Controls** 页面修改。

| 按键          | 手柄按键                    |
| ------------- | --------------------------- |
| WASD          | 左摇杆                      |
| 方向键        | 右摇杆（视角）              |
| Space         | 叉（Cross）                 |
| Left Shift    | 圈（Circle）                |
| E             | 方（Square）                |
| Q             | 三角（Triangle）            |
| 1 / 3         | L1 / R1                     |
| R / F         | L2 / R2                     |
| Z / C         | L3 / R3                     |
| I、K、J、L     | 方向键（d-pad）             |
| Enter         | Options                     |
| Tab           | 触摸板                      |
| Insert        | 移植版菜单（手柄上为 R3+L2） |

菜单中：L1/R1 或 Q/E 切换标签页。存档列表中：方向键或十字键选择，Enter 或叉键确认，
Esc 或圈键取消。

## 测试

```bash
python -m unittest discover -s tests
bash build.sh --test
ninja -C out/gpu stat-multipliers-test perf-overlay-test warmup-inbox-test graphics-settings-test
out/gpu/stat-multipliers-test.exe out/eboot.elf
out/gpu/perf-overlay-test.exe
out/gpu/warmup-inbox-test.exe
out/gpu/graphics-settings-test.exe
```

C++ 测试是 [gpu/CMakeLists.txt](gpu/CMakeLists.txt) 中的构建目标；其中校验游戏代码的测试需要
把准备好的可执行文件（`out/eboot.elf`）作为参数传入。

若要在不启动游戏的前提下，检查游戏用到的每个系统函数是否都已实现：

```bash
out/gow3-probe.exe out/boot-linked.bin --app0 <game folder> --check-imports
```

## 工作原理

| 模块     | 位置                                                             | 作用                                                                                              |
| -------- | ---------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| 准备     | `scripts/`                                                        | 读取 `eboot.bin`，应用重定位，并把游戏自带的 `libc` 与 `libSceFios2` 链接为单一镜像              |
| 加载器   | `src/probe.c`                                                     | 加载镜像、安装经过校验的 hook（修改器、倍率、血条），并启动游戏原始代码                           |
| 运行时   | `src/runtime_*.c`                                                 | 重新实现 PS4 系统函数：内存、线程、文件、音频、视频、手柄、存档与服务                             |
| GPU      | `gpu/shadps4/`                                                    | shadPS4 图形核心，将 PS4 GPU 指令翻译为 Vulkan，并包含本移植的性能优化（后台着色器缓存、延迟回读） |
| 游戏内菜单 | `gpu/shim/gow3_overlay.cpp`                                     | Dear ImGui 菜单与性能叠加层；设置位于 `gpu/shim/gow3_settings.cpp`                                 |
| 补丁     | `patches/God_of_War_III_Remastered.xml`、`scripts/patches.py`     | 启动时在内存中应用，不修改游戏文件                                                                 |
| 启动器   | `launcher/gow3_launcher_win.py`                                   | 设置、补丁、模组、按键与开始游戏按钮                                                               |

更多文档：[模组](docs/MODS.md)、[帧图与放大器](docs/upscaler.md)、[GPU 并行工作](docs/parallel_gpu.md)
与[路线图](docs/gow3/ROADMAP.md)。

## 致谢

本项目 fork 自 Supermedo 的 Windows 移植版，后者又源自 deadinside28 的原创项目：

- **deadinside28**：[bloodborne_pc](https://github.com/deadinside28/bloodborne_pc)，最初的原生移植。
- **Supermedo**（Mohammed Albarghouthi）：[bloodborne_pc for Windows](https://github.com/Supermedo/bloodborne_pc)，
  本项目所基于的分支。
- **cuesta4**：[专注于 God of War III Remastered 的 shadPS4 分支](https://github.com/cuesta4/shadPS4)
  （分支 `eltutz`）。本移植使用了其中的 GNM 混合重写、深度/模板状态与图像别名修复。
- **shadPS4 团队**：[shadPS4](https://github.com/shadps4-emu/shadPS4) 渲染器。

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
[MSYS2](https://www.msys2.org)、[LLVM](https://llvm.org) 以及 [Pillow](https://python-pillow.org)。

## 许可证

GNU GPL v2 或更高版本（见 [LICENSE](LICENSE)）。第三方组件保留各自的许可证。