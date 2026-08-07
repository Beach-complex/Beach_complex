variable "aws_region" {
  description = "AWS region for the dev observability infrastructure."
  type        = string
}

variable "project_name" {
  description = "Project name used for resource names and tags."
  type        = string
  default     = "beach-complex"
}

variable "env" {
  description = "Deployment environment name."
  type        = string
  default     = "dev"

  validation {
    condition     = contains(["dev", "staging", "prod"], var.env)
    error_message = "env must be one of dev, staging, or prod."
  }
}

variable "app_server_security_group_id" {
  description = "Application server Security Group ID allowed to access SSH."
  type        = string

  validation {
    condition     = can(regex("^sg-[0-9a-f]+$", var.app_server_security_group_id))
    error_message = "app_server_security_group_id must be a valid Security Group ID."
  }
}

variable "instances" {
  description = "Configuration for each observability EC2 instance."

  type = map(object({
    subnet_id                   = string
    ami_id                      = string
    instance_type               = string
    key_name                    = optional(string)
    volume_size_gb              = number
    associate_public_ip_address = optional(bool, false)
  }))

  validation {
    condition     = length(var.instances) > 0
    error_message = "instances must contain at least one entry."
  }

  validation {
    condition = alltrue([
      for instance in values(var.instances) : instance.volume_size_gb >= 20
    ])
    error_message = "Each observability volume must be at least 20 GiB."
  }

  validation {
    condition = alltrue([
      for instance in values(var.instances) :
      can(regex("^subnet-[0-9a-f]+$", instance.subnet_id)) &&
      can(regex("^ami-[0-9a-f]+$", instance.ami_id))
    ])
    error_message = "Each instance must use valid subnet and AMI IDs."
  }
}
