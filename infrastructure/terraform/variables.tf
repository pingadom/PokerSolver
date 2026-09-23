variable "region" {
  type    = string
  default = "eu-west-2"
}
variable "name" {
  type    = string
  default = "pokerlab-demo"
  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{2,19}$", var.name))
    error_message = "Use 3–20 lowercase letters, digits or hyphens, starting with a letter."
  }
}
variable "allowed_web_cidr" {
  type        = string
  description = "Your public IP in CIDR notation. No unrestricted public ingress by default."
  validation {
    condition     = can(cidrhost(var.allowed_web_cidr, 0)) && var.allowed_web_cidr != "0.0.0.0/0"
    error_message = "Supply a restricted IPv4 CIDR, normally your current public IP /32."
  }
}
variable "image_tag" {
  type        = string
  default     = "bootstrap"
  description = "An immutable image tag pushed to both ECR repositories, normally the Git commit SHA."
}
variable "api_count" {
  type        = number
  default     = 0
  description = "Start at zero until images have been pushed. Set to one for the demo."
  validation {
    condition     = var.api_count >= 0 && var.api_count <= 4 && floor(var.api_count) == var.api_count
    error_message = "API count must be an integer between zero and four."
  }
}
variable "worker_count" {
  type    = number
  default = 0
  validation {
    condition     = var.worker_count >= 0 && var.worker_count <= 8 && floor(var.worker_count) == var.worker_count
    error_message = "Worker count must be an integer between zero and eight."
  }
}
variable "enable_redis" {
  type        = bool
  default     = false
  description = "Optional billable ElastiCache instance; PostgreSQL remains correct without it."
}
variable "certificate_arn" {
  type        = string
  default     = ""
  description = "Optional ACM certificate in the same region. Enables HTTPS and redirects HTTP."
}
variable "alarm_actions" {
  type        = list(string)
  default     = []
  description = "Optional existing SNS topic ARNs for actionable CloudWatch alarms."
}
