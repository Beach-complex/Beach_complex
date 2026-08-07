data "aws_subnet" "selected" {
  id = var.subnet_id
}

locals {
  resource_name = "${var.project_name}-${var.env}-observability-${var.instance_name}"

  tags = {
    Name      = local.resource_name
    Component = "observability"
    Instance  = var.instance_name
  }
}

resource "aws_security_group" "this" {
  name_prefix = "${local.resource_name}-"
  description = "Network access for ${local.resource_name}"
  vpc_id      = data.aws_subnet.selected.vpc_id

  tags = local.tags

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_vpc_security_group_ingress_rule" "ssh" {
  security_group_id            = aws_security_group.this.id
  referenced_security_group_id = var.app_server_security_group_id
  description                  = "SSH from the application server"
  from_port                    = 22
  ip_protocol                  = "tcp"
  to_port                      = 22
}

resource "aws_vpc_security_group_ingress_rule" "loki" {
  security_group_id            = aws_security_group.this.id
  referenced_security_group_id = var.app_server_security_group_id
  description                  = "Loki from the application server"
  from_port                    = 3100
  ip_protocol                  = "tcp"
  to_port                      = 3100
}

resource "aws_vpc_security_group_ingress_rule" "otlp_grpc" {
  security_group_id            = aws_security_group.this.id
  referenced_security_group_id = var.app_server_security_group_id
  description                  = "OTLP gRPC from the application server"
  from_port                    = 4317
  ip_protocol                  = "tcp"
  to_port                      = 4317
}

resource "aws_vpc_security_group_ingress_rule" "otlp_http" {
  security_group_id            = aws_security_group.this.id
  referenced_security_group_id = var.app_server_security_group_id
  description                  = "OTLP HTTP from the application server"
  from_port                    = 4318
  ip_protocol                  = "tcp"
  to_port                      = 4318
}

resource "aws_vpc_security_group_egress_rule" "all" {
  security_group_id = aws_security_group.this.id
  description       = "Outbound access for package installation and service communication"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

resource "aws_iam_role" "this" {
  name = "${local.resource_name}-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Service = "ec2.amazonaws.com"
      }
      Action = "sts:AssumeRole"
    }]
  })

  tags = local.tags
}

resource "aws_iam_instance_profile" "this" {
  name = "${local.resource_name}-profile"
  role = aws_iam_role.this.name

  tags = local.tags
}

resource "aws_ebs_volume" "data" {
  availability_zone = data.aws_subnet.selected.availability_zone
  encrypted         = true
  size              = var.volume_size_gb
  type              = "gp3"

  tags = merge(local.tags, {
    Name = "${local.resource_name}-data"
  })
}

resource "aws_instance" "this" {
  ami                         = var.ami_id
  associate_public_ip_address = var.associate_public_ip_address
  iam_instance_profile        = aws_iam_instance_profile.this.name
  instance_type               = var.instance_type
  key_name                    = var.key_name
  subnet_id                   = var.subnet_id
  user_data                   = var.user_data
  vpc_security_group_ids      = [aws_security_group.this.id]

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
  }

  root_block_device {
    encrypted   = true
    volume_type = "gp3"
  }

  tags = local.tags
}

resource "aws_volume_attachment" "data" {
  device_name                    = "/dev/sdf"
  instance_id                    = aws_instance.this.id
  stop_instance_before_detaching = true
  volume_id                      = aws_ebs_volume.data.id
}
