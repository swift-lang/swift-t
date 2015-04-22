# Define missing macros for earlier versions of m4/autoconf

# Replace with equivalent expression
ifdef([m4_ifblank],[], [
m4_define([m4_ifblank],
  [m4_if(m4_normalize([[$1]]), [], [$2], [$3])])])
