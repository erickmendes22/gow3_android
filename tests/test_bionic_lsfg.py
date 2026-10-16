"""Exercise the native LSFG state reader and live file replacement without a GPU."""
from paths import ROOT
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


class BionicLsfgState(unittest.TestCase):
    def test_native_state_dll_precedence_and_live_updates(self):
        compiler = shutil.which('g++')
        if not compiler:
            self.skipTest('g++ unavailable')
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            source = directory / 'state_test.cpp'
            source.write_text(r'''
#include "gow3_lsfg_state.h"
#include <cassert>
#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <thread>
namespace fs = std::filesystem;
static void write(const fs::path& p,const char* value) { std::ofstream(p) << value; }
int main(int argc,char** argv) {
    const fs::path root=argv[1], state=root/"private/lsfg.state";
    fs::create_directories(root/"private");
    fs::create_directories(root/"games/CUSA01623");
    setenv("GOW3_LSFG_STATE_FILE",state.c_str(),1);
    setenv("GOW3_LSFG_MULTIPLIER","3",1);
    setenv("GOW3_LSFG_FLOW_SCALE","0.5",1);
    setenv("GOW3_LSFG_PERFORMANCE","0",1);
    auto initial=Gow3LsfgState::Initial();
    assert(initial.multiplier==3 && initial.flow==0.5f && !initial.performance);
    write(state,"multiplier=4");
    assert(!Gow3LsfgState::Read(state.c_str(),initial)); // partial writes cannot apply
    write(state,"multiplier=2\nflow=0.25\nperformance=1\n");
    auto on=Gow3LsfgState::Initial();
    assert(on.multiplier==2 && on.flow==0.25f && on.performance);
    Gow3LsfgState::Watcher watcher(on);
    auto next=on;
    assert(!watcher.Poll(next));
    write(root/"private/new.state","multiplier=4\nflow=0.75\nperformance=0\n");
    fs::rename(root/"private/new.state",state);
    assert(!watcher.Poll(next)); // rate-limited stat
    std::this_thread::sleep_for(std::chrono::milliseconds(275));
    assert(watcher.Poll(next) && next.multiplier==4 && next.flow==0.75f && !next.performance);
    assert(Gow3LsfgState::FindDll().empty());
    write(root/"private/Lossless.dll","private");
    assert(Gow3LsfgState::FindDll()==(root/"private/Lossless.dll").string());
    const auto game=(root/"games/CUSA01623").string()+"/";
    setenv("GOW3_GAME_DIR",game.c_str(),1);
    write(root/"games/Lossless.dll","beside game");
    assert(Gow3LsfgState::FindDll()==(root/"games/Lossless.dll").string());
    write(root/"override.dll","override");
    setenv("GOW3_LSFG_DLL",(root/"override.dll").c_str(),1);
    assert(Gow3LsfgState::FindDll()==(root/"override.dll").string());
    write(state,"multiplier=0\n");
    assert(!Gow3LsfgState::Initial().On());
}
''')
            executable = directory / 'state-test'
            subprocess.run([compiler, '-std=c++23', '-D__ANDROID__', '-pthread',
                            '-I'+str(ROOT/'gpu/shim'), '-I'+str(ROOT/'gpu/shadps4'),
                            str(source), str(ROOT/'gpu/shim/gow3_lsfg_state.cpp'),
                            '-o', str(executable)], check=True, capture_output=True, text=True)
            subprocess.run([str(executable), str(directory)], check=True,
                           capture_output=True, text=True)
