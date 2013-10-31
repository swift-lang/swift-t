#!/bin/bash

[[ -s "f4.out" ]] \
    && echo "f4.out present : PASS" \
    || echo "f4.out missing : FAIL"
[[ -f "f4.err" ]] \
    && echo "f4.err present : PASS" \
    || echo "f4.err missing : FAIL"



