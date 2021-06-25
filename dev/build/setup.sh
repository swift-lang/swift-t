
# SETUP.SH
# Do some build configuration after processing command line
# and user settings file

if (( ${PARALLEL} ))
then
  # Auto-configure parallelism based on /proc/cpuinfo
  if [[ -f /proc/cpuinfo ]]
  then
    MAKE_PARALLELISM=$( grep -c "model name" /proc/cpuinfo )
    echo "Autodetected build parallelism: $MAKE_PARALLELISM"
    if (( MAKE_PARALLELISM >= 0 )) # paranoia
    then
      :
    else
      MAKE_PARALLELISM=1
    fi
  fi
fi

export NICE_CMD=""
if (( ${#NICE} ))
then
  # Runs all key build steps under nice
  NICE_CMD="nice -n ${NICE}"
fi

CONFIGURE_ARGS=( --config-cache )
if [[ $VERBOSITY < $LOG_INFO ]]
then
  CONFIGURE_ARGS+=( --quiet )
fi
