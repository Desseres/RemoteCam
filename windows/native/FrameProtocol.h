#pragma once
#include <atomic>
#include <thread>
#include <mutex>
#include <string>
#include <vector>
#include <chrono>

namespace RemoteCam {
    constexpr unsigned Width = 1920, Height = 1080;
    constexpr unsigned FrameBytes = Width * Height * 3 / 2;
    constexpr unsigned Magic = 0x4D414352; // RCAM, little endian
    struct FrameHeader {
        unsigned magic, version, length, width, height, reserved;
    };
    static_assert(sizeof(FrameHeader) == 24);
}

// RemoteCam activation attribute: unique local pipe for this desktop session.
inline constexpr GUID REMOTECAM_PIPE_NAME =
{0xee0169d7,0x425e,0x4f01,{0xad,0x27,0x7b,0xc3,0x28,0x7a,0x90,0x25}};
