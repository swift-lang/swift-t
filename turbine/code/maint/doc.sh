#!/bin/bash -eu

# DOC.SH
# Extracts DOC() statements from target file
# Primarily used to generate Asciidoc from C code

# doc.m4 is documented here because M4 does not have comments
# doc.m4 diverts all input except that within DOC() statements
# To avoid confusing your C editor, you may want to comment
# like this: /** DOC(asciidoc text here) */

# m4 macros:
# DOCT():     Simple text output (doc-text)
# DOCN():     Simple text output plus newline (doc-newline)
# DOCNN():    Simple text output plus two newlines
# DOCD():     Use Asciidoc's definition syntax (doc-definition)
# DOC_CODE(): Code snippet.  Use this in the C source: #define DOC_CODE(x) x

# If your doc text contains comma, you should quote it:
# DOCT(`text, with comma')

# We delete leading spaces from the asciidoc file
# (outside of code snippets)

MAINT=$( cd $( dirname $0 ) ; /bin/pwd )

usage()
{
  echo   "doc.sh: usage: "
  printf "\t doc.sh <INPUT> <OUTPUT>\n"
}

if [[ ${#*} != 2 ]]
then
  usage
  exit 1
fi

INPUT=$1
OUTPUT=$2

m4 -P ${MAINT}/doc.m4 ${INPUT} > ${OUTPUT}
