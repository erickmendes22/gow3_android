# Android 移植（gow3_android）

把这份 PC 上的 God of War III 移植（Linux x86-64）搬到 Android ARM64 的方案与进度。
策略：**复用 Bloodborne-Android 的 Android 侧基础设施**（它是同谱系代码的 Android 落地），
把 gow3 的"原生执行 guest"换成 **FEXCore 的 JIT**——这是唯一需要新建的桥。

## 为什么能这么搬

三份代码同一谱系（`deadinside28/bloodborne_pc`）：

| 项目 | 平台 | guest CPU | 视频核心 |
|---|---|---|---|
| `bloodborne_pc` → `gow3_linux`（本仓库） | Linux/Windows x86-64 | **x86-64 原生执行** | shadPS4 |
| `Bloodborne-Android` | Android ARM64 | **FEXCore**（`libbbcpu.so`） | shadPS4 |
| `gow3_android`（本仓库） | Android ARM64 | **FEXCore**（`libgow3cpu.so`） | shadPS4 |

所以移植 = 换掉游戏特有层 + 把 guest 执行方式接到 FEXCore，其余（rootfs、桥、打包）
从 Bloodborne-Android 复用。

## 一、Host/guest 边界（本仓库已完成的核重构）

`src/guest_cpu.h` 定义主机与 guest 之间的全部交叉点；有两套实现，按主机架构自动选择：

- **x86-64 主机**：`src/runtime_cpu.c`（原生，guest 代码就是主机代码，跨界即普通函数调用）
- **ARM64 主机**：`cpu/guest_cpu_fex.cpp` + `cpu/host_call_arm64.S` → **libgow3cpu.so**（FEXCore JIT）

边界接口（`src/guest_cpu.h`）：
`guest_cpu_init / code / host_function / call / thread_stack / set_gs / abandon /
thread_end / return_address / stack_pointer / signal_regs / host_pc / handle_fault /
hook / tsc / tsc_frequency`。

已完成的重构（`src/`）：

| 文件 | 改动 |
|---|---|
| `probe.c` | 去掉 `#error x86-64`；入口/模块初始化/回调改走 `guest_cpu_call`；导入函数经 `guest_cpu_host_function` 包装；`guest_cpu_code` 登记镜像；fault/watchdog 用 `guest_cpu_signal_regs`；`enter_on_stack` 仅在原生路径；**作弊/血条/倍率 hook 暂只在 x86-64 生效**（它们直接写 x86-64 机器码，JIT 下需 `guest_cpu_hook`，后续再接） |
| `runtime.h` | `ABI` 改为仅在 `__x86_64__` 用 `sysv_abi` |
| `runtime.c` | 析构回调经 `guest_cpu_call` |
| `runtime_kernel.c` | `RDTSC` 经 `guest_cpu_tsc`；pthread once/keys 回调经 `guest_cpu_call` |
| `runtime_thread.c` | guest 线程入口经 `guest_cpu_call`；线程栈用 `guest_cpu_thread_stack`；GS/TCB 经 `guest_cpu_set_gs`；JIT 下额外分配 guest 线程栈 |
| `runtime_cpu.c`（新） | x86-64 原生实现（`GUEST_CPU_NATIVE`） |
| `cpu/`（新） | FEXCore 集成：`gow3cpu.cmake`、`fex_project.cmake`、`guest_cpu_fex.cpp`、`host_call_arm64.S` |

`src/` 中已无 x86-64 依赖（除 `compat_win.c`，Windows 专用，Linux/ARM 不编译）；
四个重构文件在本机通过 `cc -fsyntax-only`。

## 二、构建（ARM64 交叉）

入口：`tools/android/build/build_arm64.sh`（兼容入口：`tools/android/build_arm64.sh`），工具链：`tools/android/build/aarch64-toolchain.cmake`。
产物：`out/arm64/{gow3-probe, libgow3gpu.so, libgow3cpu.so, kgsl-chip-id}`。

依赖顺序（**前 3 步是外部输入，必须先行**）：

