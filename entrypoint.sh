#!/usr/bin/env bash

if [ "$#" -eq 0 ]; then
  echo "No model names provided"
  exit 1
fi

/bin/ollama serve &
pid=$!

sleep 5

for model_name in "$@"; do
  echo "🔴 Pulling model: $model_name..."
  ollama pull "$model_name"
  echo "🟢 Successfully pulled model: $model_name"
done

wait $pid
