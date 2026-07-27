# AWS Deployment — Phase 9

Region: **us-east-1**. Credentials come from the **SDK default chain** (env vars / SSO / instance or
task role) — never hardcoded. Set `AWS_REGION=us-east-1` and leave the `*_ENDPOINT` overrides empty in
real AWS (those are only for LocalStack/dynamodb-local in dev/test).

Names below are defaults (overridable via env): bucket `bookstore-covers`, tables `CoverMetadata` and
`UserBrowsingHistory`, SNS topic `bookstore-covers-processed`.

## 1. DynamoDB tables

```bash
# Cover metadata (PK bookId). Written by the Lambda, read by book-service.
aws dynamodb create-table --region us-east-1 \
  --table-name CoverMetadata \
  --attribute-definitions AttributeName=bookId,AttributeType=N \
  --key-schema AttributeName=bookId,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST

# Browsing history (PK userId, SK viewedAt) + 30-day TTL on expireAt.
aws dynamodb create-table --region us-east-1 \
  --table-name UserBrowsingHistory \
  --attribute-definitions AttributeName=userId,AttributeType=S AttributeName=viewedAt,AttributeType=N \
  --key-schema AttributeName=userId,KeyType=HASH AttributeName=viewedAt,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST
aws dynamodb update-time-to-live --region us-east-1 \
  --table-name UserBrowsingHistory \
  --time-to-live-specification "Enabled=true, AttributeName=expireAt"
```

## 2. S3 bucket

```bash
aws s3 mb s3://bookstore-covers --region us-east-1
```

Later (step 4) add an event notification: `s3:ObjectCreated:*`, prefix `covers/`, target = the Lambda.

## 3. SNS topic + SES email

```bash
aws sns create-topic --region us-east-1 --name bookstore-covers-processed
# Subscribe an email (confirm via the email link). SES sends the actual mail;
# in the SES sandbox, verify both sender and recipient addresses first.
aws sns subscribe --region us-east-1 \
  --topic-arn arn:aws:sns:us-east-1:<ACCOUNT_ID>:bookstore-covers-processed \
  --protocol email --notification-endpoint you@example.com
```

## 4. Lambda (`cover-image-lambda`)

```bash
# Build the shaded jar
cd bookstore-platform && mvn -pl cover-image-lambda -am package
# Artifact: bookstore-platform/cover-image-lambda/target/cover-image-lambda.jar
```

Deploy with runtime **java17**, handler `com.bookstore.coverlambda.CoverImageHandler`, and env vars
`COVER_METADATA_TABLE=CoverMetadata`, `COVER_TOPIC_ARN=arn:aws:sns:...:bookstore-covers-processed`.
The execution role needs: `s3:GetObject` on `bookstore-covers/covers/*`, `dynamodb:PutItem` on
`CoverMetadata`, and `sns:Publish` on the topic. Then wire the S3 → Lambda notification (step 2) and
grant S3 permission to invoke the function.

## 5. book-service (real AWS)

Provide AWS credentials (env/role) and:

```
AWS_REGION=us-east-1
COVER_BUCKET=bookstore-covers
# leave AWS_DYNAMODB_ENDPOINT / AWS_S3_ENDPOINT unset -> real AWS endpoints
```

## Flow & idempotency

1. `POST /api/books/{id}/cover` (ADMIN) → book-service returns a presigned S3 PUT URL for
   `covers/{id}`.
2. Client PUTs the image to S3 → `s3:ObjectCreated` fires the Lambda.
3. Lambda reads the object (size, content-type, width/height), then does a **conditional**
   `PutItem(attribute_not_exists(bookId))` on `CoverMetadata` and, only on a first-time write,
   publishes SNS → email. A duplicate S3 event fails the condition and is skipped — no duplicate row,
   no duplicate email.
4. `GET /api/books/{id}/cover` (public) → book-service reads `CoverMetadata`.

Browsing history (Feature B) needs only the `UserBrowsingHistory` table above; book-service writes it
asynchronously on a logged-in `GET /api/books/{id}` and serves `GET /api/books/me/history`.

## Local dev without real AWS

Tests use `amazon/dynamodb-local` (Testcontainers) for DynamoDB and offline presigning for S3, so no
AWS account is needed for `mvn verify`. The S3→Lambda→SNS/SES wiring is validated by deploying to a
real account as above (LocalStack can't run on this project's Colima engine — see `docs/BUGLOG.md`).
