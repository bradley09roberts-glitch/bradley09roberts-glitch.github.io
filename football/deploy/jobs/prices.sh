#!/usr/bin/env bash
# Tuesday and Friday 12:40 UK, again at 14:20 and 15:20 (the same timer): football-data's
# fixtures.csv carries the week's prices from about midday (D-024). Once the file published
# today is in: the latest results, fallback records for new fixtures, the model + market
# records of record (only fixtures with usable prices), the shadow models with prices, and
# settlement, pushing the records as they are made. A later attempt the same day does
# nothing once one has succeeded; the last attempt notifies if the file never came.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
job_start prices
today=$(uk_date)
done_marker="$FOOTY_STATE_DIR/prices-$today.done"
tries_file="$FOOTY_STATE_DIR/prices-$today.tries"
max_tries=${FOOTY_PRICES_TRIES:-3}
if [[ -e $done_marker ]]; then
  log "prices already handled today ($(cat "$done_marker"))"
  job_finish
fi
tries=$(($(cat "$tries_file" 2>/dev/null || echo 0) + 1))
echo "$tries" >"$tries_file"
# Tuesday: midweek games to Friday; Friday: the weekend to Tuesday (as the check-ins did)
case $(TZ=Europe/London date +%u) in
  2) days=${FOOTY_PRICES_DAYS:-3} ;;
  *) days=${FOOTY_PRICES_DAYS:-4} ;;
esac

# The file's own 6-hour refresh limit counts from its first download, so a retry forces it.
force=()
[[ $tries -gt 1 ]] && force=(--force)
if ! fd_fixtures "${force[@]}"; then
  job_finish
fi
published=$(json_of fd-fixtures | jq -r '.published_at // empty')
published_day=""
[[ -n $published ]] && published_day=$(TZ=Europe/London date -d "$published" +%F)
if [[ $published_day != "$today" ]]; then
  if [[ $tries -lt $max_tries ]]; then
    log "fixtures.csv was published ${published_day:-at an unknown time}, not today: next try later"
    job_finish
  fi
  echo "gave up $(now_utc)" >"$done_marker"
  notify "Football server: no prices today" \
    "football-data's fixtures.csv is still the one published ${published_day:-at an unknown time} after $tries tries, so no model + market records today. Every fixture keeps its fallback record (the daily run)." \
    3 hourglass
  job_finish
fi
note "fixtures.csv published $published (today), attempt $tries"

step settle footy settle || true
fd_update fd-update --scope core || true
pull_openfootball
sync_records || job_finish # records already pushed that this server lacks, before publishing
load_plan || job_finish
if [[ -n $DOMESTIC_FALLBACK ]]; then
  step fallback footy predict-domestic --model "$DOMESTIC_FALLBACK" \
    --divisions "$FOOTY_DIVISIONS" --days "$days" || true
fi
priced="" unpriced=""
if [[ -n $DOMESTIC_MARKET ]]; then
  if step market-dry-run footy predict-domestic --model "$DOMESTIC_MARKET" \
    --divisions "$FOOTY_DIVISIONS" --days "$days" --dry-run; then
    priced=$(json_of market-dry-run | jq '[.[]? | select(.market_used)] | length')
    unpriced=$(json_of market-dry-run | jq '[.[]? | select(.market_used | not)] | length')
    note "dry run: $priced fixtures with usable prices, $unpriced without"
  fi
  step market footy predict-domestic --model "$DOMESTIC_MARKET" \
    --divisions "$FOOTY_DIVISIONS" --days "$days" || true
  published_n=$(json_of market | jq '[.[]? | select(.id)] | length' 2>/dev/null || echo 0)
  note "$DOMESTIC_MARKET: $published_n new records"
fi
push_records || true
if [[ -n $SHADOWS_DOMESTIC ]]; then
  retry_step shadows footy publish-shadows --group domestic --days "$days" \
    --models "$SHADOWS_DOMESTIC" || true
fi
push_records || true
step settle-end footy settle || true
echo "done $(now_utc)" >"$done_marker"
summary="Model + market records: ${published_n:-0} new (${priced:-?} fixtures with prices, ${unpriced:-?} without, next $days days)."
if [[ ${#JOB_FAILED[@]} -eq 0 && ${priced:-0} == 0 && ${unpriced:-0} != 0 ]]; then
  # a new file with no usable price for any fixture: team names, dates or the file changed
  notify "Football server: prices in, but none usable" \
    "$summary The fixtures keep their fallback records. See journalctl -u footy-prices (market-dry-run)." \
    4 warning
elif [[ ${#JOB_FAILED[@]} -eq 0 ]]; then
  notify "Football server: prices in" "$summary Shadow records published." 2 soccer
fi
job_finish
