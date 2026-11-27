ARG ANDROID_LAB_VERSION=0.16.0
FROM android-lab:${ANDROID_LAB_VERSION} AS build
USER root
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential=12.10ubuntu1 cmake=3.28.3-1build7 \
    libegl-dev=1.7.0-1build1 libglu1-mesa-dev=9.0.2-1.1build1 \
    libturbojpeg0-dev=1:2.1.5-2ubuntu2 \
    libx11-dev=2:1.8.7-1ubuntu0.1 libx11-xcb-dev=2:1.8.7-1ubuntu0.1 \
    libxcb-glx0-dev=1.15-1ubuntu2 libxcb-keysyms1-dev=0.4.0-1build4 \
    libxtst-dev=2:1.2.3-1.1ubuntu0.1 libxv-dev=2:1.0.11-1.1build1
ADD --checksum=sha256:8a3249c4fa8eea928362ca97b8fe16e5b356e3a18cd16d87e3efa72a870750b6 \
    https://github.com/VirtualGL/virtualgl/releases/download/3.1.5/VirtualGL-3.1.5.tar.gz /tmp/source.tar.gz
COPY virtualgl-egl-status.patch /tmp/fix.patch
RUN mkdir /tmp/vgl && tar -xf /tmp/source.tar.gz --strip-components=1 -C /tmp/vgl \
    && cd /tmp/vgl && patch --batch --fuzz=0 -p1 < /tmp/fix.patch \
    && cmake -S . -B build -DCMAKE_INSTALL_LIBDIR=lib -DVGL_FAKEOPENCL=OFF \
        -DTJPEG_INCLUDE_DIR=/usr/include \
        -DTJPEG_LIBRARY=/usr/lib/x86_64-linux-gnu/libturbojpeg.so \
    && cmake --build build --target vglfaker dlfaker -j4 \
    && mkdir -p /graphics/bin /graphics/lib /graphics/share \
    && cp build/bin/vglrun /graphics/bin/ \
    && cp build/lib/libvglfaker.so build/lib/libdlfaker.so /graphics/lib/ \
    && cp -L /usr/lib/x86_64-linux-gnu/libEGL.so.1 \
        /usr/lib/x86_64-linux-gnu/libGLU.so.1 \
        /usr/lib/x86_64-linux-gnu/libOpenGL.so.0 \
        /usr/lib/x86_64-linux-gnu/libturbojpeg.so.0 /graphics/lib/ \
    && cp LICENSE.txt LGPL.txt /graphics/share/
FROM scratch AS runtime
COPY --from=build /graphics/ /