```bash
# 1. Debian trixie arm64 sysroot（交叉编译用头文件/库）
python3 tools/android/inputs/fetch_arm64_sysroot.py \
    --mirror https://mirrors.tuna.tsinghua.edu.cn/debian --jobs 8 \
    --vulkan-headers /opt/homebrew/opt/vulkan-headers

# 2. FEXCore 源码（FEX 主仓库 + 子模块）；也可以复用另一份本地 checkout
export GOW3_FEX_SOURCE=/path/to/Bloodborne-Android/third_party/FEX
git submodule update --init --recursive third_party/FEX

# 3. ARM64 miniz + 宿主 header-only 包（magic_enum / robin-map / VMA / Boost）
#    见 Bloodborne-Android/docs/ANDROID_THOR.md：miniz 交叉编译进 arm64-prefix，
#    header-only 包放 .local-deps/root/usr。

# 4. 构建
bash tools/android/build/build_arm64.sh
```

在 macOS Apple Silicon 上建议使用隔离入口：

```bash
bash tools/android/build/build_mac_arm64.sh
```

该 wrapper 只为本次 Android ARM64 构建设置 Homebrew LLVM/LLD、Boost 和本地
`Bloodborne-Android/third_party/FEX`；它不会修改 Linux 的 `build.sh`、shell.nix、CI
环境或持久化 shell 环境。需要其他 checkout 或依赖位置时，仍可显式设置
`GOW3_FEX_SOURCE`、`GOW3_BOOST_DIR`、`GOW3_LLVM_PREFIX` 和 `GOW3_LLD_PREFIX`。

`build_arm64.sh` 依次：编 FEX + `libgow3cpu.so` → 编 `libgow3gpu.so`（shadPS4 视频核心，
用 sysroot 的 fmt/Zydis/miniz 和宿主的 magic_enum/robin-map/VMA/Boost）→ 交叉编
`src/probe.c src/runtime*.c src/vulkan_smoke.c` + LibAtrac9 → 链接出 `gow3-probe`。
解释器按 rootfs 内绝对路径固定（Android 没有 `/lib`）。

## 三、Android 侧（沿用 Bloodborne-Android/fexdroid 架构，Gow3 命名）

当前 release 使用：

- `android-launcher/` —— Kotlin/Compose 启动器 App（主页 launcher + `ro.cobrabm.fexdroid.GameActivity` 游戏 Surface、bridge、runtime 生命周期、on-screen 控制器）
- `tools/android/build/` —— ARM64/FEX、Turnip、LSFG 和 native bundle 构建
- `tools/android/runtime/` —— runtime archive、manifest 和 ELF/archive 校验
- `tools/android/apk/` —— debug/release APK 构建与校验
- `tools/android/device/` —— `run-gow3.sh`、launch、deploy
- `tools/android/inputs/` —— rootfs/sysroot/Steam 输入和 bridge 提取
- `tools/android/config/` —— runtime 默认配置、包清单和 controller database
- 根目录 `tools/android/*.sh|*.py` —— 对旧命令、CI 和文档的兼容 wrapper
- `tools/android/mesa-patches/`、`mesa-patches/gen8/` —— Mesa/Turnip 的可审计 patch 输入
- `tools/android/lsfg-patches/` —— LSFG v1.0.0 的 Turnip/KGSL patch 输入
- `packaging/`（若存在）—— 打包脚本参考

**待做的改名**（把 BB 的客户端改成 gow3）：

| 原名 | 新名 |
|---|---|
| `bb-probe` | `gow3-probe` |
| `bbport/`（设备上的移植包目录） | `gow3port/` |
| `libbbgpu.so` / `libbbcpu.so` | `libgow3gpu.so` / `libgow3cpu.so` |
| `BB_*` 环境变量 | `GOW3_*`（gow3 侧本来就是 `GOW3_*`） |
| `run-thor.sh` | `run-gow3.sh` |
| 应用包名 `com.ducvd89.bloodborne` | 新的 gow3 包名（iOS/Java 包名规则） |
| `thor.ini` / `bbport.ini` | `gow3.ini`（gow3 侧就是 `gow3.ini`） |

