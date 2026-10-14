package com.gow3.android.data

/**
 * 一个可编辑的启动参数：其 gow3.ini 键、显示标签、分组以及 Android 运行时
 * 实际消费的取值域。`defaultValue` 是键缺失时 UI 显示的值。
 */
internal data class LaunchParam(
    val key: String,
    val label: String,
    val group: String,
    val options: List<LaunchOption>,
    val defaultValue: String,
    val description: String? = null,
)

internal data class LaunchOption(val value: String, val label: String)

/**
 * launcher 编辑的启动参数，由 Android 运行时消费的 gow3.ini 键支撑。
 * 默认值与取值域遵循 `SettingsDialog.java` 的常量（UPSCALER_VALUES/FPS_VALUES …）
 * 以及 `gpu/shim/gow3_graphics.h` / `gpu/shim/gow3_settings.cpp` 中的运行时解析器；
 * 当两者不一致时（fps_limit：Java 前端提供 40/45，但 `ParseFpsLimit` 把它们映射为 0）
 * 以运行时取值域为准，确保每个选项都实际生效。
 *
 * 各参数的默认值均为移动端优化值（render_resolution=720p、fps_limit=60、
 * engine_fps=60；display_mode 已从可编辑项移除，固定 fullscreen）。这些默认值
 * MUST 与 `tools/android/config/gow3-default.ini`（首启落盘为 gow3.ini 的基准）
 * 保持一致，避免 UI 展示默认与实际运行默认漂移。
 */
internal object LaunchParams {
    val all: List<LaunchParam> = listOf(
        LaunchParam(
            key = "upscaler", label = "Upscaler", group = "DISPLAY",
            options = listOf(
                LaunchOption("off", "Off (render at 1280×720)"),
                LaunchOption("fsr3", "FSR 3.1"),
            ),
            defaultValue = "fsr3",
            description = "Renders the game at a lower internal resolution and then uses FSR 3.1 to scale the image up to your screen, so you get more performance while keeping a sharp picture. Off renders at a fixed 1280×720.",
        ),
        LaunchParam(
            key = "render_resolution", label = "Resolution", group = "DISPLAY",
            options = listOf(
                LaunchOption("native", "Native"),
                LaunchOption("480p", "480p"),
                LaunchOption("720p", "720p"),
                LaunchOption("800p", "800p"),
                LaunchOption("1440p", "1440p"),
                LaunchOption("1800p", "1800p"),
                LaunchOption("4K", "4K"),
            ),
            // 移动端优化默认值：720p 内部分辨率 + FSR 升采样，手机/平板上更流畅省电。
            // 与 tools/android/config/gow3-default.ini 保持一致。
            defaultValue = "720p",
        ),
        LaunchParam(
            key = "fps_limit", label = "FPS limit", group = "PERFORMANCE",
            options = listOf(
                LaunchOption("30", "30"),
                LaunchOption("60", "60"),
                LaunchOption("120", "120"),
                LaunchOption("240", "240"),
                LaunchOption("0", "Unlimited"),
            ),
            // 移动端优化默认值：以 60 封顶，避免无限帧率导致发热与耗电。
            // 与 tools/android/config/gow3-default.ini 保持一致。
            defaultValue = "60",
        ),
        LaunchParam(
            key = "engine_fps", label = "Engine FPS", group = "PERFORMANCE",
            options = listOf(
                LaunchOption("60", "60 (the game's own)"),
                LaunchOption("120", "120"),
                LaunchOption("240", "240"),
            ),
            // 移动端优化默认值：游戏原生引擎速度，最稳定、最省电。
            // 与 tools/android/config/gow3-default.ini 保持一致。
            defaultValue = "60",
            description = "How fast the game engine itself updates the game world, separate from the display. 60 is the game's original speed; higher values make motion smoother but use more power.",
        ),
        LaunchParam(
            key = "vsync", label = "VSync", group = "PERFORMANCE",
            options = listOf(
                LaunchOption("0", "OFF"),
                LaunchOption("1", "ON"),
            ),
            defaultValue = "1",
            description = "Syncs the game's frames with your screen's refresh rate to stop visual tearing. Leave it ON unless you see tearing or stutter.",
        ),
        LaunchParam(
            key = "frames_queued", label = "Frames queued", group = "PERFORMANCE",
            options = listOf(
                LaunchOption("1", "1 (lowest latency)"),
                LaunchOption("2", "2"),
                LaunchOption("0", "Unlimited"),
            ),
            defaultValue = "1",
            description = "How many frames the game prepares ahead of what the screen shows. Lower numbers cut input lag; higher numbers can improve smoothness on weaker hardware.",
        ),
        LaunchParam(
            key = "skip_network_choice", label = "Offline start", group = "STARTUP",
            options = listOf(
                LaunchOption("0", "OFF"),
                LaunchOption("1", "ON"),
            ),
            defaultValue = "1",
        ),
        LaunchParam(
            key = "skip_intro", label = "Skip intros", group = "STARTUP",
            options = listOf(
                LaunchOption("0", "OFF"),
                LaunchOption("1", "ON"),
            ),
            defaultValue = "1",
        ),
        LaunchParam(
            key = "debug_camera", label = "Free camera", group = "STARTUP",
            options = listOf(
                LaunchOption("0", "OFF"),
                LaunchOption("1", "ON"),
            ),
            defaultValue = "0",
            description = "Lets you move the camera freely around the world for exploring or testing, instead of following the gameplay camera. Meant for experimentation, not normal play.",
        ),
    )

    /** 参数的当前原始值；缺失或为空时返回其默认值。 */
    fun valueOf(ini: Map<String, String>, param: LaunchParam): String {
        val v = ini[param.key]?.trim()
        return if (v.isNullOrEmpty()) param.defaultValue else v
    }

    /** 当前值的人类可读标签；未知值回退到原始值。 */
    fun labelOf(ini: Map<String, String>, param: LaunchParam): String {
        val v = valueOf(ini, param)
        return param.options.firstOrNull { it.value == v }?.label ?: v
    }
}
