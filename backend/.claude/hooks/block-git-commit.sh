#!/bin/bash
INPUT=$(cat)
TOOL=$(echo "$INPUT" | jq -r '.tool_name // empty')
[ "$TOOL" != "Bash" ] && exit 0

CMD=$(echo "$INPUT" | jq -r '.tool_input.command // empty')

if echo "$CMD" | grep -qE 'git\s+commit'; then
  echo '{"decision":"block","reason":"git commit está bloqueado. Faça o commit manualmente."}'
  exit 0
fi