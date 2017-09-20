
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

./configure --prefix=$TURBINE        \
            --with-tcl=/usr          \
            --with-c-utils=$C_UTILS  \
            --with-adlb=$ADLB        \
            --with-hdf5=no           \
            --disable-static-pkg     \
            --disable-static

make clean
