# Deploying JobTracker to AWS with Kubernetes (k3s)

This deploys your app on a single AWS EC2 instance running k3s (a lightweight,
production-grade Kubernetes distribution), with Postgres running as a pod
inside the cluster. This avoids RDS costs/complexity while still giving you
real hands-on Kubernetes experience on AWS - genuinely resume-worthy.

**Estimated cost**: a t3.small EC2 instance running 24/7 costs roughly
$15-17/month (far less if you stop it when not using it). With your $110
credit, that's months of runway, especially if you stop the instance between
demos/interviews rather than leaving it running constantly.

## Prerequisites
- Docker Desktop installed locally (to build and push the image)
- A free Docker Hub account: https://hub.docker.com (for hosting your image -
  simpler than AWS's own registry, ECR, for a project this size)
- Your AWS account with the $110 credit

## 1. Launch the EC2 instance

1. AWS Console -> EC2 -> "Launch Instance"
2. Name: `jobtracker-server`
3. AMI: **Ubuntu Server 22.04 LTS** (free tier eligible)
4. Instance type: **t3.small** (t3.micro often runs out of memory for
   Java + Postgres + k3s together; t3.small is worth the small extra cost)
5. Key pair: create a new one, download the `.pem` file, keep it safe -
   you need it to SSH in
6. Network settings -> Edit security group rules, add:
   - SSH (port 22) - source: My IP (not 0.0.0.0/0, for security)
   - Custom TCP, port 30080, source: Anywhere (0.0.0.0/0) - this is how
     you'll reach the app from your browser
7. Storage: bump to 20 GiB (default 8 GiB is tight for Docker images + k3s)
8. Launch the instance. Note its **Public IPv4 address** once it's running.

## 2. SSH into the instance and install k3s

```bash
# From your local machine, in the folder with your .pem file:
chmod 400 your-key.pem
ssh -i your-key.pem ubuntu@<EC2-PUBLIC-IP>
```

Once connected, install k3s (one command, includes kubectl):

```bash
curl -sfL https://get.k3s.io | sh -
sudo chmod 644 /etc/rancher/k3s/k3s.yaml
```

Verify it's running:
```bash
sudo k3s kubectl get nodes
```
You should see one node in `Ready` status.

For convenience, make `kubectl` work without `sudo k3s` prefix:
```bash
mkdir -p ~/.kube
sudo k3s kubectl config view --raw > ~/.kube/config
chmod 600 ~/.kube/config
sudo snap install kubectl --classic
```
Now `kubectl get nodes` should work directly.

## 3. Build and push your Docker image (from your LOCAL machine)

In your `jobtracker` project folder:

```bash
docker login
# enter your Docker Hub username/password

docker build -t YOUR_DOCKERHUB_USERNAME/jobtracker:latest .
docker push YOUR_DOCKERHUB_USERNAME/jobtracker:latest
```

This uploads your app's image to Docker Hub so the EC2 instance can pull it.

## 4. Prepare your Kubernetes manifests

In the `k8s/` folder:

1. Copy `01-secrets-template.yaml` to `01-secrets.yaml` and fill in your
   real values (DB password you choose, Gmail app password, JSearch key,
   Anthropic key). **Never commit `01-secrets.yaml` to GitHub** - it's
   already in `.gitignore`.
2. In `04-app-deployment.yaml`, replace `YOUR_DOCKERHUB_USERNAME` with your
   actual Docker Hub username (must match what you pushed to in step 3).

## 5. Copy manifests to the EC2 instance and deploy

From your local machine:

```bash
scp -i your-key.pem -r k8s ubuntu@<EC2-PUBLIC-IP>:~/jobtracker-k8s
```

Back in your SSH session on the EC2 instance:

```bash
cd ~/jobtracker-k8s
kubectl apply -f 00-namespace.yaml
kubectl apply -f 01-secrets.yaml
kubectl apply -f 02-postgres-pvc.yaml
kubectl apply -f 03-postgres-deployment.yaml
kubectl apply -f 04-app-deployment.yaml
```

## 6. Verify it's running

```bash
kubectl get pods -n jobtracker
```
Wait until both pods show `Running` (may take 1-2 minutes for the app pod,
since it needs to start the Spring Boot app and connect to Postgres).

Check logs if something looks wrong:
```bash
kubectl logs -n jobtracker deployment/jobtracker-app
kubectl logs -n jobtracker deployment/postgres
```

## 7. Access your app

Open in a browser: `http://<EC2-PUBLIC-IP>:30080/dashboard.html?userId=1`

Register and trigger a run the same way you did locally, just pointing
`curl` at your EC2 IP instead of localhost:

```bash
curl -X POST http://<EC2-PUBLIC-IP>:30080/api/users/register \
  -H "Content-Type: application/json" -d "@register.json"

curl -X POST http://<EC2-PUBLIC-IP>:30080/api/users/1/run-now
```

## 8. Managing costs

- **Stop the instance** (not terminate) when you're not actively using or
  demoing it: AWS Console -> EC2 -> select instance -> Instance State -> Stop.
  You won't be billed for compute while stopped (small storage cost remains).
  Start it again before a demo/interview; note the public IP may change
  unless you allocate an Elastic IP (small additional cost to keep it fixed).
- Check **Billing -> Cost Explorer** periodically to track credit usage.

## What to say about this in interviews / on your resume

You now have a real, concrete story: "Built a Java Spring Boot service with
a scheduled job pipeline, containerized it with Docker, and deployed it on
a self-managed Kubernetes cluster (k3s) on AWS EC2, including secrets
management, persistent storage for Postgres, and service networking." That's
accurate and defensible in a technical interview, because you actually did it.
