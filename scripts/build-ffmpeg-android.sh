#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
source "$ROOT/native/ffmpeg/versions.env"
export PATH="$HOME/.local/bin:$PATH"
NATIVE_DIR="$ROOT/ffmpeg-bridge/build/native"
WSL_CACHE_DIR="${TRANSVERSA_WSL_BUILD_CACHE:-$HOME/.cache/transversa/ffmpeg-android}"
DOWNLOADS="$WSL_CACHE_DIR/downloads"
SOURCES="$WSL_CACHE_DIR/sources"
WORK="$WSL_CACHE_DIR/work"
PREFIX="$NATIVE_DIR/install/arm64-v8a"
NDK_VERSION="$ANDROID_NDK_VERSION"
NDK_REVISION="$ANDROID_NDK_REVISION"
NDK_SHA1="$ANDROID_NDK_LINUX_SHA1"
NDK_URL="https://dl.google.com/android/repository/android-ndk-r${NDK_REVISION}-linux.zip"
ANDROID_API="$ANDROID_MIN_API"
NDK="$WSL_CACHE_DIR/android-ndk-r${NDK_REVISION}"

fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
require_tool() { command -v "$1" >/dev/null 2>&1 || fail "Missing '$1'. Install the Linux build dependencies listed in docs/ffmpeg-android-build.md."; }

for tool in curl sha1sum sha256sum tar xz unzip git make cmake ninja pkg-config nasm yasm python3; do
    require_tool "$tool"
done
CMAKE_VERSION="$(cmake --version | sed -n '1s/^cmake version //p')"
[[ -n "$CMAKE_VERSION" ]] || fail "Could not determine CMake version."
if [[ "$(printf '%s\n%s\n' 3.25 "$CMAKE_VERSION" | sort -V | head -n 1)" != "3.25" ]]; then
    fail "CMake 3.25 or newer is required for the pinned Kvazaar source (found $CMAKE_VERSION)."
fi

mkdir -p "$DOWNLOADS" "$SOURCES" "$WORK" "$PREFIX"

# Seed WSL's Linux-filesystem cache from earlier builds kept under the repo on Windows.
# Compiling against tools and sources on /mnt/c causes very slow configure probes.
seed_cache_dir() {
    local name="$1" source_dir="$NATIVE_DIR/$1" cache_dir="$WSL_CACHE_DIR/$1"
    if [[ -e "$source_dir" && ! -e "$cache_dir" ]]; then
        mkdir -p "$(dirname "$cache_dir")"
        cp -a "$source_dir" "$cache_dir"
    fi
}
seed_cache_dir "android-ndk-r${NDK_REVISION}"
for source_name in "ffmpeg-$FFMPEG_VERSION" "lame-$LAME_VERSION" x264 kvazaar; do
    if [[ -e "$NATIVE_DIR/sources/$source_name" && ! -e "$SOURCES/$source_name" ]]; then
        cp -a "$NATIVE_DIR/sources/$source_name" "$SOURCES/$source_name"
    fi
done

download() {
    local url="$1" dest="$2"
    if [[ ! -s "$dest" ]]; then
        echo "Downloading $(basename "$dest")"
        curl --fail --location --retry 3 --output "$dest.part" "$url"
        mv "$dest.part" "$dest"
    fi
}

verify_sha256() {
    local file="$1" expected="$2"
    printf '%s  %s\n' "$expected" "$file" | sha256sum --check --status || fail "SHA-256 verification failed: $file"
}

if [[ ! -x "$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android${ANDROID_API}-clang" ]]; then
    download "$NDK_URL" "$DOWNLOADS/android-ndk-r${NDK_REVISION}-linux.zip"
    printf '%s  %s\n' "$NDK_SHA1" "$DOWNLOADS/android-ndk-r${NDK_REVISION}-linux.zip" | sha1sum --check --status || fail "Android NDK SHA-1 verification failed."
    echo "Extracting Android NDK $NDK_VERSION"
    unzip -q -o "$DOWNLOADS/android-ndk-r${NDK_REVISION}-linux.zip" -d "$WSL_CACHE_DIR"
