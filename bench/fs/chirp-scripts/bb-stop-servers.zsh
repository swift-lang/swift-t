#!/bin/zsh

# Start Chirp servers on all allocated Breadboard nodes

CHIRP_SCRIPTS_HOME=$( cd $( dirname $0 ) ; /bin/pwd )
source ${CHIRP_SCRIPTS_HOME}/bb.zsh

HOSTS_FILE=$( mktemp )
bbhosts > ${HOSTS_FILE}
HOSTS=( $( cat ${HOSTS_FILE} ) )
rm ${HOSTS_FILE}

for H in ${HOSTS}
do
  ssh ${H} killall chirp_server
done

return 0
