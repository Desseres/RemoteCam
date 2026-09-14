#include <windows.h>
#include <mfapi.h>
#include <mfidl.h>
#include <mfreadwrite.h>
#include <mfvirtualcamera.h>
#include <wrl/client.h>
#include <cstdio>
#include <string>
#include "FrameProtocol.h"
#include "VirtualCameraMediaSource.h"
using Microsoft::WRL::ComPtr;

static HRESULT ListCameras(bool probe) {
    ComPtr<IMFAttributes> attrs;
    HRESULT hr = MFCreateAttributes(&attrs, 1);
    if (FAILED(hr)) return hr;
    attrs->SetGUID(MF_DEVSOURCE_ATTRIBUTE_SOURCE_TYPE, MF_DEVSOURCE_ATTRIBUTE_SOURCE_TYPE_VIDCAP_GUID);
    IMFActivate** devices = nullptr;
    UINT32 count = 0;
    hr = MFEnumDeviceSources(attrs.Get(), &devices, &count);
    if (FAILED(hr)) return hr;
    bool found = false;
    for (UINT32 i = 0; i < count; ++i) {
        wchar_t* name = nullptr;
        UINT32 size = 0;
        if (SUCCEEDED(devices[i]->GetAllocatedString(MF_DEVSOURCE_ATTRIBUTE_FRIENDLY_NAME, &name, &size))) {
            wprintf(L"CAMERA %s\n", name);
            if (probe && wcsstr(name, L"RemoteCam")) {
                found = true;
                ComPtr<IMFMediaSource> source;
                ComPtr<IMFSourceReader> reader;
                hr = devices[i]->ActivateObject(IID_PPV_ARGS(&source));
                if (SUCCEEDED(hr)) hr = MFCreateSourceReaderFromMediaSource(source.Get(), nullptr, &reader);
                for (int frame = 0; frame < 60 && SUCCEEDED(hr); ++frame) {
                    ComPtr<IMFSample> sample;
                    DWORD flags = 0;
                    LONGLONG time = 0;
                    hr = reader->ReadSample(MF_SOURCE_READER_FIRST_VIDEO_STREAM, 0, nullptr, &flags, &time, &sample);
                    if (sample && (frame % 15 == 0)) {
                        ComPtr<IMFMediaBuffer> buffer;
                        sample->ConvertToContiguousBuffer(&buffer);
                        BYTE* data = nullptr;
                        DWORD length = 0;
                        buffer->Lock(&data, nullptr, &length);
                        unsigned long long sum = 0;
                        for (DWORD j = 0; j < length; j += 101) sum += data[j];
                        buffer->Unlock();
                        printf("FRAME %d bytes=%lu time=%lld checksum=%llu\n", frame, length, time, sum);
                    }
                }
                if (source) source->Shutdown();
            }
            CoTaskMemFree(name);
        }
        devices[i]->Release();
    }
    CoTaskMemFree(devices);
    if (probe && !found) return HRESULT_FROM_WIN32(ERROR_NOT_FOUND);
    return hr;
}

int wmain(int argc, wchar_t** argv) {
    CoInitializeEx(nullptr, COINIT_MULTITHREADED);
    MFStartup(MF_VERSION);
    HRESULT hr = S_OK;
    if (argc == 2 && (wcscmp(argv[1], L"--list") == 0 || wcscmp(argv[1], L"--probe") == 0)) {
        hr = ListCameras(wcscmp(argv[1], L"--probe") == 0);
    } else if (argc == 3 && wcscmp(argv[1], L"--pipe") == 0) {
        ComPtr<IMFVirtualCamera> camera;
        hr = MFCreateVirtualCamera(MFVirtualCameraType_SoftwareCameraSource,
            MFVirtualCameraLifetime_Session, MFVirtualCameraAccess_CurrentUser,
            L"RemoteCam", VIRTUALCAMERAMEDIASOURCE_CLSID, nullptr, 0, &camera);
        if (SUCCEEDED(hr)) hr = camera->SetString(REMOTECAM_PIPE_NAME, argv[2]);
        if (SUCCEEDED(hr)) hr = camera->Start(nullptr);
        if (SUCCEEDED(hr)) {
            puts("READY RemoteCam Windows Virtual Camera");
            fflush(stdout);
            getchar(); // parent closes stdin or writes a newline to stop
            camera->Stop();
            camera->Remove();
        }
        if (camera) camera->Shutdown();
    } else {
        puts("RemoteCamHost --pipe \\.\\pipe\\RemoteCam.<session> | --list | --probe");
        hr = E_INVALIDARG;
    }
    if (FAILED(hr)) fprintf(stderr, "RemoteCam camera error: 0x%08lX\n", static_cast<unsigned long>(hr));
    MFShutdown();
    CoUninitialize();
    return FAILED(hr) ? 1 : 0;
}
