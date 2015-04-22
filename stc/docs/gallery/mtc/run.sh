#!/bin/sh -e

for F in mtc*.swift
do
  echo Running $F
  swift-t $F
done
