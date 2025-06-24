
# JENKINS MPICH SH
# Build MPICH for GCE Jenkins
# Installs to:
# TARGET=/scratch/jenkins-slave/workspace/Swift-T-MPICH/sfw/mpich-4.0.3

set -eu

renice --priority 19 --pid $$

mkdir -pv src
cd src
(
  set -x
  rm -fv mpich-4.0.3.tar.gz
  rm -fv mpich-4.0.3/configure
  rm -fr mpich-4.0.3/
)

TARGET=$WORKSPACE/sfw/mpich-4.0.3
echo "Looking for $TARGET"
if [[ -d $TARGET ]]
then
  printf "\t exists.\n"
else
  printf "\t does not exist.\n"
fi

wget --no-verbose https://www.mpich.org/static/downloads/4.0.3/mpich-4.0.3.tar.gz
tar xmf mpich-4.0.3.tar.gz
cd mpich-4.0.3
CFG=( --prefix=$TARGET
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
