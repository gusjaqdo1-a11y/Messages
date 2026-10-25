# Messages SOM — Sender ID API

## Important transport rule

A normal Android SIM SMS is sent with the phone's carrier/SIM identity. The app cannot safely or reliably replace that identity with an arbitrary Sender ID such as `M-PESA`, `BANK`, or `MYSHOP`.

Sender-ID messages therefore use the **server/provider transport**. The Android app should never contain a provider API secret.

## Recommended architecture

```text
Messages SOM Android
        |
        | HTTPS + authenticated user token
        v
Messages SOM API
        |
        | provider API key kept only on server
        v
SMS provider / aggregator
        |
        v
Mobile network -> recipient
```

## Sender ID model

Each sender is stored server-side with:

- `id`
- `name` — display name, e.g. `M-PESA`
- `provider`
- `provider_sender_id`
- `enabled`
- `allow_replies`
- `countries`
- `daily_limit`
- `monthly_limit`
- `created_at`
- `updated_at`

The admin can create, edit, disable, delete and rotate provider credentials. Secrets are never shipped inside the APK.

## API contract

### Register Sender ID

`POST /api/v1/admin/sender-ids`

```json
{
  "name": "MYSHOP",
  "provider": "example-provider",
  "provider_sender_id": "MYSHOP",
  "allow_replies": false,
  "countries": ["ET", "SO"],
  "daily_limit": 10000
}
```

### List Sender IDs

`GET /api/v1/sender-ids`

Only enabled Sender IDs that the authenticated account is allowed to use should be returned to the app.

### Send with Sender ID

`POST /api/v1/messages/send`

```json
{
  "sender_id": "MYSHOP",
  "to": "+251900000000",
  "text": "Your verification code is 123456"
}
```

Server-side provider request:

```text
provider.send(
  senderId = storedSender.provider_sender_id,
  to = normalizedRecipient,
  text = request.text
)
```

## cURL example

```bash
curl -X POST "https://YOUR-API-DOMAIN/api/v1/messages/send" \
  -H "Authorization: Bearer YOUR_USER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "sender_id": "MYSHOP",
    "to": "+251900000000",
    "text": "Your verification code is 123456"
  }'
```

## Python example

```python
import requests

API = "https://YOUR-API-DOMAIN/api/v1/messages/send"
TOKEN = "YOUR_USER_TOKEN"

payload = {
    "sender_id": "MYSHOP",
    "to": "+251900000000",
    "text": "Your verification code is 123456",
}

response = requests.post(
    API,
    json=payload,
    headers={"Authorization": f"Bearer {TOKEN}"},
    timeout=30,
)
response.raise_for_status()
print(response.json())
```

## Provider integration

The provider adapter must be implemented on the server. Do not put provider API keys in Kotlin, JavaScript shipped to the browser, GitHub source, or the APK.

A provider adapter should expose:

```text
send(senderId, to, text)
getDeliveryStatus(providerMessageId)
getBalance()
listSenderIds()
```

## Security

- Admin endpoints require an admin role.
- User endpoints require authenticated users.
- Every send is rate-limited.
- Normalize and validate E.164 recipients.
- Log provider message ID and status, but do not log OTP/message secrets unnecessarily.
- Provider API keys stay in server environment variables/secrets.
- Sender IDs are allowlisted by country/provider before they can be selected by users.
- Never allow a client to submit an arbitrary provider Sender ID directly to the provider.
