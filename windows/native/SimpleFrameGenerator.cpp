#include "pch.h"

SimpleFrameGenerator::~SimpleFrameGenerator() {
    if (m_stop) SetEvent(m_stop.get());
    if (m_thread.joinable()) m_thread.join();
}

HRESULT SimpleFrameGenerator::Initialize(IMFMediaType* type, const std::wstring& pipeName) try {
    GUID subtype{};
    UINT32 width{}, height{};
    RETURN_IF_FAILED(type->GetGUID(MF_MT_SUBTYPE, &subtype));
    RETURN_IF_FAILED(MFGetAttributeSize(type, MF_MT_FRAME_SIZE, &width, &height));
    RETURN_HR_IF(MF_E_INVALIDMEDIATYPE, subtype != MFVideoFormat_NV12 ||
        width != RemoteCam::Width || height != RemoteCam::Height);
    RETURN_HR_IF(E_INVALIDARG, pipeName.rfind(L"\\\\.\\pipe\\RemoteCam.", 0) != 0 || pipeName.size() > 200);
    if (m_thread.joinable()) return S_OK;
    m_pipeName = pipeName;
    m_frame.resize(RemoteCam::FrameBytes);
    m_stop.reset(CreateEventW(nullptr, TRUE, FALSE, nullptr));
    RETURN_LAST_ERROR_IF(!m_stop);
    m_thread = std::thread([this] { try { Receive(); } catch (...) { /* Output remains black on resource failure. */ } });
    return S_OK;
} CATCH_RETURN()

bool SimpleFrameGenerator::ReadExact(HANDLE pipe, void* data, DWORD size) {
    wil::unique_handle event(CreateEventW(nullptr, TRUE, FALSE, nullptr));
    if (!event) return false;
    BYTE* dst = static_cast<BYTE*>(data);
    while (size && WaitForSingleObject(m_stop.get(), 0) != WAIT_OBJECT_0) {
        OVERLAPPED ov{};
        ov.hEvent = event.get();
        ResetEvent(event.get());
        DWORD count = 0;
        if (!ReadFile(pipe, dst, size, &count, &ov)) {
            if (GetLastError() != ERROR_IO_PENDING) return false;
            HANDLE handles[] = {m_stop.get(), event.get()};
            DWORD result = WaitForMultipleObjects(2, handles, FALSE, 3000);
            if (result != WAIT_OBJECT_0 + 1) {
                CancelIoEx(pipe, &ov);
                GetOverlappedResult(pipe, &ov, &count, TRUE);
                return false;
            }
            if (!GetOverlappedResult(pipe, &ov, &count, FALSE)) return false;
        }
        if (!count) return false;
        dst += count;
        size -= count;
    }
    return size == 0;
}

void SimpleFrameGenerator::Receive() {
    std::vector<BYTE> incoming(RemoteCam::FrameBytes);
    while (WaitForSingleObject(m_stop.get(), 100) == WAIT_TIMEOUT) {
        wil::unique_hfile pipe(CreateFileW(m_pipeName.c_str(), GENERIC_READ, 0, nullptr,
            OPEN_EXISTING, FILE_FLAG_OVERLAPPED | SECURITY_SQOS_PRESENT | SECURITY_IDENTIFICATION, nullptr));
        if (!pipe || pipe.get() == INVALID_HANDLE_VALUE) continue;
        while (WaitForSingleObject(m_stop.get(), 0) != WAIT_OBJECT_0) {
            RemoteCam::FrameHeader header{};
            if (!ReadExact(pipe.get(), &header, sizeof(header))) break;
            if (header.magic != RemoteCam::Magic || header.version != 1 ||
                header.width != RemoteCam::Width || header.height != RemoteCam::Height ||
                header.length != RemoteCam::FrameBytes || header.reserved != 0) break;
            if (!ReadExact(pipe.get(), incoming.data(), RemoteCam::FrameBytes)) break;
            std::lock_guard<std::mutex> guard(m_mutex);
            m_frame.swap(incoming);
            m_received = GetTickCount64();
        }
    }
}

HRESULT SimpleFrameGenerator::CreateFrame(BYTE* output, DWORD length, LONG pitch) {
    if (!output || pitch < static_cast<LONG>(RemoteCam::Width) ||
        length < static_cast<ULONGLONG>(pitch) * RemoteCam::Height * 3 / 2) return E_INVALIDARG;
    // Bound output to 30 fps even when an application requests samples eagerly.
    auto now = std::chrono::steady_clock::now();
    if (m_next > now) std::this_thread::sleep_until(m_next);
    m_next = std::chrono::steady_clock::now() + std::chrono::microseconds(33333);
    std::lock_guard<std::mutex> guard(m_mutex);
    bool live = m_received && GetTickCount64() - m_received < 1500;
    for (unsigned row = 0; row < RemoteCam::Height * 3 / 2; ++row) {
        auto target = output + static_cast<size_t>(row) * pitch;
        if (live) memcpy(target, m_frame.data() + static_cast<size_t>(row) * RemoteCam::Width, RemoteCam::Width);
        else memset(target, row < RemoteCam::Height ? 16 : 128, RemoteCam::Width);
    }
    return S_OK;
}
