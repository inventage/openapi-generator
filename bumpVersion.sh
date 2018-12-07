#!/usr/bin/env bash

newVersion=$1

mvn versions:set -DgenerateBackupPoms=false -DnewVersion=${newVersion}
