#!/bin/bash
pid_file=server.pid
install_dir=`pwd`
server_jar=`ls -t $install_dir | grep server-.*\.jar | head -1`
export SERVER_PORT=7019
java -Ddir=$install_dir -jar "$server_jar" &
pid=$!
disown

echo $pid > $pid_file
echo "Server started in `pwd`(and demonized), PID: $pid"
