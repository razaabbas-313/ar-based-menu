# Atelier 3D Media Engine — Production Deployment Guide

This guide walks you through deploying the Atelier 3D Media Engine monorepo to production using free tier services:
- **Backend**: [Render.com](https://render.com) (Free Tier Web Service + Free Managed PostgreSQL)
- **Frontend**: [Vercel](https://vercel.com) (Free Tier Vite/React Static CDN)
- **Media Storage**: [AWS S3](https://aws.amazon.com/s3) (Direct Browser-to-S3 Presigned Uploads)

---

## 📋 Table of Contents
1. [Prerequisites & Repository Preparation](#1-prerequisites--repository-preparation)
2. [AWS S3 Configuration](#2-aws-s3-configuration)
3. [Backend Deployment on Render](#3-backend-deployment-on-render)
   - [Method A: 1-Click Render Blueprint (Recommended)](#method-a-1-click-render-blueprint-recommended)
   - [Method B: Manual Setup on Render](#method-b-manual-setup-on-render)
4. [Frontend Deployment on Vercel](#4-frontend-deployment-on-vercel)
5. [CORS Synchronization](#5-cors-synchronization)
6. [First-Time Verification & Walkthrough](#6-first-time-verification--walkthrough)
7. [Free Tier Optimizations & Caveats](#7-free-tier-optimizations--caveats)
8. [Troubleshooting](#8-troubleshooting)

---

## 1. Prerequisites & Repository Preparation

Ensure your local repository is committed and pushed to GitHub:

```bash
# 1. Initialize git (if not already done)
git init

# 2. Check status (verify secrets.properties, target/, dist/, and node_modules/ are ignored)
git status

# 3. Add all files and commit
git add .
git commit -m "feat: production deployment setup for Render and Vercel"

# 4. Link to your GitHub repository and push
git branch -M main
git remote add origin https://github.com/<your-username>/<your-repo-name>.git
git push -u origin main
```

---

## 2. AWS S3 Configuration

Atelier uses a **Zero-Server-RAM** architecture: the backend generates cryptographically signed presigned URLs, and dish photos / 3D models stream directly between the client browser and your AWS S3 bucket.

### A. CORS Configuration
In AWS Console -> **S3** -> Your Bucket -> **Permissions** tab -> **Cross-origin resource sharing (CORS)**, paste:

```json
[
  {
    "AllowedHeaders": ["*"],
    "AllowedMethods": ["PUT", "GET", "HEAD"],
    "AllowedOrigins": [
      "http://localhost:5173",
      "https://*.vercel.app",
      "https://*.onrender.com"
    ],
    "ExposeHeaders": ["ETag"],
    "MaxAgeSeconds": 3600
  }
]
```
*(If you have a custom domain on Vercel like `https://menu.yourdomain.com`, add it to `AllowedOrigins` as well).*

### B. Bucket Public Read Policy
Example only: no policy has been deployed or AWS access tested by Milestone B1. Review the assumptions below before applying anything. The authoritative local templates are [s3-public-read-policy.json](infra/s3-public-read-policy.json) and [iam-policy.json](infra/iam-policy.json).

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "PublicReadPublishedMedia",
      "Effect": "Allow",
      "Principal": "*",
      "Action": "s3:GetObject",
      "Resource": "arn:aws:s3:::YOUR_BUCKET_NAME/restaurant-atelier/*"
    }
  ]
}
```
Replace `YOUR_BUCKET_NAME` only in a separately approved environment. The examples assume one application IAM principal in the **same AWS account** as the bucket, no other grants or ACLs exposing private objects, and the default configured roots. This is not a verified production arrangement. If roots are customized, adjust both policies to match; never use a bucket-wide wildcard as a shortcut.

### C. Application permissions and private references

Attach the reviewed identity policy example to the intended application principal, not to anonymous guests. It grants only PutObject, GetObject and DeleteObject under `restaurant-atelier/*` and `restaurant-atelier-private/*`. GetObject permission also supports HEAD; there is no separate `s3:HeadObject` IAM action. ListBucket uses the bucket ARN and `s3:prefix` conditions limited to these two roots, not object ARNs or unrestricted bucket listing. Private listing supports a future approved managed-prefix cleanup operation; it does not mean that complete private cleanup is currently implemented. No bucket administration, ACL changes, version deletion or unrelated-prefix permissions are granted.

The bucket example grants anonymous GetObject only under the public root. It contains **no anonymous private Allow**. Private references rely on default denial in the assumed policy arrangement; do not add another policy/ACL that exposes them. The old unconditional private GetObject Deny was removed because it denied the application's signer as well. Applicable explicit Deny statements still override an Allow, including when a URL is presigned. Review all applicable policies before concluding access is possible.

Browser PUTs remain direct to S3, with the exact signed Content-Type, length and browser-settable headers. Authorized reference readers receive temporary presigned GETs using the application's permissions; a signature is not an authorization override. Preserve ETag exposure in CORS. CORS controls browser behavior, not anonymous/private authorization. Presigned URLs are bearer capabilities and can be reused until expiry; existing metadata validation is not binary-content validation.

Block Public Access settings at organization/account/bucket/access-point levels can prevent the intended anonymous public read. Do not disable all safeguards to resolve a 403. Review the effective settings and keep ACL-based public access blocked where compatible. KMS encryption may require separately scoped KMS permissions and a compatible key policy; no KMS rights are invented here. Cross-account use requires separately reviewed identity/resource/trust policies. **Stop deployment if principal, KMS, Block Public Access or cross-account requirements are unknown**; these local templates do not resolve unknown production settings.

### D. Future nonproduction verification — separate approval required

Use a dedicated disposable bucket, synthetic public/private objects and a narrowly scoped test identity after the bucket, region, principal, budget and cleanup actions are explicitly approved. An isolated prefix in a production bucket does not safely isolate bucket-policy or Block Public Access changes.

Verify authorized private PUT/HEAD/GET/DELETE, anonymous private GET denial, intended anonymous public GET, rejection of unrelated/lookalike prefixes, scoped listing, expired signatures and signed browser headers/CORS. Record sanitized statuses, not credentials or signed URLs. Local `S3PolicyTemplateTest` assertions parse JSON and verify static template scope; they are **not AWS IAM evaluation or deployed access tests**. No AWS validation is performed by B1. B2+B3 now implement private cache signing and truthful deletion results locally; [sprint results](project-documentation/SPRINT_B2_B3_RESULTS.md) describe verification and remaining lifecycle limits. Granting permission alone does not fix lifecycle or retention defects.

New private PUTs require the signed `Cache-Control: private, no-store` header; existing browser upload code already sends the required headers. Private reference GETs include a signed response-cache-control override, including explicitly keyed historical references. Public PUTs retain immutable caching. Existing object metadata, old signed URLs, previously cached copies and unkeyed legacy fallback URLs are not retroactively repaired. Review them separately in an approved nonproduction environment; do not copy objects or invalidate live caches under this sprint.

Deletion outcomes are S3 acknowledgements, explicit failures or unknown outcomes; an incomplete prefix listing is never complete success. Acknowledgement does not prove purge of historical versions/delete markers. Single-object cleanup remains best effort, and replacement/expiry/hard-deletion crash recovery is not durable until B4. Never interpret deleted database rows or cleanup-request counts as physical purge evidence.

Local regression command from `backend/`: `mvn -o '-Dtest=S3PolicyTemplateTest' test` (requires already cached dependencies). The suite checks JSON parsing, public-only anonymous grants, absence of the old signer-blocking Deny, bounded object actions/resources, bucket-prefix listing conditions and unrelated/lookalike-prefix exclusion. It does not load AWS credentials or contact AWS. Public-prefix access is prefix-wide, including known pending-object keys; database publication/privacy state is not an S3 authorization boundary.

---

## 3. Backend Deployment on Render

### Method A: 1-Click Render Blueprint (Recommended)

1. Log in to your [Render Dashboard](https://dashboard.render.com).
2. Click **New +** -> **Blueprint**.
3. Connect your GitHub repository.
4. Render detects [`render.yaml`](./render.yaml) and automatically creates:
   - **`atelier-postgres`**: PostgreSQL database (Free tier).
   - **`atelier-backend`**: Docker Web Service running Spring Boot 3.3.5 / Java 21 (Free tier).
5. Fill in the prompted secret variables:
   - `ATELIER_SUPERADMIN_PASSWORD`: Your chosen master password for `/admin`.
   - `AWS_ACCESS_KEY_ID`: Your AWS IAM access key.
   - `AWS_SECRET_ACCESS_KEY`: Your AWS IAM secret key.
6. Click **Apply**. Render will provision the database, build the Docker container, run Flyway migrations, and start the service!

---

### Method B: Manual Setup on Render

#### Step 1: Create PostgreSQL Database
1. In Render Dashboard, click **New +** -> **PostgreSQL**.
2. Set:
   - **Name**: `atelier-postgres`
   - **Database**: `atelier_db`
   - **User**: `atelier_user`
   - **Plan**: `Free`
3. Click **Create Database**.
4. Once created, copy the **Internal Database URL** (e.g. `postgres://atelier_user:...@dpg-...:5432/atelier_db`).

#### Step 2: Create Web Service
1. Click **New +** -> **Web Service**.
2. Connect your GitHub repository.
3. Configure settings:
   - **Name**: `atelier-backend`
   - **Region**: Same region as your PostgreSQL (e.g. Oregon).
   - **Runtime**: `Docker`
   - **Root Directory**: `backend` (or leave blank if using root Dockerfile)
   - **Dockerfile Path**: `Dockerfile`
   - **Plan**: `Free`
4. Set **Health Check Path**: `/actuator/health`
5. Add the following **Environment Variables**:

| Variable Name | Value / Description |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DATABASE_URL` | Paste your Render PostgreSQL **Internal Database URL** |
| `ATELIER_JWT_SECRET` | Generate 64 hex characters (e.g., run `openssl rand -hex 32`) |
| `ATELIER_SUPERADMIN_EMAIL` | `admin@atelier.local` |
| `ATELIER_SUPERADMIN_PASSWORD` | Choose a strong super admin password |
| `ATELIER_CORS_ORIGINS` | `http://localhost:5173,https://*.vercel.app` |
| `AWS_REGION` | `ap-south-1` (or your S3 region) |
| `AWS_S3_BUCKET` | `atelier-ar-bucket` |
| `AWS_ACCESS_KEY_ID` | Your AWS Access Key |
| `AWS_SECRET_ACCESS_KEY` | Your AWS Secret Key |
| `ATELIER_MAX_PUBLISHED_MODEL_BYTES` | `26214400` (25 MB published model cap) |

6. Click **Create Web Service**.

> [!NOTE]
> Atelier includes a built-in `DatabaseUrlEnvironmentPostProcessor` that automatically translates Render's `postgres://...` URL into a standard Spring Boot JDBC connection (`jdbc:postgresql://...`) and extracts the credentials seamlessly.

---

## 4. Frontend Deployment on Vercel

1. Log in to [Vercel](https://vercel.com) and click **Add New...** -> **Project**.
2. Select your GitHub repository.
3. In the project setup screen:
   - **Framework Preset**: `Vite`
   - **Root Directory**: Click *Edit* and select `frontend` *(If left as root, the included root `vercel.json` will automatically build the `frontend` subfolder)*.
   - **Build and Output Settings**:
     - Build Command: `npm run build`
     - Output Directory: `dist`
4. In **Environment Variables**, add:

| Key | Value | Example |
|---|---|---|
| `VITE_API_BASE_URL` | Your Render backend domain (no trailing slash) | `https://atelier-backend.onrender.com` |
| `VITE_CURRENCY` | Currency code for menus | `USD` |
| `VITE_SALES_EMAIL` | Optional sales contact email | `sales@yourdomain.com` |

5. Click **Deploy**. Vercel will install dependencies, build the React SPA, and assign you a URL (e.g. `https://atelier-ar.vercel.app`).

---

## 5. CORS Synchronization

Once Vercel gives you your frontend domain (e.g. `https://atelier-frontend.vercel.app`):
1. Go back to Render Dashboard -> `atelier-backend` -> **Environment**.
2. Update `ATELIER_CORS_ORIGINS` to include your exact Vercel domain:
   ```
   http://localhost:5173,https://atelier-frontend.vercel.app,https://*.vercel.app
   ```
3. Render will redeploy in seconds with the updated CORS rule.

---

## 6. First-Time Verification & Walkthrough

### 1. Actuator Health Check
Open `https://<your-backend>.onrender.com/actuator/health` in your browser.
Expected response:
```json
{"status":"UP","components":{"db":{"status":"UP"},"diskSpace":{"status":"UP"},"ping":{"status":"UP"}}}
```

### 2. Sign In to Super Admin
1. Navigate to `https://<your-frontend>.vercel.app/admin`.
2. Sign in with:
   - **Email**: `admin@atelier.local` (or the email set in `ATELIER_SUPERADMIN_EMAIL`).
   - **Password**: Your configured `ATELIER_SUPERADMIN_PASSWORD`.
3. Create your first restaurant:
   - Click **+ Add Restaurant**.
   - Fill in name (e.g. *Bistro Parisien*), slug (e.g. *bistro-parisien*), and owner email.
   - Toggle **Studio Direct 3D Upload** ON if you want the owner to directly upload `.glb` models.
   - Copy the temporary owner password generated.

### 3. Open Restaurant Studio
1. Open an incognito tab and navigate to `https://<your-frontend>.vercel.app/studio`.
2. Sign in with the owner email and temporary password.
3. The app prompts you to create a new password. Enter a new password and proceed.
4. Add your first dish:
   - Upload a dish photo.
   - Add a 3D model (`.glb`) directly or click **Request 3D Model Concierge**.
   - Click Save.

### 4. View Guest 3D Menu on Mobile
1. Click **View Public Menu** or scan the QR code.
2. The URL opens at `https://<your-frontend>.vercel.app/menu/bistro-parisien`.
3. Tap on the dish to view the interactive 3D model or tap **View in your space (AR)** to view the dish on your table!

---

## 7. Free Tier Optimizations & Caveats

### Render Free Tier (512 MB RAM limit)
Render's free tier has a hard limit of 512 MB RSS. Exceeding this triggers error 137 (OOM killed).
Atelier is specifically pre-configured with flags in `Dockerfile`:
```bash
JAVA_OPTS="-Xms192m -Xmx320m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:MaxMetaspaceSize=128m -Djava.awt.headless=true -Dfile.encoding=UTF-8"
```
- **`-XX:+UseSerialGC`**: Saves ~60-80 MB of GC native metadata compared to G1GC.
- **`-XX:TieredStopAtLevel=1`**: Disables C2 compiler profiling, saving ~35 MB of code cache and speeding up startup.
- **`-Xmx320m -XX:MaxMetaspaceSize=128m`**: Bounds JVM memory strictly within ~460 MB, safely under the 512 MB limit.

### Cold Starts
Render free services spin down after 15 minutes of inactivity. The first request after spin-down takes ~30-45 seconds while the container boots. Subsequent requests are fast (< 100ms).

### Draco 3D Web Worker Content Security Policy
Vite bundles `@google/model-viewer` and Three.js, which use Web Workers for DRACO and KTX2 mesh decompression.
Both `frontend/vercel.json` and root `vercel.json` have been configured with:
```
worker-src 'self' blob:; child-src 'self' blob:;
```
to ensure 3D rendering is never blocked by browser CSP.

---

## 8. Troubleshooting

| Symptom | Cause | Solution |
|---|---|---|
| **Render crashes with exit 137** | Process exceeded 512 MB RAM | Ensure you use the provided `Dockerfile` which sets `-XX:+UseSerialGC` and `-Xmx320m`. |
| **Backend says "Refusing to start with 'local' profile"** | `SPRING_PROFILES_ACTIVE` not set | Set environment variable `SPRING_PROFILES_ACTIVE=prod` in Render. |
| **Backend says "ATELIER_JWT_SECRET must be configured"** | Missing or short JWT secret | Set `ATELIER_JWT_SECRET` with at least 32 characters (e.g. 64-hex string). |
| **CORS error in browser console** | Origin mismatch | Update `ATELIER_CORS_ORIGINS` in Render with your exact Vercel URL. |
| **S3 Upload returns 403 Forbidden** | S3 CORS or IAM credentials | Verify `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, and ensure `s3-cors.json` is applied to your bucket. |
| **404 Not Found on refreshing React pages** | Missing SPA rewrite | Ensure `vercel.json` exists in `frontend/` or root with rewrite `/(.*) -> /index.html`. |
