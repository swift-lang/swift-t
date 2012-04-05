
# Assumes the presence of:
checkvars MODE

# Sets: OUTPUT_DIR OUTPUT START STOP

START=$( date +%s )

# Launch it
case ${MODE}
  in
  "mpiexec")
    OUTPUT="turbine-output.txt"
    OUTPUT_DIR=${PWD}
    turbine -l -n ${PROCS} ${=COMMAND} >& ${OUTPUT}
    exitcode "turbine failed!"
    ;;
  "cobalt")
    # User must edit Tcl to add user libs
    unset TURBINE_USER_LIB
    OUTPUT_TOKEN_FILE=$( mktemp )
    ${TURBINE_COBALT} -d ${OUTPUT_TOKEN_FILE} \
      -n ${PROCS} ${PROGRAM_TCL} --N=${N} --delay=${DELAY}
    exitcode "turbine-cobalt failed!"
    read OUTPUT_DIR < ${OUTPUT_TOKEN_FILE}
    declare OUTPUT_DIR
    rm ${OUTPUT_TOKEN_FILE}
    OUTPUT=$( ls ${OUTPUT_DIR}/*.output )
    ;;
  *)
    print "unknown MODE: ${MODE}"
    return 1
    ;;
esac

STOP=$( date +%s )
