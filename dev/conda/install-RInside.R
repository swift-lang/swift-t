
# INSTALL RINSIDE
# Installs RInside for the Anaconda package
# Called by build-generic.sh

install.packages("RInside",
                 repos="http://cran.us.r-project.org")
if (! library("RInside",
    character.only=TRUE, logical.return=TRUE)) {
    quit(status=1)
}
print("Swift-RInside-SUCCESS")
