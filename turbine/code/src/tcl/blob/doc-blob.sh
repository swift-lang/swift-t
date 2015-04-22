#!/bin/bash -eu

# DOC-BLOB.SH
# Makes the blob documentation

BLOB_SRC=$( cd $( dirname $0 ) ; /bin/pwd ) 
MAINT=$( cd ${BLOB_SRC}/../../../maint ; /bin/pwd ) 

usage()
{
  echo   "doc-blob.sh: usage: "
  print "\t doc-blob.sh\n"
}

INPUT=${BLOB_SRC}/blob.h
TXT=blob.txt
OUTPUT=blob.html

${MAINT}/doc.sh ${INPUT} ${TXT}
asciidoc ${TXT}
echo "Generated: ${OUTPUT}"
