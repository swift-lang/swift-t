
# Jenkins - configure and generate Makefile
# Should be sourced by jenkins.sh

MPICH=/tmp/mpich-install
C_UTILS=/tmp/exm-install/c-utils
ADLB=/tmp/exm-install/lb
TURBINE=/tmp/exm-install/turbine
PATH=${PATH}:$MPICH/bin

rm -rf autom4te.cache
./bootstrap

./configure --prefix=$TURBINE        \
            --with-tcl=/usr          \
            --with-mpi=$MPICH        \
            --with-c-utils=$C_UTILS  \
            --with-adlb=$ADLB        \
            --with-hdf5=no           \
            --disable-static-pkg     \
            --disable-static

