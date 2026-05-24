# FASAL — Design Document Index

A complete set of design artefacts for the FASAL agricultural-logistics platform. All diagrams use **Mermaid** syntax — they render natively on GitHub, VS Code (with the Markdown + Mermaid extension), Obsidian, and most modern markdown viewers.

## Documents

| # | File | Audience | Purpose |
|---|---|---|---|
| 01 | [SRS_DOCUMENT.md](01_SRS_DOCUMENT.md) | Product / Stakeholders | Software Requirements Specification — what the system must do |
| 02 | [USE_CASE_DIAGRAM.md](02_USE_CASE_DIAGRAM.md) | Product / Analysts | Actors, use cases, and their relationships |
| 03 | [ARCHITECTURE_DOCUMENT.md](03_ARCHITECTURE_DOCUMENT.md) | Architects / Senior Dev | High-level architecture, layers, components, tech choices |
| 04 | [ER_DIAGRAM.md](04_ER_DIAGRAM.md) | DB / Backend | Entity-relationship diagram + table reference |
| 05 | [CLASS_DESIGN.md](05_CLASS_DESIGN.md) | Backend developers | Class diagrams for models, services, routes, algorithm |
| 06 | [USER_FLOW.md](06_USER_FLOW.md) | UX / Frontend | Step-by-step user journeys per persona |
| 07 | [DATA_FLOW_DIAGRAM.md](07_DATA_FLOW_DIAGRAM.md) | Architects / Backend | DFD Levels 0, 1, and 2 |
| 08 | [SEQUENCE_DIAGRAMS.md](08_SEQUENCE_DIAGRAMS.md) | Full-stack developers | End-to-end call sequences for key operations |
| 09 | [STATE_DIAGRAMS.md](09_STATE_DIAGRAMS.md) | Backend developers | State machines for Vehicle, Route, Produce Listing |
| 10 | [DEPLOYMENT_DIAGRAM.md](10_DEPLOYMENT_DIAGRAM.md) | DevOps / Setup | Local-dev and notional production deployment |

## Companion Files Outside `docs/`

| File | Purpose |
|---|---|
| `../README.md` | Original setup & quick-start guide |
| `../PROJECT_CONTEXT.md` | Single comprehensive context dump (LLM-friendly) |
| `../GITHUB_ISSUES.md` | Ready-to-file GitHub issues for known bugs |

## How to Read These Documents

* **For a quick understanding** of the system → read **01 SRS**, **03 Architecture**, then **06 User Flow**.
* **For backend implementation** → read **04 ER Diagram**, **05 Class Design**, **08 Sequence Diagrams**, **09 State Diagrams**.
* **For frontend work** → read **06 User Flow**, **08 Sequence Diagrams**.
* **For DevOps / deployment** → read **03 Architecture** and **10 Deployment**.

## Notation Used Across Documents

| Symbol / Style | Meaning |
|---|---|
| Mermaid `erDiagram` | Entity-Relationship Diagram |
| Mermaid `classDiagram` | Class Diagram |
| Mermaid `sequenceDiagram` | Sequence Diagram |
| Mermaid `stateDiagram-v2` | State Diagram |
| Mermaid `flowchart TD/LR` | Flowchart / DFD / User Flow |
| `<<Service>>`, `<<Route>>`, etc. | Stereotype labels indicating component role |
| 🟢 / 🟡 / 🔴 | Status / freshness colour bands (Q ≥ 0.7, 0.4–0.69, < 0.4) |
