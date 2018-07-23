#!/bin/sh

set -e

export SPRING_PROFILES_ACTIVE=docker

echo "Starting..."
exec "$@"