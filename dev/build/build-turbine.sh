#!/usr/bin/env bash
set -eu

# BUILD TURBINE

THIS=$(   dirname  $0 )
SCRIPT=$( basename $0 )

${THIS}/check-settings.sh
source ${THIS}/functions.sh
source ${THIS}/options.sh
source ${THIS}/swift-t-settings.sh

[[ $SKIP == *T* ]] && exit

LOG $LOG_INFO "Building Turbine"
cd ${TURBINE_SRC}

run_bootstrap

EXTRA_ARGS=""

if (( ENABLE_MPE )); then
    EXTRA_ARGS+=" --with-mpe"
fi

if (( ENABLE_PYTHON )); then
  if (( ${#PYTHON_EXE} > 0 )); then
    EXTRA_ARGS+=" --with-python-exe=${PYTHON_EXE}"
  else
    EXTRA_ARGS+=" --enable-python"
  fi
fi

if (( ENABLE_R )); then
  EXTRA_ARGS+=" --enable-r"
fi
if [[ ${R_INSTALL:-} != "" ]]; then
  EXTRA_ARGS+=" --with-r=${R_INSTALL}"
fi

if [[ ${RINSIDE_INSTALL:-} != "" ]]; then
  EXTRA_ARGS+=" --with-rinside=${RINSIDE_INSTALL}"
fi

if [[ ${RCPP_INSTALL:-} != "" ]]
then
  EXTRA_ARGS+=" --with-rcpp=${RCPP_INSTALL}"
fi

if (( ENABLE_JULIA )); then
  if [[ ${JULIA_INSTALL:-} != "" ]]; then
    EXTRA_ARGS+=" --with-julia=${JULIA_INSTALL}"
  else
    echo "Have to specify julia install directory if enabling"
    exit 1
  fi
fi

if (( ENABLE_JVM_SCRIPTING )); then
  echo "JVM Scripting enabled"
  EXTRA_ARGS+=" --enable-jvm-scripting"
fi

if [[ "${USE_JVM_SCRIPT_HOME:-}" != "" ]]; then
  EXTRA_ARGS+=" --with-jvm-scripting=${USE_JVM_SCRIPT_HOME}"
fi

if [[ "${TCL_INSTALL:-}" != "" ]]; then
  EXTRA_ARGS+=" --with-tcl=${TCL_INSTALL}"
fi

if [[ "${TCL_VERSION:-}" != "" ]]; then
  EXTRA_ARGS+=" --with-tcl-version=$TCL_VERSION"
fi

if [[ "${TCLSH_LOCAL:-}" != "" ]]; then
  EXTRA_ARGS+=" --with-tclsh-local=${TCLSH_LOCAL}"
fi

if [[ "${TCL_LIB_DIR:-}" != "" ]]; then
  EXTRA_ARGS+=" --with-tcl-lib-dir=${TCL_LIB_DIR}"
fi

if [[ "${TCL_INCLUDE_DIR:-}" != "" ]]; then
  EXTRA_ARGS+=" --with-tcl-include=${TCL_INCLUDE_DIR}"
fi

if [[ "${TCL_SYSLIB_DIR:-}" != "" ]]; then
  EXTRA_ARGS+=" --with-tcl-syslib-dir=${TCL_SYSLIB_DIR}"
fi

if (( DISABLE_XPT )); then
    EXTRA_ARGS+=" --enable-checkpoint=no"
fi

if (( SWIFT_T_DEV )); then
  EXTRA_ARGS+=" --enable-dev"
fi

if (( DISABLE_STATIC_PKG )); then
  EXTRA_ARGS+=" --disable-static-pkg"
fi

if [[ "${MPI_INSTALL:-}" != "" ]]; then
  EXTRA_ARGS+=" --with-mpi=${MPI_INSTALL}"
fi

if (( ! SWIFT_T_CHECK_MPICC ))
then
  EXTRA_ARGS+=" --disable-mpi-checks"
fi

if (( SWIFT_T_CUSTOM_MPI )); then
  EXTRA_ARGS+=" --enable-custom-mpi"
fi

if [[ "${MPI_INCLUDE:-}" != "" ]]; then
  EXTRA_ARGS+=" --with-mpi-include=${MPI_INCLUDE}"
fi

if [[ "${MPI_LIB_DIR:-}" != "" ]]; then
  EXTRA_ARGS+=" --with-mpi-lib-dir=${MPI_LIB_DIR}"
fi

if [[ "${MPI_LIB_NAME:-}" != "" ]]; then
  EXTRA_ARGS+=" --with-mpi-lib-name=${MPI_LIB_NAME}"
fi

if (( DISABLE_ZLIB )); then
  EXTRA_ARGS+=" --without-zlib --disable-checkpoint"
fi

if [[ "${ZLIB_INSTALL:-}" != "" ]]; then
  EXTRA_ARGS+=" --with-zlib=$ZLIB_INSTALL"
fi

if (( ENABLE_MKSTATIC_CRC )); then
  EXTRA_ARGS+=" --enable-mkstatic-crc-check"
fi

if [[ "${WITH_HDF5:-}" == "" ]]; then
  EXTRA_ARGS+=" --with-hdf5=no"
else
  EXTRA_ARGS+=" --with-hdf5=$WITH_HDF5"
fi

common_args

if (( RUN_CONFIGURE )) || [[ ! -f Makefile ]]
then
  if (( ENABLE_JVM_SCRIPTING )); then
    mvn -f ${USE_JVM_SCRIPT_HOME}/swift-jvm/pom.xml clean
    mvn -f ${USE_JVM_SCRIPT_HOME}/swift-jvm/pom.xml package -Dmaven.test.skip=true
  fi
  rm -f config.cache
  (
    set -ex
    ./configure --config-cache \
                --prefix=${TURBINE_INSTALL} \
                --with-c-utils=${C_UTILS_INSTALL} \
                --with-adlb=${LB_INSTALL} \
                ${EXTRA_ARGS} \
                --disable-log
    )
  assert $? "Configure failed!"
fi

report_turbine_includes()
{
  echo
  echo Make failed.  The following may be useful:
  echo
  set -x
  rm -fv deps_contents.txt
  make check_includes
  exit 1
}

check_make
make_clean
if ! make_all
then
  report_turbine_includes
fi
make_install
