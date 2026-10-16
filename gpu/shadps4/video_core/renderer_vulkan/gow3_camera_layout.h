// SPDX-License-Identifier: GPL-2.0-or-later
// CUSA01715: discover camera transforms from bound guest vertex constants.
// Do not assume Bloodborne's 864-byte buffer layout or hardcoded offsets.
#pragma once
#include <algorithm>
#include <array>
#include <cmath>
#include <cstddef>

namespace Gow3CameraLayout {
using Affine = std::array<float, 12>;
using Projection = std::array<float, 4>;
struct Pose {
    Affine view{}, inverse{};
    unsigned view_offset = 0, inverse_offset = 0;
    bool valid = false;
};
struct Lens {
    Projection projection{};
    unsigned offset = 0;
    bool valid = false;
};
inline bool Near(float a, float b, float eps = 0.025f) {
    return std::isfinite(a) && std::isfinite(b) && std::abs(a - b) <= eps;
}
inline float Matrix(const float* values, size_t start, int r, int c, bool column) {
    return values[start + (column ? c * 4 + r : r * 4 + c)];
}
inline bool ReadAffine(const float* values, size_t count, size_t start, bool column,
                       Affine& out) {
    if (start + 16 > count) return false;
    for (int c = 0; c < 3; ++c)
        if (!Near(Matrix(values, start, 3, c, column), 0.f, 0.002f)) return false;
    if (!Near(Matrix(values, start, 3, 3, column), 1.f, 0.002f)) return false;
    for (int r = 0; r < 3; ++r) {
        for (int c = 0; c < 4; ++c) {
            const float v = Matrix(values, start, r, c, column);
            if (!std::isfinite(v) || std::abs(v) > (c == 3 ? 1000000.f : 1.5f))
                return false;
            out[r * 4 + c] = v;
        }
    }
    // Camera view and inverse matrices have rigid orthonormal axes.
    for (int r = 0; r < 3; ++r) {
        float len = 0.f;
        for (int c = 0; c < 3; ++c) len += out[r * 4 + c] * out[r * 4 + c];
        if (!Near(len, 1.f, 0.06f)) return false;
        for (int other = 0; other < r; ++other) {
            float dot = 0.f;
            for (int c = 0; c < 3; ++c)
                dot += out[r * 4 + c] * out[other * 4 + c];
            if (!Near(dot, 0.f, 0.045f)) return false;
        }
    }
    return true;
}
inline bool InversePair(const Affine& a, const Affine& b) {
    float error = 0.f;
    for (int r = 0; r < 3; ++r) {
        for (int c = 0; c < 4; ++c) {
            float v = c == 3 ? a[r * 4 + 3] : 0.f;
            for (int k = 0; k < 3; ++k)
                v += a[r * 4 + k] * b[k * 4 + c];
            const float expected = c == r ? 1.f : 0.f;
            error = std::max(error, std::abs(v - expected));
        }
    }
    return error < 0.025f;
}
inline Pose FindPose(const float* data, size_t count) {
    Pose out{};
    if (!data || count < 32) return out;
    count = std::min(count, size_t(1024));
    for (size_t i = 0; i + 16 <= count; i += 4) {
        for (int format = 0; format < 2; ++format) {
            Affine a{};
            if (!ReadAffine(data, count, i, format != 0, a)) continue;
            const size_t end = std::min(count, i + 272);
            for (size_t j = i + 16; j + 16 <= end; j += 4) {
                for (int format2 = 0; format2 < 2; ++format2) {
                    Affine b{};
                    if (!ReadAffine(data, count, j, format2 != 0, b)) continue;
                    if (InversePair(a, b) && InversePair(b, a)) {
                        out.view = a; out.inverse = b;
                        out.view_offset = unsigned(i);
                        out.inverse_offset = unsigned(j);
                        out.valid = true;
                        return out;
                    }
                }
            }
        }
    }
    return out;
}
inline Lens FindProjection(const float* data, size_t count) {
    Lens out{};
    if (!data || count < 16) return out;
    count = std::min(count, size_t(1024));
    for (size_t i = 0; i + 16 <= count; i += 4) {
        for (int layout = 0; layout < 2; ++layout) {
            const bool col = layout != 0;
            const auto m = [&](int r, int c) { return Matrix(data, i, r, c, col); };
            const float sx = m(0,0), sy = m(1,1), z = m(2,2), dz = m(2,3);
            if (!(sx > 0.2f && sx < 12.f && sy > 0.2f && sy < 12.f &&
                  z > 0.5f && z < 1.5f && dz < -0.00001f && dz > -1000.f))
                continue;
            if (!Near(m(3,2), 1.f, .001f) || !Near(m(3,3), 0.f, .001f) ||
                !Near(m(0,1), 0.f, .001f) || !Near(m(1,0), 0.f, .001f) ||
                !Near(m(0,2), 0.f, .001f) || !Near(m(1,2), 0.f, .001f) ||
                !Near(m(3,0), 0.f, .001f) || !Near(m(3,1), 0.f, .001f)) continue;
            const float near_plane = -dz / z;
            if (!(near_plane > 0.0001f && near_plane < 1000.f)) continue;
            out.projection = {sx, sy, z, dz};
            out.offset = unsigned(i);
            out.valid = true;
            return out;
        }
    }
    return out;
}
inline bool SimilarPose(const Affine& a, const Affine& b, float threshold=.015f) {
    for (int i = 0; i < 12; ++i)
        if (std::abs(a[i] - b[i]) > threshold) return false;
    return true;
}
} // namespace Gow3CameraLayout
