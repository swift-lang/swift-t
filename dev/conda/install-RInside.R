
# INSTALL RINSIDE
# Installs RInside for the Anaconda package
# Called by build-generic.sh

cat("install-RInside.R: starting...\n")

install.packages("RInside",
                 # repos="http://cran.us.r-project.org",
                 repos="https://cran.case.edu",
                 verbose=TRUE)
if (! library("RInside",
    character.only=TRUE, logical.return=TRUE)) {
    quit(status=1)
}
cat("install-RInside.R: Swift-RInside-SUCCESS\n")
