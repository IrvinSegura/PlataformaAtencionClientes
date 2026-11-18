#!/usr/bin/env bash
# Creates in Stripe what the platform's billing expects: one meter per limited metric, and for each paid plan a
# product with a monthly price and metered prices whose first tier (the plan's included quantity) costs nothing,
# plus a one-time price for each recharge. Prices carry lookup keys (supportchat_<plan>, supportchat_<plan>_<metric>,
# supportchat_topup_<recharge>), which is how the backend finds them.
#
# Usage:
#   STRIPE_SECRET_KEY=sk_test_... scripts/stripe-setup.sh [webhook-url]
#
# Safe to run again: meters and prices that already exist are kept. With a webhook URL, e.g.
# https://chat.example.com/api/v1/billing/stripe/webhook, it also registers the webhook and prints its signing
# secret for STRIPE_WEBHOOK_SECRET.
#
# Keep PLANS and TOP_UPS in line with supportchat.billing.plans and supportchat.billing.top-ups in
# backend/src/main/resources/application.yml.
set -euo pipefail

: "${STRIPE_SECRET_KEY:?Set STRIPE_SECRET_KEY (use a test key, sk_test_..., first)}"
API="${STRIPE_API:-https://api.stripe.com}"
CURRENCY="${BILLING_CURRENCY:-usd}"
WEBHOOK_URL="${1:-}"

# plan code|name|monthly price in cents|included answers|actions|WhatsApp messages|overage price per unit in cents
# for answers|actions|WhatsApp messages (decimals allowed; empty = the metric stops at its limit)
PLANS=(
  "STARTER|Inicial|2900|2000|500|2000|2|1|1"
  "PRO|Profesional|9900|10000|3000|10000|1.5|0.8|0.8"
)
# recharge code|name|price in cents
TOP_UPS=(
  "ANSWERS_500|500 respuestas extra|1000"
  "ANSWERS_2000|2000 respuestas extra|3500"
  "ACTION_RUNS_200|200 acciones extra|400"
  "WHATSAPP_MESSAGES_500|500 mensajes de WhatsApp extra|500"
)
METRICS=(answers action_runs whatsapp_messages)
METRIC_NAMES=("Respuestas del asistente" "Acciones ejecutadas" "Mensajes de WhatsApp")

stripe() {
  local method="$1" path="$2"
  shift 2
  curl -sS --fail-with-body -X "$method" "$API$path" -u "$STRIPE_SECRET_KEY:" "$@"
}

# The first "id" of a JSON answer whose value starts with the given prefix
first_id() {
  grep -o "\"id\": *\"$1[^\"]*\"" | head -n 1 | sed 's/.*"\([^"]*\)"$/\1/'
}

meter_for() {
  local event="$1" name="$2" id
  # One line per meter (each object starts with its id), then the one with this event name
  id="$(stripe GET "/v1/billing/meters?status=active&limit=100" | tr -d '\n' \
    | sed 's/"id": *"mtr_/\n&/g' | grep "\"event_name\": *\"$event\"" | first_id mtr_ || true)"
  if [[ -z "$id" ]]; then
    id="$(stripe POST /v1/billing/meters \
      -d "display_name=$name" \
      -d "event_name=$event" \
      -d "default_aggregation[formula]=sum" \
      -d "customer_mapping[type]=by_id" \
      -d "customer_mapping[event_payload_key]=stripe_customer_id" \
      -d "value_settings[event_payload_key]=value" | first_id mtr_)"
    echo "  meter $event created: $id" >&2
  fi
  echo "$id"
}

price_exists() {
  stripe GET "/v1/prices?active=true&lookup_keys[]=$1" | grep -q '"id": *"price_'
}

echo "Meters"
declare -A METERS
for i in "${!METRICS[@]}"; do
  METERS[${METRICS[$i]}]="$(meter_for "supportchat_${METRICS[$i]}" "${METRIC_NAMES[$i]}")"
  echo "  supportchat_${METRICS[$i]}: ${METERS[${METRICS[$i]}]}"
