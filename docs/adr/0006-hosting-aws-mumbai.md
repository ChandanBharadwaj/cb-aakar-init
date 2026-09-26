# ADR-0006: Host on AWS ap-south-1

- Status: Proposed
- Date: 2026-09-26
- Deciders: Chandan Bharadwaj (product owner)

## Context
Customers, payments, shipping and the studio are in India. Data residency and latency favour an Indian region. The team is small and needs managed services.

## Decision
Recommend: **AWS ap-south-1 (Mumbai)**: ECS Fargate for services, RDS PostgreSQL, Amazon MQ (RabbitMQ), S3 with CloudFront, Secrets Manager. Terraform in `infra/`. Local development uses the Docker Compose stack.

## Consequences
One cloud account and IAM model to secure. Alternatives (GCP asia-south1, Azure Central India) remain possible since services are containers and Postgres is portable.
