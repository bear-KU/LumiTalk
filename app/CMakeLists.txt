cmake_minimum_required(VERSION 3.22.1)

project(lumitalk)

find_library(log-lib log)

# OpenCV
set(OpenCV_DIR $ENV{OPENCV_ANDROID_SDK}/sdk/native/jni)
find_package(OpenCV REQUIRED)

add_library(opencv_java4 SHARED IMPORTED)
set_target_properties(opencv_java4 PROPERTIES
    IMPORTED_LOCATION $ENV{OPENCV_ANDROID_SDK}/sdk/native/libs/arm64-v8a/libopencv_java4.so
)

# libc++_shared.so
set(CMAKE_ANDROID_STL_TYPE c++_shared)

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
        ${OpenCV_INCLUDE_DIRS}
)

target_link_libraries(
        native-receive
        opencv_java4
        ${log-lib}
)
