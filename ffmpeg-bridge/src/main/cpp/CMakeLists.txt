# SPDX-License-Identifier: GPL-3.0-or-later
cmake_minimum_required(VERSION 3.24)
project(transversa_ffmpeg LANGUAGES C CXX)

if(NOT DEFINED TRANSVERSA_FFMPEG_PREFIX)
    message(FATAL_ERROR "TRANSVERSA_FFMPEG_PREFIX is required. Build the pinned FFmpeg Android bundle first.")
endif()

set(FFMPEG_INCLUDE_DIR "${TRANSVERSA_FFMPEG_PREFIX}/include")
set(FFMPEG_LIBRARY_DIR "${TRANSVERSA_FFMPEG_PREFIX}/lib")
set(FFMPEG_ARCHIVES
    avformat avcodec avfilter swscale swresample avutil x264 kvazaar mp3lame
)

foreach(component IN LISTS FFMPEG_ARCHIVES)
    set(archive "${FFMPEG_LIBRARY_DIR}/lib${component}.a")
    if(NOT EXISTS "${archive}")
        message(FATAL_ERROR "Missing ${archive}. Run :ffmpeg-bridge:buildFfmpegAndroid.")
    endif()
    add_library("ffmpeg_${component}" STATIC IMPORTED GLOBAL)
    set_target_properties("ffmpeg_${component}" PROPERTIES IMPORTED_LOCATION "${archive}")
endforeach()

add_library(transversa_ffmpeg SHARED ffmpeg_jni.cpp)
target_include_directories(transversa_ffmpeg PRIVATE "${FFMPEG_INCLUDE_DIR}")
target_compile_features(transversa_ffmpeg PRIVATE cxx_std_17)
target_compile_options(transversa_ffmpeg PRIVATE -Wall -Wextra -Werror -fvisibility=hidden)
target_link_options(transversa_ffmpeg PRIVATE -Wl,-z,max-page-size=16384)

# FFmpeg static archives reference each other, so rescan the group until all symbols resolve.
target_link_libraries(transversa_ffmpeg PRIVATE
    "$<LINK_GROUP:RESCAN,ffmpeg_avformat,ffmpeg_avcodec,ffmpeg_avfilter,ffmpeg_swscale,ffmpeg_swresample,ffmpeg_avutil,ffmpeg_x264,ffmpeg_kvazaar,ffmpeg_mp3lame>"
    android mediandk log m z dl
)
