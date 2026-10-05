
# osx-arm64 DEPS SH

USE_ANT=1
USE_CLANG=1
USE_GCC=0
USE_TK=1
USE_ZSH=0

# We export the SPECs so that M4 can use them in m4_getenv()

if [[ ${PYTHON_VERSION} == 3.13.* ]] {
  PYTHON_VERSION=3.13.2
  export SPEC_PYTHON="python==$PYTHON_VERSION"
}

# Prevent MPICH from updating Clang to 19
# SPEC_CLANG='clang==16.0.6'
export SPEC_CLANG='clang==18.1.8'
export SPEC_MPICH='mpich==4.1.2'
# Fix for strstr issue- there is bad behavior by package tk-8.6.13-*
#     in the Anaconda repos.  See below.
export SPEC_TK='tk>=8.6.15'

# This is a macOS two-level-namespace symbol-binding bug, triggered by a bad tk==8.6.13 conda build.

# The mechanism:
# 1. On macOS, each undefined symbol in a dylib is recorded with which library it should come from. The linker assigns that to the first library on
#    the link line that exports the symbol.
# 2. The link command (build log line 871–873) passes -ltcl8.6 before libSystem is implicitly added:
# mpicxx -dynamiclib -ltcl8.6 -o lib/libtclturbine.dylib ... -ltcl8.6 ...
# 3. On the GitHub runner, the libtcl8.6.dylib it linked against exported strstr (Tcl bundles a compat/strstr.c). So the linker bound
#    libtclturbine's _strstr to libtcl8.6.dylib instead of libc.
# 4. At runtime, the loaded libtcl8.6.dylib does not export strstr → Symbol not found: _strstr ... Expected in ... libtcl8.6.dylib.

# Why you couldn't reproduce it locally: I extracted your local /tmp/woz/311-B/.../swift-t-r-1.6.9-py311_2.conda and checked it — in your build,
# _strstr binds correctly to libSystem (lazy-bind libSystem/_strstr). Your package is fine. The difference is purely the Tcl that was present at
# link time.

# Confirmed smoking gun — the tk==8.6.13 pin is not unique across build strings, and they disagree:

# ┌────────────────────────────────────────────────┬─────────────────┐
# │                    tk build                    │ exports strstr? │
# ├────────────────────────────────────────────────┼─────────────────┤
# │ tk-8.6.13-h892fb3f_3, -hd3d0363_3, -h010d191_3 │ YES (breaks)    │
# ├────────────────────────────────────────────────┼─────────────────┤
# │ tk-8.6.13-hbeba79b_4, tk-8.6.15-*              │ no (works)      │
# └────────────────────────────────────────────────┴─────────────────┘

# Your local build resolved to hbeba79b_4 (good); GitHub pulled a _3 build (bad). The pin lives in dev/conda/osx-arm64/deps.sh:21 →
# SPEC_TK='tk==8.6.13'.

# Fix options

# 1. Tighten the tk pin (quickest, targeted). In deps.sh, pin to the good build so GitHub can't pick a _3:
# export SPEC_TK='tk==8.6.13=hbeba79b_4'   # or simply tk>=8.6.15
# 2. Fix it at the source so no tk build can poison the link (most robust). Force two-level bind of libc symbols to libSystem by searching it
# before -ltcl8.6, e.g. add -lSystem early, or (simplest and historically used here) re-enable flat namespace on Mac — turbine/code/Makefile.in:139
# has MAC_FLAT = # -Wl,-flat_namespace deliberately commented out. The real reason -ltcl8.6 appears before libSystem should also be looked at;
# that ordering is what lets Tcl capture libc symbols.
