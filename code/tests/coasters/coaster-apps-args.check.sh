#!/bin/bash

[[ -s "test-3.0.out" ]] \
    &&  echo "test-3.0.out present : PASS" \
    ||  echo "test-3.0.out missing : FAIL"

[[ -f "test-3.0.err" ]] \
    && echo "test-3.0.err present : PASS" \
    || echo "test-3.0.err present : FAIL"
[[ -s "test-3.1.out" ]] \
    && echo "test-3.1.out present : PASS" \
    || echo "test-3.1.out present : FAIL"
[[ -f "test-3.1.err" ]] \
    && echo "test-3.1.err present : PASS" \
    || echo "test-3.1.err present : FAIL"


