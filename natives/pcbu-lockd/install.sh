#!/bin/bash
# Uses the top-level monorepo build (../../build), not a standalone
# cmake-build-debug — pcbu_lockd depends on pcbu_common, which is only
# configured as part of that build.
set -e
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cmake --build "$REPO_ROOT/build" --target pcbu_lockd

sudo install -m 755 -o root -g root "$REPO_ROOT/build/natives/pcbu-lockd/pcbu_lockd" /usr/local/sbin/pcbu_lockd
sudo install -m 644 "$REPO_ROOT/desktop/res/systemd/pcbu-lockd.service" /etc/systemd/system/pcbu-lockd.service
sudo systemctl daemon-reload
sudo systemctl enable --now pcbu-lockd
