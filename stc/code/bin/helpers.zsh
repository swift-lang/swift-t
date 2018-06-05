
# HELPERS.ZSH
# Misc. shell helpers

make_temp()
# Handle temp file creation across platforms
# Always has .SUFFIX somewhere, preferably at the end
{
  local SUFFIX=$1
  local USE_DIR=$2
  local NAME=$3
  local MKTEMP_TYPE=""
  local SUFFIX_ARG=""

  # Attempt to determine mktemp version
  # Mac mktemp does not support -V
  if mktemp -V >& /dev/null
  then
    # Linux:
    MKTEMP_VERSION=$( mktemp -V | awk '{print $NF;exit}' )
    if (( ${MKTEMP_VERSION} > 2 ))
    then
      MKTEMP_TYPE="Linux"
    else
      MKTEMP_TYPE="BSD"
    fi
  else
    MKTEMP_TYPE="Mac"
  fi

  if [[ ${MKTEMP_TYPE} == "Linux" ]]
  then
      # Modern mktemp.  .tic at the end.
      SUFFIX_ARG=( --suffix .${SUFFIX} )
  else
      # Old (BSD?) mktemp.  .SUFFIX. will be in the middle of the name
      NAME=${NAME}.${SUFFIX}
  fi

  mktemp ${SUFFIX_ARG} ${USE_DIR}/swift-t-${NAME}.XXX
}
