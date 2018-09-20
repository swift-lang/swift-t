#!/usr/bin/env zsh

EXM=${HOME}/exm

# User may need to set this
PLOTTER=swift_plotter.zsh

TIME_COMPLETIONS=${EXM}/scripts/mpe/time-counts.tcl
CLOG=adlb.clog2
RANK=-1

export MPE_EVENTS="set1"
${TIME_COMPLETIONS} ${CLOG} ${RANK} > cA.data
[[ ${?} == 0 ]] || { print ERROR ; return 1 }
print OK

${PLOTTER} -s foreach-1D-completions.{cfg,eps} cA.data
