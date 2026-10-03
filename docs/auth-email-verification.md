# Local registration and email ownership

## API contract (breaking change for local registration)

1. `POST /api/v1/auth/email-verification/request` with `{ "email": "..." }`.
2. Read the 6-digit code from the mailbox.
3. `POST /api/v1/auth/register` with `email`, `displayName`, `password`,
   `confirmPassword`, and `verificationCode`.
4. Only a valid, unused code creates an email-verified local `USER` account.
   The successful response remains HTTP 201 with an access token and HttpOnly
   refresh cookie. A missing/mismatched confirmation or invalid code is HTTP 400;
   no login session is issued.

Codes are separate from password-reset codes: only the hash is stored in
`registration_email_challenges` (Flyway V026). Codes expire after 10 minutes,
allow at most 5 incorrect attempts, and are atomically single-use. Resend has a
60-second per-email cooldown; endpoints also have per-IP rate limits. Failed SMTP
sends invalidate the just-issued challenge. Passwords and codes must not be logged.

## Existing users and login

Existing unverified local users are NOT silently marked verified by the migration.
They can visit `/verify-email`, request a code, and submit
`POST /api/v1/auth/verify-email` with `email`, `code`, `newPassword`, and
`confirmPassword`. Activation replaces the old password and revokes refresh
sessions, preventing account pre-hijacking via an attacker-chosen legacy password.
Already-verified users should sign in or use password reset instead. This endpoint
never logs the user in: the new password is still required at `/login`.
Login and refresh refuse unverified accounts. Already-issued access JWTs remain
valid until their normal 15-minute expiry.

A valid password-reset code also proves ownership and marks the email verified
before saving the new password. Forgot-password sends to the stored account email,
not an arbitrary recovery address, and its response stays generic. A reset code
cannot be used as a registration verification code or vice versa.

Google OAuth requires Google's `email_verified=true` and does not silently link a
LOCAL account. New Google users remain `USER`; no HTTP registration field can
choose ADMIN. The frontend includes `/auth/oauth2/success` (clears the token hash)
and `/auth/oauth2/error` (generic safe failure page).

## Kafka mail delivery

When `MAIL_KAFKA_ENABLED=true`, both verification and password-reset requests use
an email delivery pipeline rather than calling SMTP in the HTTP request:

`identity use case → PostgreSQL event_outbox → outbox relay → Kafka → SMTP consumer`.

The event types are `email.verification` and `email.password-reset`, with separate
consumer topics and consumer group `knowledge-gym-email`. SMTP failures are thrown
back to the Kafka listener, so Spring Kafka retries and sends poison messages to
`email.events.dlq`; the outbox relay also keeps a failed Kafka publish pending and
records attempts/error text. Producer idempotence and `acks=all` are enabled.

The API still returns a generic accepted response after the outbox row is written;
it does not claim inbox delivery. The outbox payload necessarily contains the
short-lived plaintext code needed to render the email, so PostgreSQL/Kafka access,
backups, and DLQ retention must be restricted. Codes remain hashed in the challenge
and password-reset tables. Enable this mode only when Kafka is healthy and SMTP is
configured; otherwise keep `MAIL_KAFKA_ENABLED=false` for the direct SMTP adapter.

## Deployment and SMTP acceptance

Deploy FE and BE together: older frontend registration payloads no longer satisfy
the API contract. Flyway V026 must be applied after the existing V025 migration.
Set `MAIL_SMTP_ENABLED=true` with valid `MAIL_USERNAME` and `MAIL_PASSWORD` on
production. Keep mail health disabled if SMTP availability should not take down
the API health endpoint. The application refuses mail operations when SMTP is
turned off; it never claims a code was sent via a console fallback.

Automated mail tests use a mock mail sender or a test-only capture service. These
are NOT proof of delivery from CT102 to a real inbox. Live acceptance requires an
operator-approved recipient: request a verification code through the UI, confirm
receipt (check spam), register, log out/in, then request a reset code and verify
password reset and old refresh-token revocation. Never post SMTP credentials or
verification codes in issue logs.
