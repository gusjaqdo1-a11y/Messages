# Messages SOM — Admin Control

## Admin responsibilities

The production admin panel should control:

- Dashboard and delivery statistics
- Users and roles
- Sender IDs
- Sender ID approval/disable/delete
- Provider configuration
- Provider health and balance
- Country availability
- Per-user and per-Sender-ID limits
- Message logs and delivery states
- Failed/retry queue
- Broadcasts
- App-to-app messages
- FCM notification campaigns
- Block/disable users
- API keys and service tokens
- Webhook configuration
- Audit log
- System settings

## Sender ID registration flow

1. Admin signs in.
2. Open **Sender IDs**.
3. Select **Add Sender ID**.
4. Enter display name, provider, provider sender ID, countries and reply policy.
5. Verify the provider accepted the Sender ID.
6. Set status to `active`.
7. The Android app receives only active Sender IDs allowed for that user/country.

## Admin API

```text
POST   /api/v1/admin/sender-ids
GET    /api/v1/admin/sender-ids
PATCH  /api/v1/admin/sender-ids/:id
DELETE /api/v1/admin/sender-ids/:id

GET    /api/v1/admin/users
PATCH  /api/v1/admin/users/:id

GET    /api/v1/admin/messages
GET    /api/v1/admin/messages/:id
POST   /api/v1/admin/messages/:id/retry

GET    /api/v1/admin/providers
POST   /api/v1/admin/providers
PATCH  /api/v1/admin/providers/:id

GET    /api/v1/admin/audit-log
```

## App API

```text
GET  /api/v1/sender-ids
POST /api/v1/messages/send
GET  /api/v1/messages/:id
GET  /api/v1/messages/:id/status
```

## Admin authentication

Use real authentication and an `admin` role/claim. Do not hard-code an admin password into the APK or web JavaScript.

Recommended roles:

- `owner` — unrestricted platform control
- `admin` — operational control
- `support` — users/messages read + limited actions
- `operator` — sender/provider operations
- `user` — normal app access

Every privileged action must create an audit-log entry.

## Deployment

The Android repository contains the application and API contract. The admin backend requires a production database/auth provider and provider credentials. The Vercel account currently available to the deployment connector is not authorized for the `numelixa-build` team, so a production admin URL cannot be safely claimed until that Vercel scope is authorized.

Do not use a public static admin page as a substitute for authenticated server-side control.
