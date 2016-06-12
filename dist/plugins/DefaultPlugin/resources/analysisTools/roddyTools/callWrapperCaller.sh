#!/usr/bin/env bash

source ${CONFIG_FILE}
set -xuv
# Use the system/available Java version to call the callWrapper Groovy script

#java -cp ${TOOL_CALL_WRAPPER} de.dkfz.roddy.execution.CallWrapper

#java -cp `dirname ${TOOL_CALL_WRAPPER}`:~/bin/groovy/embeddable/groovy-all-2.4.6.jar CallWrapper

# Get groovy from maven and put it to /tmp (if not there...)

# Interesting page about flock, maybe useful later on:
# http://www.kfirlavi.com/blog/2012/11/06/elegant-locking-of-bash-program/
downloadIsOK=true
function downloadGroovyLibrary() {
  src=http://central.maven.org/maven2/org/codehaus/groovy/groovy-all/2.4.7/groovy-all-2.4.7.jar
  dst=/tmp/groovy-all-2.4.7.jar
  lck=${dst}~
  echo $lck
  (
    flock 200
    [[ ! -f $dst ]] && echo "Download groovy jar file from the maven repository" && wget $src -O $dst || downloadIsOK=false
    [[ ! $downloadIsOK ]] && echo "Donwload of groovy from Maven repo failed. Exitting script." && exit 1
  ) 200>$lck
}

downloadGroovyLibrary &
downloadGroovyLibrary &
downloadGroovyLibrary &
downloadGroovyLibrary &

echo "Wait"
wait

#groovy ${TOOL_CALL_WRAPPER}