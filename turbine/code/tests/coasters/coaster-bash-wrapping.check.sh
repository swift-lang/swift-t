#!/bin/bash

DIR=$PWD
echo "$DIR/test5.0.out"
[[ -s "$DIR/test5.0.out" ]] \
    && echo "test5.0.out present : PASS" \
    || echo "test5.0.out missing : FAIL"
[[ -f "$DIR/test5.0.err" ]] \
    && echo "test5.0.err present : PASS" \
    || echo "test5.0.err missing : FAIL"
