
# OPTIONS.SH
# Parse command-line options into environment variables

if (( ${OPTIONS_DONE:-0} ))
then
  # We already parsed the options
  return
fi

source $THIS/help.sh

# Defaults
export RUN_BOOTSTRAP=1
export RUN_CONFIGURE=1
export RUN_MAKE=1
export RUN_MAKE_CLEAN=1
export RUN_MAKE_INSTALL=1
export SKIP=""
export VERBOSITY=$LOG_INFO

while getopts "BcCfhmqs:vy" OPTION
do
  case $OPTION in
    B) RUN_BOOTSTRAP=0      ;;
    c) RUN_MAKE_CLEAN=0     ;;
    C) RUN_BOOTSTRAP=0
       RUN_CONFIGURE=0      ;;
    f) # Fast
       RUN_BOOTSTRAP=0
       RUN_CONFIGURE=0
       RUN_MAKE_CLEAN=0     ;;
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
