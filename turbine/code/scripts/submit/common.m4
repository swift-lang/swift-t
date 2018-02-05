divert(`-1')
# common.m4 : Reusable M4 functions
# Ensure there is no newline at the end of this file
changecom(`dnl')
define(`getenv', `esyscmd(printf -- "$`$1' ")')
define(`getenv_nospace', `esyscmd(printf -- "$`$1'")')
divert