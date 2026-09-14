#pragma once

// Frames live only in RAM. The Frame Server reads through a local, ACL-protected
// pipe; it never opens the network or loads any third-party decoder.
class SimpleFrameGenerator {
public:
    ~SimpleFrameGenerator();
    HRESULT Initialize(IMFMediaType* type, const std::wstring& pipeName);
    HRESULT CreateFrame(BYTE* output, DWORD length, LONG pitch);
private:
    void Receive();
    bool ReadExact(HANDLE pipe, void* data, DWORD size);
    std::wstring m_pipeName;
    wil::unique_handle m_stop;
    std::thread m_thread;
    std::mutex m_mutex;
    std::vector<BYTE> m_frame;
    ULONGLONG m_received = 0;
    std::chrono::steady_clock::time_point m_next{};
};
