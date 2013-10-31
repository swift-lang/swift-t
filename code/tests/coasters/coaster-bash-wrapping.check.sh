#!/bin/bash

[[ -s "test5.0.out" ]] \
    && echo "test5.0.out present : PASS" \
    || echo "test5.0.out missing : FAIL"
[[ -f "test5.0.err" ]] \
    && echo "test5.0.err present : PASS" \
    || echo "test5.0.err missing : FAIL"
[[ -s "test5.1.out" ]] \
    && echo "test5.1.out present : PASS" \
    || echo "test5.1.out missing : FAIL"
[[ -f "test5.1.err" ]] \
    && echo "test5.1.err present : PASS" \
    || echo "test5.1.err missing : FAIL"


