Helper scripts to do a complete rebuild of ADLB/Turbine/STC in-place.

These are pretty rough and ready, but work for me.  Feel free to extend.

Copy build-vars.sh.template to build-vars.sh and then modify to match
your own directory structure.

./rebuild-all.sh - rebuilds c-utils/adlb/turbine/stc
./mpi_build.sh - build mpich2 with my config options
