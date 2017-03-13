
# GET VERSIONS
# from all Swift/T components

# This has to be manually edited:
SWIFT_T_VERSION=1.2

# These are automatically extracted:
CUTILS_VERSION=$(  cat c-utils/code/version.txt )
ADLBX_VERSION=$(   cat lb/code/version.txt      )
TURBINE_VERSION=$( cat turbine/code/version.txt )
STC_VERSION=$(     cat stc/code/etc/version.txt )
