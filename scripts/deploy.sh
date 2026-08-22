#!/bin/bash
# MyFinance Deployment Script (Multi-Tenant)
# Run this on EC2 to pull latest code, build, and deploy.
# Usage: bash scripts/deploy.sh [branch-name]
#
# Prerequisites on EC2:
#   - Java 17 (amazon-corretto-17)
#   - Node.js 18+ (via nvm)
#   - AWS CLI configured (for S3 upload)
#   - Git with repo access
#
# FIRST DEPLOY NOTE:
#   If upgrading from a pre-multi-tenant version, delete the old DB:
#   rm -f /home/ec2-user/data/myfinance.mv.db
#   The app will create a fresh DB with userId columns on all tables.
#   Default admin: admin / admin123 (ADMIN role)

set -e

BRANCH="${1:-main}"
REPO_DIR="/home/ec2-user/myfinance"
S3_BUCKET="${S3_BUCKET:-myfinance-frontend}"
BACKEND_SERVICE="myfinance"

echo "========================================="
echo "  MyFinance Deployment"
echo "  Branch: $BRANCH"
echo "========================================="
echo ""

# ─── Pull Latest Code ───
echo "[1/5] Pulling latest code from branch: $BRANCH"
if [ -d "$REPO_DIR" ]; then
  cd "$REPO_DIR"
  git fetch origin
  git checkout "$BRANCH"
  git pull origin "$BRANCH"
else
  git clone -b "$BRANCH" https://github.com/kumarmugu/myfinance.git "$REPO_DIR"
  cd "$REPO_DIR"
fi
echo "      Done. Commit: $(git rev-parse --short HEAD)"

# ─── Build Backend ───
echo ""
echo "[2/5] Building backend..."
cd "$REPO_DIR/backend"
./mvnw package -DskipTests -q
echo "      JAR built: target/myfinance-backend-1.0.0-SNAPSHOT.jar"

# ─── Deploy Backend ───
echo ""
echo "[3/5] Deploying backend..."
sudo systemctl stop $BACKEND_SERVICE 2>/dev/null || true
cp target/myfinance-backend-1.0.0-SNAPSHOT.jar /home/ec2-user/myfinance-backend.jar

# Create systemd service if not exists
if [ ! -f /etc/systemd/system/$BACKEND_SERVICE.service ]; then
  sudo tee /etc/systemd/system/$BACKEND_SERVICE.service > /dev/null <<EOF
[Unit]
Description=MyFinance Backend
After=network.target

[Service]
User=ec2-user
ExecStart=/usr/bin/java -jar /home/ec2-user/myfinance-backend.jar --spring.datasource.url=jdbc:h2:file:/home/ec2-user/data/myfinance
WorkingDirectory=/home/ec2-user
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF
  sudo systemctl daemon-reload
  sudo systemctl enable $BACKEND_SERVICE
fi

sudo systemctl start $BACKEND_SERVICE
echo "      Backend started on port 8080"

# ─── Build Frontend ───
echo ""
echo "[4/5] Building frontend..."
cd "$REPO_DIR/frontend"
npm install --silent
npm run build
echo "      Frontend built: dist/"

# ─── Deploy Frontend to S3 ───
echo ""
echo "[5/5] Uploading frontend to S3..."
aws s3 sync dist/ "s3://$S3_BUCKET/" --delete --quiet
echo "      Uploaded to s3://$S3_BUCKET/"

# ─── Backup Database ───
echo ""
echo "[Backup] Copying database to S3..."
BACKUP_KEY="backups/myfinance-$(date +%Y%m%d-%H%M%S).mv.db"
if [ -f /home/ec2-user/data/myfinance.mv.db ]; then
  aws s3 cp /home/ec2-user/data/myfinance.mv.db "s3://$S3_BUCKET/$BACKUP_KEY" --quiet
  echo "      Backup: s3://$S3_BUCKET/$BACKUP_KEY"
else
  echo "      No database file found (first deploy)"
fi

echo ""
echo "========================================="
echo "  Deployment Complete!"
echo "  Backend: http://$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4):8080"
echo "  Frontend: https://$S3_BUCKET.s3.amazonaws.com/index.html"
echo "========================================="
