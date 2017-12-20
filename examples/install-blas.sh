
# INSTALL BLAS

# Installs BLAS.  Can be run interactively or from Jenkins.

# Build BLAS
BV=3.6.0 # BLAS Version
export BLAS=/tmp/exm-blas-build/BLAS-$BV/BLAS.a
if [[ -f ${BLAS} ]]
then
  print "Found BLAS: ${BLAS}"
else
  print "Downloading BLAS..."
  mkdir -p /tmp/exm-blas-build
  pushd /tmp/exm-blas-build
  rm -fv blas.tgz
  wget http://www.netlib.org/blas/blas-$BV.tgz
  tar xfz blas-$BV.tgz
  cd BLAS-$BV
  echo "Compiling BLAS..."
  for f in *.f
  do
    gfortran -fPIC -c ${f}
  done
  ar cr BLAS.a *.o
  echo "BLAS successfully installed in ${PWD}"
  popd
fi
