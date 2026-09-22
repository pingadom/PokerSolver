#!/bin/sh
set -eu
awslocal sqs create-queue --region eu-west-2 --queue-name pokerlab-dlq --attributes MessageRetentionPeriod=1209600
cat > /tmp/pokerlab-queue-attributes.json <<'JSON'
{"VisibilityTimeout":"120","ReceiveMessageWaitTimeSeconds":"10","RedrivePolicy":"{\"deadLetterTargetArn\":\"arn:aws:sqs:eu-west-2:000000000000:pokerlab-dlq\",\"maxReceiveCount\":\"5\"}"}
JSON
awslocal sqs create-queue --region eu-west-2 --queue-name pokerlab --attributes file:///tmp/pokerlab-queue-attributes.json
