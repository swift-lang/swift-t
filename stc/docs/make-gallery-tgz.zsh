#!/bin/zsh
set -eu

# MAKE-GALLERY-TGZ.ZSH
# Pack example programs for WWW

if [[ ! -d gallery ]]
then
  print "gallery directory not found!"
  exit 1
fi

print cleaning...
gallery/clean.sh
print

TGZ=gallery.tar.gz

FILES=( $( find gallery/*(/) -maxdepth 1   \
              -name "*.sh"     -o \
              -name "*.swift"  -o \
              -name "*.manifest"  \
      ) )

tar cfzv ${TGZ} ${FILES}
du -h ${TGZ}
