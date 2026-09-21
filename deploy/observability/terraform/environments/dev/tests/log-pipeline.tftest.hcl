mock_provider "aws" {
  mock_data "aws_subnet" {
    defaults = {
      availability_zone = "us-east-1a"
      vpc_id            = "vpc-0123456789abcdef0"
    }
  }
}

variables {
  instance_name                = "local-test"
  project_name                 = "beach-complex"
  app_server_security_group_id = "sg-0123456789abcdef0"
  app_server_private_ip        = "10.0.1.200"
  subnet_id                    = "subnet-0123456789abcdef0"
  ami_id                       = "ami-0123456789abcdef0"
  instance_type                = "t3.small"
  volume_size_gb               = 30
}

run "dev_logs" {
  command = plan
  module {
    source = "../../modules/observability_ec2"
  }
  variables {
    env = "dev"
  }
  assert {
    condition = (
      aws_vpc_security_group_ingress_rule.loki.from_port == 3100 &&
      aws_vpc_security_group_ingress_rule.loki.to_port == 3100 &&
      aws_vpc_security_group_ingress_rule.loki.ip_protocol == "tcp" &&
      aws_vpc_security_group_ingress_rule.loki.referenced_security_group_id == var.app_server_security_group_id &&
      aws_vpc_security_group_ingress_rule.loki.cidr_ipv4 == null &&
      aws_vpc_security_group_ingress_rule.loki.cidr_ipv6 == null
    )
    error_message = "Loki must accept TCP 3100 only from the app security group."
  }
  assert {
    condition     = local.loki_retention_period == "72h" && strcontains(local.cloud_init_plan_probe, "LOKI_RETENTION_PERIOD=72h")
    error_message = "Dev bootstrap must set the ADR-014 three-day retention."
  }
  assert {
    condition     = local.cloud_init_plan_probe_bytes <= 16384
    error_message = "The new Loki asset must fit EC2 user data."
  }
}

run "staging_retention" {
  command = plan
  module {
    source = "../../modules/observability_ec2"
  }
  variables {
    env = "staging"
  }
  assert {
    condition     = strcontains(local.cloud_init_plan_probe, "LOKI_RETENTION_PERIOD=168h")
    error_message = "Staging bootstrap must set seven-day retention."
  }
}

run "prod_retention" {
  command = plan
  module {
    source = "../../modules/observability_ec2"
  }
  variables {
    env = "prod"
  }
  assert {
    condition     = strcontains(local.cloud_init_plan_probe, "LOKI_RETENTION_PERIOD=336h")
    error_message = "Prod bootstrap must set fourteen-day retention."
  }
}
