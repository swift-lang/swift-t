
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

  # Probe for --suffix support rather than parsing the version:
  # version strings vary (GNU "9.4", uutils "0.10.0"),
  # and "0.10.0" is not a number zsh can compare.
  # -u only prints a name, so the probe creates no file.
  # Mac mktemp does not support -V
  if mktemp -u --suffix=.x >& /dev/null
  then
    # GNU or uutils coreutils:
    MKTEMP_TYPE="Linux"
  elif mktemp -V >& /dev/null
  then
    MKTEMP_TYPE="BSD"
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
