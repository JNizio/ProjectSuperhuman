cmake_minimum_required(VERSION 3.22.1)

project(psh_heart_rate LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(
    psh_heart_rate
    SHARED
    heart_rate_engine.cpp
)

target_compile_options(
    psh_heart_rate
    PRIVATE
    -Wall
    -Wextra
    -Wpedantic
)

find_library(log-lib log)

target_link_libraries(
    psh_heart_rate
    ${log-lib}
)
