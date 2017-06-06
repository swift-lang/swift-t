
# OPTIONS.SH
# Extract command-line options

# Defaults
export FORCE_BOOTSTRAP=0
export RUN_MAKE=1

while getopts "Bcm" OPT
do
  case $OPT in
    B) FORCE_BOOTSTRAP=1 ;;
    c) MAKE_CLEAN=0      ;;
    m) RUN_MAKE=0        ;;
    *) exit 1            ;; # Bash prints an error message
  esac
done
