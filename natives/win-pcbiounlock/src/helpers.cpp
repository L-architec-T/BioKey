//
// THIS CODE AND INFORMATION IS PROVIDED "AS IS" WITHOUT WARRANTY OF
// ANY KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING BUT NOT LIMITED TO
// THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS FOR A
// PARTICULAR PURPOSE.
//
// Copyright (c) Microsoft Corporation. All rights reserved.
//
// Helper functions for copying parameters and packaging the buffer
// for GetSerialization.

#include "helpers.h"
#include <intsafe.h>
#include <lm.h>
#include <wtsapi32.h>

HRESULT FieldDescriptorCoAllocCopy(_In_ const CREDENTIAL_PROVIDER_FIELD_DESCRIPTOR &rcpfd,
                                   _Outptr_result_nullonfailure_ CREDENTIAL_PROVIDER_FIELD_DESCRIPTOR **ppcpfd) {
  HRESULT hr;
  *ppcpfd = nullptr;
  DWORD cbStruct = sizeof(**ppcpfd);

  CREDENTIAL_PROVIDER_FIELD_DESCRIPTOR *pcpfd = (CREDENTIAL_PROVIDER_FIELD_DESCRIPTOR *)CoTaskMemAlloc(cbStruct);
  if(pcpfd) {
    pcpfd->dwFieldID = rcpfd.dwFieldID;
    pcpfd->cpft = rcpfd.cpft;
    pcpfd->guidFieldType = rcpfd.guidFieldType;

    if(rcpfd.pszLabel) {
      hr = SHStrDupW(rcpfd.pszLabel, &pcpfd->pszLabel);
    } else {
      pcpfd->pszLabel = nullptr;
      hr = S_OK;
    }
  } else {
    hr = E_OUTOFMEMORY;
  }

  if(SUCCEEDED(hr)) {
    *ppcpfd = pcpfd;
  } else {
    CoTaskMemFree(pcpfd);
  }

  return hr;
}

HRESULT FieldDescriptorCopy(_In_ const CREDENTIAL_PROVIDER_FIELD_DESCRIPTOR &rcpfd, _Out_ CREDENTIAL_PROVIDER_FIELD_DESCRIPTOR *pcpfd) {
  HRESULT hr;
  CREDENTIAL_PROVIDER_FIELD_DESCRIPTOR cpfd;

  cpfd.dwFieldID = rcpfd.dwFieldID;
  cpfd.cpft = rcpfd.cpft;
  cpfd.guidFieldType = rcpfd.guidFieldType;

  if(rcpfd.pszLabel) {
    hr = SHStrDupW(rcpfd.pszLabel, &cpfd.pszLabel);
  } else {
    cpfd.pszLabel = nullptr;
    hr = S_OK;
  }

  if(SUCCEEDED(hr)) {
    *pcpfd = cpfd;
  }

  return hr;
}

HRESULT UnicodeStringInitWithString(_In_ PWSTR pwz, _Out_ UNICODE_STRING *pus) {
  HRESULT hr;
  if(pwz) {
    size_t lenString = wcslen(pwz);
    USHORT usCharCount;
    hr = SizeTToUShort(lenString, &usCharCount);
    if(SUCCEEDED(hr)) {
      USHORT usSize;
      hr = SizeTToUShort(sizeof(wchar_t), &usSize);
      if(SUCCEEDED(hr)) {
        hr = UShortMult(usCharCount, usSize, &(pus->Length));
        if(SUCCEEDED(hr)) {
          pus->MaximumLength = pus->Length;
          pus->Buffer = pwz;
          hr = S_OK;
        } else {
          hr = HRESULT_FROM_WIN32(ERROR_ARITHMETIC_OVERFLOW);
        }
      }
    }
  } else {
    hr = E_INVALIDARG;
  }
  return hr;
}

