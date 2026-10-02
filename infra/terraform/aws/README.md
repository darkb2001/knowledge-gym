# Terraform — AWS cron for Knowledge Gym (m11b)

Node 20 Lambdas call the production tunnel:

- `rate(6 hours)` → `POST /api/v1/internal/collect`
- `cron(0 3 * * ? *)` → `POST /api/v1/internal/backup`
- AWS Budgets alarm at `$1` / month

Daily-challenge is omitted until m12.

## Apply

```bash
cd infra/terraform/aws
cp terraform.tfvars.example terraform.tfvars   # fill secrets; do not commit
terraform init
terraform plan
terraform apply
```

After apply, confirm prod `.env` has `APP_CRON_MODE=external` so Spring does not
double-run collect/writer. Outbox relay + writer stale reclaim stay embedded.

Until AWS credentials exist, keep LXC host cron from `docs/19-m11b-ops.md`.

## Note on `/internal/backup`

The Lambda only HTTP-triggers the app. The app must be able to execute
`scripts/backup-db.sh` with host tools (`pg_dump`, `restic`, optionally `docker`).
If the app container cannot reach those tools, prefer host crontab for backup and
use Lambda only for `/internal/collect`.