fi
[[ -x "$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android${ANDROID_API}-clang" ]] || fail "NDK extraction did not produce the expected toolchain."

TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
SYSROOT="$TOOLCHAIN/sysroot"
CC="$TOOLCHAIN/bin/aarch64-linux-android${ANDROID_API}-clang"
CXX="$TOOLCHAIN/bin/aarch64-linux-android${ANDROID_API}-clang++"
AR="$TOOLCHAIN/bin/llvm-ar"
RANLIB="$TOOLCHAIN/bin/llvm-ranlib"
STRIP="$TOOLCHAIN/bin/llvm-strip"
JOBS="${TRANSVERSA_BUILD_JOBS:-$(nproc)}"
export ANDROID_NDK_ROOT="$NDK" ANDROID_NDK="$NDK"
export CC CXX AR RANLIB STRIP
export CFLAGS="--sysroot=$SYSROOT -fPIC -O2 -fvisibility=hidden"
export CXXFLAGS="$CFLAGS"
export LDFLAGS="--sysroot=$SYSROOT"
export PKG_CONFIG_PATH="$PREFIX/lib/pkgconfig"
export PKG_CONFIG_LIBDIR="$PREFIX/lib/pkgconfig"
unset PKG_CONFIG_SYSROOT_DIR

FFMPEG_ARCHIVE="$DOWNLOADS/ffmpeg-$FFMPEG_VERSION.tar.xz"
LAME_ARCHIVE="$DOWNLOADS/lame-$LAME_VERSION.tar.gz"
download "https://ffmpeg.org/releases/ffmpeg-$FFMPEG_VERSION.tar.xz" "$FFMPEG_ARCHIVE"
download "https://downloads.sourceforge.net/project/lame/lame/$LAME_VERSION/lame-$LAME_VERSION.tar.gz" "$LAME_ARCHIVE"
verify_sha256 "$FFMPEG_ARCHIVE" "$FFMPEG_SHA256"
verify_sha256 "$LAME_ARCHIVE" "$LAME_SHA256"

if [[ ! -d "$SOURCES/ffmpeg-$FFMPEG_VERSION" ]]; then tar -xf "$FFMPEG_ARCHIVE" -C "$SOURCES"; fi
if ! grep -q "attribute_visibility_hidden" "$SOURCES/ffmpeg-$FFMPEG_VERSION/libavutil/tx_template.c"; then
    python3 -c "p='$SOURCES/ffmpeg-$FFMPEG_VERSION/libavutil/tx_template.c'; c=open(p).read(); c=c.replace('#include \"mem.h\"', '#include \"mem.h\"\n#include \"attributes_internal.h\"').replace('DECLARE_ALIGNED(32, TXSample, TX_TAB(ff_tx_tab_ ##name))[size]', 'attribute_visibility_hidden DECLARE_ALIGNED(32, TXSample, TX_TAB(ff_tx_tab_ ##name))[size]'); open(p,'w').write(c)"
fi
if [[ ! -d "$SOURCES/lame-$LAME_VERSION" ]]; then tar -xf "$LAME_ARCHIVE" -C "$SOURCES"; fi
if [[ ! -d "$SOURCES/x264/.git" ]]; then
    mkdir -p "$SOURCES/x264"
    git -C "$SOURCES/x264" init -q
    git -C "$SOURCES/x264" remote add origin https://code.videolan.org/videolan/x264.git
fi
git -C "$SOURCES/x264" fetch --quiet --depth 1 origin "$X264_COMMIT"
git -C "$SOURCES/x264" checkout --quiet --detach FETCH_HEAD
[[ "$(git -C "$SOURCES/x264" rev-parse HEAD)" == "$X264_COMMIT" ]] || fail "x264 checkout does not match the pinned commit."
if [[ ! -d "$SOURCES/kvazaar/.git" ]]; then
    mkdir -p "$SOURCES/kvazaar"
    git -C "$SOURCES/kvazaar" init -q
    git -C "$SOURCES/kvazaar" remote add origin https://github.com/ultravideo/kvazaar.git
