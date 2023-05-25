m4_divert(`-1')m4_changecom(`dnl')dnl
dnl common.m4 : Reusable M4 functions
dnl Ensure there is no newline at the end of this file
dnl (use ./delete-last-nl.sh)
dnl Define convenience macros
dnl This simply does environment variable substition when m4 runs
m4_define(`getenv',         `m4_esyscmd(printf -- "$`$1'")')dnl
m4_define(`getenv_nospace', `m4_esyscmd(printf -- "$`$1'")')dnl
dnl bash_l is used to optionally run bash as non-login shell-
dnl        defaults to login shell
m4_define(`bash_l',m4_ifelse(getenv(TURBINE_BASH_L),`0',`',` -l'))dnl
m4_divert