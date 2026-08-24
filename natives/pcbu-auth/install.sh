#!/bin/bash
cd cmake-build-debug || exit
cmake ..
cd ..
cmake --build ./cmake-build-debug --target all
su -c "cp cmake-build-debug/pcbu_auth /usr/local/sbin/pcbu_auth && chmod +x /usr/local/sbin/pcbu_auth && chmod u+s /usr/local/sbin/pcbu_auth"
