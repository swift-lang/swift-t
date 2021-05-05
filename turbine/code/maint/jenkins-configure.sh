
# JENKINS CONFIGURE

# Should be sourced by jenkins.sh and jenkins-tests.zsh;
#  both configure/make from scratch

# Jenkins - important variables
C_UTILS=/tmp/exm-install/c-utils
ADLB=/tmp/exm-install/lb
TURBINE=/tmp/exm-install/turbine
PATH=${PATH}:$TURBINE/bin

rm -rf autom4te.cache
./bootstrap

set -x

ls /tmp/exm-install
ls /tmp/exm-install/c-utils

./configure --prefix=$TURBINE        \
            --with-tcl=/usr          \
            --with-c-utils=$C_UTILS  \
            --with-adlb=$ADLB        \
            --with-hdf5=no           \
            --disable-static-pkg     \
            --disable-static         \
            --with-python-exe=$(which python3)

make clean
