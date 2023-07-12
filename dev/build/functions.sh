
# FUNCTIONS SH

# Code reuse for build scripts

if (( ${FUNCTIONS_DONE:-0} ))
then
  return
fi

export LOG_FATAL=0
export LOG_WARN=1
export LOG_INFO=2 # Default
export LOG_DEBUG=3
export LOG_TRACE=4

assert()
{
  local CODE=$1 MESSAGE=$2
  if (( CODE != 0 ))
  then
    echo $MESSAGE
    echo CODE=$CODE
    exit 1
  fi
}

LOG()
{
  if (( ${#} == 0 ))
  then
    echo "msg(): requires LVL !"
    exit 1
  fi
  local LVL=$1
  shift
  if (( LVL <= VERBOSITY ))
  then
    echo $*
  fi
}

LOG_WAIT()
{
  # User can skip waits with WAIT=0
  if (( ${WAIT:-1} == 0 ))
  then
    return
  fi
  if [ -t 1 ] # Is the output a terminal?
  then
    echo "  waiting $* seconds: press enter to skip ..."
    if read -t $*
    then
      echo "  skipped."
    fi
  fi
}

log_status()
{
  if (( VERBOSITY > LOG_TRACE ))
  then
    VERBOSITY=$LOG_TRACE
  fi
  if (( VERBOSITY < LOG_FATAL ))
  then
    VERBOSITY=$LOG_FATAL
  fi

  if (( VERBOSITY == $LOG_DEBUG ))
  then
    echo "Logging at LOG_DEBUG"
  fi
  if (( VERBOSITY == $LOG_TRACE ))
  then
    echo "Logging at LOG_TRACE"
  fi
}

run_bootstrap()
{
  if (( RUN_BOOTSTRAP )) || [[ ! -f configure ]]
  then
    rm -rfv config.cache config.status
    rm -rf  autom4te.cache
    ./bootstrap
  fi
}

common_args()
{
  if (( SWIFT_T_OPT_BUILD )); then
    EXTRA_ARGS+="--enable-fast"
  fi

  if (( SWIFT_T_DEBUG_BUILD )); then
    export CFLAGS="-g -O0"
  fi

  if (( DISABLE_SHARED )); then
    EXTRA_ARGS+=" --disable-shared"
  fi

  if (( DISABLE_STATIC )); then
    EXTRA_ARGS+=" --disable-static"
  fi

  if [[ ${CROSS_HOST:-} != "" ]]
  then
    EXTRA_ARGS+=" --host=$CROSS_HOST"
  fi
}

check_make()
{
  if (( ! RUN_MAKE ))
  then
    exit
  fi

  MAKE_QUIET=""
  if (( VERBOSITY <= $LOG_WARN ))
  then
    MAKE_QUIET="--quiet"
  fi

  MAKE_V=""
  if (( VERBOSITY == $LOG_TRACE ))
  then
    MAKE_V="V=1"
  fi
}

make_clean()
{
  if (( RUN_MAKE_CLEAN ))
  then
    if [ -f Makefile ]
    then
      make clean
    fi
  fi
}

make_all()
{
  ${NICE_CMD} make -j ${MAKE_PARALLELISM} ${MAKE_V} ${MAKE_QUIET}
}

make_install()
{
  if (( RUN_MAKE_INSTALL ))
  then
    make ${MAKE_QUIET} install
  fi
}

check_lock()
{
  DIR=$1
  if [[ -f $DIR/lock ]]
  then
    echo "cannot install: lock exists: $DIR/lock"
    echo "                to unlock use 'lock.sh -u'"
    return 1
  fi
}

log_path()
# Pretty print a colon-separated variable
{
  echo ${1}:
  eval echo \$$1 | tr : '\n' | nl
}

FUNCTIONS_DONE=1
