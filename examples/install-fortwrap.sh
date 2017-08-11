
# INSTALL FORTWRAP

# Installs FortWrap.  Can be run interactively or from Jenkins.

FV=git # FortWrap Version
path+=/tmp/exm-fortwrap-${FV}
if [[ -e /tmp/exm-fortwrap-${FV}/fortwrap.py ]]
then
  echo "Found FortWrap: $( which fortwrap.py )"
else
  echo "Downloading FortWrap"
  mkdir -p /tmp/exm-fortwrap-${FV}
  pushd /tmp/exm-fortwrap-${FV}
  wget https://raw.githubusercontent.com/mcfarljm/fortwrap/master/fortwrap.py
  chmod u+x fortwrap.py
  echo "FortWrap successfully installed in /tmp/exm-fortwrap-${FV}"
  popd
fi
