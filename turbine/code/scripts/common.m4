divert(`-1')changecom(`dnl')dnl
dnl common.m4 : Reusable M4 functions
dnl Ensure there is no newline at the end of this file
dnl (use ./delete-last-nl.sh)
dnl Define convenience macros
dnl This simply does environment variable substition when m4 runs
define(`getenv',         `esyscmd(printf -- "$`$1'")')dnl
define(`getenv_nospace', `esyscmd(printf -- "$`$1'")')dnl
dnl bash_l is used to optionally run bash as non-login shell-
dnl        defaults to login shell
define(`bash_l',ifelse(getenv_nospace(TURBINE_BASH_L),`0',`',` -l'))dnl
divert