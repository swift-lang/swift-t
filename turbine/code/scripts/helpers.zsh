
# Copyright 2013 University of Chicago and Argonne National Laboratory
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License

# HELPERS.ZSH
# Reusable general-purpose shell helper functions.

KB=1024
MB=$(( 1024*KB ))
GB=$(( 1024*MB ))

@()
# Verbose operation
{
  print
  print ${*}
  print
  ${*}
}

DATE_FMT_S="%D{%Y-%m-%d} %D{%H:%M:%S}"
log()
# General-purpose log line
# You may set global LOG_LABEL to get a message prefix
{
  print ${(%)DATE_FMT_S} ${LOG_LABEL:-} ${*}
}

abort()
{
  local MSG="${*}"
  print ${MSG}
  exit 1
}

assert()
{
  local ERR=$1
  shift
  local MSG="${*}"
  check ${ERR} "${MSG}" || exit ${ERR}
  return 0
}

exitcode()
{
  local ERR=$?
  local MSG="${*}"
  assert ${ERR} "${MSG}"
}

# If CODE is non-zero, print MSG and return CODE
check()
{
  local CODE=$1
  shift
  local MSG=${*}

  if (( ${CODE} != 0 ))
    then
    print ${MSG}
    return ${CODE}
  fi
  return 0
}

bail()
{
  CODE=$1
  shift
  MSG="${*}"
  print "${MSG}"
  set +x
}

crash()
{
  local CODE=1
  while getopts "c" OPTION
  do
    case ${OPTION}
      in
      (c) CODE=${OPTARG} ;;
    esac
  done
  shift $(( OPTIND-1 ))

  MSG="${*}"
  bail ${CODE} ${MSG}
  exit ${CODE}
}