static void _UnicodeStringPackedUnicodeStringCopy(__in const UNICODE_STRING &rus, __in PWSTR pwzBuffer, __out UNICODE_STRING *pus) {
  pus->Length = rus.Length;
  pus->MaximumLength = rus.Length;
  pus->Buffer = pwzBuffer;

  CopyMemory(pus->Buffer, rus.Buffer, pus->Length);
}

HRESULT KerbInteractiveUnlockLogonInit(_In_ PWSTR pwzDomain, _In_ PWSTR pwzUsername, _In_ PWSTR pwzPassword,
                                       _In_ CREDENTIAL_PROVIDER_USAGE_SCENARIO cpus, _Out_ KERB_INTERACTIVE_UNLOCK_LOGON *pkiul) {
  KERB_INTERACTIVE_UNLOCK_LOGON kiul;
  ZeroMemory(&kiul, sizeof(kiul));

  KERB_INTERACTIVE_LOGON *pkil = &kiul.Logon;


  HRESULT hr = UnicodeStringInitWithString(pwzDomain, &pkil->LogonDomainName);
  if(SUCCEEDED(hr)) {
    hr = UnicodeStringInitWithString(pwzUsername, &pkil->UserName);
    if(SUCCEEDED(hr)) {
      hr = UnicodeStringInitWithString(pwzPassword, &pkil->Password);
      if(SUCCEEDED(hr)) {
        switch(cpus) {
          case CPUS_UNLOCK_WORKSTATION:
            pkil->MessageType = KerbWorkstationUnlockLogon;
            hr = S_OK;
            break;

          case CPUS_LOGON:
          case CPUS_CREDUI:
            pkil->MessageType = KerbInteractiveLogon;
            hr = S_OK;
            break;

          default:
            hr = E_FAIL;
            break;
        }

        if(SUCCEEDED(hr)) {
          CopyMemory(pkiul, &kiul, sizeof(*pkiul));
        }
      }
    }
  }

  return hr;
}


HRESULT KerbInteractiveUnlockLogonPack(_In_ const KERB_INTERACTIVE_UNLOCK_LOGON &rkiulIn, _Outptr_result_bytebuffer_(*pcb) BYTE **prgb,
                                       _Out_ DWORD *pcb) {
  HRESULT hr;

  const KERB_INTERACTIVE_LOGON *pkilIn = &rkiulIn.Logon;

  DWORD cb = sizeof(rkiulIn) + pkilIn->LogonDomainName.Length + pkilIn->UserName.Length + pkilIn->Password.Length;

  KERB_INTERACTIVE_UNLOCK_LOGON *pkiulOut = (KERB_INTERACTIVE_UNLOCK_LOGON *)CoTaskMemAlloc(cb);
  if(pkiulOut) {
    ZeroMemory(&pkiulOut->LogonId, sizeof(pkiulOut->LogonId));

    BYTE *pbBuffer = (BYTE *)pkiulOut + sizeof(*pkiulOut);

    KERB_INTERACTIVE_LOGON *pkilOut = &pkiulOut->Logon;

    pkilOut->MessageType = pkilIn->MessageType;

    _UnicodeStringPackedUnicodeStringCopy(pkilIn->LogonDomainName, (PWSTR)pbBuffer, &pkilOut->LogonDomainName);
    pkilOut->LogonDomainName.Buffer = (PWSTR)(pbBuffer - (BYTE *)pkiulOut);
    pbBuffer += pkilOut->LogonDomainName.Length;

    _UnicodeStringPackedUnicodeStringCopy(pkilIn->UserName, (PWSTR)pbBuffer, &pkilOut->UserName);
    pkilOut->UserName.Buffer = (PWSTR)(pbBuffer - (BYTE *)pkiulOut);
    pbBuffer += pkilOut->UserName.Length;

    _UnicodeStringPackedUnicodeStringCopy(pkilIn->Password, (PWSTR)pbBuffer, &pkilOut->Password);
    pkilOut->Password.Buffer = (PWSTR)(pbBuffer - (BYTE *)pkiulOut);

    *prgb = (BYTE *)pkiulOut;
    *pcb = cb;

    hr = S_OK;
  } else {
    hr = E_OUTOFMEMORY;
  }

  return hr;
}

