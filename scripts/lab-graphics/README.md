# Private emulator graphics

This optional Linux x86_64 helper gives the pinned Android Lab emulator NVIDIA rendering on its private Xvfb display. Run `make stop`, `make lab-gpu`, then `make dikciz-run` from the repository root. Docker must already support NVIDIA GPU containers. Use `make lab-software` while the lab is stopped to restore the original renderer. See [source development](https://psyb0t.github.io/dikciz-launcher/source-development/#rendering-and-frame-measurements).

The builder extends the pinned tool image only to compile libraries, then exports a small runtime tree. It never replaces either Android Lab image. Build artifacts and selection remain under `.android-lab/graphics/`. The Compose overlay requests one NVIDIA GPU, mounts the runtime read-only and preserves the lab's existing device, display, network and CPU boundaries.

| Dependency | Pin and purpose |
| --- | --- |
| [VirtualGL](https://github.com/VirtualGL/virtualgl/releases/tag/3.1.5) | Version 3.1.5, source SHA-256 `8a3249c4fa8eea928362ca97b8fe16e5b356e3a18cd16d87e3efa72a870750b6`. Supplies the EGL-backed OpenGL interposer and launcher. |
| [EGL status correction](https://github.com/VirtualGL/virtualgl/commit/a9250f03be6da067472c48a6ad216d884612ced8) | Reviewed upstream correction applied with zero fuzz. The three `GLX_MAX_PBUFFER_*` queries must return `Success`, rather than `EGL_TRUE`, or the emulator rejects every framebuffer configuration. Remove the patch when upgrading to a release containing the fix. |
| Ubuntu build libraries | Exact direct package versions in `Dockerfile`, fetched through the tool image's signed Ubuntu repositories. Runtime copies contain only required graphics loaders and TurboJPEG. |
| Lab entrypoint | `../android-lab-renderer.patch` changes only the emulator invocation and renderer. The extension patches a generated copy and mounts it read-only. A patch mismatch stops startup. Recheck it when upgrading Android Lab. |

VirtualGL's license and LGPL text are included in the exported runtime's `share/` directory. [VirtualGL licensing](https://github.com/VirtualGL/virtualgl/blob/3.1.5/LICENSE.txt) governs these libraries. They are development tools and are not packaged in the Dikciz APK.

Measure the actual gesture with the focused drag selector in the development guide. Retain the full `gfxinfo framestats` output and renderer logs. Changing the screen resolution or hiding missed frames invalidates comparisons.
