
# INSTALL RINSIDE
# Installs RInside for the Anaconda package
# Called by build-generic.sh

cat("install-RInside.R: starting...\n")

# Install explicitly into R.home("library"): without lib=, install.packages()
# can resolve .libPaths()[1] to $BUILD_PREFIX/lib/R/library (e.g. via
# R_LIBS_USER/R_LIBS_SITE set in the build environment), even though this
# script itself is run via $PREFIX/bin/Rscript. That leaves RInside installed
# only in the ephemeral build prefix, so libtclturbine links against a
# libRInside.dylib that is never packaged. See build-generic.sh.
r_lib <- R.home("library")
cat("install-RInside.R: installing into", r_lib, "\n")

install.packages("RInside",
                 lib=r_lib,
                 # repos="http://cran.us.r-project.org",
                 repos="https://cran.case.edu",
                 verbose=TRUE)
if (! library("RInside", lib.loc=r_lib,
    character.only=TRUE, logical.return=TRUE)) {
    quit(status=1)
}
cat("install-RInside.R: Swift-RInside-SUCCESS\n")