static HRESULT _LsaInitString(__out PSTRING pszDestinationString, __in PCSTR pszSourceString) {
  size_t cchLength = strlen(pszSourceString);
  USHORT usLength;
  HRESULT hr = SizeTToUShort(cchLength, &usLength);
  if(SUCCEEDED(hr)) {
    pszDestinationString->Buffer = (PCHAR)pszSourceString;
    pszDestinationString->Length = usLength;
    pszDestinationString->MaximumLength = pszDestinationString->Length + 1;
    hr = S_OK;
  }
  return hr;
}

HRESULT RetrieveNegotiateAuthPackage(_Out_ ULONG *pulAuthPackage) {
  HRESULT hr;
  HANDLE hLsa;

  NTSTATUS status = LsaConnectUntrusted(&hLsa);
  if(SUCCEEDED(HRESULT_FROM_NT(status))) {
    ULONG ulAuthPackage;
    LSA_STRING lsaszKerberosName;
    _LsaInitString(&lsaszKerberosName, NEGOSSP_NAME_A);

    status = LsaLookupAuthenticationPackage(hLsa, &lsaszKerberosName, &ulAuthPackage);
    if(SUCCEEDED(HRESULT_FROM_NT(status))) {
      *pulAuthPackage = ulAuthPackage;
      hr = S_OK;
    } else {
      hr = HRESULT_FROM_NT(status);
    }
    LsaDeregisterLogonProcess(hLsa);
  } else {
    hr = HRESULT_FROM_NT(status);
  }

  return hr;
}

static HRESULT _ProtectAndCopyString(_In_ PCWSTR pwzToProtect, _Outptr_result_nullonfailure_ PWSTR *ppwzProtected) {
  *ppwzProtected = nullptr;

  PWSTR pwzToProtectCopy;
  HRESULT hr = SHStrDupW(pwzToProtect, &pwzToProtectCopy);
  if(SUCCEEDED(hr)) {
    DWORD cchProtected = 0;
    if(!CredProtectW(FALSE, pwzToProtectCopy, (DWORD)wcslen(pwzToProtectCopy) + 1, nullptr, &cchProtected, nullptr)) {
      DWORD dwErr = GetLastError();

      if((ERROR_INSUFFICIENT_BUFFER == dwErr) && (0 < cchProtected)) {
        PWSTR pwzProtected = (PWSTR)CoTaskMemAlloc(cchProtected * sizeof(wchar_t));
        if(pwzProtected) {
          if(CredProtectW(FALSE, pwzToProtectCopy, (DWORD)wcslen(pwzToProtectCopy) + 1, pwzProtected, &cchProtected, nullptr)) {
            *ppwzProtected = pwzProtected;
            hr = S_OK;
          } else {
            CoTaskMemFree(pwzProtected);

            dwErr = GetLastError();
            hr = HRESULT_FROM_WIN32(dwErr);
          }
        } else {
          hr = E_OUTOFMEMORY;
        }
      } else {
        hr = HRESULT_FROM_WIN32(dwErr);
      }
    } else {
      hr = E_UNEXPECTED;
    }

    CoTaskMemFree(pwzToProtectCopy);
  }

  return hr;
}

