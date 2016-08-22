
This directory contains shell scripts to configure and build Swift/T.

 swift-t-settings.sh.template to swift-t-settings.sh and then modify
the variables to match your directory structure and configuration.

./build-all.sh - build c-utils/adlb/turbine/stc from fresh source.
Copies swift-t-settings.sh.template to swift-t-settings.sh and then
allows the user to modify swift-t-settings.sh before the build begins.

./rebuild-all.sh - rebuilds c-utils/adlb/turbine/stc from scratch without
reinitializing swift-t-settings.sh

./fast-build-all.sh - does a build without reconfiguring

./mpi_build.sh - build MPICH from source

./tcl_build.sh - build Tcl from source
