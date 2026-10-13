
# SETTINGS.SED
# sed substitutions for swift-t-settings.sh - see build-generic.sh

# Under Anaconda, we have to be very careful here.
# Point Turbine at the launcher and tclsh in the host $PREFIX (provided
# by the meta.yaml host: section: mpich and tk).  $PREFIX is expanded when
# swift-t-settings.sh is sourced at build time; because these live in the
# host prefix (not the build env) Conda relocates them into the install
# environment, so the installed turbine-config.sh is relocatable.
# init-settings.sh copies swift-t-settings.sh FROM
# dev/build/swift-t-settings.sh.template (NOT the unused
# swift-t-settings.sh.template.conda, which looks similar but is dead --
# do not anchor on it). That real template has NO uncommented LAUNCHER= or
# TCLSH= line, only commented examples ("# LAUNCHER=mpiexec",
# "# TCLSH_LOCAL=/usr/bin/tclsh" -- note the different variable name), so
# each value is appended after MPI_VERSION=3 (the nearest known-unique
# anchor) instead of rewriting a line in place -- same trick as
# PYTHON_EXE/R_INSTALL below. Both lines anchor on MPI_VERSION
# INDEPENDENTLY (not chained off each other's inserted line): sed anchors
# the ^ in a later command to the start of the whole pattern space, not to
# after an embedded newline inserted by an earlier substitution in the
# same cycle, so a second sed anchored on the line the first one just
# inserted never matches and silently appends nothing -- verified the hard
# way: 2026-10-13
# TCLSH is derived from whatever tclsh binary the host tk package actually
# provides (tclsh8.6, tclsh9.0, ...) rather than hardcoded, so a SPEC_TK
# bump (deps.sh) does not require a matching edit here.
# Use a command substitution
# instead, with "head -n1" as the pipeline's LAST stage so a glob miss
# (ls exits non-zero) still leaves the substitution's own exit status at
# 0 under set -e (no pipefail in this bash script, so only the last
# stage's status counts): 2026-10-13
s@.*LAUNCHER=.*@LAUNCHER=$PREFIX/bin/mpiexec@
s@.*TCLSH=.*@m4_getenv(USE_TCLSH)@

s@SWIFT_T_PREFIX=.*@SWIFT_T_PREFIX=$PREFIX/swift-t@

# Enable Python AND pin PYTHON_EXE to the host $PREFIX python (relocatable).
# swift-t-settings.sh has no uncommented PYTHON_EXE="" line to anchor on
# (unlike LAUNCHER/TCLSH above), so append the assignment after ENABLE_PYTHON.
# Without PYTHON_EXE set, build-turbine.sh passes --enable-python and Turbine
# auto-detects $BUILD_PREFIX/bin/python, baking a non-relocatable _build_env
# path into turbine-config.sh (TURBINE_USE_PYTHON) -> "python in PATH does
# not match the Python used at compile time".  python is in the meta.yaml
# host: section, so $PREFIX/bin/python exists at build and Conda relocates it.
s@ENABLE_PYTHON=0@ENABLE_PYTHON=1@
s@.*PYTHON_EXE.*@PYTHON_EXE="$PREFIX/bin/python"@
s@PARALLELISM=.*@PARALLELISM=4@
s@ENABLE_CONDA_LINUX=.*@ENABLE_CONDA_LINUX="$ENABLE_CONDA_LINUX"@
m4_ifelse(m4_getenv(ENABLE_R),`1',`m4_include(settings-R.sed)')

# Local Variables:
# mode: sh;
# End:
