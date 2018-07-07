
# OPTIONS.SH
# Parse command-line options into environment variables

if (( ${OPTIONS_DONE:-0} ))
then
  # We already parsed the options
  return
fi

help()
{
  cat <<EOF
usage: build-swift-t.sh [-Bcfhm]

build-swift-t.sh reads the swift-t-settings.sh file as edited by the user.

The C build process for c-utils, lb, and turbine is:
./bootstrap # Run autoconf, etc.
./configure ...
make
make install

For STC, it is simply:

ant ... install

The ... indicates various options, all set by swift-t-settings.sh .

With no options, build.sh only runs bootstrap
if configure does not exist.

Then, it runs configure, make, and make install.

The following options change this behavior:

-B     Force run ./bootstrap
-C     Do not run ./configure
-c     Do not 'make clean' or 'ant clean'
-f     Fast mode: do not run ./configure, do not 'make clean' or 'ant clean'
       Same as -Cc
-h     This help message
-m     Do not run 'make'
-s T|S Skip Turbine (T) or STC (S)
-q     Quiet: omit some output
-y     Do not run 'make install' (dry-run)

Later options override earlier options.

EOF
}

# Defaults
export RUN_BOOTSTRAP=0
export RUN_CONFIGURE=1
export RUN_MAKE=1
export RUN_MAKE_CLEAN=1
export RUN_MAKE_INSTALL=1
export SKIP=""
export VERBOSITY=$LOG_INFO

while getopts "BcCfhmqs:vy" OPTION
do
  case $OPTION in
    B) RUN_BOOTSTRAP=1      ;;
    c) RUN_MAKE_CLEAN=0     ;;
    C) RUN_CONFIGURE=0      ;;
    f) # Fast
       RUN_MAKE_CLEAN=0
       RUN_CONFIGURE=0      ;;
    h) help ; exit 0        ;;
    m) RUN_MAKE=0           ;;
    q) # Quiet
      : $(( VERBOSITY -- )) ;; # Do not error on zero (set -e)
    s) SKIP+=$OPTARG        ;;
    v) # Verbose
      : $(( VERBOSITY ++ )) ;; # Do not error on zero (set -e)
    y) # DrY run
       RUN_MAKE_INSTALL=0   ;;
    *) exit 1               ;; # Bash prints an error message
  esac
done

log_status

export OPTIONS_DONE=1
