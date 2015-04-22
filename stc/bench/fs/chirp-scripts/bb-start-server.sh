#!/bin/dash

CHIRP_FS=/tmp/chirp_fs

mkdir -p ${CHIRP_FS}

SERVER_LOG=${CHIRP_FS}/server.log

# Allow disconnection
exec <   /dev/null
exec >>  /dev/null
exec 2>> /dev/null

# Start server
nohup chirp_server -r ${CHIRP_FS} -o ${SERVER_LOG} &

# Set permissions
chirp localhost setacl / "hostname:bblogin*.mcs.anl.gov" write

exit 0
