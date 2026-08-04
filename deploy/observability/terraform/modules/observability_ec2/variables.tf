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

variable "vpc_id" {
  description = "VPC ID for the instance security group."
  type        = string
}

variable "allowed_admin_cidr" {
  description = "IPv4 CIDR allowed to access SSH and Grafana."
  type        = string
}

variable "subnet_id" {
  description = "Subnet ID for the EC2 instance."
  type        = string
}

variable "ami_id" {
  description = "AMI ID for the EC2 instance."
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

variable "user_data" {
  description = "Optional cloud-init user data added in a later PR."
  type        = string
  default     = null
  nullable    = true
}
