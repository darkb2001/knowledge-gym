variable "aws_region" {
  type        = string
  description = "AWS region for Lambda / EventBridge / Budgets"
  default     = "ap-southeast-1"
}

variable "name_prefix" {
  type        = string
  description = "Resource name prefix"
  default     = "knowledge-gym"
}

variable "api_base_url" {
  type        = string
  description = "Public API origin, e.g. https://api.darkb-tech.io.vn"
}

variable "cron_token" {
  type        = string
  description = "Shared CRON_TOKEN matching prod app.cron.token"
  sensitive   = true
}

variable "budget_usd" {
  type        = string
  description = "Monthly AWS Budgets hard ceiling (USD)"
  default     = "1"
}

variable "budget_alert_email" {
  type        = string
  description = "Email for Budgets alarm"
}
