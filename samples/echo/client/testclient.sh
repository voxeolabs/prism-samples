#!/bin/sh
# -----------------------------------------------------------------------------
# Make client script for test soap service
#
#
# -----------------------------------------------------------------------------
if [ -z "$JAVA_HOME" ]; then
  RUNJAVA=java
  else
  RUNJAVA="$JAVA_HOME"/bin/java
fi

# resolve links - $0 may be a softlink
PRG="$0"

while [ -h "$PRG" ]; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '.*/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`/"$link"
  fi
done

# Get standard environment variables
PRGDIR=`dirname "$PRG"`

# Only set REAL_PATH if not already set
REAL_PATH=`cd "$PRGDIR" ; pwd`

# LIBRARY
LIBRARY=$REAL_PATH/lib
CLASSPATH=$CLASSPATH:.

CLASSPATH=$CLASSPATH:$LIBRARY/activation.jar
CLASSPATH=$CLASSPATH:$LIBRARY/FastInfoset.jar
CLASSPATH=$CLASSPATH:$LIBRARY/http.jar
CLASSPATH=$CLASSPATH:$LIBRARY/jaxb-impl.jar
CLASSPATH=$CLASSPATH:$LIBRARY/jaxb-xjc.jar
CLASSPATH=$CLASSPATH:$LIBRARY/jaxws-rt.jar
CLASSPATH=$CLASSPATH:$LIBRARY/jaxws-tools.jar
CLASSPATH=$CLASSPATH:$LIBRARY/jsr181-api.jar
CLASSPATH=$CLASSPATH:$LIBRARY/jsr250-api.jar
CLASSPATH=$CLASSPATH:$LIBRARY/resolver.jar
CLASSPATH=$CLASSPATH:$LIBRARY/saaj-impl.jar
CLASSPATH=$CLASSPATH:$LIBRARY/sjsxp.jar
CLASSPATH=$CLASSPATH:$LIBRARY/stax-ex.jar
CLASSPATH=$CLASSPATH:$LIBRARY/streambuffer.jar

MAINCLASS=com.micromethod.sipmethod.sample.echo.AddressManagerClient

$RUNJAVA $JAVA_OPTS -cp $CLASSPATH -Djava.endorsed.dirs="$LIBRARY/endorsed" $MAINCLASS "$@" 

if [ ! -z "$SM_PID" ]; then
	echo $! > $SM_PID
fi