HRESULT ProtectIfNecessaryAndCopyPassword(_In_ PCWSTR pwzPassword, _In_ CREDENTIAL_PROVIDER_USAGE_SCENARIO cpus,
                                          _Outptr_result_nullonfailure_ PWSTR *ppwzProtectedPassword) {
  *ppwzProtectedPassword = nullptr;

  HRESULT hr;

  if(pwzPassword && *pwzPassword) {
    PWSTR pwzPasswordCopy;
    hr = SHStrDupW(pwzPassword, &pwzPasswordCopy);
    if(SUCCEEDED(hr)) {
      bool bCredAlreadyEncrypted = false;
      CRED_PROTECTION_TYPE protectionType;

      if(CredIsProtectedW(pwzPasswordCopy, &protectionType)) {
        if(CredUnprotected != protectionType) {
          bCredAlreadyEncrypted = true;
        }
      }

      if(CPUS_CREDUI == cpus || bCredAlreadyEncrypted) {
        hr = SHStrDupW(pwzPasswordCopy, ppwzProtectedPassword);
      } else {
        hr = _ProtectAndCopyString(pwzPasswordCopy, ppwzProtectedPassword);
      }

      CoTaskMemFree(pwzPasswordCopy);
    }
  } else {
    hr = SHStrDupW(L"", ppwzProtectedPassword);
  }

  return hr;
}

void KerbInteractiveUnlockLogonUnpackInPlace(_Inout_updates_bytes_(cb) KERB_INTERACTIVE_UNLOCK_LOGON *pkiul, DWORD cb) {
  if(sizeof(*pkiul) <= cb) {
    KERB_INTERACTIVE_LOGON *pkil = &pkiul->Logon;

    if(((ULONG_PTR)pkil->LogonDomainName.Buffer + pkil->LogonDomainName.MaximumLength <= cb) &&
       ((ULONG_PTR)pkil->UserName.Buffer + pkil->UserName.MaximumLength <= cb) &&
       ((ULONG_PTR)pkil->Password.Buffer + pkil->Password.MaximumLength <= cb)) {
      pkil->LogonDomainName.Buffer = pkil->LogonDomainName.Buffer ? (PWSTR)((BYTE *)pkiul + (ULONG_PTR)pkil->LogonDomainName.Buffer) : nullptr;

      pkil->UserName.Buffer = pkil->UserName.Buffer ? (PWSTR)((BYTE *)pkiul + (ULONG_PTR)pkil->UserName.Buffer) : nullptr;

      pkil->Password.Buffer = pkil->Password.Buffer ? (PWSTR)((BYTE *)pkiul + (ULONG_PTR)pkil->Password.Buffer) : nullptr;
    }
  }
}

HRESULT KerbInteractiveUnlockLogonRepackNative(_In_reads_bytes_(cbWow) BYTE *rgbWow, _In_ DWORD cbWow,
                                               _Outptr_result_bytebuffer_(*pcbNative) BYTE **prgbNative, _Out_ DWORD *pcbNative) {
  HRESULT hr = E_OUTOFMEMORY;
  PWSTR pszDomainUsername = nullptr;
  DWORD cchDomainUsername = 0;
  PWSTR pszPassword = nullptr;
  DWORD cchPassword = 0;

  *prgbNative = nullptr;
  *pcbNative = 0;

  CredUnPackAuthenticationBufferW(CRED_PACK_WOW_BUFFER, rgbWow, cbWow, pszDomainUsername, &cchDomainUsername, nullptr, nullptr, pszPassword,
                                  &cchPassword);
  if(ERROR_INSUFFICIENT_BUFFER == GetLastError()) {
    pszDomainUsername = (PWSTR)LocalAlloc(0, cchDomainUsername * sizeof(wchar_t));
    if(pszDomainUsername) {
      pszPassword = (PWSTR)LocalAlloc(0, cchPassword * sizeof(wchar_t));
      if(pszPassword) {
        if(CredUnPackAuthenticationBufferW(CRED_PACK_WOW_BUFFER, rgbWow, cbWow, pszDomainUsername, &cchDomainUsername, nullptr, nullptr, pszPassword,
                                           &cchPassword)) {
          hr = S_OK;
        } else {
          hr = GetLastError();
        }
      }
    }
  }

  if(SUCCEEDED(hr)) {
    hr = E_OUTOFMEMORY;
    CredPackAuthenticationBufferW(0, pszDomainUsername, pszPassword, *prgbNative, pcbNative);
    if(ERROR_INSUFFICIENT_BUFFER == GetLastError()) {
      *prgbNative = (BYTE *)LocalAlloc(LMEM_ZEROINIT, *pcbNative);
      if(*prgbNative) {
        if(CredPackAuthenticationBufferW(0, pszDomainUsername, pszPassword, *prgbNative, pcbNative)) {
          hr = S_OK;
        } else {
          LocalFree(*prgbNative);
        }
      }
    }
  }

  LocalFree(pszDomainUsername);
  if(pszPassword) {
    SecureZeroMemory(pszPassword, cchPassword * sizeof(wchar_t));
    LocalFree(pszPassword);
  }
  return hr;
}

