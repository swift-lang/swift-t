
# GET VERSIONS
# from all Swift/T components

# This has to be manually edited:
SWIFT_T_VERSION=1.6.5

THIS=$( cd $( dirname $0 ) ; /bin/pwd )
SWIFT_TOP=$( cd $THIS/.. ; /bin/pwd )

# These  are automatically extracted:
CUTILS_VERSION=$(  cat $SWIFT_TOP/c-utils/code/version.txt )
ADLBX_VERSION=$(   cat $SWIFT_TOP/lb/code/version.txt      )
TURBINE_VERSION=$( cat $SWIFT_TOP/turbine/code/version.txt )
STC_VERSION=$(     cat $SWIFT_TOP/stc/code/etc/version.txt )
