cmake_minimum_required(VERSION 3.22.1)

project(lumitalk)

find_library(log-lib log)

add_library(
        native-send
        SHARED
        src/main/cpp/send/send.cpp
        src/main/cpp/send/bridge.cpp
)

target_include_directories(
        native-send PRIVATE
        src/main/cpp/send
)

target_link_libraries(
        native-send
        ${log-lib}
)


add_library(
        native-receive
        SHARED
        src/main/cpp/receive/receive.cpp
        src/main/cpp/receive/bridge.cpp
)

target_include_directories(
        native-receive PRIVATE
        src/main/cpp/receive
)

target_link_libraries(
        native-receive
        ${log-lib}
)
