#!/bin/bash

/usr/sbin/sshd &

if [[ "$1" == "redis-server" ]]; then
  chown -R redis:redis /data
  exec gosu redis "$@"
fi
exec "$@"