checkvar()
# Assert variable is set
# If given -e, refer to user environment in error message
{
  local E=""
  zparseopts -D e=E
  local VAR=$1

  if ! (( ${(P)+VAR} ))
  then
    if (( ${#E} > 0 ))
    then
      crash "You must set environment variable: ${VAR}"
    else
      crash "Not set: ${VAR}"
    fi
  fi
  return 0
}

checkvars()
# Assert all variables are set
# If given -e, refer to user environment in error message
{
  local E=""
  zparseopts -D e=E
  local VARS
  VARS=( ${*} )
  local V
  for V in ${VARS}
  do
    checkvar ${E} ${V}
  done
  return 0
}

checkint()
{
  local VAR=$1

  checkvar ${VAR}
  if [[ ${(P)VAR} != <-> ]]
  then
    crash "Not an integer: ${VAR}"
  fi
  return 0
}

compile()
{
  SWIFT=$1
  TCL=$2
  shift 2

  if [[ ! -f ${TCL} || ${SWIFT} -nt ${TCL} ]]
  then
    stc ${*} ${SWIFT} ${TCL}
    return ${?}
  fi
  return 0
}

# Check if file $1 is uptodate wrt remaining arguments
# $1 is uptodate if it exists and is newer than remaining arguments
# If $2 does not exist, fail
uptodate()
{
  if [[ ${#} < 2 ]]
  then
    print "uptodate: Need at least 2 args!"
    return 1
  fi

  local OPTION
  local VERBOSE=0
  while getopts "v" OPTION
  do
    case ${OPTION}
      in
      v)
        VERBOSE=1 ;;
    esac
  done
  shift $(( OPTIND-1 ))

  local TARGET=$1
  shift
  local PREREQS
  PREREQS=( ${*} )

  local f
  for f in ${PREREQS}
  do
    if [[ ! -f ${f} ]]
    then
      ((VERBOSE)) && print "not found: ${f}"
      return 1
    fi
  done

  if [[ ! -f ${TARGET} ]]
  then
    ((VERBOSE)) && print "does not exist: ${TARGET}"
    return 1
  fi

  for f in ${PREREQS}
  do
    if [[ ${TARGET} -nt ${f} ]]
    then
      ((VERBOSE)) && print "${TARGET} : ${f} is uptodate"
    else
      ((VERBOSE)) && print "${TARGET} : ${f} is not uptodate"
      return 1
    fi
  done
  return 0
}

within()
{
  local TIME=$1
  shift
  local START STOP DIFF LEFT
  START=$( nanos )
  ${*}
  STOP=$( nanos )
  DIFF=$(( STOP-START ))
  if (( DIFF < 0 ))
    then
    print "TIME exceeded (${DIFF} > ${TIME})!"
    return 1
  fi
  LEFT=$(( TIME-LEFT ))
  sleep ${LEFT}
  return 0
}

login_ip()
# Obtain a visible IP address for this node
{
  ifconfig | grep "inet addr" | head -1 | cut -f 2 -d : | cut -f 1 -d ' '
}

scan()
# Use shoot to output the contents of a scan
{
  [[ $1 == "" ]] && return
  typeset -g -a $1
  local i=1
  local T
  while read T
  do
    eval "${1}[${i}]='${T}'"
    (( i++ ))
  done
}

shoot()
# print out an array loaded by scan()
{
  local i
  local N
  N=$( eval print '${#'$1'}' )
    # print N $N
  for (( i=1 ; i <= N ; i++ ))
  do
    eval print -- "$"${1}"["${i}"]"
  done
}

scan_kv()
{
  [[ $1 == "" ]] && return 1
  typeset -g -A $1
  while read T
  do
   A=( ${T} )
   KEY=${A[1]%:} # Strip any tailing :
   VALUE=${A[2,-1]}
   eval "${1}[${KEY}]='${VALUE}'"
  done
  return 0
}

shoot_kv()
{
  local VAR=$1
  eval print -a -C 2 \"'${(kv)'$VAR'[@]}'\"
  return 0
}

tformat()
# Convert seconds to hh:mm:ss
{
  local -Z 2 T=$1
  local -Z 2 M

  if (( T <= 60 ))
  then
    print "${T}"
  elif (( T <= 3600 ))
  then
    M=$(( T/60 ))
    print "${M}:$( tformat $(( T%60 )) )"
  else
    print "$(( T/3600 )):$( tformat $(( T%3600 )) )"
  fi
}

bformat()
# Format byte counts
{
  local BYTES=$1
  local LENGTH=${2:-3}
  local UNIT
  local UNITS
  UNITS=( "B" "KB" "MB" "GB" "TB" )

  local B=${BYTES}
  for (( UNIT=0 ; UNIT < 4 ; UNIT++ ))
   do
   (( B /= 1024 ))
   (( B == 0 )) && break
  done

  local RESULT=${UNITS[UNIT+1]}
  if [[ ${RESULT} == "B" ]]
    then
    print "${BYTES} B"
  else
    local -F BF=${BYTES}
    local MANTISSA=$(( BF / (1024 ** UNIT ) ))
    MANTISSA=$( significant ${LENGTH} ${MANTISSA} )
    print "${MANTISSA} ${RESULT}"
  fi

  return 0
}

significant()
# Report significant digits from floating point number
{
  local DIGITS=$1
  local NUMBER=$2

  local -F FLOAT=${NUMBER}
  local RESULT
  local DOT=0
  local LZ=1 # Leading zeros
  local C
  local i=1
  while (( 1 ))
   do
    C=${FLOAT[i]}
    [[ ${C} != "0" ]] && [[ ${C} != "." ]] && break
    [[ ${C} == "." ]] && DOT=1
    RESULT+=${C}
    (( i++ ))
  done
  while (( ${DIGITS} > 0 ))
  do
    C=${FLOAT[i]}
    if [[ ${C} == "" ]]
      then
      (( ! DOT )) && RESULT+="." && DOT=1
      C="0"
    fi
    RESULT+=${C}
    [[ ${C} == "." ]] && (( DIGITS++ )) && DOT=1
    (( i++ ))
    (( DIGITS-- ))
  done
  if (( ! DOT )) # Extra zeros to finish out integer
    then
    local -i J=${NUMBER}
    # J=${J}
    while (( ${#RESULT} < ${#J} ))
     do
     RESULT+="0"
    done
  fi
  print ${RESULT}
  return 0
}

turbine_stats_walltime()
# Extract the average walltime from the Turbine STATS
{
  LOG=$1
  if [[ ${LOG} == "" ]]
  then
    print "turbine_stats_walltime(): Not given: LOG"
  fi
  grep walltime ${LOG} | zclm -1 | avg
}

# Local variables:
# mode: sh
# sh-basic-offset: 2
# End:

clm()
# Select columns from input with awk
{
    CMD="{ print"
    for (( i=1 ; i<=${#*} ; i++ ))
    do
	eval idx='${'${i}'}'
	CMD="${CMD} "'$'${idx}
	(( i < ${#*} )) && CMD=${CMD}'" "'
    done
    CMD=${CMD}" }"
    awk "${CMD}"
}

rm0()
# File removal, ok with empty argument list
# Safer than rm -f
{
  local R V
  zparseopts -D -E r=R v=V
  if (( ${#*} == 0 ))
  then
    return 0
  fi
  rm ${R} ${V} ${*}
}

log_path()
# Pretty print a colon-separated variable
{
  local v
  for v in ${*}
  do
    _log_path ${v}
  done
}
_log_path()
{
  local v=$1
  print ${v}:
  print -l ${(Ps.:.)v} | nl
}
