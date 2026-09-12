# ADR-001: Local AI Worker product direction

## Context

Days 1–10 established Engineering Review Mentor and Week 2 conversation,
context, derived-memory and branch-topology boundaries. That use case is useful,
but cloud coding agents still spend substantial context on local preparatory
work: repository exploration, searches, architecture reconstruction, test
location, documentation comparison and evidence collection.

The project needs a direction that lets future challenge capabilities add value
without recasting completed Mentor work or pre-building infrastructure.

## Decision

The product direction is **Local AI Worker**: a local system for researching
user-controlled code, documents and other data, performing bounded work and
producing compact evidence-backed artifacts for users and strong cloud agents.
Engineering Review Mentor remains a specialized use case of the Worker.

The Worker, not a local model, is the product. It combines state, deterministic
tools, search/retrieval, model calls, evidence and artifacts. Local and cloud
provider choices must remain replaceable, while their current concrete
integrations may differ in cost and capability.

## Why

Local deterministic discovery can reduce expensive cloud context spent finding
facts before difficult reasoning begins. Evidence-backed output also makes the
selected context inspectable instead of asking a user or cloud model to trust a
local LLM conclusion.

This direction preserves the successful Mentor increment while creating a
natural place for later Task/Run state, MCP tool delegation, retrieval, local
models and pipelines.

## Consequences

- Future Days should add small layers: Task/Run state, a tool boundary,
  retrieval, a local provider or orchestration only when their real requirement
  appears.
- Repository/document research should prefer deterministic local evidence before
  LLM reasoning.
- Future artifacts may expose human-readable conclusions and machine-readable
  evidence metadata.
- Existing conversation invariants remain stable: raw history is canonical,
  summary/facts are derived, branches are topology, ContextPolicy is per-call
  projection, and metrics are observations.

## What remains unchanged

Day 1–10 functionality, Engineering Review Mentor, current dialog/agent APIs,
Week 2 persistence and context semantics, existing task/run evidence, and the
current DeepSeek integration remain unchanged. This ADR does not create a new
runtime component or alter current product behavior.

## What is deliberately postponed

No exact local model, Ollama/llama.cpp/other runtime, vector database, MCP
transport, workflow engine or plugin architecture is selected. No MCP server,
RAG/vector DB, local-model runtime, generic agent framework or pipeline is
implemented by this decision. Those choices wait for the applicable challenge
requirement or a demonstrated product need.
