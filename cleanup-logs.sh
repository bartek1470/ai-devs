#!/bin/bash

LOGS_DIR="./.cache/ai-devs/logs"
# keep only logs of 5 latest program runs
# better would be to implement logback TriggeringPolicy for RollingFileAppender
ls -t "$LOGS_DIR" | grep -v '\(\.history\|\.request\)\.log$' | tail -n +5 | xargs -I {} trash-put "$LOGS_DIR/{}"
ls -t "$LOGS_DIR" | grep '\.request\.log$' | tail -n +5 | xargs -I {} trash-put "$LOGS_DIR/{}"