fi
git -C "$SOURCES/kvazaar" fetch --quiet --depth 1 origin "$KVAZAAR_COMMIT"
git -C "$SOURCES/kvazaar" checkout --quiet --detach FETCH_HEAD
[[ "$(git -C "$SOURCES/kvazaar" rev-parse HEAD)" == "$KVAZAAR_COMMIT" ]] || fail "Kvazaar checkout does not match the pinned commit."

if [[ ! -s "$PREFIX/lib/libx264.a" ]]; then
    echo "Building x264 for arm64-v8a"
    mkdir -p "$WORK/x264"
    pushd "$WORK/x264" >/dev/null
    if [[ -f Makefile ]]; then make distclean; fi
    "$SOURCES/x264/configure" \
        --prefix="$PREFIX" --host=aarch64-linux-android --sysroot="$SYSROOT" \
        --enable-static --enable-pic --disable-cli --disable-opencl
    make -j"$JOBS"
    make install
    popd >/dev/null
fi

if [[ ! -s "$PREFIX/lib/libkvazaar.a" ]]; then
    echo "Building Kvazaar $KVAZAAR_VERSION for arm64-v8a"
    cmake -S "$SOURCES/kvazaar" -B "$WORK/kvazaar" -G Ninja \
        -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM="android-$ANDROID_API" \
            -DANDROID_NDK="$NDK" -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_INSTALL_PREFIX="$PREFIX" -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
        -DBUILD_SHARED_LIBS=OFF -DBUILD_TESTS=OFF -DBUILD_KVAZAAR_BINARY=OFF
    cmake --build "$WORK/kvazaar" --parallel "$JOBS"
    cmake --install "$WORK/kvazaar"
fi
KVAZAAR_PC="$PREFIX/lib/pkgconfig/kvazaar.pc"
if [[ -f "$KVAZAAR_PC" ]] && ! grep -Eq '^Libs\.private:.*(^|[[:space:]])-lm([[:space:]]|$)' "$KVAZAAR_PC"; then
    if grep -q '^Libs\.private:' "$KVAZAAR_PC"; then
        sed -i '/^Libs\.private:/ s/$/ -lm/' "$KVAZAAR_PC"
    else
        printf '\nLibs.private: -lm\n' >> "$KVAZAAR_PC"
    fi
fi

if [[ ! -s "$PREFIX/lib/libmp3lame.a" ]]; then
    echo "Building LAME for arm64-v8a"
    mkdir -p "$WORK/lame"
    pushd "$WORK/lame" >/dev/null
    if [[ -f Makefile ]]; then make distclean; fi
    "$SOURCES/lame-$LAME_VERSION/configure" \
        --host=aarch64-linux-android --prefix="$PREFIX" \
        --enable-static --disable-shared --disable-frontend --disable-decoder
    make -j"$JOBS"
    make install
    popd >/dev/null
fi

# Bump this suffix whenever the required FFmpeg configure feature set changes.
FFMPEG_FINGERPRINT="ffmpeg-$FFMPEG_VERSION-x264-$X264_COMMIT-kvazaar-$KVAZAAR_COMMIT-lame-$LAME_VERSION-ndk-$NDK_VERSION-api-$ANDROID_API-png-zlib-v2"
FFMPEG_FINGERPRINT_FILE="$PREFIX/ffmpeg-build-fingerprint.txt"
if [[ ! -f "$FFMPEG_FINGERPRINT_FILE" || "$(<"$FFMPEG_FINGERPRINT_FILE")" != "$FFMPEG_FINGERPRINT" ]]; then
    rm -f "$PREFIX"/lib/libavformat.a "$PREFIX"/lib/libavcodec.a \
        "$PREFIX"/lib/libavfilter.a "$PREFIX"/lib/libswscale.a \
        "$PREFIX"/lib/libswresample.a "$PREFIX"/lib/libavutil.a \
        "$PREFIX"/lib/libavdevice.a "$PREFIX"/lib/pkgconfig/libav*.pc
