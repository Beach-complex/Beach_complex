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

variable "vpc_id" {
  description = "VPC ID for observability resources."
  type        = string

  validation {
    condition     = can(regex("^vpc-[0-9a-f]+$", var.vpc_id))
    error_message = "vpc_id must be a valid VPC ID."
  }
}

variable "allowed_admin_cidr" {
  description = "IPv4 CIDR allowed to access SSH and Grafana."
  type        = string

  validation {
    condition     = can(cidrnetmask(var.allowed_admin_cidr))
    error_message = "allowed_admin_cidr must be a valid IPv4 CIDR."
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
