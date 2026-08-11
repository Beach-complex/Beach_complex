variable "instance_name" {
  description = "Stable name of this observability instance."
  type        = string
}

variable "project_name" {
  description = "Project name used for resource names and tags."
  type        = string
}

variable "env" {
  description = "Deployment environment name."
  type        = string
}

variable "app_server_security_group_id" {
  description = "Application server Security Group ID allowed to access SSH."
  type        = string
}

variable "subnet_id" {
  description = "Subnet ID for the EC2 instance."
  type        = string
}

variable "ami_id" {
  description = "Ubuntu Server 24.04 LTS AMI ID for the EC2 instance."
  type        = string
}

variable "instance_type" {
  description = "EC2 instance type."
  type        = string
}

variable "key_name" {
  description = "Optional EC2 key pair name."
  type        = string
  default     = null
  nullable    = true
}

variable "volume_size_gb" {
  description = "Size of the persistent observability EBS volume in GiB."
  type        = number
}

variable "associate_public_ip_address" {
  description = "Whether to associate a public IPv4 address with the instance."
  type        = bool
  default     = false
}
