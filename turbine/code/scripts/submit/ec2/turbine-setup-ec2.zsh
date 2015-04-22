#!/bin/zsh
# Copyright 2013 University of Chicago and Argonne National Laboratory
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License

# TURBINE: SETUP EC2

# Creates files on all EC2 instances to run Turbine
# The Swift/T AMI should have installed all the major software:
#     this is just for minor settings

# You must set the following environment variables:
#  TURBINE_EC2_KEY: The path to the private key file

# Requires ec2-host
# See: http://instagram-engineering.tumblr.com/post/11399488246/simplifying-ec2-ssh-connections

# Assumes TURBINE_USER is root (for now)

# Creates: Remote KEY file (pem) for inter-instance SSH for MPICH
#          Remote .ssh/config file to automate SSH options
#          Remote hosts.txt file to pass to mpiexec -f

TURBINE_USER=root

TURBINE_HOME=$( cd $( dirname $0 )/../../.. ; /bin/pwd )
declare TURBINE_HOME
source ${TURBINE_HOME}/scripts/helpers.zsh

TURBINE_EC2_HOME=$( cd $( dirname $0 ) ; /bin/pwd )
exitcode "turbine-setup-ec2: configuration error!"

which ec2-host > /dev/null
exitcode "ec2-host not found!"

KEY=$( basename ${TURBINE_EC2_KEY} )
exitcode "Not set correctly: TURBINE_EC2_KEY"
sed "s@TURBINE_EC2_KEY@${KEY}@;s/TURBINE_USER/${TURBINE_USER}/" \
  < ${TURBINE_EC2_HOME}/config.template > ./config
exitcode "turbine-setup-ec2: configuration error!"

HOSTS=( $( ec2-host | clm 2) )

HOSTFILE=$( mktemp )
exitcode "Could not run ec2-host!"

ec2-host | clm 2 > ${HOSTFILE}
exitcode "Could not write HOSTFILE: ${HOSTFILE}"

# Note: @ is defined in helpers.zsh
for H in ${HOSTS}
do
  print
  print "turbine-setup-ec2: configuring: ${H}"
  ACCT=${TURBINE_USER}@${H}
  @ scp config             ${ACCT}:.ssh/config
  exitcode "Could not scp to ${ACCT}"
  @ scp ${TURBINE_EC2_KEY} ${ACCT}:
  exitcode "Could not scp to ${ACCT}"
  @ scp ${HOSTFILE}        ${ACCT}:hosts.txt
  exitcode "Could not scp to ${ACCT}"
done
