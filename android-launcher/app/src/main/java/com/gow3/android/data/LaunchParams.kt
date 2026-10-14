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
)

internal data class LaunchOption(val value: String, val label: String)

/**
 * launcher 编辑的启动参数，由 Android 运行时消费的 gow3.ini 键支撑。
 * 默认值与取值域遵循 `SettingsDialog.java` 的常量（UPSCALER_VALUES/FPS_VALUES …）
 * 以及 `gpu/shim/gow3_graphics.h` / `gpu/shim/gow3_settings.cpp` 中的运行时解析器；
 * 当两者不一致时（fps_limit：Java 前端提供 40/45，但 `ParseFpsLimit` 把它们映射为 0）
 * 以运行时取值域为准，确保每个选项都实际生效。
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
            defaultValue = "native",
        ),
        LaunchParam(
            key = "display_mode", label = "Display mode", group = "DISPLAY",
            options = listOf(
                LaunchOption("windowed", "Windowed"),
                LaunchOption("borderless", "Borderless"),
                LaunchOption("fullscreen", "Fullscreen"),
            ),
            defaultValue = "windowed",
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
            defaultValue = "0",
        ),
        LaunchParam(
            key = "engine_fps", label = "Engine FPS", group = "PERFORMANCE",
            options = listOf(
                LaunchOption("60", "60 (the game's own)"),
                LaunchOption("120", "120"),
                LaunchOption("240", "240"),
            ),
            defaultValue = "120",
        ),
        LaunchParam(
            key = "vsync", label = "VSync", group = "PERFORMANCE",
            options = listOf(
                LaunchOption("0", "OFF"),
                LaunchOption("1", "ON"),
            ),
            defaultValue = "1",
        ),
        LaunchParam(
            key = "frames_queued", label = "Frames queued", group = "PERFORMANCE",
            options = listOf(
                LaunchOption("1", "1 (lowest latency)"),
                LaunchOption("2", "2"),
                LaunchOption("0", "Unlimited"),
            ),
            defaultValue = "1",
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
