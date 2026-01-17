# Core Agent Types

This document defines four core agent types used to map a single-word agent key to a specific system prompt, LLM configuration, and execution scope. Each agent has a narrowly defined responsibility to ensure predictable, auditable, and enterprise-safe behavior.

---

## 1. Analyst

**Purpose**  
Responsible for understanding intent, resolving ambiguity, and producing a concrete execution plan and technical design.

**Responsibilities**
- Ingest unstructured or semi-structured input such as requirements, bugs, or logs
- Query external systems via MCP tools to gather missing context
- Ask clarifying questions when information is incomplete
- Analyze impact and constraints
- Produce a step-by-step execution plan and design artifacts

**Typical Inputs**
- Requirement documents
- Bug reports
- Log files
- Webhook events from issue trackers or monitoring systems

**Outputs**
- Clarified requirements
- Assumptions and open questions
- Execution plan
- Technical design suitable for downstream agents

---

## 2. Engineer

**Purpose**  
Implements approved designs by producing production-ready code without reinterpreting scope or intent.

**Responsibilities**
- Write or modify code based on provided designs and requirements
- Add or update tests
- Follow project conventions, standards, and constraints
- Avoid scope expansion or design changes

**Typical Inputs**
- Approved execution plan
- Technical design from the Analyst agent

**Outputs**
- Code diffs or patches
- Tests aligned to requirements
- Build or execution instructions when required

---

## 3. Reviewer

**Purpose**  
Independently validates correctness, security, and alignment with requirements before merge or deployment.

**Responsibilities**
- Review code for correctness, maintainability, and security risks
- Validate implementation against original requirements and design
- Identify edge cases and regressions
- Surface risks and recommendations without implementing fixes

**Typical Inputs**
- Code produced by the Engineer agent
- Original requirements and design artifacts

**Outputs**
- Findings categorized by severity
- Concrete recommendations
- Explicit approval or rejection signal

---

## 4. Diagnostician

**Purpose**  
Performs root cause analysis (RCA) when other agents fail during MCP tool interactions or LLM execution. This agent investigates framework-level issues and produces actionable diagnostic reports.

**Responsibilities**
- Analyze failure context when any agent completes with `success: false`
- Investigate MCP tool configuration issues, connectivity problems, and schema mismatches
- Examine LLM response failures, timeouts, and malformed outputs
- Query diagnostic MCP tools to gather system state and logs
- Produce structured RCA reports with identified root causes and remediation steps

**Typical Inputs**
- Failure response from a preceding agent execution
- Error logs and stack traces
- MCP tool invocation history
- LLM request and response payloads

**Outputs**
- Root cause analysis report
- Identified failure category (tool misconfiguration, connectivity, schema mismatch, LLM error, etc.)
- Remediation recommendations
- Confidence level of diagnosis

**Invocation**
- Triggered optionally by the client when an agent returns `success: false`
- Requires explicit client configuration to enable diagnostic mode
- Not automatically invoked; client must choose to invoke with specific configuration

---

## Agent Responsibility Matrix

| Key          | Agent         | Thinks | Writes Code | Validates | Uses MCP Tools |
|--------------|---------------|--------|-------------|-----------|----------------|
| ANALYST      | Analyst       | Yes    | No          | Partial   | Yes            |
| ENGINEER     | Engineer      | No     | Yes         | No        | Yes            |
| REVIEWER     | Reviewer      | Yes    | No          | Yes       | Yes            |
| DIAGNOSTICIAN| Diagnostician | Yes    | No          | Yes       | Yes            |

---