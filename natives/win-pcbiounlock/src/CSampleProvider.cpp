//
// THIS CODE AND INFORMATION IS PROVIDED "AS IS" WITHOUT WARRANTY OF
// ANY KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING BUT NOT LIMITED TO
// THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS FOR A
// PARTICULAR PURPOSE.
//
// Copyright (c) Microsoft Corporation. All rights reserved.
//
// CSampleProvider implements ICredentialProvider, which is the main
// interface that logonUI uses to decide which tiles to display.
// In this sample, we will display one tile that uses each of the nine
// available UI controls.

// clang-format off
#include <initguid.h>

#include "CSampleProvider.h"
#include "CUnlockCredential.h"
#include "guid.h"
#include "storage/AppSettings.h"
#include "storage/LoggingSystem.h"
#include "storage/PairedDevicesStorage.h"
#include "utils/StringUtils.h"
// clang-format on

CSampleProvider::CSampleProvider()
    : _cRef(1), _rgCredProvFieldDescriptors(), _pCredProviderUserArray(nullptr), _pCredProvEvents(nullptr), _upAdviseContext(0),
      _fRecreateEnumeratedCredentials(true), _cpus() {
  DllAddRef();
  LoggingSystem::Init("module");

  AddFieldDescriptor(SFI_TILEIMAGE, CPFT_TILE_IMAGE, "Image", CPFG_CREDENTIAL_PROVIDER_LOGO);
  AddFieldDescriptor(SFI_USERNAME, CPFT_SMALL_TEXT, "Username");
  AddFieldDescriptor(SFI_MESSAGE, CPFT_SMALL_TEXT, "Message");
  AddFieldDescriptor(SFI_PASSWORD, CPFT_PASSWORD_TEXT, I18n::Get("password"));
  AddFieldDescriptor(SFI_SUBMIT_BUTTON, CPFT_SUBMIT_BUTTON, "Submit");
  AddFieldDescriptor(SFI_RETRY_BUTTON, CPFT_COMMAND_LINK, I18n::Get("retry"), CPFG_STYLE_LINK_AS_BUTTON);
}

CSampleProvider::~CSampleProvider() {
  for(const auto cred : _pCredentials)
    cred->Release();
  _pCredentials.clear();
  if(_pCredProviderUserArray != nullptr) {
    _pCredProviderUserArray->Release();
    _pCredProviderUserArray = nullptr;
  }
  for(auto &desc : _rgCredProvFieldDescriptors)
    CoTaskMemFree(desc.pszLabel);
  DllRelease();
  LoggingSystem::Destroy();
}

void CSampleProvider::AddFieldDescriptor(DWORD id, CREDENTIAL_PROVIDER_FIELD_TYPE type, const std::string &label, GUID guid) {
  LPWSTR labelCopy{};
  SHStrDupW(StringUtils::ToWideString(label).c_str(), &labelCopy);
  _rgCredProvFieldDescriptors.emplace_back(id, type, labelCopy, guid);
}

HRESULT CSampleProvider::SetUsageScenario(CREDENTIAL_PROVIDER_USAGE_SCENARIO cpus, DWORD ) {
  HRESULT hr;

  switch(cpus) {
    case CPUS_LOGON:
    case CPUS_UNLOCK_WORKSTATION:
    case CPUS_CREDUI:
      _cpus = cpus;
      _fRecreateEnumeratedCredentials = true;
      hr = S_OK;
      break;

    case CPUS_CHANGE_PASSWORD:
      hr = E_NOTIMPL;
      break;

    default:
      hr = E_INVALIDARG;
      break;
  }
  return hr;
}

HRESULT CSampleProvider::SetSerialization(_In_ CREDENTIAL_PROVIDER_CREDENTIAL_SERIALIZATION const * ) {
  return E_NOTIMPL;
}

HRESULT CSampleProvider::Advise(_In_ ICredentialProviderEvents *pcpe, _In_ UINT_PTR upAdviseContext) {
  if(_pCredProvEvents != NULL) {
    _pCredProvEvents->Release();
  }
  _pCredProvEvents = pcpe;
  _pCredProvEvents->AddRef();
  _upAdviseContext = upAdviseContext;
  return S_OK;
}

HRESULT CSampleProvider::UnAdvise() {
  if(_pCredProvEvents != NULL) {
    _pCredProvEvents->Release();
    _pCredProvEvents = NULL;
  }
  return S_OK;
}

HRESULT CSampleProvider::GetFieldDescriptorCount(_Out_ DWORD *pdwCount) {
  *pdwCount = SFI_NUM_FIELDS;
  return S_OK;
}

HRESULT CSampleProvider::GetFieldDescriptorAt(DWORD dwIndex, _Outptr_result_nullonfailure_ CREDENTIAL_PROVIDER_FIELD_DESCRIPTOR **ppcpfd) {
  HRESULT hr;
  if((dwIndex < _rgCredProvFieldDescriptors.size()) && ppcpfd) {
    *ppcpfd = nullptr;
    hr = FieldDescriptorCoAllocCopy(_rgCredProvFieldDescriptors[dwIndex], ppcpfd);
  } else {
    hr = E_INVALIDARG;
  }

  return hr;
}

