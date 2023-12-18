
# HELP SH
# Used by options.sh
# This help is used by all the install scripts
# Assumes SCRIPT is set

help()
{
  cat <<EOF
usage: $SCRIPT [-Bcfhm]

Reads the swift-t-settings.sh file as edited by the user.

Run build-swift-t.sh to build all of Swift/T, or one of:
build-cutils.sh build-lb.sh build-turbine.sh build-stc.sh
to build only that component.

The C build process for c-utils, lb, and turbine is:

./bootstrap # Run autoconf, etc.
./configure ...
make
make install

For STC, it is simply:

ant ... install

The ... indicates various options, all set by swift-t-settings.sh .

By default, build.sh only runs bootstrap
if configure does not exist.

Then, it runs configure, make, and make install.

The following options change this behavior:

-B     Do not run ./bootstrap
-C     Do not run ./configure
-c     Do not 'make clean' or 'ant clean'
-f     Fast mode: do not run ./bootstrap, do not run ./configure,
       do not 'make clean' or 'ant clean'
       Same as -BCc
-h     This help message
-j     Autodetect parallelism based on processor count (Linux only)
       Implies -n
-m     Do not compile with 'make' or 'ant'
-n     Run key build steps under nice -n 15
-s T|S Skip Turbine (T) or STC (S)
-q     Quiet:   reduce   verbosity (may be given more than once)
-v     Verbose: increase verbosity (may be given more than once)
-y     Do not run 'make install' or 'ant install' (dry-run)

Later options override earlier options.

Set environment variable WAIT=0 to avoid any user check delays.

EOF
}
