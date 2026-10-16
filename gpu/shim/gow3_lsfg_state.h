// Adapted from ducvd89/Bloodborne-Android 856bd99b05ee088782e881b99fca7945110ea0ca.
// bbport: the live settings of Lossless Scaling frame generation (Bionic only).
//
// The app's "Frame generation" menu writes gow3port/lsfg.state (whole file, then a rename) and the
// presenter follows it while the game runs; run-bionic.sh writes the same file at start from
// gow3.ini and names it in GOW3_LSFG_STATE_FILE. The file is the one source of truth:
//
//     multiplier=3      0 or missing: off; 2, 3, 4: frames shown per game frame
//     flow=0.75         flow scale, 0.25 .. 1.0
//     performance=1     1: the light model, 0: the quality model
//
// Without GOW3_LSFG_STATE_FILE (a harness, a desktop run) the same three settings come from the
// environment once, at start: GOW3_LSFG_MULTIPLIER, GOW3_LSFG_FLOW_SCALE, GOW3_LSFG_PERFORMANCE.
#pragma once
#ifdef __ANDROID__
#include <chrono>
#include <string>
#include "common/types.h"

namespace Gow3LsfgState {

struct State {
    u32 multiplier = 0; ///< 0: off, else 2-4
    float flow = 1.0f; // Match the existing launcher/dialog defaults when the INI has no key.
    bool performance = false;

    bool On() const {
        return multiplier >= 2;
    }
    bool operator==(const State& other) const {
        return multiplier == other.multiplier && flow == other.flow && performance == other.performance;
    }
};

/// GOW3_LSFG_STATE_FILE, or null.
const char* FilePath();

/// Reads the file into `state` (keys it lacks keep their value). False when it is missing, cannot
/// be read, or has no valid multiplier line. Only complete lines count: a half-written last line
/// is ignored.
bool Read(const char* path, State& state);

/// The environment's settings (GOW3_LSFG_MULTIPLIER, GOW3_LSFG_FLOW_SCALE, GOW3_LSFG_PERFORMANCE).
State FromEnvironment();

/// The state at start: the file when there is one and it reads, else the environment.
State Initial();

/// The player's Lossless.dll: GOW3_LSFG_DLL when that file exists, else Lossless.dll beside the selected game folder, then gow3port/Lossless.dll next to
/// the state file (the same priority as the launcher).
/// Empty when there is none.
std::string FindDll();

/// Follows the file cheaply: at most one stat() every 250 ms, a read only when it changed.
class Watcher {
public:
    /// `initial`: the state the game started with (Initial()).
    explicit Watcher(const State& initial) : last{initial} {}

    /// True when the file was rewritten and reads: `state` is then what it says (equal to the
    /// last one when only the time changed: a retry the player asked for).
    bool Poll(State& state);

private:
    std::chrono::steady_clock::time_point last_check{};
    long long signature_time = 0;
    long long signature_size = -1;
    unsigned long long signature_inode = 0;
    bool seen = false;
    State last;
};

} // namespace Gow3LsfgState
#endif // __ANDROID__
