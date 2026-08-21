data "aws_subnet" "selected" {
  id = var.subnet_id
}

locals {
  resource_name = "${var.project_name}-${var.env}-observability-${var.instance_name}"

  # 데이터 볼륨 연결 설정의 단일 기준이다. EBS 연결 리소스와 cloud-init,
  # 마운트 경로를 사용하는 모든 곳에서 같은 값을 사용해야 한다.
  requested_attachment_device = "/dev/sdf"
  mount_point                 = "/opt/beach-observability"
  mount_verifier_base64 = base64encode(
    file("${path.module}/../../../scripts/verify-mount-runtime.sh")
  )
  prometheus_config_rendered = templatefile(
    "${path.module}/../../../compose/prometheus/prometheus.yml.tftpl",
    {
      app_server_private_ip = var.app_server_private_ip
    }
  )
  compose_base64gzip = base64gzip(
    file("${path.module}/../../../compose/docker-compose.yml")
  )
  prometheus_config_base64gzip = base64gzip(local.prometheus_config_rendered)
  tempo_config_base64gzip = base64gzip(
    file("${path.module}/../../../compose/tempo/tempo.yaml")
  )
  grafana_datasources_base64gzip = base64gzip(
    file("${path.module}/../../../compose/grafana/provisioning/datasources/datasources.yml")
  )

  # EBS 연결과 마운트에 필요한 값을 cloud-init 템플릿에 주입해
  # EC2 user data로 전달할 최종 설정을 생성한다.
  cloud_init_rendered = templatefile("${path.module}/cloud-init.yml.tftpl", {
    observability_volume_id        = aws_ebs_volume.data.id
    requested_attachment_device    = local.requested_attachment_device
    mount_point                    = local.mount_point
    mount_verifier_base64          = local.mount_verifier_base64
    compose_base64gzip             = local.compose_base64gzip
    prometheus_config_base64gzip   = local.prometheus_config_base64gzip
    tempo_config_base64gzip        = local.tempo_config_base64gzip
    grafana_datasources_base64gzip = local.grafana_datasources_base64gzip
  })

  # 최초 plan에서는 실제 Volume ID가 unknown이므로 같은 길이의 placeholder로
  # 렌더링해 plan 단계의 크기 검증값을 확정한다.
  cloud_init_plan_probe = templatefile("${path.module}/cloud-init.yml.tftpl", {
    observability_volume_id        = "vol-00000000000000000"
    requested_attachment_device    = local.requested_attachment_device
    mount_point                    = local.mount_point
    mount_verifier_base64          = local.mount_verifier_base64
    compose_base64gzip             = local.compose_base64gzip
    prometheus_config_base64gzip   = local.prometheus_config_base64gzip
    tempo_config_base64gzip        = local.tempo_config_base64gzip
    grafana_datasources_base64gzip = local.grafana_datasources_base64gzip
  })

  # cloud-init은 gzip user data를 자동 해제한다. EC2에 실제로 전달할 gzip payload를
  # Base64로 만들고 같은 값을 user_data_base64와 크기 검증에서 함께 사용한다.
  cloud_init_plan_probe_base64gzip = base64gzip(local.cloud_init_plan_probe)
  cloud_init_rendered_base64gzip   = base64gzip(local.cloud_init_rendered)

  # Base64 결과에서 "=" padding을 제거한 뒤 3/4를 곱해 EC2가 제한하는
  # Base64 디코딩 후 gzip payload의 raw byte 수를 계산한다.
  cloud_init_plan_probe_bytes = floor(
    length(replace(local.cloud_init_plan_probe_base64gzip, "=", "")) * 3 / 4
  )
  cloud_init_rendered_bytes = floor(
    length(replace(local.cloud_init_rendered_base64gzip, "=", "")) * 3 / 4
  )

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

resource "aws_vpc_security_group_ingress_rule" "app_metrics" {
  security_group_id            = var.app_server_security_group_id
  referenced_security_group_id = aws_security_group.this.id
  description                  = "Spring Boot Actuator metrics from the observability server"
  from_port                    = 8081
  ip_protocol                  = "tcp"
  to_port                      = 8081
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
  user_data_base64            = local.cloud_init_rendered_base64gzip
  user_data_replace_on_change = true
  vpc_security_group_ids      = [aws_security_group.this.id]
  volume_tags = merge(local.tags, {
    Name = "${local.resource_name}-root"
  })

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
  }

  root_block_device {
    encrypted   = true
    volume_type = "gp3"
  }

  lifecycle {
    precondition {
      condition     = local.cloud_init_plan_probe_bytes <= 16384
      error_message = "Plan-time EC2 user data size probe must not exceed 16 KiB."
    }

    precondition {
      condition     = local.cloud_init_rendered_bytes <= 16384
      error_message = "Actual rendered EC2 user data must not exceed 16 KiB."
    }
  }

  tags = local.tags
}

resource "aws_volume_attachment" "data" {
  device_name                    = local.requested_attachment_device
  instance_id                    = aws_instance.this.id
  stop_instance_before_detaching = true
  volume_id                      = aws_ebs_volume.data.id
}
