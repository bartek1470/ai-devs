#!/bin/bash

# keep only logs of 5 latest program runs
# better would be to implement logback TriggeringPolicy for RollingFileAppender
ls -t ./logs | grep -v '\(\.history\|\.request\)\.log$' | tail -n +5 | xargs -I {} trash-put ./logs/{}
ls -t ./logs | grep '\.request\.log$' | tail -n +5 | xargs -I {} trash-put ./logs/{}
