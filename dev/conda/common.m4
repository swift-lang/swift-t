m4_divert(`-1')m4_changecom(`dnl')dnl
dnl common.m4 : Reusable M4 functions
dnl This file must have a newline at the end for OSX.
dnl Simply remove this text:
m4_define(`COMMENT', `')dnl
dnl This simply does environment variable substition when m4 runs:
m4_define(`m4_getenv',  `m4_esyscmd(printf -- "$`$1'")')dnl
m4_divert
