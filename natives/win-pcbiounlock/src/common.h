//
// THIS CODE AND INFORMATION IS PROVIDED "AS IS" WITHOUT WARRANTY OF
// ANY KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING BUT NOT LIMITED TO
// THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS FOR A
// PARTICULAR PURPOSE.
//
// Copyright (c) Microsoft Corporation. All rights reserved.
//
// This file contains some global variables that describe what our
// sample tile looks like.  For example, it defines what fields a tile has
// and which fields show in which states of LogonUI. This sample illustrates
// the use of each UI field type.

#pragma once
#include "helpers.h"

enum SAMPLE_FIELD_ID {
  SFI_TILEIMAGE = 0,
  SFI_USERNAME = 1,
  SFI_MESSAGE = 2,
  SFI_PASSWORD = 3,
  SFI_SUBMIT_BUTTON = 4,
  SFI_RETRY_BUTTON = 5,
  SFI_NUM_FIELDS = 6,
};

struct FIELD_STATE_PAIR {
  CREDENTIAL_PROVIDER_FIELD_STATE cpfs;
  CREDENTIAL_PROVIDER_FIELD_INTERACTIVE_STATE cpfis;
};


static const FIELD_STATE_PAIR s_rgFieldStatePairs[] = {
    {CPFS_DISPLAY_IN_BOTH, CPFIS_NONE},
    {CPFS_DISPLAY_IN_BOTH, CPFIS_NONE},
    {CPFS_DISPLAY_IN_BOTH, CPFIS_NONE},
    {CPFS_DISPLAY_IN_SELECTED_TILE, CPFIS_FOCUSED},
    {CPFS_DISPLAY_IN_SELECTED_TILE, CPFIS_NONE},
    {CPFS_DISPLAY_IN_SELECTED_TILE, CPFIS_NONE},
};
