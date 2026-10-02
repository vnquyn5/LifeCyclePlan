#!/usr/bin/env sh
set -eu

echo "Install mobile dependencies with: cd mobile && npm install"
echo "Resolve backend dependencies with: cd backend && mvn dependency:resolve"
echo "Copy .env.example files before starting local infrastructure."
