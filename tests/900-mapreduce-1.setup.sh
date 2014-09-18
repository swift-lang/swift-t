#!/bin/bash
mkdir -p 900-data
for i in $(seq 0 9); do
  touch "./900-data/input-${i}.txt"
done