HRESULT DomainUsernameStringAlloc(_In_ PCWSTR pwszDomain, _In_ PCWSTR pwszUsername, _Outptr_result_nullonfailure_ PWSTR *ppwszDomainUsername) {
  HRESULT hr;
  *ppwszDomainUsername = nullptr;
  size_t cchDomain = wcslen(pwszDomain);
  size_t cchUsername = wcslen(pwszUsername);
  size_t cbLen = sizeof(wchar_t) * (cchDomain + 1 + cchUsername + 1);
  PWSTR pwszDest = (PWSTR)HeapAlloc(GetProcessHeap(), 0, cbLen);
  if(pwszDest) {
    hr = StringCbPrintfW(pwszDest, cbLen, L"%s\\%s", pwszDomain, pwszUsername);
    if(SUCCEEDED(hr)) {
      *ppwszDomainUsername = pwszDest;
    } else {
      HeapFree(GetProcessHeap(), 0, pwszDest);
    }
  } else {
    hr = E_OUTOFMEMORY;
  }

  return hr;
}

HRESULT SplitDomainAndUsername(_In_ PCWSTR pszQualifiedUserName, _Outptr_result_nullonfailure_ PWSTR *ppszDomain,
                               _Outptr_result_nullonfailure_ PWSTR *ppszUsername) {
  HRESULT hr = E_UNEXPECTED;
  *ppszDomain = nullptr;
  *ppszUsername = nullptr;
  PWSTR pszDomain;
  PWSTR pszUsername;
  const wchar_t *pchWhack = wcschr(pszQualifiedUserName, L'\\');
  const wchar_t *pchEnd = pszQualifiedUserName + wcslen(pszQualifiedUserName) - 1;

  if(pchWhack != nullptr) {
    const wchar_t *pchDomainBegin = pszQualifiedUserName;
    const wchar_t *pchDomainEnd = pchWhack - 1;
    const wchar_t *pchUsernameBegin = pchWhack + 1;
    const wchar_t *pchUsernameEnd = pchEnd;

    size_t lenDomain = pchDomainEnd - pchDomainBegin + 1;
    pszDomain = static_cast<PWSTR>(CoTaskMemAlloc(sizeof(wchar_t) * (lenDomain + 1)));
    if(pszDomain != nullptr) {
      hr = StringCchCopyNW(pszDomain, lenDomain + 1, pchDomainBegin, lenDomain);
      if(SUCCEEDED(hr)) {
        size_t lenUsername = pchUsernameEnd - pchUsernameBegin + 1;
        pszUsername = static_cast<PWSTR>(CoTaskMemAlloc(sizeof(wchar_t) * (lenUsername + 1)));
        if(pszUsername != nullptr) {
          hr = StringCchCopyNW(pszUsername, lenUsername + 1, pchUsernameBegin, lenUsername);
          if(SUCCEEDED(hr)) {
            *ppszDomain = pszDomain;
            *ppszUsername = pszUsername;
          } else {
            CoTaskMemFree(pszUsername);
          }
        } else {
          hr = E_OUTOFMEMORY;
        }
      }

      if(FAILED(hr)) {
        CoTaskMemFree(pszDomain);
      }
    } else {
      hr = E_OUTOFMEMORY;
    }
  }
  return hr;
}