fi

if [[ ! -s "$PREFIX/lib/libavcodec.a" ]]; then
    echo "Building FFmpeg for arm64-v8a"
    mkdir -p "$WORK/ffmpeg"
    pushd "$WORK/ffmpeg" >/dev/null
    if [[ -f Makefile ]]; then make distclean; fi
    PKG_CONFIG="$(command -v pkg-config)"
    "$SOURCES/ffmpeg-$FFMPEG_VERSION/configure" \
        --prefix="$PREFIX" --target-os=android --arch=aarch64 --cpu=armv8-a \
        --enable-cross-compile --sysroot="$SYSROOT" --cc="$CC" --cxx="$CXX" \
        --ar="$AR" --ranlib="$RANLIB" --strip="$STRIP" \
        --pkg-config="$PKG_CONFIG" --pkg-config-flags=--static \
        --extra-cflags="-I$PREFIX/include -fPIC -fvisibility=hidden" --extra-ldflags="-L$PREFIX/lib" \
        --enable-gpl --enable-libx264 --enable-libkvazaar --enable-libmp3lame \
        --enable-zlib --enable-encoder=png \
        --enable-jni --enable-mediacodec --enable-pic --enable-static --disable-shared \
        --disable-programs --disable-doc --disable-debug --disable-network \
        --disable-devices --disable-autodetect --disable-w32threads --disable-os2threads
    make -j"$JOBS"
    make install
    popd >/dev/null
fi

for lib in avformat avcodec avfilter swscale swresample avutil x264 kvazaar mp3lame; do
    [[ -s "$PREFIX/lib/lib$lib.a" ]] || fail "Expected static library lib$lib.a was not installed."
done

mkdir -p "$PREFIX/share/transversa/licenses"
cp "$SOURCES/ffmpeg-$FFMPEG_VERSION/COPYING.GPLv2" "$PREFIX/share/transversa/licenses/FFmpeg-COPYING.GPLv2"
cp "$SOURCES/ffmpeg-$FFMPEG_VERSION/COPYING.GPLv3" "$PREFIX/share/transversa/licenses/FFmpeg-COPYING.GPLv3"
cp "$SOURCES/ffmpeg-$FFMPEG_VERSION/COPYING.LGPLv2.1" "$PREFIX/share/transversa/licenses/FFmpeg-COPYING.LGPLv2.1"
cp "$SOURCES/x264/COPYING" "$PREFIX/share/transversa/licenses/x264-COPYING"
cp "$SOURCES/kvazaar/LICENSE" "$PREFIX/share/transversa/licenses/kvazaar-LICENSE"
find "$SOURCES/lame-$LAME_VERSION" -maxdepth 1 -iname 'COPYING*' -exec cp -t "$PREFIX/share/transversa/licenses" {} +
{
    printf 'FFmpeg %s\nx264 %s\nKvazaar %s (commit %s)\nLAME %s\nAndroid NDK %s\nABI arm64-v8a\nAPI %s\n' \
        "$FFMPEG_VERSION" "$X264_COMMIT" "$KVAZAAR_VERSION" "$KVAZAAR_COMMIT" "$LAME_VERSION" "$NDK_VERSION" "$ANDROID_API"
    printf '\nFFmpeg configure flags:\n'
    cat "$WORK/ffmpeg/ffbuild/config.mak" | sed -n 's/^FFMPEG_CONFIGURATION=//p'
} > "$PREFIX/build-manifest.txt"
printf '%s\n' "$FFMPEG_FINGERPRINT" > "$FFMPEG_FINGERPRINT_FILE"

echo "Native libraries are ready at: $PREFIX"
echo "Run Gradle :app:assembleDebug to compile and package the JNI bridge."
