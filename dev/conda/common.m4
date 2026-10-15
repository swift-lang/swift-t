m4_divert(`-1')m4_changecom(`dnl')dnl
dnl common.m4 : Reusable M4 functions
dnl This file must have a newline at the end for OSX.
dnl Simply remove this text:
m4_define(`COMMENT', `')dnl
dnl This simply does environment variable substition when m4 runs:
m4_define(`m4_getenv',  `m4_esyscmd(printf -- "$`$1'")')dnl
dnl m4_getenv_commas: like m4_getenv but safe when the value contains
dnl commas, which are valid in an Anaconda match-spec such as
dnl "tk>=8.6.15,<9.0a0".  Plain m4_getenv in an m4_ifelse/m4_include
dnl argument would let such a comma act as an argument separator and
dnl silently drop the rest of the expansion.  _requote rejoins the
dnl comma-split args inside one quote level so the comma stays literal.
m4_define(`_requote', ``$*'')dnl
m4_define(`m4_getenv_commas', `_requote(m4_esyscmd(printf -- "$`$1'"))')dnl
m4_divert
