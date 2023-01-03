
# JENKINS MPICH SH
# Build MPICH for GCE Jenkins

set -eu

renice --priority 19 --pid $$

rm -rfv mpich-4.0.3.tar.gz mpich-4.0.3/  # Delete this
mkdir -pv src
cd src
rm -rfv mpich-4.0.3.tar.gz mpich-4.0.3/

wget --no-verbose https://www.mpich.org/static/downloads/4.0.3/mpich-4.0.3.tar.gz
tar xfz mpich-4.0.3.tar.gz
cd mpich-4.0.3
CFG=( --prefix=$WORKSPACE/sfw/mpich-4.0.3
      --with-pm=hydra
      --enable-shared
      --with-enable-g=dbg,meminit
      --disable-fortran
      --disable-cxx
)

set -x
./configure ${CFG[@]}
make
make install
