#!/bin/bash

sleep 2
ls  ./multiplex-test*
cat ./multiplex-test*
#rm /homes/yadunand/bin/test* &> /dev/null

exit 0
DIR=/homes/yadunand/bin
[[ -s "$DIR/test{0,1,2}.out" ]] \
    && echo "test{0,1,2}.out present : PASS" \
    || echo "test5.0.out missing : FAIL"
[[ -f "$DIR/test{0,1,2}.err" ]] \
    && echo "test5.0.err present : PASS" \
    || echo "test5.0.err missing : FAIL"
