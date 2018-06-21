
# FUNCTIONS SH

# Code reuse for build scripts

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

make_install()
{
  if (( RUN_MAKE_INSTALL ))
  then
    make install
  fi
}