static bool WEqualsIgnoreCase(const std::wstring &a, const std::wstring &b) {
  return CompareStringOrdinal(a.c_str(), -1, b.c_str(), -1, TRUE) == CSTR_EQUAL;
}

static std::wstring GetMicrosoftAccountEmail(const std::wstring &userName) {
  LPUSER_INFO_24 info = nullptr;
  std::wstring email{};
  if(NetUserGetInfo(nullptr, userName.c_str(), 24, reinterpret_cast<LPBYTE *>(&info)) == NERR_Success && info != nullptr) {
    if(info->usri24_internet_identity && info->usri24_internet_principal_name != nullptr)
      email = info->usri24_internet_principal_name;
  }
  if(info != nullptr)
    NetApiBufferFree(info);
  return email;
}

static bool SessionMatchesUser(DWORD sessionId, const std::wstring &domain, const std::wstring &user) {
  bool match = false;
  LPWSTR pUser = nullptr;
  DWORD userBytes = 0;
  if(WTSQuerySessionInformationW(WTS_CURRENT_SERVER_HANDLE, sessionId, WTSUserName, &pUser, &userBytes) && pUser) {
    if(pUser[0] != L'\0') {
      if(WEqualsIgnoreCase(domain, L"MicrosoftAccount")) {
        const auto email = GetMicrosoftAccountEmail(pUser);
        match = !email.empty() && WEqualsIgnoreCase(email, user);
      } else if(WEqualsIgnoreCase(pUser, user)) {
        LPWSTR pDomain = nullptr;
        DWORD domainBytes = 0;
        if(WTSQuerySessionInformationW(WTS_CURRENT_SERVER_HANDLE, sessionId, WTSDomainName, &pDomain, &domainBytes) && pDomain) {
          match = WEqualsIgnoreCase(pDomain, domain);
          WTSFreeMemory(pDomain);
        } else {
          match = true;
        }
      }
    }
    WTSFreeMemory(pUser);
  }
  return match;
}

static LONGLONG GetSessionLogonTimeSeconds(DWORD sessionId) {
  LPWSTR pInfo = nullptr;
  DWORD infoBytes = 0;
  LONGLONG seconds = 0;
  if(WTSQuerySessionInformationW(WTS_CURRENT_SERVER_HANDLE, sessionId, WTSSessionInfo, &pInfo, &infoBytes) && pInfo) {
    const auto info = reinterpret_cast<PWTSINFOW>(pInfo);
    if(info->LogonTime.QuadPart > 0) {
      FILETIME ftNow{};
      GetSystemTimeAsFileTime(&ftNow);
      ULARGE_INTEGER now{};
      now.LowPart = ftNow.dwLowDateTime;
      now.HighPart = ftNow.dwHighDateTime;
      if(static_cast<LONGLONG>(now.QuadPart) >= info->LogonTime.QuadPart)
        seconds = (static_cast<LONGLONG>(now.QuadPart) - info->LogonTime.QuadPart) / 10000000LL;
    }
    WTSFreeMemory(pInfo);
  }
  return seconds;
}

bool IsUserLoggedOn(const std::wstring &userDomain, LONGLONG minLogonTimeSecs) {
  const auto sep = userDomain.find(L'\\');
  if(sep == std::wstring::npos)
    return false;
  const std::wstring domain = userDomain.substr(0, sep);
  const std::wstring user = userDomain.substr(sep + 1);

  PWTS_SESSION_INFOW pSessions = nullptr;
  DWORD count = 0;
  if(!WTSEnumerateSessionsW(WTS_CURRENT_SERVER_HANDLE, 0, 1, &pSessions, &count))
    return false;

  bool loggedOn = false;
  for(DWORD i = 0; i < count && !loggedOn; i++) {
    const auto &session = pSessions[i];
    if(SessionMatchesUser(session.SessionId, domain, user) && GetSessionLogonTimeSeconds(session.SessionId) >= minLogonTimeSecs)
      loggedOn = true;
  }
  WTSFreeMemory(pSessions);
  return loggedOn;
}