### 画面尺寸：窗口不得超过显示尺寸

送显链路上有四个尺寸，原本互不一致：guest 向 `gow3-gpu` 请求 **1920×1080**（`src/probe.c` 里
写死）、`Xvfb` 屏幕是 **1280×720**、display bridge 把 SurfaceView 的 buffer 强制设成帧缓冲
尺寸、SurfaceFlinger 再把 buffer 缩放到 SurfaceView 区域。

关键约束：**bridge 只做 1:1 裁剪拷贝，不做任何缩放或信箱填充**。它把每帧按
`min(源, 目标)` 从左上角逐像素拷进 buffer，于是 1920×1080 的帧只剩左上角 1280×720，右侧和
底部约 1/3 在 SurfaceFlinger 看到之前就已经丢了 —— Android 侧再怎么缩放 SurfaceView 也救不
回来。这正是「游戏画面比屏幕更大、菜单文字被切掉」的成因。

因此窗口尺寸改为**不超过当前显示尺寸**（`Gow3Graphics::ResolveWindowSize`，
`gpu/shim/gow3_graphics.h`）：请求尺寸放得下就保持不变（桌面 ≥1920×1080 显示零变化），放不
下就退到显示尺寸。显示尺寸由 SDL 的 `SDL_GetDesktopDisplayMode` 查询，在
`SDL_InitSubSystem(SDL_INIT_VIDEO)` 之后、创建窗口之前取得；查不到或尺寸非法时回退到请求
尺寸（`src/probe.c` 传下来的 1920×1080）并打日志。

不改动的东西：`Xvfb` 启动参数、`runtime-manifest.json` 契约、bridge 二进制、触控映射
（`GameActivity` 仍按 1280×720 虚拟空间映射，X 屏幕尺寸没变，所以触控天然对齐）、以及任何
`gow3.ini` 键。`render_resolution` / `output_res` 仍只管 guest 内部分辨率与 upscaler 输出，不
被重新解释为窗口尺寸。

### 鼠标光标隐藏（overlay_mouse）

**问题**：`overlay_mouse=0`（默认）后，on-screen 手柄浮层的空白触摸不再映射为鼠标移动/左键，
但 Xvfb 仍把 X11 光标合成进 1280×720 共享内存帧缓冲，display bridge 整帧呈现到 Android
Surface —— 玩家一进入游戏画面就会看到一个停在原处、动不了的鼠标光标，对主机游戏既无用又
干扰视线。

**方案**（与同谱系 Bloodborne-Android 一致）：在 `gpu/shim/window.cpp` 用 SDL3 官方 API 隐藏
游戏窗口上的 OS 光标，不新增独立 X11 工具二进制、不改 payload/runtime manifest。

- `WindowSDL::UpdateCursor()` 在 `PollEvents()` 末尾每帧调用，用 `SDL_HideCursor()` /
  `SDL_ShowCursor()` 隐藏/显示窗口光标，`cursor_hidden` 标志去抖（只在状态翻转时调 SDL API）；
  每帧检查可自愈 SDL 在窗口映射/焦点变化时的隐式光标重设。参考实现：
  `Bloodborne-Android/gpu/shim/window.cpp` 的 `UpdateCursor()`。
- 决策点收敛在 launcher：`GameActivity` 读 `overlay_mouse` 一次（`mouseEnabled` 字段），
  `overlay_mouse != 1` 时给游戏进程 env 设 `GOW3_HIDE_CURSOR=1`（与 `GOW3_GAME_DIR` 等既有
  `GOW3_*` 进程契约一致，`run-gow3.sh` 的 `exec` 自然继承）；shim 在窗口构造时读该 env，不重复
  解析 `gow3.ini`。`overlay_mouse=1` 时保留可见光标并跟随触控（触控板用途）。
- 不触碰：`src/`、`cpu/`、bridge JNI 库、`run-gow3.sh` 逻辑、桌面/Linux 路径。

