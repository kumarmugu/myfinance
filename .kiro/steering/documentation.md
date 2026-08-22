---
inclusion: auto
---

# Documentation Standards

## At End of Development (Before Session Ends)

Before completing work, ensure these are up to date:

### 1. User Guide (`frontend/src/pages/UserGuide.tsx`)
- Written for **non-technical users**
- Use simple, clear language — no jargon
- Explain **what each feature does** and **how to use it** step by step
- Include tips and common workflows
- Group by navigation section for easy discovery
- Mention any gotchas (e.g. "delete won't work if data references exist")

### 2. Design Document (`docs/DESIGN.md`)
- Written for **developers**
- Full database schema with column types and constraints
- API specifications with request/response examples
- Entity relationships and data flow
- Business logic documentation
- Include any new tables, endpoints, or enums added

### 3. Architecture Document (`docs/ARCHITECTURE.md`)
- High-level system overview
- Tech stack with versions
- Module breakdown
- Data model diagrams (text-based)
- Keep in sync with current state

### 4. OpenAPI Specification
- Auto-generated via springdoc-openapi at runtime
- Embedded in the app at: Documentation → API Docs (Swagger) tab
- Raw JSON spec at: `http://localhost:8080/v3/api-docs`
- Ensure all new controllers have proper `@Tag` annotations for grouping

## Documentation Quality

- User Guide: Think of someone who has never seen the app before
- Design Doc: Think of a new developer joining the project
- Both should be complete enough to understand the system without reading code
