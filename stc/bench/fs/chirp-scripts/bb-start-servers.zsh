#!/usr/bin/env zsh

# Start Chirp servers on all allocated Breadboard nodes

CHIRP_SCRIPTS_HOME=$( cd $( dirname $0 ) ; /bin/pwd )
source ${CHIRP_SCRIPTS_HOME}/bb.zsh

# Check if chirp servers are up
check_servers()
{
  local HOSTS
  HOSTS=( ${*} )
  print "Checking ${#HOSTS} servers..."
  touch sanity.txt
  for H in ${HOSTS}
  do
    chirp ${H} put sanity.txt
    if [[ ${?} != 0 ]]
    then
      print "Server not working: ${H}"
      exit 1
    fi
  done
}

TMP=/tmp
CHIRP_FS=${TMP}/chirp_fs

mkdir -p ${CHIRP_FS}

HOSTS_FILE=$( mktemp )
bbhosts > ${HOSTS_FILE}
HOSTS=( $( cat ${HOSTS_FILE} ) )
rm ${HOSTS_FILE}

# Start each server
for H in ${HOSTS}
do
  print "${H} starting..."
  ssh ${H} ${CHIRP_SCRIPTS_HOME}/bb-start-server.sh
  print ok
done

# wait

# Optional sanity check:
check_servers ${HOSTS}

return 0
