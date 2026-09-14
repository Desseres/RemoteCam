#include "pch.h"

extern "C" HRESULT __stdcall DllGetClassObject(REFCLSID clsid, REFIID iid, void** result) try {
    if (!result) return E_POINTER;
    *result = nullptr;
    if (clsid != CLSID_VirtualCameraMediaSource) return CLASS_E_CLASSNOTAVAILABLE;
    return winrt::make_self<VirtualCameraMediaSourceActivateFactory>()->QueryInterface(iid, result);
} CATCH_RETURN()

extern "C" HRESULT __stdcall DllCanUnloadNow() {
    return winrt::get_module_lock() ? S_FALSE : S_OK;
}