HRESULT CSampleProvider::GetCredentialCount(_Out_ DWORD *pdwCount, _Out_ DWORD *pdwDefault, _Out_ BOOL *pbAutoLogonWithDefault) {
  *pdwDefault = CREDENTIAL_PROVIDER_NO_DEFAULT;
  *pbAutoLogonWithDefault = FALSE;

  bool recreated = false;
  if(_fRecreateEnumeratedCredentials) {
    _fRecreateEnumeratedCredentials = false;
    _ReleaseEnumeratedCredentials();
    _CreateEnumeratedCredentials();
    recreated = true;
  }

  int idx{};
  bool forceDefaultProv = AppSettings::Get().winForceDefaultCredProv;
  for(const auto cred : _pCredentials) {
    if(cred->IsUnlockSuccess()) {
      *pdwDefault = idx;
      *pbAutoLogonWithDefault = TRUE;
      break;
    }
    if (recreated && forceDefaultProv && *pdwDefault == CREDENTIAL_PROVIDER_NO_DEFAULT) {
      *pdwDefault = idx;
    }
    idx++;
  }

  *pdwCount = static_cast<DWORD>(_pCredentials.size());
  return S_OK;
}

HRESULT CSampleProvider::GetCredentialAt(DWORD dwIndex, _Outptr_result_nullonfailure_ ICredentialProviderCredential **ppcpc) {
  HRESULT hr = E_INVALIDARG;
  if(ppcpc == nullptr) {
    return hr;
  }
  *ppcpc = nullptr;

  if(dwIndex < _pCredentials.size()) {
    const auto cred = _pCredentials[dwIndex];
    hr = cred->QueryInterface(IID_ICredentialProviderCredential, reinterpret_cast<void **>(ppcpc));
  }
  return hr;
}

HRESULT CSampleProvider::SetUserArray(_In_ ICredentialProviderUserArray *users) {
  if(_pCredProviderUserArray) {
    _pCredProviderUserArray->Release();
  }
  _pCredProviderUserArray = users;
  _pCredProviderUserArray->AddRef();
  return S_OK;
}

void CSampleProvider::_CreateEnumeratedCredentials() {
  switch(_cpus) {
    case CPUS_LOGON:
    case CPUS_UNLOCK_WORKSTATION:
    case CPUS_CREDUI: {
      _EnumerateCredentials();
      break;
    }
    default:
      break;
  }
}

void CSampleProvider::_ReleaseEnumeratedCredentials() {
  for(const auto cred : _pCredentials)
    cred->Release();
  _pCredentials.clear();
}

HRESULT CSampleProvider::_EnumerateCredentials() {
  HRESULT hr = S_OK;
  if(_pCredProviderUserArray != nullptr) {
    DWORD dwUserCount = 0;
    hr = _pCredProviderUserArray->GetCount(&dwUserCount);
    if(SUCCEEDED(hr) && dwUserCount > 0) {
      for(DWORD i = 0; i < dwUserCount; i++) {
        ICredentialProviderUser *pCredUser;
        hr = _pCredProviderUserArray->GetAt(i, &pCredUser);
        if(SUCCEEDED(hr)) {
          PWSTR userDomain{};
          hr = pCredUser->GetStringValue(PKEY_Identity_QualifiedUserName, &userDomain);
          if(SUCCEEDED(hr)) {
            auto userDomainStr = StringUtils::FromWideString(std::wstring(userDomain));
            auto userDevices = PairedDevicesStorage::GetDevicesForUser(userDomainStr);
            if(!userDevices.empty()) {
              auto cred = new(std::nothrow) CUnlockCredential();
              if(cred != nullptr) {
                hr = cred->Initialize(_cpus, _rgCredProvFieldDescriptors.data(), s_rgFieldStatePairs, pCredUser, this, userDomain);
                if(FAILED(hr)) {
                  spdlog::error("Failed to initialize credential.");
                  cred->Release();
                  cred = nullptr;
                } else if(SUCCEEDED(hr)) {
                  _pCredentials.emplace_back(cred);
                }
              } else {
                spdlog::error("Failed to allocate credential.");
                hr = E_OUTOFMEMORY;
              }
            }
            CoTaskMemFree(userDomain);
          } else {
            spdlog::error("Failed to get QualifiedUserName.");
          }
          pCredUser->Release();
        } else {
          spdlog::error("Failed to get user in credProviderUserArray.");
          hr = E_OUTOFMEMORY;
        }
      }

      if(_pCredentials.empty()) {
        hr = E_ABORT;
        spdlog::error("Could not find any paired user.");
      }
    } else {
      hr = E_ABORT;
      spdlog::error("No users found (count=0).");
    }
  } else {
    hr = E_ABORT;
    spdlog::error("No users found (array=nullptr).");
  }
  return hr;
}

void CSampleProvider::UpdateCredsStatus() const {
  if(_pCredProvEvents != nullptr) {
    _pCredProvEvents->CredentialsChanged(_upAdviseContext);
  } else {
    spdlog::error("Failed to update credential provider.");
  }
}

HRESULT CSample_CreateInstance(_In_ REFIID riid, _Outptr_ void **ppv) {
  HRESULT hr;
  if(auto pProvider = new(std::nothrow) CSampleProvider()) {
    hr = pProvider->QueryInterface(riid, ppv);
    pProvider->Release();
  } else {
    hr = E_OUTOFMEMORY;
  }
  return hr;
}