done

for plan in "${PLANS[@]}"; do
  IFS='|' read -r code name monthly inc_answers inc_actions inc_whatsapp over_answers over_actions over_whatsapp <<< "$plan"
  key="supportchat_$(tr '[:upper:]' '[:lower:]' <<< "$code")"
  echo "Plan $code"
  product="$(stripe GET "/v1/prices?lookup_keys[]=$key&expand[]=data.product" | tr -d '\n' \
    | grep -o '"product": *{ *"id": *"prod_[^"]*"' | sed 's/.*"\(prod_[^"]*\)"$/\1/' || true)"
  if [[ -z "$product" ]]; then
    product="$(stripe POST /v1/products -d "name=Plan $name" -d "metadata[plan]=$code" | first_id prod_)"
    echo "  product created: $product"
  fi
  if price_exists "$key"; then
    echo "  $key exists"
  else
    stripe POST /v1/prices -d "product=$product" -d "currency=$CURRENCY" -d "unit_amount=$monthly" \
      -d "recurring[interval]=month" -d "lookup_key=$key" -d "transfer_lookup_key=true" > /dev/null
    echo "  $key created"
  fi
  included=("$inc_answers" "$inc_actions" "$inc_whatsapp")
  overage=("$over_answers" "$over_actions" "$over_whatsapp")
  for i in "${!METRICS[@]}"; do
    metric="${METRICS[$i]}"
    # A metric without overage price is cut off by the backend at its limit: nothing to charge
    [[ -z "${overage[$i]}" ]] && continue
    metered="${key}_$metric"
    if price_exists "$metered"; then
      echo "  $metered exists"
      continue
    fi
    stripe POST /v1/prices -d "product=$product" -d "currency=$CURRENCY" \
      -d "recurring[interval]=month" -d "recurring[usage_type]=metered" -d "recurring[meter]=${METERS[$metric]}" \
      -d "billing_scheme=tiered" -d "tiers_mode=graduated" \
      -d "tiers[0][up_to]=${included[$i]}" -d "tiers[0][unit_amount_decimal]=0" \
      -d "tiers[1][up_to]=inf" -d "tiers[1][unit_amount_decimal]=${overage[$i]}" \
      -d "nickname=${METRIC_NAMES[$i]} ($code)" -d "lookup_key=$metered" -d "transfer_lookup_key=true" > /dev/null
    echo "  $metered created"
  done
done

echo "Recharges"
for top_up in "${TOP_UPS[@]}"; do
  IFS='|' read -r code name amount <<< "$top_up"
  key="supportchat_topup_$(tr '[:upper:]' '[:lower:]' <<< "$code")"
  if price_exists "$key"; then
    echo "  $key exists"
    continue
  fi
  product="$(stripe POST /v1/products -d "name=Recarga: $name" -d "metadata[top_up]=$code" | first_id prod_)"
  stripe POST /v1/prices -d "product=$product" -d "currency=$CURRENCY" -d "unit_amount=$amount" \
    -d "lookup_key=$key" -d "transfer_lookup_key=true" > /dev/null
  echo "  $key created"
done

if [[ -n "$WEBHOOK_URL" ]]; then
  echo "Webhook"
  secret="$(stripe POST /v1/webhook_endpoints -d "url=$WEBHOOK_URL" \
    -d "enabled_events[]=checkout.session.completed" \
    -d "enabled_events[]=checkout.session.async_payment_succeeded" \
    -d "enabled_events[]=customer.subscription.created" \
    -d "enabled_events[]=customer.subscription.updated" \
    -d "enabled_events[]=customer.subscription.deleted" \
    | grep -o '"secret": *"whsec_[^"]*"' | sed 's/.*"\(whsec_[^"]*\)"$/\1/')"
  echo "  registered $WEBHOOK_URL"
  echo "  STRIPE_WEBHOOK_SECRET=$secret"
fi
echo "Done. Turn on the customer portal once at https://dashboard.stripe.com/settings/billing/portal"
