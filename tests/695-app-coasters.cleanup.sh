#!/bin/bash

echo "Killing service ${COASTER_SVC_PID} and immediate children"
for child_pid in $(ps -ef| awk '$3 == '${COASTER_SVC_PID}' { print $2 }')
do
  echo "Killing process $pid"
  kill $child_pid
done

kill ${COASTER_SVC_PID}
wait || true
