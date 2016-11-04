
# Build command-line options

export RUN_MAKE=1

while getopts "cm" OPT
do
  case $OPT in
    c) MAKE_CLEAN=0 ;;
    m) RUN_MAKE=0   ;;
    ?) echo exit 1  ;;
  esac
done
