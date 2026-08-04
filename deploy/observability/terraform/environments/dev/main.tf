module "observability_ec2" {
  for_each = var.instances

  source = "../../modules/observability_ec2"

  instance_name               = each.key
  project_name                = var.project_name
  env                         = var.env
  vpc_id                      = var.vpc_id
  allowed_admin_cidr          = var.allowed_admin_cidr
  subnet_id                   = each.value.subnet_id
  ami_id                      = each.value.ami_id
  instance_type               = each.value.instance_type
  key_name                    = each.value.key_name
  volume_size_gb              = each.value.volume_size_gb
  associate_public_ip_address = each.value.associate_public_ip_address
}
