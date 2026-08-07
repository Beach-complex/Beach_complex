output "instance_id" {
  description = "EC2 instance ID."
  value       = aws_instance.this.id
}

output "private_ip" {
  description = "EC2 private IP address."
  value       = aws_instance.this.private_ip
}

output "public_ip" {
  description = "EC2 public IP address, if assigned."
  value       = aws_instance.this.public_ip
}

output "security_group_id" {
  description = "Security group ID."
  value       = aws_security_group.this.id
}

output "volume_id" {
  description = "Persistent observability EBS volume ID."
  value       = aws_ebs_volume.data.id
}

output "iam_role_name" {
  description = "EC2 IAM role name."
  value       = aws_iam_role.this.name
}
