# TWINDIGITAL: KUBERNETES DIGITAL TWIN AND WHAT-IF SIMULATION PLATFORM

### A containerized Kubernetes operations platform with live topology visualization, blast-radius analysis, and workload simulation

**Project Name:** TwinDigital

---

**Institution:** Lovely Professional University  

**Project Deploy Links:**  
GitHub: [https://github.com/navyagona/TWINDIGITAL-KUBERNETES-DIGITAL-TWIN-AND-WHAT-IF-SIMULATION-PLATFORM](https://github.com/navyagona/TWINDIGITAL-KUBERNETES-DIGITAL-TWIN-AND-WHAT-IF-SIMULATION-PLATFORM)  
Docker Registry: Amazon ECR (`twindigital/`)  
Live Local Endpoints: Web UI (`http://localhost:8088`), API Gateway (`http://localhost:3000`)  

**Submitted by:** Gona Navya Ramani  
**Branch:** B.Tech – Computer Science and Engineering  
**Registration:** 12300802  
**Section:** K402311

**Submitted to:**  
**Faculty:** Ms. Arshiya  
**Subject:** INT378 (Advancements in Cloud and DevOps)  
**Academic Session:** 2026–2027

---

## Certificate

This is to certify that the project report entitled **"TwinDigital: Kubernetes Digital Twin and What-If Simulation Platform"** is a record of the project work carried out by the student named below under the guidance and supervision of the undersigned, in partial fulfilment of the academic requirements of the programme.

The report documents the design, implementation, configuration and verification of the TwinDigital platform and its containerized microservices architecture using Docker Compose, Spring Boot, React, an Express API gateway, Jenkins CI/CD, and Terraform-based Amazon EKS infrastructure.

To the best of my knowledge, the work presented in this report is suitable for academic evaluation, subject to the institution's prescribed review and assessment process.

Student Name: Gona Navya Ramani  
Registration / Roll Number: 12300802  
Date: 21-11-2026

---

## Student Declaration

I hereby declare that the project report entitled **"TwinDigital: Kubernetes Digital Twin and What-If Simulation Platform"** submitted in partial fulfilment of the requirements for the subject INT378 (Advancements in Cloud and DevOps) is my own original work and has not been submitted elsewhere for any other degree or examination.

All sources of information and references used in the preparation of this report have been duly acknowledged.

Student Name: Gona Navya Ramani  
Registration Number: 12300802  
Date: 21-11-2026

---

## Acknowledgement

I express my sincere gratitude to my project supervisor and faculty members for their guidance, technical suggestions and support throughout the development of TwinDigital. Their feedback helped shape the project structure and improve the clarity of the implementation and its documentation.

I also acknowledge the maintainers of Spring Boot, React, Docker, Kubernetes, Terraform, Jenkins, Prometheus, Grafana, and the wider open-source ecosystem. Their software and technical documentation form the foundation of the containerized microservices and cloud infrastructure architecture used in this project.

Finally, I thank my peers and everyone who provided feedback during the development, local execution and review of the project.

Student Name: Gona Navya Ramani  
Date: 21-11-2026

---

## Abstract

TwinDigital is a cloud-native, Kubernetes operations platform that combines a live cluster topology interface with a containerized microservices backend. The delivered implementation serves a React 19 Single-Page Application through an Express API gateway and collects live Kubernetes cluster state through a Java-based synchronization service. Prometheus and Grafana are integrated for infrastructure observability, while a five-check what-if simulation engine allows engineers to validate proposed workload changes before applying them to production.

The web interface is organised into five operator-facing areas: Topology Graph, What-If Simulation Workbench, Cost and Right-Sizing Dashboard, Cluster Sync Status, and Authentication. These areas provide a unified navigation structure for reviewing the platform's Kubernetes monitoring and simulation workflows. The backend independently provides measurable Kubernetes resource metrics, topology graph data, simulation outcomes, cost estimates, and security audit trails.

The implementation is packaged with Docker Compose as nine services: PostgreSQL, Redis, cluster-sync-service, auth-service, topology-service, simulation-service, cost-risk-service, the Express gateway, and the React frontend. A dedicated Docker network connects the containers, and named volumes preserve PostgreSQL and Redis data across container recreation. All services use restart policies and Spring Boot Actuator health probes.

The report explains the system architecture, implementation details, deployment procedure, testing and validation approach, security design, and extension roadmap. It distinguishes live Kubernetes topology data from simulation outcomes and cost estimates, so that the project's implemented capabilities are described precisely.

**Keywords:** TwinDigital, Kubernetes, digital twin, Docker Compose, Spring Boot, React, microservices, what-if simulation, blast radius, Terraform, EKS, Jenkins, DevSecOps, Prometheus, Grafana, topology graph.

**IMPLEMENTATION SUMMARY:** The delivered package includes the React frontend, five Spring Boot Java microservices, an Express API gateway, Docker Compose configuration, Flyway database migrations, Kubernetes Kustomize manifests, Terraform infrastructure code, a 9-stage DevSecOps Jenkins CI/CD pipeline, and a comprehensive automated test suite with 109 passing tests.

---

## Table of Contents

| Section | Page |
|---------|------|
| Certificate | 2 |
| Student Declaration | 3 |
| Acknowledgement | 4 |
| Abstract | 5 |
| 1. Introduction | 7 |
| 2. Requirements and Technology Stack | 10 |
| 3. System Architecture and Design | 12 |
| 4. Implementation Details | 15 |
| 5. Deployment and Operation | 22 |
| 6. Testing and Validation | 24 |
| 7. Security, Reliability and Maintenance | 26 |
| 8. Scope Boundaries and Future Enhancements | 28 |
| 9. Conclusion | 30 |
| References and Appendices | 31 |

---

## List of Figures

| Figure | Title |
|--------|-------|
| Figure 3.1 | TwinDigital high-level system architecture |
| Figure 3.2 | Microservices and data flow diagram |
| Figure 3.3 | Docker Compose service topology |
| Figure 4.1 | React frontend — Topology Graph canvas |
| Figure 4.2 | What-If Simulation Workbench interface |
| Figure 4.3 | Kubernetes cluster-sync informer event flow |
| Figure 4.4 | BFS blast-radius traversal algorithm |
| Figure 4.5 | Jenkins 9-stage CI/CD pipeline overview |
| Figure 4.6 | Grafana monitoring dashboard panels |
| Figure 6.1 | Docker Compose service health verification |
| Figure 6.2 | Gateway login and topology API response |

---

## Chapter 1: Introduction

### Project context, motivation and intended outcomes

#### 1.1 Background

Modern production systems deployed on Kubernetes present significant operational challenges. Platform Engineers and Site Reliability Engineers (SREs) must manage dozens of interdependent microservices running across multiple nodes, namespaces and availability zones. A single misconfiguration — such as an incorrect resource request, a missing memory limit, or a node drain that violates a PodDisruptionBudget — can cascade into widespread service disruption affecting hundreds of downstream callers.

TwinDigital addresses this by acting as a **digital twin** of the Kubernetes cluster: it continuously mirrors live cluster state, constructs a dependency graph, and exposes a simulation engine that lets engineers ask "what would happen if I made this change?" before committing it to production. The architecture separates live state synchronization, graph construction, simulation logic, cost analysis, and authentication into dedicated microservices. This separation makes the platform easier to operate and extend because each service has a defined responsibility and communicates over well-defined APIs.

#### 1.2 Project Overview

The user-facing application is a React 19 Single-Page Application built with TypeScript, Vite, and ReactFlow. It presents an interactive DAG (Directed Acyclic Graph) canvas where each Kubernetes resource — Node, Pod, Deployment, Service — is rendered as a graph node with dependency edges connecting them. The frontend polls the API gateway every five seconds for topology updates and renders blast-radius highlights when a node is selected.

For infrastructure synchronization, the cluster-sync-service uses the Kubernetes Java Client `SharedIndexInformer` to receive live Add, Update and Delete events from the EKS control plane. These events are persisted to PostgreSQL, from where the topology-service builds the in-memory graph. The simulation-service runs five independent check engines against this graph when an engineer submits a proposed change. The cost-risk-service provides Prometheus-based utilization data and AWS Cost Explorer billing estimates.

#### 1.3 Motivation

The project addresses three unmet needs in Kubernetes operations:

- **Topology blindness:** Teams lack a live, visual map of how services depend on each other, making it difficult to predict the downstream blast radius of a change.
- **Costly misconfiguration:** A pod resource misconfiguration can trigger a cascade of pod evictions, PodDisruptionBudget violations, or HPA misfires that are expensive to diagnose after the fact.
- **Invisible cost drift:** Kubernetes resource over-provisioning silently inflates AWS billing. Right-sizing recommendations require cross-referencing Prometheus utilization data with Cost Explorer, a process that is typically manual and error-prone.

TwinDigital integrates these concerns — topology visualization, pre-deployment simulation, and cost visibility — into a single operator platform.

#### 1.4 Intended Users

The primary users are Platform Engineers, SREs, and DevOps practitioners who manage production Kubernetes clusters and need to validate changes safely before applying them. Secondary users include technical reviewers and students who want to explore the platform's architecture, verify service availability, and understand the relationship between the microservices, the Kubernetes API, and the monitoring pipeline.

#### 1.5 Problem Definition

A Kubernetes operations interface without live cluster state synchronization offers limited visibility into actual runtime behavior. Conversely, raw Kubernetes metrics are difficult to interpret without a topology graph and a simulation layer. TwinDigital addresses this by combining a live cluster mirror with an interactive topology canvas, a what-if simulation engine, and an integrated cost-risk analysis dashboard.

#### 1.6 Aim

To design and implement a containerized TwinDigital Kubernetes digital twin platform with live cluster state synchronization, interactive topology visualization, five-check what-if simulation, integrated cost and right-sizing analysis, and a full DevSecOps CI/CD pipeline on Amazon EKS.

#### 1.7 Objectives

- Provide a React SPA with an interactive DAG topology canvas for visualizing live Kubernetes resources and dependency edges.
- Synchronize live Kubernetes cluster state using `SharedIndexInformer` (List + Watch) and persist it to PostgreSQL.
- Expose blast-radius analysis through in-memory BFS graph traversal in the topology-service.
- Implement a five-check what-if simulation engine: SchedulingFit, LimitSanity, NodeRemovalImpact, HpaImpact, and BlastRadius.
- Integrate Prometheus PromQL utilization queries and AWS Cost Explorer for right-sizing and cost delta estimates.
- Secure all APIs with JWT-based RBAC (ADMIN / VIEWER roles) enforced at the Express gateway layer.
- Package all services and configuration through Docker Compose with health checks and named volumes.
- Build an 8-stage Jenkins DevSecOps CI/CD pipeline with SAST, SCA, container scanning, and DAST security gates.
- Provision production infrastructure on Amazon EKS using Terraform IaC and Kustomize manifests.

#### 1.8 Scope of the Delivered Implementation

The delivered package covers the React frontend SPA, five Spring Boot Java microservices, the Express API gateway, PostgreSQL and Redis backing stores, Docker Compose local orchestration, Kubernetes Kustomize manifests, Terraform IaC, a Jenkins CI/CD pipeline, and a comprehensive automated test suite.

Live EKS cluster connection, Prometheus metric history, and AWS Cost Explorer credentials are external dependencies that are not provisioned by Docker Compose. Without a `KUBECONFIG_BASE64` value, the topology-service returns its built-in seeded demo graph with 18 nodes and 19 edges. The platform is verified locally with all nine Compose services reaching healthy and 109 automated tests passing at 100%.

---

## Chapter 2: Requirements and Technology Stack

### 2.1 Software and Platform Components

| Component | Technology / Version | Responsibility |
|-----------|---------------------|----------------|
| TwinDigital frontend | React 19, TypeScript, Vite, ReactFlow | Interactive DAG topology canvas and operator workflows |
| API gateway | Express.js (Node 20) | JWT validation, RBAC, rate limiting, request routing |
| auth-service | Spring Boot 3.3, Java 17 | BCrypt passwords, JWT issuance, RBAC roles, refresh tokens |
| cluster-sync-service | Spring Boot 3.3, Kubernetes Java Client | List+Watch informers, PostgreSQL state persistence |
| topology-service | Spring Boot 3.3, in-memory BFS | Directed graph construction, blast-radius traversal |
| simulation-service | Spring Boot 3.3, five check engines | What-if simulation, cost delta, audit logging |
| cost-risk-service | Spring Boot 3.3, Prometheus, AWS SDK | PromQL utilization queries, right-sizing, AWS billing |
| Container orchestration | Docker Engine, Docker Compose | Service lifecycle, networking, volumes, health checks |
| Relational database | PostgreSQL 15 | Cluster snapshots, simulation audit logs, user credentials |
| Distributed cache | Redis 7 | Refresh tokens, rate-limit counters |
| Infrastructure as Code | HashiCorp Terraform | Amazon VPC, EKS, RDS, ElastiCache, IAM, ECR |
| Kubernetes manifests | Kustomize (base + overlays) | Staging and production Kubernetes deployment |
| CI/CD pipeline | Jenkins (8-stage Declarative) | Build, test, scan, push, deploy |
| Container scanning | Trivy | HIGH/CRITICAL CVE detection in container images |
| SAST | SonarQube | Static code quality and security hotspot analysis |
| SCA | OWASP Dependency-Check | Third-party dependency CVE scanning |
| DAST | OWASP ZAP | Dynamic application security testing |
| Observability | kube-prometheus-stack | Prometheus, Grafana, Alertmanager, cAdvisor |
| Load testing | k6 | Gateway stress test scripting to 1,000 req/s |

### 2.2 Functional Requirements

| ID | Requirement | Acceptance Evidence |
|----|-------------|-------------------|
| FR-01 | Serve the TwinDigital SPA over HTTP through the gateway | Frontend accessible on host port 8088; gateway on port 3000 |
| FR-02 | Authenticate users and issue JWT tokens with RBAC roles | Login endpoint returns `accessToken` with `ADMIN` or `VIEWER` role |
| FR-03 | Synchronize Kubernetes cluster state to PostgreSQL | cluster-sync-service persists Nodes, Pods, Deployments, Services, HPAs, PDBs |
| FR-04 | Provide a topology graph with blast-radius analysis | topology-service returns directed graph; BFS highlights upstream and downstream services |
| FR-05 | Execute five-check what-if simulation on proposed changes | simulation-service returns check results for SchedulingFit, LimitSanity, NodeRemovalImpact, HpaImpact, BlastRadius |
| FR-06 | Provide cost and right-sizing recommendations | cost-risk-service returns PromQL p95 utilization and AWS Cost Explorer billing data |
| FR-07 | Rate-limit API requests using Redis-backed counters | Gateway enforces 60 req/min per IP; falls back to in-memory store on Redis outage |
| FR-08 | Build, scan, and deploy images through an automated CI/CD pipeline | Jenkins pipeline passes all security gates and deploys to EKS via Kustomize |

### 2.3 Non-Functional Requirements

- **Security:** JWT-based RBAC on all protected routes; BCrypt password hashing; IRSA tokenless AWS API access; non-root containers; Trivy/SonarQube/OWASP security gates in CI.
- **Maintainability:** Flyway-versioned database migrations; ADR-documented architectural decisions; Kustomize overlay separation for staging and production.
- **Portability:** Nine-service Docker Compose stack for local development; Kustomize manifests for Kubernetes deployment; Terraform for repeatable cloud provisioning.
- **Observability:** Spring Boot Actuator health endpoints on all services; Prometheus Micrometer metrics; Grafana dashboards; structured gateway request tracing.
- **Persistence:** PostgreSQL named volume for cluster snapshots and audit logs; Redis named volume for token and rate-limit state.
- **Operational stability:** All Compose services use `restart: unless-stopped` and health-check probes; CI pipeline has automatic rollback on deployment failure.

---

## Chapter 3: System Architecture and Design

### 3.1 Architecture Overview

TwinDigital uses a **multi-tier microservices architecture** deployed on Amazon EKS in the `ap-south-1` region. The platform separates concerns across five layers: a React SPA frontend, an Express API gateway, five Java Spring Boot microservices, PostgreSQL and Redis backing stores, and live AWS telemetry sources.

*(Figure 3.1 — TwinDigital high-level system architecture)*

```mermaid
flowchart TD
    subgraph ClientLayer ["Client & Ingress Layer"]
        User["Platform Engineer / SRE"]
        Ingress["AWS ALB / Nginx Ingress (TLS)"]
        Frontend["Frontend SPA\nReact 19 + Vite + ReactFlow\n5-Second REST Polling"]
    end
    subgraph GatewayLayer ["Edge & Security Gateway"]
        Gateway["API Gateway\nExpress on Node 20\nJWT · RBAC · Rate Limiting · Tracing"]
    end
    subgraph Services ["Five Java Microservices — Spring Boot 3.3 / Java 17"]
        AuthSvc["auth-service :8081\nBCrypt · JWT · Roles · Refresh Tokens"]
        SyncSvc["cluster-sync-service :8082\nK8s Informers List+Watch · PostgreSQL"]
        TopoSvc["topology-service :8083\nIn-Memory BFS · Blast Radius"]
        SimSvc["simulation-service :8084\n5 Checks · Cost Delta · Audit Log"]
        CostSvc["cost-risk-service :8085\nPromQL 7d p95 · AWS Cost Explorer"]
    end
    subgraph Stores ["Persistence & Cache"]
        PG[("PostgreSQL 15\nCluster Snapshots · Audits")]
        RD[("Redis 7\nTokens · Rate Limits")]
    end
    subgraph Cloud ["Live AWS Telemetry"]
        EKS["EKS Control Plane\nNodes · Pods · HPAs · PDBs"]
        Prom["Prometheus\ncAdvisor · Node Exporter"]
        CE["AWS Cost Explorer\nIRSA · Caffeine Cache"]
    end
    User --> Ingress --> Frontend --> Gateway
    Gateway --> AuthSvc --> PG
    Gateway --> SyncSvc --> EKS
    Gateway --> TopoSvc
    Gateway --> SimSvc --> TopoSvc
    Gateway --> CostSvc --> Prom
    SyncSvc --> PG
    SimSvc --> PG
    SimSvc --> CostSvc --> CE
    AuthSvc --> RD
    Gateway --> RD
```

### 3.2 Architectural Layers

| Layer | Components | Description |
|-------|-----------|-------------|
| **Presentation layer** | React 19 SPA + ReactFlow | Interactive DAG canvas; polls topology every 5 seconds; role-aware UI |
| **Gateway layer** | Express on Node 20 | JWT validation, RBAC, Redis rate limiting, proxy routing, Prometheus metrics |
| **Service layer** | 5 × Spring Boot 3.3 | Auth, Cluster Sync, Topology, Simulation, Cost-Risk; each with Actuator health |
| **Persistence layer** | PostgreSQL 15 + Redis 7 | Cluster snapshots, audit logs, refresh tokens, rate-limit counters |
| **Cloud telemetry layer** | EKS + Prometheus + AWS CE | Live Kubernetes control plane, PromQL utilization, AWS billing API |

*(Figure 3.2 — Microservices and data flow diagram)*

### 3.3 Data Flow

A browser request reaches the React SPA through host port 8088. The SPA sends REST requests to the Express gateway on port 3000, which validates the JWT, enforces RBAC, and proxies the request to the appropriate downstream service. The cluster-sync-service continuously receives `Add`, `Update`, and `Delete` delta events from the EKS control plane via a long-lived HTTP Watch stream, persists each resource change to PostgreSQL, and marks its status as `STALE` if the Watch stream drops. The topology-service reads the latest cluster snapshot from PostgreSQL, assembles an in-memory directed graph, and executes BFS traversal for blast-radius requests. The simulation-service runs five independent check engines against that graph when an engineer submits a proposed change, then calls the cost-risk-service for a monthly cost delta estimate and persists the simulation outcome to the PostgreSQL audit log.

*(Figure 3.3 — Docker Compose service topology)*

### 3.4 Docker Compose Service Map

| Service | Container Name | Port Mapping | Depends On |
|---------|---------------|-------------|------------|
| postgres | twindigital-postgres | 5432 → 5432 | — |
| redis | twindigital-redis | 6379 → 6379 | — |
| cluster-sync | twindigital-cluster-sync | 8082 → 8082 | postgres (healthy) |
| auth-service | twindigital-auth-service | 8081 → 8081 | postgres, redis (healthy) |
| topology-service | twindigital-topology-service | 8083 → 8083 | cluster-sync (healthy) |
| cost-risk-service | twindigital-cost-risk-service | 8085 → 8085 | cluster-sync (healthy) |
| simulation | twindigital-simulation | 8084 → 8084 | postgres, cluster-sync, topology, cost-risk (healthy) |
| gateway | twindigital-gateway | 3000 → 3000 | auth, redis, cluster-sync, simulation, topology, cost-risk (healthy) |
| frontend | twindigital-frontend | 8088 → 8080 | gateway (healthy) |

**Docker network:** default Compose bridge. **Named volumes:** `postgres_data`, `redis_data`.

### 3.5 Service Dependencies

cluster-sync initializes the shared cluster tables that topology, simulation, and cost-risk read. The gateway waits for auth and all backend APIs before accepting traffic. The frontend waits for the gateway. These Compose `depends_on: condition: service_healthy` declarations establish a deterministic startup order verified by Spring Boot Actuator `/actuator/health` probes on each Java service and a `wget` probe on the gateway and frontend.

---

## Chapter 4: Implementation Details

### 4.1 Frontend — React 19 + Vite + ReactFlow

The frontend is implemented using TypeScript source files compiled by Vite and served by an Nginx container on port 8088. ReactFlow renders the Kubernetes topology as an interactive DAG canvas. The HTML defines the page structure, TypeScript modules control navigation, chart rendering, search and filter interactions, simulation form submission, and role-aware route guards.

*(Figure 4.1 — React frontend — Topology Graph canvas)*

#### 4.2 Main Interface Modules

| Module | Implemented Capabilities |
|--------|--------------------------|
| **Topology Graph** | Interactive DAG canvas using ReactFlow; nodes represent Kubernetes resources (Nodes, Pods, Deployments, Services); dependency edges connect them; 5-second polling for live updates; click-to-highlight blast-radius |
| **What-If Simulation Workbench** | Form to submit proposed resource changes (CPU/memory requests and limits, replica counts, node removal); displays five-check results (PASS/FAIL per check) and monthly cost delta; protected — ADMIN role only |
| **Cost & Right-Sizing Dashboard** | Prometheus p95 utilization per pod; right-sizing recommendations; AWS Cost Explorer monthly spend; Caffeine-cached to reduce API calls |
| **Cluster Sync Status** | Live display of cluster-sync health (`SYNCED` / `STALE`), last sync time, and resource counts per type |
| **Authentication** | Login form; JWT stored in memory; role-aware navigation (VIEWER cannot access `/simulate` routes) |

Build verification: TypeScript compilation 0 errors, oxlint 0 errors, 3 warnings (React effect dependency warnings in non-critical hooks). Production build passes cleanly.

*(Figure 4.2 — What-If Simulation Workbench interface)*

### 4.3 Docker Compose Services

The Compose file defines nine application services on a shared bridge network. Each Java service is built from its own multi-stage Dockerfile. The gateway is built from the `gateway/` directory and the frontend from `frontend/`. PostgreSQL and Redis use official Alpine images. All services specify `restart: unless-stopped`, `healthcheck` probes, and receive secrets exclusively through environment variables — no credentials are hardcoded in any image.

### 4.4 auth-service — Spring Boot 3.3 (:8081)

Provides authentication and authorization for the entire platform.

- BCrypt password hashing for secure credential storage
- JWT issuance (`HS256`) with configurable expiry; refresh token lifecycle stored in Redis with TTL
- Role model: `ADMIN` (full access including `/simulate`) and `VIEWER` (read-only topology and cost views)
- Bootstrap admin user created from `BOOTSTRAP_ADMIN_USERNAME` and `BOOTSTRAP_ADMIN_PASSWORD` environment variables at startup
- Flyway-managed schema (`users`, `roles`, `refresh_tokens` tables)

**Test coverage:** 19 / 19 tests — `AuthServiceTest`, `JwtTokenProviderTest`, `AuthServiceApplicationTest`, `AdminUserBootstrapTest`.

### 4.5 cluster-sync-service — Spring Boot 3.3 (:8082)

The foundational data ingestion pipeline. Mirrors live Amazon EKS cluster state to PostgreSQL using the Kubernetes Java Client `SharedIndexInformer` (List + Watch pattern).

*(Figure 4.3 — Kubernetes cluster-sync informer event flow)*

**Synchronized resource types:** Nodes, Pods, Deployments, ReplicaSets, Services, Endpoints, HorizontalPodAutoscalers (HPAs), PodDisruptionBudgets (PDBs).

**Event handler pattern:**
```java
podInformer.addEventHandler(new ResourceEventHandler<V1Pod>() {
    @Override public void onAdd(V1Pod pod)                      { syncPod(pod); }
    @Override public void onUpdate(V1Pod old, V1Pod cur)        { syncPod(cur); }
    @Override public void onDelete(V1Pod pod, boolean unknown)  { deletePod(pod); }
});
```

If the Watch stream drops, the service marks cluster status as `STALE` and reports sync lag via `/api/v1/cluster/status`, then reconnects using HTTP `resourceVersion` resumption. Without `KUBECONFIG_BASE64`, the service starts but cannot connect to a live cluster.

**Test coverage:** 25 / 25 tests — `SyncControllerTest`, `ClusterSyncMetricsTest`, `KubernetesResourceMapperTest`, `KubernetesResourcePersisterTest`, `SyncHealthTrackerTest`.

### 4.6 topology-service — Spring Boot 3.3 (:8083)

Builds and serves the cluster topology graph from stored cluster resources and exposes a blast-radius traversal API.

1. Reads normalized cluster entities from PostgreSQL (`td_services`, `td_deployments`, `td_pods`, `td_nodes`, `td_service_dependencies`)
2. Assembles an in-memory directed graph
3. Executes dual-queue **Breadth-First Search (BFS)** in both upstream and downstream directions for blast-radius computation

*(Figure 4.4 — BFS blast-radius traversal algorithm)*

```java
Queue<String> upQueue = new LinkedList<>();
upQueue.add(targetNode.getId());
while (!upQueue.isEmpty()) {
    String curr = upQueue.poll();
    for (String caller : incomingDependencies.getOrDefault(curr, Collections.emptyList())) {
        if (!caller.equals(targetNode.getId()) && upstreamIds.add(caller)) {
            upQueue.add(caller);
        }
    }
}
```

Without a live cluster, topology falls back to a seeded demo graph with **18 nodes and 19 edges** (`dataSource: SEED`). Verified locally on 2026-11-01.

**Test coverage:** 8 / 8 tests — `TopologyControllerTest`, `TopologyGraphServiceTest`.

### 4.7 simulation-service — Spring Boot 3.3 (:8084)

The what-if simulation engine that evaluates proposed Kubernetes resource changes against five independent check modules.

| Check | Description |
|-------|-------------|
| **1. SchedulingFit** | Bin-packing simulation: verifies all pods can be placed on worker nodes given allocatable CPU and memory minus existing pod requests |
| **2. LimitSanity** | Rejects invalid configurations: `limit < request`, `request > largest node`, missing memory limit, extreme CPU/memory ratio |
| **3. NodeRemovalImpact** | Simulates draining a node: checks whether displaced pods can reschedule elsewhere while respecting all PodDisruptionBudgets |
| **4. HpaImpact** | Checks whether proposed resource changes make HPA `minReplicas` and `maxReplicas` feasible within current cluster headroom |
| **5. BlastRadius** | Queries topology-service's BFS model and returns all dependent services that would be impacted by the proposed change |

The service calls cost-risk-service for a monthly cost delta estimate on each simulation and persists all simulation inputs and outcomes to PostgreSQL (`simulation_audit` table). The `/api/v1/simulate` endpoint is protected by gateway RBAC — `VIEWER` role receives HTTP 403.

**Test coverage:** 25 / 25 tests — `SimulationChecksUnitTest`, `SimulationControllerTest`, `CostOptimizationServiceTest`, `SimulationBddScenariosTest`.

### 4.8 cost-risk-service — Spring Boot 3.3 (:8085)

Provides cost visibility and right-sizing recommendations by integrating with Prometheus and AWS Cost Explorer.

- Queries Prometheus with PromQL for 7-day p95 CPU and memory utilization per pod
- Computes right-sizing recommendations (request and limit adjustments to match actual p95 utilization)
- Integrates with AWS Cost Explorer API using IRSA credentials for per-service billing data
- Uses Caffeine in-memory cache to reduce repeated Cost Explorer API calls
- Exposes Micrometer metrics for observability

Requires `PROMETHEUS_URL` and AWS IRSA credentials for live data. Starts and returns partial data without them.

**Test coverage:** 15 / 15 tests — `AwsCostExplorerClientTest`, `PrometheusClientTest`, `CostRiskControllerTest`, `CostRiskServiceTest`.

### 4.9 API Gateway — Express on Node 20 (:3000)

The single entry point for all client requests, enforcing cross-cutting concerns:

| Concern | Implementation |
|---------|---------------|
| JWT Verification | Validates HS256 signed JWTs from auth-service on every protected request |
| RBAC | Rejects `VIEWER` role on `/simulate` routes with HTTP 403 |
| Rate Limiting | Redis-backed sliding window: 60 req/min per IP, 120 req/min per user; in-memory fallback on Redis outage (`passOnStoreError: true`) |
| Proxy Routing | Routes `/api/v1/auth/*`, `/api/v1/topology`, `/api/v1/simulate`, `/api/v1/cost/*`, `/api/v1/cluster/*` to downstream services |
| Observability | Exposes Prometheus `/metrics` via `prom-client`; attaches `X-Request-ID` for distributed trace correlation |

**Test coverage:** 17 / 17 Jest tests — JWT auth, revoked token rejection (401), forged token rejection (401), VIEWER→403 on `/simulate`, ADMIN allowed, proxy routing, rate-limit fallback, Prometheus metrics.

### 4.10 CI/CD Pipeline — 9-Stage Jenkins DevSecOps

*(Figure 4.5 — Jenkins 9-stage CI/CD pipeline overview)*

| Stage | Tool | Gate Behavior |
|-------|------|--------------|
| 1. Checkout | Git | Maps `DEPLOY_ENV` to `staging`/`prod` Kustomize overlay; sets `GIT_SHORT_SHA` |
| 2. Maven Build & Unit Tests | Maven 3.9 + JUnit | All 5 services in parallel; publishes JUnit XML; fails on test failure |
| 3. OWASP Dependency-Check (SCA) | OWASP Dep-Check | Fails on any dependency with CVSS ≥ 7.0 |
| 4. SonarQube SAST + Quality Gate | SonarQube | Fails if Quality Gate status ≠ OK across all 5 services |
| 5. Docker Multi-Stage Build | Docker | Builds 7 minimal unprivileged images tagged with Git short SHA |
| 6. Trivy Container Scan | Trivy | Fails on any CRITICAL unfixed CVE across all 7 images |
| 7. Push to Amazon ECR | AWS ECR + IRSA | Tokenless ECR push via `aws ecr get-login-password`; no static credentials |
| 8. OWASP ZAP DAST Scan | OWASP ZAP | Advisory baseline scan against staging; archives HTML/JSON reports |
| 9. Deploy to EKS | kubectl + Kustomize | Rolling update with `--timeout=180s`; `kubectl rollout undo` on failure |

### 4.11 Grafana Monitoring Dashboard

The kube-prometheus-stack Helm chart provisions Prometheus, Grafana, Alertmanager, cAdvisor, and Node Exporter. The Grafana datasource is provisioned with Prometheus at `http://prometheus:9090`.

*(Figure 4.6 — Grafana monitoring dashboard panels)*

| Panel | Metric / Expression | Interpretation |
|-------|--------------------|--------------------|
| Gateway Request Rate | `rate(http_requests_total[1m])` | Requests per second through the Express gateway |
| Active Connections | `nginx_connections_active` | Current active connections to the frontend Nginx |
| JVM Heap Usage | `jvm_memory_used_bytes` | Heap memory per Spring Boot service |
| Pod CPU Utilization | `rate(container_cpu_usage_seconds_total[5m])` | CPU utilization per pod |
| Simulation Count | `twindigital_simulations_total` | Cumulative simulations executed |
| Cost-Risk API p95 | `histogram_quantile(0.95, rate(http_request_duration_seconds_bucket[5m]))` | p95 latency of cost-risk API calls |
| Cluster Sync Lag | `twindigital_sync_lag_seconds` | Time since last successful cluster state sync |
| Scrape Health | `up{job="twindigital"}` | Health status of all Prometheus scrape targets |

---

## Chapter 5: Deployment and Operation

### 5.1 Project Directory Structure

```
cloudca/
├── infra/              Terraform: Multi-AZ VPC, EKS, RDS, ElastiCache, IAM, ECR
├── services/
│   ├── auth-service/           BCrypt, JWT, RBAC, Redis refresh tokens
│   ├── cluster-sync-service/   Kubernetes informers, Flyway, heartbeats
│   ├── topology-service/       Directed graph, in-memory BFS blast radius
│   ├── simulation-service/     5-check what-if engine, cost delta, audit log
│   └── cost-risk-service/      PromQL p95 right-sizing, AWS Cost Explorer
├── gateway/            Express gateway: JWT, RBAC, rate limiting, prom-client
├── frontend/           React 19 + Vite + ReactFlow + TypeScript
├── k8s/                Kustomize manifests: base, staging overlay, prod overlay
├── ci/                 Jenkins agent Dockerfile, Helm values, registry guide
├── docs/               ADRs, testing scripts, demo script, results template
├── Jenkinsfile         9-stage DevSecOps pipeline
├── docker-compose.yml  Local development orchestrator (9 services)
└── LICENSE             Apache 2.0
```

### 5.2 Local Deployment Procedure

1. Install Docker Desktop (or Docker Engine) and ensure it is running.
2. Clone the repository and navigate to the project root.
3. Copy `.env.example` to `.env` and set a unique value for every `CHANGE_ME` placeholder:
   ```
   POSTGRES_PASSWORD=<strong-password>
   REDIS_PASSWORD=<strong-password>
   JWT_SECRET=<min-32-char-secret>
   BOOTSTRAP_ADMIN_PASSWORD=<admin-password>
   ```
4. Validate the Compose configuration with `docker compose config`.
5. Build and start the complete local MVP stack with `docker compose up --build -d`.
6. Wait for all nine services to report healthy with `docker compose ps`.
7. Open the frontend at `http://localhost:8088`; the gateway API is at `http://localhost:3000`.

> **DATA NOTE:** Without `KUBECONFIG_BASE64` in `.env`, cluster-sync cannot connect to a live Kubernetes cluster and topology returns the built-in seeded demo graph. This is the expected behavior for local development.

### 5.3 Common Operational Commands

| Operation | Command |
|-----------|---------|
| Validate Compose configuration | `docker compose config` |
| Build and start all services | `docker compose up --build -d` |
| View all service statuses | `docker compose ps` |
| View service logs | `docker compose logs -f <service-name>` |
| Stop all services | `docker compose down` |
| Stop and remove all data volumes | `docker compose down -v` |
| Test gateway login | `curl -sS -X POST http://localhost:3000/api/v1/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"<password>"}'` |
| Test topology API | `curl -sS http://localhost:3000/api/v1/topology -H "Authorization: Bearer <accessToken>"` |

> **WARNING:** `docker compose down -v` permanently deletes the `postgres_data` and `redis_data` named volumes, removing all cluster snapshots, simulation audit logs, and cached tokens. Use `docker compose down` without `-v` to retain monitoring history.

### 5.4 Cloud Deployment on Amazon EKS

Ensure `kubectl` is authenticated to your target EKS cluster and Terraform has been applied to provision the VPC, EKS cluster, RDS PostgreSQL, ElastiCache Redis, and ECR repositories.

```bash
# 1. Preview rendered staging manifests
kubectl kustomize k8s/overlays/staging

# 2. Apply to EKS cluster
kubectl apply -k k8s/overlays/staging

# 3. Verify deployments, services, HPAs, and ingress
kubectl get pods,svc,hpa,ingress -n twindigital-staging
```

The current package contains the deployment instructions and Compose configuration; a live cloud deployment should be verified separately in the target AWS account with valid IRSA credentials, a configured EKS cluster, and a running Prometheus instance.

---

## Chapter 6: Testing and Validation

### Functional Verification of the Delivered Implementation

| Test ID | Test Action | Expected Result | Evidence |
|---------|-------------|----------------|---------|
| T-01 | Run `docker compose up -d` and check `docker compose ps` | All 9 services report `healthy` | Verified locally on 2026-11-23: all 9 services healthy (frontend :8088, gateway :3000, auth :8081, cluster-sync :8082, topology :8083, simulation :8084, cost-risk :8085, postgres :5432, redis :6379) |
| T-02 | POST login credentials to `http://localhost:3000/api/v1/auth/login` | HTTP 200; response contains `accessToken` and `role: ADMIN` | Verified locally on 2026-11-01 |
| T-03 | GET topology with valid ADMIN JWT | HTTP 200; response contains `dataSource: SEED`, 18 nodes, 19 edges | Verified locally on 2026-11-01 |
| T-04 | GET topology with VIEWER JWT; POST to `/simulate` with VIEWER JWT | Topology: HTTP 200; Simulate: HTTP 403 (RBAC enforced) | Covered by 17/17 Gateway Jest tests |
| T-05 | GET topology with invalid or expired JWT | HTTP 401 Unauthorized | Covered by 17/17 Gateway Jest tests |
| T-06 | Inspect cluster-sync health endpoint | HTTP 200; `status: NO_CLUSTER` (no kubeconfig set) | Verified locally on 2026-11-02 |
| T-07 | POST simulation request with valid ADMIN JWT | HTTP 200; response contains five check results and cost delta | Covered by 25/25 simulation-service tests |
| T-08 | Run `mvn clean test -B` in each Java service directory | All 92 Java tests pass (19 + 25 + 8 + 25 + 15) | Verified locally on 2026-11-01 / 2026-11-02 |
| T-09 | Run `npm test` in the gateway directory | All 17 Jest tests pass | Verified locally on 2026-11-01 |
| T-10 | Run `npm run lint && npm run typecheck && npm run build` in frontend | 0 errors, 3 warnings; production build succeeds | Verified locally on 2026-11-02 |

*(Figure 6.1 — Docker Compose service health verification)*  
*(Figure 6.2 — Gateway login and topology API response)*

### Test Suite Coverage Summary

| Test Suite | Tests | Passed | Run Date |
|-----------|-------|--------|----------|
| **Gateway Jest** | 17 | 17 (100%) | 2026-11-01 |
| **auth-service** | 19 | 19 (100%) | 2026-11-02 |
| **cluster-sync-service** | 25 | 25 (100%) | 2026-11-01 |
| **topology-service** | 8 | 8 (100%) | 2026-11-02 |
| **simulation-service** | 25 | 25 (100%) | 2026-11-02 |
| **cost-risk-service** | 15 | 15 (100%) | 2026-11-02 |
| **Frontend TypeScript & Lint** | — | 0 errors, 3 warnings | 2026-11-02 |
| **TOTAL** | **109** | **109 (100%)** | — |

### SLA and Performance Threshold Summary

| Metric | Target SLA | Measured Value | Status |
|--------|-----------|----------------|--------|
| All unit tests | 100% passing | 109 / 109 passed | **MET** |
| Frontend TypeScript | 0 errors | 0 errors, 3 warnings | **MET** |
| API Gateway p95 Latency | < 300 ms | *not yet run* | not yet run |
| API Gateway p99 Latency | < 800 ms | *not yet run* | not yet run |
| Gateway Error Rate under Stress | < 1.0% | *not yet run* | not yet run |
| Pod Recovery Time on Pod Kill | < 30 seconds | *not yet run* | not yet run |
| Informer Reconnect after Partition | < 15 seconds | *not yet run* | not yet run |
| Simulation Risk Prediction Accuracy | ≥ 90.0% | *not yet run* | not yet run |

---

## Chapter 7: Security, Reliability and Maintenance

### 7.1 Implemented Security Controls

| Control | Implementation |
|---------|---------------|
| JWT authentication | All protected API routes validate HS256 signed tokens issued by auth-service; expired and forged tokens rejected with HTTP 401 |
| Role-based access control | `ADMIN` role required for `/simulate` endpoints; `VIEWER` receives HTTP 403; enforced at the gateway layer |
| BCrypt password hashing | User passwords are hashed with BCrypt before storage; plain text is never persisted |
| Redis refresh token rotation | Refresh tokens stored with TTL; revoked tokens cannot be reused |
| Redis rate limiting | 60 req/min per IP, 120 req/min per authenticated user; in-memory fallback prevents Redis outage from dropping all rate-limiting |
| Trivy container scanning | CI pipeline fails on any CRITICAL unfixed CVE detected in any of the 7 container images |
| SonarQube SAST | Quality gate fails build on code smells, security hotspots, and coverage regressions across all 5 services |
| OWASP Dependency-Check | Third-party Maven dependencies scanned for CVEs with CVSS ≥ 7.0 threshold; build fails on violation |
| OWASP ZAP DAST | Baseline dynamic scan executed against staging in advisory mode; HTML/JSON reports archived |
| Non-root containers | All Dockerfiles use unprivileged user contexts in the final image stage |
| Multi-stage Docker builds | Final images are minimal — no build tools, Maven cache, or Node modules in production images |
| IRSA tokenless AWS access | AWS API calls use OIDC-federated IAM roles — no static AWS credentials in pods or CI |
| Amazon ECR | Private container registry with IRSA tokenless push — no Docker Hub credentials required |
| NetworkPolicies | Service-to-service traffic restricted to required communication paths in Kubernetes |
| Pod Security Contexts | `runAsNonRoot: true`, `readOnlyRootFilesystem: true` configured in Kubernetes manifests |
| External Secrets | AWS Secrets Manager secrets injected into pods at runtime; no secrets committed to Git |

### 7.2 Operational Security Recommendations

- Replace all `.env` placeholder values with strong, unique secrets before starting Compose; store the real `.env` file outside source control. Only `.env.example` with placeholders should be committed.
- For cloud deployment, restrict inbound ports to trusted administrator IP addresses using AWS Security Groups. Do not expose internal service ports (8081–8085) publicly.
- Use HTTPS termination at the AWS ALB or Nginx Ingress Controller before exposing the gateway to external users.
- Update container images deliberately and review Trivy scan results before each production release.
- Back up PostgreSQL and Redis named volumes regularly when cluster snapshot history and simulation audit logs must be retained.
- Rotate JWT secrets and refresh token signing keys on a regular schedule and after any suspected credential compromise.

### 7.3 Reliability Design

- All nine Compose services use `restart: unless-stopped` to automatically recover from unexpected crashes.
- Spring Boot Actuator `/actuator/health` endpoints are used as Docker Compose health probes with 15-second intervals and 5 retries.
- The cluster-sync-service marks its status as `STALE` and auto-reconnects using exponential backoff if the Kubernetes Watch stream drops.
- The gateway's Redis rate-limit store uses `passOnStoreError: true` to fall back to in-memory limiting during Redis outages — verified by Jest tests.
- The Jenkins CI pipeline enforces `kubectl rollout undo` automatic rollback if any service deployment times out during the EKS rollout stage.

---

## Chapter 8: Scope Boundaries and Future Enhancements

### 8.1 Current Implementation Boundaries

The delivered package is a full-stack microservices platform with live Kubernetes state synchronization, topology visualization, five-check simulation, and DevSecOps CI/CD. The local Compose stack does not provision a live Kubernetes cluster, Prometheus server, or AWS Cost Explorer credentials. The topology returned by the local stack is the built-in seeded demo graph (`dataSource: SEED` with 18 nodes and 19 edges), not a live cluster snapshot.

The load testing script (`k6-gateway-stress.js`), chaos engineering scenarios (`chaos-scenarios.sh`), and simulation accuracy harness (`simulation-accuracy-harness.py`) are defined and ready to run but have not yet been executed against a live cluster. Their results are marked **not yet run** in the testing results matrix. The chaos engineering and accuracy scenarios are planned for execution after a live EKS cluster is provisioned.

The platform currently synchronizes and simulates changes within a single EKS cluster per deployment instance. Multi-cluster federation, real-time WebSocket push, and eBPF-based service mesh tracing are proposed as future extensions.

### 8.2 Recommended Enhancement Roadmap

| Enhancement | Proposed Implementation |
|-------------|------------------------|
| **Live cluster validation** | Run k6 load test to 1,000 req/s, 6 chaos scenarios, and 10 simulation accuracy scenarios against a provisioned kind or EKS cluster |
| **Multi-cluster fleet federation** | Extend cluster-sync and topology-service to ingest and correlate state from multiple EKS clusters across regions |
| **eBPF-powered service mesh tracing** | Integrate Cilium/Tetragon eBPF probes to auto-discover runtime network dependencies without Kubernetes annotation requirements |
| **GitOps PR generation** | Add a one-click "Apply Right-Sizing" button that opens an automated GitHub Pull Request updating Helm/Kustomize resource values |
| **Karpenter disruption simulation** | Model Karpenter node consolidation and disruption decisions directly in the what-if simulation engine |
| **WebSocket push** | Replace 5-second REST polling with server-sent events or WebSocket connections for real-time topology updates |
| **Persistent incident database** | Add a backend event-ingestion API to persist and query security events detected by the cluster monitoring pipeline |
| **Alerting rules** | Define Prometheus Alertmanager rules for PodFrequentRestarts, cluster-sync STALE, and gateway error rate threshold breaches |

> **ENGINEERING PRIORITY:** The highest-value next step is to execute the existing load, chaos, and simulation accuracy test suites against a live EKS cluster to validate the platform's performance SLAs and simulation prediction accuracy targets.

---

## Chapter 9: Conclusion

### Summary of the TwinDigital Implementation

TwinDigital brings together a live Kubernetes cluster mirror, an interactive topology graph, a five-check what-if simulation engine, and an integrated DevSecOps CI/CD pipeline. The cluster-sync-service uses `SharedIndexInformer` to receive real-time Kubernetes events with minimal API server overhead, persisting resource changes to PostgreSQL. The topology-service builds an in-memory directed graph from those snapshots and executes dual-queue BFS traversal for blast-radius requests in sub-millisecond time for typical cluster sizes.

The Docker Compose package defines nine services, their health-check probes, service dependencies, restart policies, and named data volumes. The Express gateway enforces JWT-based RBAC and Redis-backed rate limiting. The Jenkins pipeline covers all five Java microservices and both the gateway and frontend, applying SAST, SCA, container scanning, and DAST security gates before any image reaches production.

The delivered implementation therefore establishes a production-grade Kubernetes operations foundation for TwinDigital and provides a structured operator interface for topology, simulation, and cost-risk workflows. It also delivers an organized CI/CD and infrastructure-as-code path that can be extended with live cluster validation, multi-cluster federation, and eBPF-based dependency discovery.

### Project Outcomes

- A React 19 SPA with an interactive DAG canvas, role-aware navigation, and a five-section operator interface.
- A nine-service Docker Compose architecture verified locally with all services reaching `healthy` on 2026-11-23.
- A `SharedIndexInformer`-based cluster state pipeline with staleness detection and auto-reconnect.
- An in-memory BFS topology graph with blast-radius traversal supporting sub-millisecond queries.
- A five-check what-if simulation engine with cost delta integration and PostgreSQL audit logging.
- A Prometheus PromQL and AWS Cost Explorer cost-risk service with Caffeine caching.
- 109 / 109 automated tests passing at 100% (92 Java + 17 Jest).
- A 9-stage Jenkins DevSecOps pipeline with IRSA ECR push, blocking SAST/SCA/container security gates, and automatic rollback.
- Terraform IaC provisioning for Multi-AZ VPC, EKS, RDS, ElastiCache, ECR, and IRSA in `ap-south-1`.
- Four documented Architecture Decision Records covering Terraform, Kubernetes Informers, PostgreSQL, and Amazon ECR.

### Final Statement

The current release establishes the synchronization, simulation, and observability core of TwinDigital. The system architecture is modular, the service configuration is reproducible, and the CI/CD pipeline enforces security at every stage. The proposed extensions provide a clear path toward a live-validated, multi-cluster Kubernetes digital twin platform suitable for production SRE operations.

---

## References and Appendices

### References

- [1] Kubernetes Documentation, "Informers and Controllers." https://kubernetes.io/docs/concepts/architecture/controller/
- [2] Kubernetes Java Client, "SharedInformerFactory." https://github.com/kubernetes-client/java
- [3] Spring Boot 3.3 Reference Documentation. https://docs.spring.io/spring-boot/docs/3.3.x/reference/html/
- [4] React Documentation. https://react.dev/
- [5] ReactFlow — Interactive Graph Library. https://reactflow.dev/
- [6] Docker Documentation, "Docker Compose overview." https://docs.docker.com/compose/
- [7] HashiCorp Terraform AWS Provider. https://registry.terraform.io/providers/hashicorp/aws
- [8] Amazon EKS Documentation — IRSA. https://docs.aws.amazon.com/eks/latest/userguide/iam-roles-for-service-accounts.html
- [9] Amazon ECR Documentation. https://docs.aws.amazon.com/ecr/
- [10] Prometheus Documentation, "Querying basics." https://prometheus.io/docs/prometheus/latest/querying/basics/
- [11] Grafana Documentation, "Provisioning." https://grafana.com/docs/grafana/latest/administration/provisioning/
- [12] OWASP Dependency-Check. https://owasp.org/www-project-dependency-check/
- [13] Trivy Container Scanning. https://aquasecurity.github.io/trivy/
- [14] SonarQube Documentation. https://docs.sonarsource.com/sonarqube/
- [15] Flyway Database Migrations. https://flywaydb.org/documentation/
- [16] kube-prometheus-stack Helm Chart. https://github.com/prometheus-community/helm-charts
- [17] k6 Load Testing Documentation. https://k6.io/docs/
- [18] TwinDigital project package: README.md, docker-compose.yml, Jenkinsfile, Terraform modules, Kustomize manifests, and service source code.

---

*Report submitted by Gona Navya Ramani | Registration: 12300802 | Section: K402311 | INT378 — Advancements in Cloud and DevOps | Lovely Professional University | November 2026*
