#!/usr/bin/env bash
set -e

THISDIR=$( dirname $0 )
source ${THISDIR}/swift-t-settings.sh

if (( RUN_AUTOTOOLS )); then
  ./bootstrap
elif [ ! -f configure ]; then
  ./bootstrap
fi

EXTRA_ARGS=
if (( SWIFT_T_OPT_BUILD )); then
    EXTRA_ARGS+=" --enable-fast"
fi

if (( ENABLE_MPE )); then
    EXTRA_ARGS+=" --with-mpe"
fi

if (( SWIFT_T_STATIC_BUILD )); then
  EXTRA_ARGS+=" --disable-shared"
fi

if (( ENABLE_JVM_SCRIPTING )); then
  echo "JVM Scripting enabled"
  EXTRA_ARGS+=" --enable-jvm-scripting"
fi

if [ ! -z "$USE_JVM_SCRIPT_HOME" ]; then
  EXTRA_ARGS+=" --with-jvm-scripting=${USE_JVM_SCRIPT_HOME}"
fi

if (( ENABLE_PYTHON )); then
  EXTRA_ARGS+=" --enable-python"
fi

if (( ${#PYTHON_EXE} > 0 )); then
  EXTRA_ARGS+=" --with-python-exe=${PYTHON_EXE}"
fi

if (( ENABLE_R )); then
  EXTRA_ARGS+=" --enable-r"
fi
if [ ! -z "$R_INSTALL" ]; then
  EXTRA_ARGS+=" --with-r=${R_INSTALL}"
fi

if [ ! -z "$RINSIDE_INSTALL" ]; then
  EXTRA_ARGS+=" --with-rinside=${RINSIDE_INSTALL}"
fi

if [ ! -z "$RCPP_INSTALL" ]; then
  EXTRA_ARGS+=" --with-rcpp=${RCPP_INSTALL}"
fi

if (( ENABLE_JULIA )); then
  if [ ! -z "$JULIA_INSTALL" ]; then
    EXTRA_ARGS+=" --with-julia=${JULIA_INSTALL}"
  else
    echo "Have to specify julia install directory if enabling"
    exit 1
  fi
fi

if [ ! -z "$COASTER_INSTALL" ]; then
  EXTRA_ARGS+=" --with-coaster=${COASTER_INSTALL}"
fi

if [ ! -z "$TCL_INSTALL" ]; then
  EXTRA_ARGS+=" --with-tcl=${TCL_INSTALL}"
fi

if [ ! -z "$TCL_VERSION" ]; then
  EXTRA_ARGS+=" --with-tcl-version=$TCL_VERSION"
fi

if [ ! -z "$TCLSH_LOCAL" ]; then
  EXTRA_ARGS+=" --with-tcl-local=${TCLSH_LOCAL}"
fi

if [ ! -z "$TCL_LIB_DIR" ]; then
  EXTRA_ARGS+=" --with-tcl-lib-dir=${TCL_LIB_DIR}"
fi

if [ ! -z "$TCL_INCLUDE_DIR" ]; then
  EXTRA_ARGS+=" --with-tcl-include=${TCL_INCLUDE_DIR}"
fi

if [ ! -z "$TCL_SYSLIB_DIR" ]; then
  EXTRA_ARGS+=" --with-tcl-syslib-dir=${TCL_SYSLIB_DIR}"
fi

if (( DISABLE_XPT )); then
    EXTRA_ARGS+=" --enable-checkpoint=no"
fi

if (( SWIFT_T_DEV )); then
  EXTRA_ARGS+=" --enable-dev"
fi

if (( DISABLE_STATIC )); then
  EXTRA_ARGS+=" --disable-static"
fi

if [ ! -z "$MPI_INSTALL" ]; then
  EXTRA_ARGS+=" --with-mpi=${MPI_INSTALL}"
fi

if (( SWIFT_T_CUSTOM_MPI )); then
  EXTRA_ARGS+=" --enable-custom-mpi"
fi

if [ ! -z "$MPI_INCLUDE" ]; then
  EXTRA_ARGS+=" --with-mpi-include=${MPI_INCLUDE}"
fi

if [ ! -z "$MPI_LIB_DIR" ]; then
  EXTRA_ARGS+=" --with-mpi-lib-dir=${MPI_LIB_DIR}"
fi

if [ ! -z "$MPI_LIB_NAME" ]; then
  EXTRA_ARGS+=" --with-mpi-lib-name=${MPI_LIB_NAME}"
fi

if (( DISABLE_ZLIB )); then
  EXTRA_ARGS+=" --without-zlib --disable-checkpoint"
fi

if [ ! -z "$ZLIB_INSTALL" ]; then
  EXTRA_ARGS+=" --with-zlib=$ZLIB_INSTALL"
fi

if (( ENABLE_MKSTATIC_CRC )); then
  EXTRA_ARGS+=" --enable-mkstatic-crc-check"
fi

if [ -z "$WITH_HDF5" ]; then
  EXTRA_ARGS+=" --with-hdf5=no"
else
  EXTRA_ARGS+=" --with-hdf5=$WITH_HDF5"
fi

if (( CONFIGURE )); then
  echo ${USE_JVM_SCRIPT_HOME}
  if (( ENABLE_JVM_SCRIPTING )); then
    mvn -f ${USE_JVM_SCRIPT_HOME}/swift-jvm/pom.xml clean
    mvn -f ${USE_JVM_SCRIPT_HOME}/swift-jvm/pom.xml package -Dmaven.test.skip=true
  fi
  rm -f config.cache
  (
    set -ex
    ./configure --config-cache \
                --with-adlb=${LB_INSTALL} \
                ${CRAY_ARGS} \
                --with-c-utils=${C_UTILS_INSTALL} \
                --prefix=${TURBINE_INSTALL} \
                ${EXTRA_ARGS} \
                --disable-log
    )
fi

if (( ! RUN_MAKE )); then
  exit
fi

if (( MAKE_CLEAN ))
then
  rm -fv deps_contents.txt
  rm -fv config.cache
  if [ -f Makefile ]
  then
    make clean
  fi
fi

if ! make -j ${MAKE_PARALLELISM}
then
  echo
  echo Make failed.  The following may be useful:
  echo
  set -x
  rm -fv deps_contents.txt
  make check_includes
  exit 1
fi

make install
