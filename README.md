<div align="center">

<img src="https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk" />
<img src="https://img.shields.io/badge/Spring_Boot-3.4.3-6DB33F?style=flat-square&logo=springboot" />
<img src="https://img.shields.io/badge/LangChain4j-1.11.0-2496ED?style=flat-square" />
<img src="https://img.shields.io/badge/Qdrant-Vector_DB-DC143C?style=flat-square" />
<img src="https://img.shields.io/badge/License-MIT-green?style=flat-square" />

# Drawsee Backend

**Multimodal RAG backend with circuit structure recovery, token-budget-aware retrieval, and streaming task orchestration**

[Live Demo](http://drawsee.cn) · [Frontend Repo](https://github.com/devinlovekoala/drawsee-web) · [Report Bug](https://github.com/devinlovekoala/drawsee-backend-java/issues)

</div>

---

## Overview

Drawsee is a tree-structured AI conversation platform for electronics education. This repository is the backend service — it handles RAG ingestion, circuit structure recovery, LLM orchestration, streaming task queuing, and simulation execution.

The frontend ([drawsee-web](https://github.com/devinlovekoala/drawsee-web)) renders conversation as an interactive node graph. This service drives the content: every node in the frontend tree corresponds to a task type managed here.

---

## What Makes This Different From Standard RAG

Standard RAG pipelines treat all document content as text chunks and retrieve by embedding similarity. Drawsee adds two layers on top of that:

**Circuit structure recovery.** When the ingestion pipeline encounters a high-complexity schematic page, it calls a vision model and parses the output into a `CircuitDesign` object — a structured representation of components, connection topology, and SPICE netlist. This data enters the knowledge store as first-class entities rather than as prose descriptions. At query time, the evidence bundle passed to the LLM includes the actual component list and net topology, not a caption of them.

**Token-budget-aware retrieval.** `ContextBudgetManager` treats the LLM context window as a constrained resource. Rather than using a fixed `topK`, it explicitly partitions the window into dialogue history budget, retrieval context budget, and output reservation — then derives `topK`, `chunkMaxTokens`, and `maxChunksInContext` from the current state at each turn. As conversation grows, retrieval depth adjusts automatically.

---

## Key Capabilities

### Multimodal Document Ingestion
Apache Tika extracts text from PDF and Office documents. A semantic-aware chunker splits on weak breakpoints (line break > period > space) with overlapping windows (size=800, overlap=200). High-complexity pages trigger visual processing; circuit schematic pages produce `CircuitDesign` objects alongside text embeddings.

### Token-Budget-Aware Retrieval
`ContextBudgetManager` solves context allocation explicitly at each turn — not heuristically. Retrieval depth responds to conversation state rather than ignoring it.

### Latency-Constrained RAG Enhancement
`RagEnhancementService` wraps retrieval in an async timeout. If retrieval exceeds `ragTimeoutMs`, the system degrades gracefully to direct generation. First-token latency is preserved as a hard constraint; retrieval quality is a best-effort target.

### Heterogeneous Evidence Fusion
The generation prompt assembles six evidence sources simultaneously: retrieved text summaries · circuit component list · connection topology · SPICE netlist · compressed dialogue history · user question. The LLM reasons over structured circuit data directly.

### Streaming Task Framework
Worker layer uses a template method pattern (`WorkFlow` base class). Tasks execute via RabbitMQ and push tokens to the frontend through Redis Stream → SSE. Supported task types: knowledge Q&A, circuit analysis, animation generation, simulation.

---

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│              drawsee-web  (frontend)                          │
│              github.com/devinlovekoala/drawsee-web            │
└────────────────────────────┬─────────────────────────────────┘
                             │ HTTP / SSE
┌────────────────────────────▼─────────────────────────────────┐
│                   drawsee-backend-java                        │
│                                                               │
│   Controller → Service → Worker (MQ)                          │
│                  │                                            │
│       ┌──────────┼──────────────┐                            │
│       ▼          ▼              ▼                             │
│  RAG Pipeline  Circuit        LLM          Animation          │
│  Ingestion /   Structure    Qwen /         Workflow           │
│  Query         Recovery     DeepSeek                         │
└───┬───────────────────────────────────────────────────────────┘
    │
    ├── MySQL      metadata + chunks
    ├── Qdrant     vector index
    ├── Redis      cache + stream push
    ├── MinIO      file storage
    └── RabbitMQ   async task queue
```

### Ingestion Pipeline

```
PDF / Office document
        │
        ▼
DocumentParser (Apache Tika)
        │
        ▼
TextChunker                   overlapping sliding window
        │                     size=800, overlap=200
        │                     weak semantic breakpoint correction
        ▼
PdfMultimodalService          high-complexity pages → VLM
        │                     circuit pages → CircuitDesign + netlist
        ▼
EmbeddingService
        │
        ├── Qdrant            vector index
        └── MySQL             chunk metadata
```

### Query Pipeline

```
User question + dialogue history
        │
        ▼
ContextBudgetManager          derive topK / chunkMaxTokens dynamically
        │
        ▼
RagEnhancementService         async retrieval with timeout degradation
        │
        ├── Qdrant vector search
        └── result summarization (noise reduction)
        │
        ▼
Evidence bundle assembly
        ├── retrieved text summaries
        ├── circuit BOM + topology + netlist
        └── compressed dialogue history
        │
        ▼
LLM streaming → Redis Stream → SSE → drawsee-web
```

---

## Tech Stack

| Category | Component | Version |
|----------|-----------|---------|
| Core framework | Spring Boot | 3.4.3 |
| Runtime | Java | 21 |
| AI framework | LangChain4j | 1.11.0 |
| Auth | Sa-Token | 1.39.0 |
| ORM | MyBatis Spring Boot Starter | 3.0.4 |
| Cache / distributed | Redisson | 3.41.0 |
| Object storage | MinIO SDK | 8.6.0 |
| Document parsing | Apache Tika | 2.x |
| Message queue | RabbitMQ (Spring AMQP) | — |
| Vector database | Qdrant | — |
| Relational database | MySQL 8 | — |
| LLM providers | Qwen / DeepSeek / SiliconFlow | — |

---

## Project Structure

```
src/main/java/cn/yifan/drawsee/
├── controller/            HTTP entry points, organized by business module
├── service/
│   ├── business/
│   │   ├── parser/        document parsing, chunking, PDF multimodal processing
│   │   ├── rag/           RAG ingestion and query orchestration
│   │   └── circuit/       circuit structure recovery and analysis
│   └── ...
├── worker/                MQ consumers, WorkFlow template method task framework
├── consumer/              RabbitMQ message consumers
├── config/                Spring configuration (CORS, RabbitMQ, Redis, etc.)
├── pojo/                  entity / dto / vo
├── mapper/                MyBatis mappers
├── tool/                  LangChain4j Tool definitions
├── assistant/             AI Assistant interface definitions
├── constant/              enums and constants
├── util/                  utilities
├── schedule/              scheduled tasks
└── exception/             global exception handling
```

---

## Getting Started

### Prerequisites

- Java 21+, Maven 3.8+
- MySQL 8, Redis, RabbitMQ, MinIO, Qdrant

Start all infrastructure with Docker Compose:

```bash
docker compose -f docker/docker-compose.yml up -d
```

### Installation

```bash
# 1. Clone
git clone https://github.com/devinlovekoala/drawsee-backend-java.git
cd drawsee-backend-java

# 2. Configure
cp src/main/resources/application.yaml.example src/main/resources/application.yaml
# Fill in all ${ENV_VAR} placeholders — see Configuration section

# 3. Run (development)
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 4. Verify
curl http://localhost:6868/actuator/health
# → {"status":"UP"}
```

### Production Build

```bash
mvn clean package -DskipTests
java -jar target/drawsee-*.jar
```

---

## Configuration

All sensitive values are injected via environment variables — never hardcoded.

| Variable | Description |
|----------|-------------|
| `MYSQL_HOST` / `MYSQL_PASSWORD` | MySQL connection |
| `REDIS_HOST` | Redis address |
| `RABBITMQ_HOST` | RabbitMQ address |
| `MINIO_ENDPOINT` / `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | MinIO connection |
| `QDRANT_HOST` | Qdrant vector store address |
| `QWEN_API_KEY` | Qwen (Tongyi) API key |
| `DEEPSEEK_API_KEY` / `SILICONFLOW_API_KEY` | DeepSeek / SiliconFlow API keys |
| `EMBEDDING_API_KEY` | Embedding model API key |
| `SEARCH_API_KEY` | Baidu AppBuilder search API key |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP credentials |
| `INTERNAL_JWT_SECRET` | Internal service JWT secret (≥ 32 chars) |

Full variable list: [`src/main/resources/application.yaml.example`](src/main/resources/application.yaml.example)

---

## API Modules

| Module | Responsibility |
|--------|---------------|
| **AI Task** | Async AI task scheduling via RabbitMQ; WorkFlow template method drives Q&A, analysis, animation, simulation |
| **Knowledge RAG** | Knowledge base management, document ingestion, multimodal retrieval Q&A |
| **Circuit Analysis** | Circuit simulation, SPICE solving, structured result interpretation |
| **Animation** | Storyboard generation and rendering task orchestration |
| **File** | MinIO upload / download / management |
| **Auth** | Sa-Token login, permission management |
| **User / Email** | Registration, email verification |

---

## Design Decisions

**Why recover circuit structure instead of captioning?**
Caption-based multimodal RAG embeds component values and topology into prose. Those values are then retrievable only via fuzzy embedding similarity — asking "what is the value of R1?" requires the embedding of that question to land near the embedding of a sentence mentioning R1. Structure recovery makes components first-class entities with exact-match retrieval. This distinction directly motivated the [CircuitModalProcessor](https://github.com/devinlovekoala/RAG-Anything) research extension, which generalizes this approach to the RAG-Anything framework.

**Why dynamic token budgeting instead of fixed Top-K?**
Fixed `topK` is blind to how much context the current dialogue already consumes. As conversation history grows, the same `topK=5` may overflow the context window or leave it wastefully underused. `ContextBudgetManager` treats the window as a constrained resource and solves the allocation problem at each turn explicitly.

**Why async timeout degradation for RAG?**
In an educational context, a stalled retrieval that blocks generation for 10+ seconds destroys the experience. Degrading gracefully to direct generation — while logging the miss — keeps the system responsive and makes the latency/quality tradeoff visible and debuggable.

---

## Contributing

1. Fork and create a feature branch off `develop`
2. Run `mvn spotless:apply` before committing
3. Open a Pull Request to `develop` — CI must pass before merge

> **Security:** `src/main/resources/application.yaml` is git-ignored. Never commit files containing real credentials.

---

## Related

- **Frontend:** [drawsee-web](https://github.com/devinlovekoala/drawsee-web) — React + ReactFlow tree-structured conversation UI
- **Research extension:** [CircuitModalProcessor](https://github.com/devinlovekoala/RAG-Anything) — generalizing circuit structure recovery into the RAG-Anything framework (HKUDS)

## Acknowledgements

Built on [LangChain4j](https://github.com/langchain4j/langchain4j), [Qdrant](https://github.com/qdrant/qdrant), [Apache Tika](https://tika.apache.org/), and the Qwen / DeepSeek model families.
