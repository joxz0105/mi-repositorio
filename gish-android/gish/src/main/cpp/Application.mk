# ABIs and platform are injected by Gradle (abiFilters / APP_PLATFORM), so they
# are deliberately not pinned here. This file exists for the settings ndk-build
# cannot take from the Gradle DSL.

APP_STL := none
APP_CFLAGS += -Wno-implicit-function-declaration
APP_CPPFLAGS += -frtti

# The vendored SDL2/OpenAL/Gish sources predate modern clang defaults; these
# warnings are noise here and would otherwise bury real diagnostics.
APP_CFLAGS += -Wno-deprecated-declarations -Wno-pointer-sign
