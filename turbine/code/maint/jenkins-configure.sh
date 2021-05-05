
# JENKINS CONFIGURE

# Should be sourced by jenkins.sh and jenkins-tests.zsh;
#  both configure/make from scratch

# Jenkins - important variables
EXM_INSTALL=/tmp/exm-install
C_UTILS=$EXM_INSTALL/c-utils
ADLB=$EXM_INSTALL/lb
TURBINE=$EXM_INSTALL/turbine
PATH=${PATH}:$TURBINE/bin

rm -rf autom4te.cache
./bootstrap

for D in $EXM_INSTALL $C_UTILS $ADLB
do
  if ! [[ -d $D ]]
  then
    echo "Directory not found: $D"
    return 1
  fi
done

./configure --prefix=$TURBINE        \
            --with-tcl=/usr          \
            --with-c-utils=$C_UTILS  \
            --with-adlb=$ADLB        \
            --with-hdf5=no           \
            --disable-static-pkg     \
            --disable-static         \
            --with-python-exe=$(which python3)

make clean
