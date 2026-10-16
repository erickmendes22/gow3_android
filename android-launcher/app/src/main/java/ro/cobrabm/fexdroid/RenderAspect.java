package ro.cobrabm.fexdroid;

/** Picture aspect used to fit the Android surface; internal rendering stays in the game patch. */
final class RenderAspect {
    private RenderAspect() {}

    static int[] of(String resolution) {
        if ("840x360".equals(resolution) || "1120x480".equals(resolution)
                || "1260x540".equals(resolution) || "1680x720".equals(resolution)) {
            return new int[]{7, 3};
        }
        return new int[]{16, 9};
    }

    static int[] fit(int width, int height, String resolution, boolean fullscreen) {
        if (fullscreen || width <= 0 || height <= 0) return new int[]{width, height};
        int[] aspect = of(resolution);
        if ((long)width * aspect[1] > (long)height * aspect[0]) {
            width = (int)((long)height * aspect[0] / aspect[1]);
        } else {
            height = (int)((long)width * aspect[1] / aspect[0]);
        }
        return new int[]{width, height};
    }
}
