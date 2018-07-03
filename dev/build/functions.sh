
# FUNCTIONS SH

# Code reuse for build scripts

if (( ${FUNCTIONS_DONE:-0} ))
then
  return
fi

export LOG_FATAL=0
export LOG_WARN=1
export LOG_INFO=2
export LOG_DEBUG=3

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

run_bootstrap()
{
  if (( RUN_BOOTSTRAP )) || [[ ! -f configure ]]
  then
    rm -rfv config.cache config.status
    rm -rf  autom4te.cache
    ./bootstrap
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
  make -j ${MAKE_PARALLELISM} ${MAKE_QUIET}
}

make_install()
{
  if (( RUN_MAKE_INSTALL ))
  then
    make ${MAKE_QUIET} install
  fi
}

FUNCTIONS_DONE=1
