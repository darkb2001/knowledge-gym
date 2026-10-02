terraform {
  required_version = ">= 1.5.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    archive = {
      source  = "hashicorp/archive"
      version = "~> 2.4"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

locals {
  name_prefix = var.name_prefix
}

data "archive_file" "collect" {
  type        = "zip"
  output_path = "${path.module}/build/collect.zip"
  source {
    content  = file("${path.module}/lambda/common/http.mjs")
    filename = "http.mjs"
  }
  source {
    content  = file("${path.module}/lambda/collect/index.mjs")
    filename = "index.mjs"
  }
}

data "archive_file" "backup" {
  type        = "zip"
  output_path = "${path.module}/build/backup.zip"
  source {
    content  = file("${path.module}/lambda/common/http.mjs")
    filename = "http.mjs"
  }
  source {
    content  = file("${path.module}/lambda/backup/index.mjs")
    filename = "index.mjs"
  }
}

resource "aws_iam_role" "lambda" {
  name = "${local.name_prefix}-cron-lambda"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "lambda_basic" {
  role       = aws_iam_role.lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_lambda_function" "collect" {
  function_name = "${local.name_prefix}-collect"
  role          = aws_iam_role.lambda.arn
  handler       = "index.handler"
  runtime       = "nodejs20.x"
  filename      = data.archive_file.collect.output_path
  source_code_hash = data.archive_file.collect.output_base64sha256
  timeout       = 60
  environment {
    variables = {
      KG_API_BASE_URL = var.api_base_url
      CRON_TOKEN      = var.cron_token
    }
  }
}

resource "aws_lambda_function" "backup" {
  function_name = "${local.name_prefix}-backup"
  role          = aws_iam_role.lambda.arn
  handler       = "index.handler"
  runtime       = "nodejs20.x"
  filename      = data.archive_file.backup.output_path
  source_code_hash = data.archive_file.backup.output_base64sha256
  # Backup can take minutes when Garage is included; Lambda max is 15m.
  timeout = 600
  environment {
    variables = {
      KG_API_BASE_URL = var.api_base_url
      CRON_TOKEN      = var.cron_token
    }
  }
}

resource "aws_cloudwatch_event_rule" "collect" {
  name                = "${local.name_prefix}-collect"
  description         = "Knowledge Gym collector every 6 hours"
  schedule_expression = "rate(6 hours)"
}

resource "aws_cloudwatch_event_rule" "backup" {
  name                = "${local.name_prefix}-backup"
  description         = "Knowledge Gym restic backup daily 03:00 UTC"
  schedule_expression = "cron(0 3 * * ? *)"
}

resource "aws_cloudwatch_event_target" "collect" {
  rule = aws_cloudwatch_event_rule.collect.name
  arn  = aws_lambda_function.collect.arn
}

resource "aws_cloudwatch_event_target" "backup" {
  rule = aws_cloudwatch_event_rule.backup.name
  arn  = aws_lambda_function.backup.arn
}

resource "aws_lambda_permission" "collect_events" {
  statement_id  = "AllowEventBridgeCollect"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.collect.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.collect.arn
}

resource "aws_lambda_permission" "backup_events" {
  statement_id  = "AllowEventBridgeBackup"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.backup.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.backup.arn
}

resource "aws_budgets_budget" "monthly" {
  name              = "${local.name_prefix}-monthly"
  budget_type       = "COST"
  limit_amount      = var.budget_usd
  limit_unit        = "USD"
  time_unit         = "MONTHLY"
  time_period_start = "2026-10-01_00:00"

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = [var.budget_alert_email]
  }
}

output "collect_function_name" {
  value = aws_lambda_function.collect.function_name
}

output "backup_function_name" {
  value = aws_lambda_function.backup.function_name
}
