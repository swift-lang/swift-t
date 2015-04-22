
# Helper scripts for Breadboard

bbhosts()
{
  OUTPUT=$1
  if [[ ${OUTPUT} = "" ]]
  then
    heckle stat | grep ${USER} | clm 1
  else
    heckle stat | grep ${USER} | clm 1 > ${OUTPUT}
    print "WROTE: ${OUTPUT}"
  fi
  return 0
}
