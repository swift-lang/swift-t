
# OPTIONS.SH
# Extract command-line options

# Defaults
export RUN_BOOTSTRAP=0
export RUN_CONFIGURE=1
export RUN_MAKE=1
export MAKE_CLEAN=1

help()
{
  echo <<EOF
usage: build.sh [-Bcfhm]

The C build process for c-utils, lb, and turbine is:
./bootstrap # Run autoconf, etc.
./configure
make
make install

With no options, build.sh only runs bootstrap
if configure does not exist.

Then, it runs configure, make, and make install.

build.sh refers to the swift-t-settings.sh file as edited by the user.

The following options change this behavior:

-B    Force run ./bootstrap
-c    Do not 'make clean'
-f    Fast mode: do not run ./configure, do not 'make clean'
-h    This help message
-m    Do not run 'make'

Later options override earlier options.

EOF
}

while getopts "Bcm" OPTION
do
  case $OPTION in
    B) RUN_BOOTSTRAP=1 ;;
    c) MAKE_CLEAN=0      ;;
    f) # Fast
      MAKE_CLEAN=0
      RUN_AUTOTOOLS=0    ;;
    h) help ; exit 0     ;;

    m) RUN_MAKE=0        ;;
    ?) echo exit 1       ;;
    *) exit 1            ;; # Bash prints an error message
  esac
done