**为什么不用独立 X11 工具隐藏根光标**（如 `gow3-hidecursor`）——主要取舍如下：

- **优点**（方案）：改动最小（只动 `window.cpp` + launcher env）；官方 SDL API，X11/Wayland/
  Windows 天然一致，不把 Xlib 引进 GPU 层；真机验证过；SDL 隐式重设光标时每帧自愈。
- **缺点/边界**（方案）：只隐藏窗口光标，依赖「窗口铺满 1280×720 显示」的隐含假设——若未来窗口
  缩小/非全屏，根光标会从 letterbox 区域露出（当前窗口尺寸受上一节 `ResolveWindowSize` 约束，
  窗口==显示，不触发）；启动黑屏阶段（窗口映射前）的根光标不处理，只靠 Android 状态文字盖住
  （极短暂）；行为绑定 SDL3 版本，个别后端失效时静默降级为「光标可见」。
- **否决的替代**（独立 `gow3-hidecursor` Xlib 工具）：优点是能从第一帧前覆盖根光标、与 SDL 无关；
  但要多一个 ARM64 二进制（build/bundle/manifest/payload 全链路）、动态链接 rootfs `libX11` 有
  运行时缺库风险、只在会话启动跑一次（运行中新建带光标的窗口会失效）、且与已证实的
  Bloodborne 路径不一致，属于重复发明。

本方案对应 OpenSpec change：`openspec/changes/hide-x11-cursor-when-mouse-off/`。

## 四、Android runtime 的完整脚本构建链

完整 game-free runtime 不依赖手工复制 Gen8 Turnip 或 ARM64 bundle。首次拿到项目后，
先从官方 fexdroid legacy APK 初始化 ARM64 FEX rootfs，再按固定顺序运行生成步骤：

```bash
# 初始化：下载 fexdroid APK 中的 assets/rootfs-arm64.tar，迁移 ELF loader
# 路径，并解压到项目目录
python3 tools/android/inputs/fetch_rootfs.py

# 编译依赖：Debian ARM64 sysroot、host headers/libraries 和本地复用组件
bash tools/android/build/build_android_runtime.sh

# APK packaging/signing (after runtime validation)
bash tools/android/apk/build_kotlin_launcher.sh

# 开发 APK：不构建 Turnip、LSFG、Steam libraries；复用 `.local-deps/android/` 中已有产物
bash tools/android/debug/build_debug_apk.sh

# 生成结果
# out/arm64/bundle       Gow3 native ARM64 bundle
# out/arm64/turnip       Adreno 6xx/7xx Turnip
# out/arm64/turnip-gen8  Adreno 8xx Turnip
# out/arm64/lsfg         LSFG v1.0.0 layer
# out/release/runtime.tar.gz  rootfs + gow3port + home，禁止游戏内容
```

wrapper 内部的依赖顺序是：

```text
build/build_arm64.sh
  → build/build_arm64_bundle.py
  → build/build_turnip.sh main
  → build/build_turnip.sh gen8
  → build/build_lsfg.sh
  → runtime/build_release_runtime.py
```

`mesa-patches`、`mesa-patches/gen8` 和 `lsfg-patches` 是源码 patch 输入，不是生成物；
fexdroid APK、迁移后的 rootfs、Mesa/LSFG checkout、sysroot、venv、bridge 和生成的
ARM64 libraries 属于初始化缓存、`.local-deps/` 或 `out/`。`Lossless.dll`、游戏 dump、账号和玩家 shader 不参与
构建，也不会进入 APK。最终应用私有目录仍是 `rootfs/`、`gow3port/`、`home/`，不会
生成 `gow3port/rootfs`。

## 五、当前进度

**ARM64 原生侧已全部构建成功**（`bash tools/android/build/build_arm64.sh`）：

