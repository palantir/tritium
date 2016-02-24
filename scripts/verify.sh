#!/usr/bin/env bash

cd "$(dirname "$0")"/..

./gradlew build --parallel
