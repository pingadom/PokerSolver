# AWS demonstration deployment

Terraform defines ECR repositories, ECS Fargate API/workers, a restricted ALB, SQS and DLQ, private RDS PostgreSQL, optional private encrypted Redis, a private artifact bucket, CloudWatch logs/alarms and distinct task IAM roles. The API image includes the built React UI, served on the same origin. No AWS infrastructure was deployed during local development.

## Costs and boundaries

Applying the configuration creates billable RDS storage/instance, ALB, Secrets Manager, public IPv4 allocation for running tasks, log ingestion and Container Insights. Redis is off and ECS desired counts are zero initially; RDS and ALB still cost money at zero task count. Check the AWS pricing calculator for your region and intended duration before applying. Destroy demo resources promptly. This is not a free-tier promise.

To avoid a NAT gateway in a short-lived demo, tasks use public subnets/public IPs. Worker security groups have no inbound rules; API ingress accepts only the ALB. Database/Redis use isolated subnets with no internet route and accept only the relevant task security groups. HTTPS egress is allowed for AWS endpoints/ECR. Set `allowed_web_cidr` to your public IP /32; unrestricted access is rejected. Authentication is not implemented.

RDS creates and manages its master password in Secrets Manager. Terraform references only its ARN; it never reads a secret value or accepts a password variable. ECS injects the password at startup. Secure Terraform state even though it does not contain the password. A production adaptation should use separate migration and restricted application database roles, verified database certificates (`sslmode=verify-full` with the RDS CA), stronger backup/deletion protection and private tasks with NAT or VPC endpoints. The demo uses encrypted PostgreSQL transport (`sslmode=require`) and one managed database credential. Restart tasks after secret rotation so connection pools receive the new value.

## Provision and run

Prerequisites: Terraform 1.13+, Docker, AWS CLI v2, an AWS account/profile with permission to create these resources, and a reviewed plan. These commands are manual; Codex does not apply infrastructure without explicit permission.

```sh
cd infrastructure/terraform
cp terraform.tfvars.example terraform.tfvars
# Edit allowed_web_cidr; leave task counts at zero for bootstrap.
terraform init
terraform fmt -check
terraform validate
terraform plan -out=demo.tfplan
terraform apply demo.tfplan
terraform output -json repository_urls
```

Build and push API/worker images from the repository root using a unique commit tag. Substitute your region/account and repository URLs from the outputs; never commit AWS credentials.

```sh
aws ecr get-login-password --region eu-west-2 | docker login --username AWS --password-stdin ACCOUNT.dkr.ecr.eu-west-2.amazonaws.com
docker build --build-arg MODULE=api -t API_REPOSITORY:COMMIT_SHA .
docker build --build-arg MODULE=worker -t WORKER_REPOSITORY:COMMIT_SHA .
docker push API_REPOSITORY:COMMIT_SHA
docker push WORKER_REPOSITORY:COMMIT_SHA
```

Set `image_tag` to that SHA, `api_count=1`, and `worker_count=2`. Review a fresh `terraform plan` and apply it. Wait for ECS service stability. Open the `api_endpoint` output from the allowed network. The same host serves `/`, `/api/v1/simulations`, `/v3/api-docs`, and `/swagger-ui/index.html`. Only the unauthenticated, IP-restricted demo should use the HTTP default. For HTTPS supply an ACM certificate ARN and map a DNS name covered by that certificate to the ALB; visit that DNS name rather than the raw ALB hostname.

Scale workers by changing `worker_count` (0–8) and reviewing/applying the plan. SQS is encrypted and retries five times before redrive. Configure existing SNS topic ARNs in `alarm_actions` for alerts. The S3 artifact bucket is provisioned for manually uploaded benchmark artifacts; runtime exports are a deferred stretch goal.

## GitHub deployment template

The manual deployment workflow requires an existing OIDC role, restricted to this repository and the `aws-demo` GitHub Environment. Protect that environment with required reviewers. Configure repository variables `AWS_ROLE_ARN`, `AWS_REGION`, `ECR_API_REPOSITORY`, `ECR_WORKER_REPOSITORY`, `ECS_CLUSTER`, `ECS_API_SERVICE`, and `ECS_WORKER_SERVICE`. It builds immutable images and registers updated ECS task definitions. It does not run Terraform or create IAM/OIDC resources.

The OIDC role needs ECR authorization, upload and image-read permissions for only the two repositories, ECS Describe/RegisterTaskDefinition and UpdateService for this deployment, and `iam:PassRole` for only the task/execution roles. Restrict the trust policy subject to `repo:YOUR_OWNER/YOUR_REPO:environment:aws-demo`, audience `sts.amazonaws.com`. AWS actions with no resource-level scope require `Resource="*"`; do not widen other resources to match. Provision this role through your account's established IAM process and review it before enabling the workflow.

After a workflow rollout, update Terraform's `image_tag` to the deployed SHA before the next plan so it does not roll the service back. Bootstrap repositories and at least one task revision through Terraform first.

## Tear down

```sh
terraform plan -destroy
terraform destroy
```

The demo explicitly skips the RDS final snapshot and has deletion protection off: destroying it removes the database. Export anything you intend to retain first. ECR repositories and S3 buckets deliberately do not force deletion of contents; remove unwanted demo images/artifacts through the AWS console or CLI after review, then rerun destroy if AWS reports nonempty resources. The provider lockfile is committed; backend credentials, state and plans are ignored. Configure an encrypted remote state backend with locking for shared environments.
