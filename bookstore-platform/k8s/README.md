# Kubernetes manifests

Deploys the platform to a cluster: one PostgreSQL (database per service), a
single-node Kafka, the seven backend services + the gateway, all wired via a
shared ConfigMap/Secret, with Actuator liveness/readiness probes and HPAs for the
two hottest services.

All services listen on **8080** and reach each other by Service DNS name
(`http://book-service:8080`, `postgres:5432`, `kafka:9092`). Only the gateway is
exposed externally.

## Build and load the images

The manifests use `imagePullPolicy: IfNotPresent` with `bookstore/<service>:latest`,
so build the images from the repo and load them into your local cluster:

```bash
cd bookstore-platform
for s in config-server user-service book-service order-service payment-service \
         notification-service analytics-service api-gateway; do
  docker build --build-arg SERVICE=$s -t bookstore/$s:latest .
done

# kind:
kind load docker-image bookstore/config-server:latest bookstore/user-service:latest ... 
# minikube:
# minikube image load bookstore/<service>:latest   (per image)
```

## Deploy

```bash
kubectl apply -f k8s/00-namespace.yaml
kubectl apply -f k8s/          # applies the rest (config, postgres, kafka, services, gateway, hpa)
kubectl -n bookstore get pods -w
```

Reach the gateway via the `api-gateway` Service (LoadBalancer). On kind/minikube use
`kubectl -n bookstore port-forward svc/api-gateway 8080:80` or `minikube tunnel`.

## Notes

- **Secrets:** `10-config.yaml` holds local-dev placeholder credentials; a real
  cluster sources the Secret from a secret manager (Sealed Secrets / External
  Secrets / SSM), never from git.
- **Storage:** PostgreSQL uses an `emptyDir` (ephemeral) for simplicity; production
  would use a StatefulSet + PersistentVolumeClaim.
- **HPA** needs the metrics-server installed.
- **AWS features** (browsing history, covers) need real AWS credentials injected
  (e.g. IRSA on EKS); without them those book-service calls fail best-effort.
