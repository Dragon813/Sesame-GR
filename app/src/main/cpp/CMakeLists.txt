cmake_minimum_required(VERSION 3.22.1)

project("watermark")

add_library(
        watermark
        SHARED
        watermark.cpp
)

find_library(
        log-lib
        log
)

target_link_libraries(
        watermark
        ${log-lib}
)
