cmake_minimum_required(VERSION 3.22)

project(pcbu_lockd)
set(CMAKE_CXX_STANDARD 23)

if(WIN32)
    add_compile_definitions(WINDOWS)
elseif(APPLE)
    add_compile_definitions(APPLE)
elseif(UNIX)
    add_compile_definitions(LINUX)
endif()

set(PCBU_LOCKD_SOURCES src/main.cpp)
if(UNIX AND NOT APPLE)
    # Login1UnlockWatcher calls PlatformHelper::PlayMacro, which (like SelectGreeterUser)
    # only exists under #ifdef LINUX — same Linux-only scope as the rest of the post-unlock
    # macro feature.
    list(APPEND PCBU_LOCKD_SOURCES src/Login1UnlockWatcher.cpp)
endif()

add_executable(pcbu_lockd ${PCBU_LOCKD_SOURCES})
target_include_directories(pcbu_lockd PRIVATE common-cpp/src)
target_link_libraries(pcbu_lockd PRIVATE pcbu_common)
add_custom_command(TARGET pcbu_lockd
        POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E make_directory ${CMAKE_SOURCE_DIR}/desktop/res/natives/${TARGET_OS}/${TARGET_ARCH}
        COMMAND ${CMAKE_COMMAND} -E copy $<TARGET_FILE:pcbu_lockd> ${CMAKE_SOURCE_DIR}/desktop/res/natives/${TARGET_OS}/${TARGET_ARCH})
