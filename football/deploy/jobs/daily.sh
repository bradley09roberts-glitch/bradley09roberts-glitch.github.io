#!/usr/bin/env bash
# Daily 08:00 UK (audit H1): the latest results, settlement, then records for fixtures that
# have none yet: the fallback of record for league games, the UEFA model of record, and the
# shadow models without prices (each run publishes only what is new; a record is never
# replaced). Ends with `footy doctor`, which notifies any problem.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
job_start daily
fd_update fd-update || true
pull_openfootball
step settle footy settle || true
# records already pushed that this server lacks (a restored disk) first, so none is made twice
if sync_records && load_plan; then
  if [[ -n $DOMESTIC_FALLBACK ]]; then
    step fallback footy predict-domestic --model "$DOMESTIC_FALLBACK" \
      --divisions "$FOOTY_DIVISIONS" --days "${FOOTY_DOMESTIC_DAYS:-7}" || true
  fi
  for model in ${UEFA_MODELS//,/ }; do
    step "uefa-$model" footy predict-euro --model "$model" --days "${FOOTY_UEFA_DAYS:-3}" || true
  done
  push_records || true # the records of record are pushed before the long shadow run
  if [[ -n $SHADOWS_PURE ]]; then
    retry_step shadows footy publish-shadows --group all --models "$SHADOWS_PURE" || true
  fi
fi
push_records || true
run_doctor
job_finish
