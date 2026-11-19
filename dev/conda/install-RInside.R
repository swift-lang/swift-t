
# INSTALL RINSIDE
# Installs RInside for the Anaconda package
# Called by build-generic.sh

cat("install-RInside.R: starting...\n")

# Install explicitly into R.home("library"):
#   Without lib=, install.packages()
#   can resolve .libPaths()[1] to $BUILD_PREFIX/lib/R/library
#   (e.g. via R_LIBS_USER/R_LIBS_SITE set in the build environment),
#   even though this script itself is run via $PREFIX/bin/Rscript.
# That leaves RInside installed only in the ephemeral build prefix,
# so libtclturbine links against a libRInside.dylib
# that is never packaged. See build-generic.sh.

r_lib <- R.home("library")
cat("install-RInside.R: installing into", r_lib, "\n")

# Try several mirrors: a single mirror being briefly
# unreachable from CI failed the R legs intermittently.
repos <- c("https://cloud.r-project.org",
           "https://cran.case.edu",
           "https://cran.rstudio.com")
success <- FALSE
for (repo in repos)
{
  cat("install-RInside.R: try repo:", repo, "\n")

  # This function does not check for errors:
  install.packages("RInside", lib=r_lib, repos=repo,
                   verbose=TRUE)
  # Try to load the library:
  success <- library("RInside", lib.loc=r_lib,
                     character.only=TRUE, logical.return=TRUE)

  if (success) break
}
if (! success)
{
  # No repo worked:
  cat("install-RInside.R: Swift-RInside-FAILED\n")
  quit(status=1)
}

# Success:
cat("install-RInside.R: Swift-RInside-SUCCESS\n")
