cmake_minimum_required(VERSION 3.22.1)

project(native_send)

add_library(
        native-send
        SHARED
        src/main/cpp/send/send.cpp
        src/main/cpp/send/bridge.cpp
)

target_include_directories(native-send PRIVATE
    src/main/cpp/send
)

find_library(
        log-lib
        log)

target_link_libraries(
        native-send
        ${log-lib})
