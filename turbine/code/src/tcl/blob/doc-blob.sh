#!/bin/bash -eu

# DOC-BLOB.SH
# Makes the blob documentation Asciidoc file blob.txt
# Copy blob.txt into the gh-pages branch and build it into blob.html

BLOB_SRC=$( cd $( dirname $0 ) ; /bin/pwd )
MAINT=$( cd ${BLOB_SRC}/../../../maint ; /bin/pwd )

usage()
{
  echo   "doc-blob.sh: usage: "
  print "\t doc-blob.sh\n"
}

INPUT=${BLOB_SRC}/blob.h
TXT=blob.txt

${MAINT}/doc.sh ${INPUT} ${TXT}
echo "Generated: ${TXT}"
