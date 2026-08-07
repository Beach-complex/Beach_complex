output "instance_ids" {
  description = "EC2 instance IDs keyed by instance name."
  value = {
    for name, instance in module.observability_ec2 : name => instance.instance_id
  }
}

output "private_ips" {
  description = "Private IP addresses keyed by instance name."
  value = {
    for name, instance in module.observability_ec2 : name => instance.private_ip
  }
}

output "public_ips" {
  description = "Public IP addresses keyed by instance name."
  value = {
    for name, instance in module.observability_ec2 : name => instance.public_ip
  }
}

output "security_group_ids" {
  description = "Security group IDs keyed by instance name."
  value = {
    for name, instance in module.observability_ec2 : name => instance.security_group_id
  }
}

output "volume_ids" {
  description = "Observability EBS volume IDs keyed by instance name."
  value = {
    for name, instance in module.observability_ec2 : name => instance.volume_id
  }
}

output "iam_role_names" {
  description = "EC2 IAM role names keyed by instance name."
  value = {
    for name, instance in module.observability_ec2 : name => instance.iam_role_name
  }
}
