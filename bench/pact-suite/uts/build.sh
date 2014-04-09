#!/usr/bin/env bash
set -e

pushd uts-src
make
popd

stc -O3 -C uts.ic uts.swift
