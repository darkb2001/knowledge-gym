# Product: Knowledge Gym

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Confirmed by the owner: both junior developers preparing for interviews and working developers revisiting/deepening IT knowledge. The interface must support choosing a topic first rather than imposing a scheduled session as the entry point.

## Product Purpose

Knowledge Gym supports learning and practising IT knowledge through a question library, flashcards, quizzes and mock interviews, with personal notes and progress feedback. Product success for this redesign: learners can choose what to study, understand available practice modes and read learning material comfortably.

## Operating Context

Existing application routes cover authentication, questions and answers, module-based flashcards and quizzes, mock interviews, notes, mindmap, progress dashboard, blog, writer administration and search administration. The owner confirmed that admin surfaces are included in the redesign.

## Capabilities and Constraints

- Current frontend stack after authorized security remediation: Next.js 15.5.24, React 19.3.0, TypeScript, Tailwind CSS 3, Vitest 4.1.11 and ESLint.
- Preserve API contracts, authentication/session restoration, search/filter/pagination behaviour, HTML sanitization, SRS scheduling and practice submissions.
- Preserve existing deep links and distinguish actual implemented capabilities from roadmap items in repository documentation.
- Bilingual Vietnamese/English UI is required by the owner. Translating learning content is not automatically included in UI localization; availability of English content remains an open decision.
- Do not invent completion statistics, learner outcomes, customer endorsements or capabilities.
- The repository contains pre-existing uncommitted changes; retain them and do not overwrite unrelated backend work.

## Brand Commitments

Keep the Knowledge Gym product name. The owner requested a completely new visual identity, friendliness, creativity and appeal without a generic AI-generated aesthetic. A bright beige palette is preferred; a very dark interface is explicitly unwanted. Other color choices are delegated. These constraints apply to learner and admin interfaces.

## Evidence on Hand

Product documentation: `../docs/00-overview.md` and `../docs/01-ux-modes.md`. Implementation: `app/`, `components/`, `lib/`. Code-based redesign audit and static detector baseline: `docs/design/REDESIGN-AUDIT.md` and `docs/design/impeccable-baseline.json`.

No marketing evidence or customer claims have been supplied for this redesign. Route existence alone is not proof that every roadmap capability is live.

## Product Principles

1. Let learners choose a topic before choosing a practice mode.
2. Serve beginners and experienced developers without making either navigate irrelevant complexity.
3. Prioritize readable learning material and useful task feedback over decorative analytics.
4. Treat administration as a distinct work area within the same product identity.
5. Keep bilingual UI coherent without pretending that untranslated source content has been translated.

## Open Decisions

The owner subsequently delegated the remaining design decisions and authorized direct implementation when Open Design was unavailable. Study Commons is implemented as a light beige application with topic-first entry. UI preference persists in localStorage under `kg.locale` (VI/EN), with document-language and cross-tab synchronization; source learning content stays in its original language. Automated UI fixture checks and desktop/mobile screenshots are recorded in `docs/design/browser-verification.json`. Live backend/OAuth/worker integration and the availability of translated learning content remain unverified. The owner subsequently requested dependency remediation: full and production-only npm audits now report zero vulnerabilities, with clean install, compiler/build/tests and freshly recaptured UI checks. See docs/design/SECURITY-REMEDIATION.md; this does not imply whole-application security certification.