| 产物 | 说明 | 状态 |
|---|---|---|
| `out/fex/libgow3cpu.so` | FEXCore + `guest_cpu.h` 边界（16 个符号全导出） | ✅ aarch64 |
| `out/gpu/libgow3gpu.so` | shadPS4 视频核心 + 着色器重编译器 + FSR + imgui | ✅ aarch64（77 MB） |
| `out/arm64/gow3-probe` | 加载器，解释器已指向 rootfs 内 loader，RUNPATH `$ORIGIN/../lib` | ✅ aarch64 |
| `out/arm64/kgsl-chip-id` | Turnip 变体探测 | ✅ aarch64 |

- [x] `src/` 重构到 `guest_cpu` 边界
- [x] `cpu/` FEX 集成（`gow3cpu`）
- [x] 复制 BB 的 Android 客户端与工具脚本
- [x] FEXCore 源码 + 全部子模块
- [x] ARM64 sysroot（复用 GamePort 的 arm64 sysroot + 补差集，314 包）
- [x] 宿主 header-only 依赖：Boost 1.87 / tsl-robin-map / VMA / glslang（GitHub 直取）
- [x] **三个 ARM64 产物全部构建成功**
- [x] 客户端改名（`bbport`→`gow3port`、`bb-probe`→`gow3-probe`、包名 `com.gow3.android`、`run-gow3.sh`）
- [x] **payload 打包**（`tools/android/runtime/build_release_runtime.py`：唯一 release assembler；`build_payload.py` 仅为兼容入口）
- [x] **APK 打包**（`android-launcher/` Gradle + `tools/android/apk/build_kotlin_launcher.sh`；`build_release_apk.sh`/`build_debug_apk.sh` 为 release/debug 入口）
- [ ] 真机验证（见下）

## 六、构建产物

| 产物 | 说明 |
|---|---|
| `out/release/runtime.tar.gz` | payload（235 MB）：`rootfs/`（fexdroid arm64 用户态 + 补齐的 SDL3/ffmpeg/fmt/Zydis 等）+ `gow3port/`（probe、GPU/CPU 库、scripts、patches、run-gow3.sh、ini、controller db） |
| `out/release/God of War III-<version>.apk` | Kotlin launcher 已签名 release APK：`com.gow3.android` / arm64-v8a；`assets/runtime.payload` + `runtime.sha256`，`lib/arm64-v8a/{libfxdisplay.so,libfxio.so}` |

Release 一条命令：`bash tools/android/apk/build_release_apk.sh`（`build_release_runtime.py` 由脚本内部调用，再走 `android-launcher` Gradle `assembleRelease` + apksigner 签名）。
Debug 一条命令：`bash tools/android/debug/build_debug_apk.sh`。Debug payload 不执行 Turnip、LSFG 或 Steam libraries 的构建，而是复用 `.local-deps/android/` 中的现成产物；如果存在 `steam-libs/jars.txt`，还会启用真实 JavaSteam UI，否则使用 debug stub。

Kotlin/Compose frontend 位于 `android-launcher/`，使用 `com.gow3.android`，主页 `MainActivity` 只提供启动按钮，游戏 Surface、bridge 和 runtime 生命周期在 `ro.cobrabm.fexdroid.GameActivity`；旧 Java frontend（`tools/android/frontend/`）已删除。构建 Kotlin APK：

```bash
bash tools/android/apk/build_kotlin_launcher.sh
```

该入口只消费 `out/android-debug/assets/runtime.payload`、`runtime.sha256` 和仓库内 `arm64-v8a` bridge（`build_debug_apk.sh` 负责把 debug payload 暂存到该目录），不把生成的 runtime 或玩家游戏内容提交到仓库；最终 APK 仍需通过 application id、ABI、payload digest、runtime manifest 和 game-free 校验。

### GitHub Actions release 构建

`.github/workflows/android.yml` 在 pull request 上只执行 Android 相关源码检查；受信任的
branch/tag 或手动 workflow 才执行 ARM64、Turnip、LSFG、runtime payload 和 release APK
构建。仓库需要配置以下四个 GitHub Secrets：

- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
- `ANDROID_KEYSTORE_BASE64`

