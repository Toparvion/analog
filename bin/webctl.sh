#!/bin/bash

componentName="analog"
debugPort=8084

# Since default JDK on the server is JDK_1.6 we forcibly make JAVA_HOME to point to JDK_1.9
#JAVA_HOME=/usr/jdk/jdk-9.0.4

#Set APP_HOME to point on the application folder.
DIR=`dirname $0`
cd $DIR
APP_HOME=`pwd`

PID_FILE=$APP_HOME/$componentName.pid

JAVA_OPTS=`echo "
-D_$componentName
"`
JAVA_OPTS="$JAVA_OPTS -Xmx256m -XX:MaxMetaspaceSize=256m -XX:+HeapDumpOnOutOfMemoryError"
JAVA_OPTS="$JAVA_OPTS -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:$debugPort"
JAVA_OPTS="$JAVA_OPTS -Dfile.encoding=UTF-8"

if [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA="$JAVA_HOME/bin/java"
else
    JAVA=`which java`
fi

if [ ! -x "$JAVA" ]; then
    echo "Could not find any executable java binary. Please install java in your PATH or set JAVA_HOME"
    exit 1
fi

start_service()
{
	echo "Starting $componentName ..."
	PROG_OPTS="" #"--spring.profiles.active=$PROFILE"
	exec nohup "$JAVA" $JAVA_OPTS -jar ${componentName}.jar $PROG_OPTS &> console.out &
	echo $! > $PID_FILE
	echo "$componentName has been started"

	return 0
}

# Checks existence of process by PID.
check_pid() {
	[ -d "/proc/$1" ] && return 0 || return 1
}

#
# Stops or terminates service.
#
stop_service() {
	set +e

	if [ ! -e $PID_FILE ]; then
		echo "$componentName is not running"
		return 0		
	fi

	pid=`cat $PID_FILE`
	
	if check_pid $pid; then
		echo "Stopping $componentName ..."

		# TERM first, then KILL if not dead.
        kill -TERM $pid >/dev/null 2>&1
		
		for i in {1..10}; do
			if check_pid $pid; then
				sleep 2
			else
				rm -f $PID_FILE
				echo "$componentName has been stopped"
				return 0
			fi
		done

		kill -KILL $pid >/dev/null 2>&1
        sleep 2
		
		if check_pid $pid; then
			echo "Failed to stop $componentName"
			return 1
		else 
			rm -f $PID_FILE
			echo "$componentName has been stopped"
			return 0
		fi
	else
		rm -f $PID_FILE
		echo "$componentName is not running"
		return 0
	fi
}

case "$1" in
	start)
    	start_service
		;;
	stop)
    	stop_service
		;;
	restart)
		stop_service
		start_service
		;;
	*)
    	echo "Usage: $0 {start|stop|restart}" >&2
    	exit 1
		;;
esac

echo
exit 0
