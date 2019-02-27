#!/usr/bin/env zsh

# Start a local Chirp server

CHIRP_FS=/sandbox/${USER}/chirp_fs

mkdir -pv ${CHIRP_FS}

chirp_server -r ${CHIRP_FS} -o ${HOME}/local-server.log &
CHIRP_PID=${!}

declare CHIRP_PID

return 0