rootfs 由 `tools/android/inputs/fetch_rootfs.py` 从固定版本的官方 fexdroid legacy APK
下载并从 `assets/rootfs-arm64.tar` 初始化；它不是普通 Debian 镜像，也不是用户手工准备的
外部 rootfs 包。ARM64 native、Turnip 和 LSFG 仍由仓库内脚本在 runner 上构建。keystore
只写入 runner 临时目录，最终 artifact 只包含签名 APK 和 SHA-256 文件。

## 七、真机验证的前置

- **游戏转储**：需要你自己的 **CUSA01623 版本 01.02**（`eboot.bin` / `sce_module` / `sce_sys`）。
  放进 `gow3port/game/`（或设 `GOW3_GAME_DIR`）。
- **不装游戏也能验 GPU**：`run-gow3.sh vulkan` 跑 Vulkan 自检；`run-gow3.sh check` 用
  `--check-imports` 列出游戏用到但尚未实现的 PS4 导入（需要已准备好的镜像）。
- **设备**：arm64 + 4 KB 页 + Adreno。当前测试机是 MIUI，`adb install` 报
  `INSTALL_FAILED_USER_RESTRICTED` —— 需要在开发者选项里打开「USB 安装 / Install via USB」。

### 为 ARM64 所做的平台移植改动

| 文件 | 改动 |
|---|---|
| `src/probe.c` | 入口/模块初始化/导入/回调走 `guest_cpu_*`；x86-64 机器码 hook 安装函数按 `GUEST_CPU_NATIVE` 门控 |
| `src/runtime.c` / `runtime_kernel.c` | 析构与回调经 `guest_cpu_call`；RDTSC 经 `guest_cpu_tsc`；去掉 `<x86intrin.h>` |
| `src/runtime_thread.c` | guest 线程栈/入口经边界；去掉 `<asm/prctl.h>`（`set_gs` 走 `guest_cpu_set_gs`） |
| `src/runtime.h` | `ABI` 仅在 `__x86_64__` 用 `sysv_abi` |
| `gpu/shadps4/common/arch.h` | 新增 `COMMON_CPU_RELAX()`（x86 `pause` / arm64 `yield`） |
| `gpu/shadps4/common/signal_context.cpp` | 用带 ARM64 支持的版本（`uc_mcontext.pc` / ESR 解码） |
| `gpu/shadps4/.../vk_{bind_helper,draw_pipe}.h`、`vk_{rasterizer,scheduler}.cpp` | 去掉直接 x86 内建，改用 `Common::FencedRDTSC()` / `COMMON_CPU_RELAX()` |
| `gpu/shadps4/.../flatten_extended_userdata_pass.cpp` | xbyak 头按架构门控（xbyak 是 x86 汇编器） |
| `gpu/shim/gow3_write_log.cpp` | 寄存器读取改 `guest_cpu_signal_regs`；TSC 走 `Common::FencedRDTSC()` |
| sysroot | 覆盖为 Vulkan-Headers **v1.4.363**（旧版缺 `eMesaKosmickrisp` 等） |

## 五、外部输入获取（本机的实际做法）

`conda-forge` 在当前代理下不可达，所以宿主 header-only 依赖直接从 GitHub 取：

```bash
# 1. arm64 sysroot：复用 GamePort-Android 的 .local-deps/android/sysroot，再用本仓库脚本补差集
python3 tools/android/inputs/fetch_arm64_sysroot.py # 已加 --mirror / 重试 / 索引缓存

# 2. FEXCore
git submodule update --init --recursive third_party/FEX   # 或按提交浅克隆

# 3. 宿主依赖（GitHub）
#    Boost 1.87（b2 headers 生成合并头 + 手写最小 BoostConfig.cmake）
#    tsl-robin-map、VulkanMemoryAllocator（cmake install 到 .local-deps/root/usr）
#    glslang（cmake -DENABLE_OPT=OFF 构建 glslangValidator 到同一前缀）

# 4. Vulkan-Headers v1.4.363 覆盖进 sysroot 的 usr/include/{vulkan,vk_video}

# 5. 构建
bash tools/android/build/build_arm64.sh
```
