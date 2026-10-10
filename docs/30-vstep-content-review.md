# VSTEP content expansion — local implementation checkpoint

Inspection date: 2026-10-10. **Not a released or production-accepted version.**

**Historical checkpoint:** the audio interruption and browser-pending items below are superseded by [31-hcmus-english-preparation.md](31-hcmus-english-preparation.md): 39 entries, HCMUS-specific preparation, 171 completed assets and final local checks. Preserve this earlier snapshot/source review rather than rewriting its evidence as if it were current.

## Implemented

- Catalog: 31 versioned practice entries, including the nine released warm-ups, individual tasks and two complete skill-format sets. Composite sets reuse the individual passages/clips; this is not 31 independent examination papers.
- 14 original worked responses: six Writing tasks and eight Speaking tasks covering social interaction, solution discussion and topic development. Plain-English model text plus bilingual review notes; no numeric Speaking/Writing grades or certified bands.
- API returns models only inside submitted, owned-attempt feedback. Draft feedback is null; the exercise catalog exposes neither models, objective keys/explanations nor transcripts. Additional HTTP tests cover every subjective task and another principal's rejected lookup.
- Reading set: four passages, 10 questions each, 60-minute guidance. Measured word counts 501 / 541 / 557 / 552, total 2,151 (within the published 1,900–2,500 range).
- Listening set: eight notices, three conversations with four questions each, three talks with five questions each: 35 questions, approximately 40-minute guidance. Replay/speed controls remain learning aids, unlike official once-only playback.
- Frontend section selector retains all answers, shows global question numbering and section progress, and displays worked responses in feedback. Browser acceptance for this version is **pending**.
- Family, working life, career exploration and part-time work are additional independently authored thematic variants, not alleged recalled questions from named exam sittings.
- Original nine-exercise constructor block is byte-identical to backend HEAD: SHA-256 `ad1105c894b8b9dc794ee79d4ce04689af3abb713a4e0075c1e13a214c49faef`.

## Source review and recency boundaries

All content above is independently authored. No third-party question collection, model answer, PDF or audio was incorporated into the production content. Public availability is not reuse permission.

| Source | Observed evidence | Classification / limitation |
| --- | --- | --- |
| [ULIS format](https://vstep.vnu.edu.vn/test-format/) | Reading 60 min / 40 questions / four passages; Listening about 40 min / 35 questions / three parts; Writing 60 min, letter/email ≥120 words (one-third), essay ≥250 (two-thirds); Speaking 12 min / three parts | Official format reference; does not validate our item difficulty, scoring or timing. |
| [ULIS sample](https://vstep.vnu.edu.vn/sample-test/) | Retrieval incomplete | Do not claim the complete sample bank was obtained. |
| [University of Foreign Language Studies, Da Nang](https://vstep.ufl.udn.vn/NLTA/Dinhdang/2025/De-thi-mau-Vstep-bac-3-52025.9.10067) | Search candidate for a sample with audio; direct retrieval failed | Date/content not fully independently verified here. No controls bypassed. |
| [SGU format/sample materials](https://ttnnth-ngoaingu.sgu.edu.vn/tai-lieu-dinh-dang-de-thi-vstep/) | Retrieved page displays `2025-10-09T09:50:09+07:00`, a sample PDF and Track 7 / 8 / 9 links | University-hosted resource; page publication date is not proof of a newly authored paper. Linked audio was not imported or quality-tested. |
| [Dong Thap illustrative PDF](https://vstep.dthu.edu.vn/mainpage/page/27.pdf) | Downloaded and extracted illustrative paper; site also lists October/September 2026 examination notices | A current examination notice does not date the sample itself. Public PDF is reference-only; no wholesale reuse. |
| [AJC October 2026 notice](https://ajc.hcma.vn/thong-bao-ve-viec-thi-va-cap-chung-chi-tieng-anh-bac-35-theo-khung-nang-luc-ngoai-ngu-6-bac-cua-viet-nam-ky-thi-thang-10-nam-2026-15318.htm) | Linked illustrative paper extracted; cites the 2015 format decision; PDF metadata created 2018 / modified 2025 | **Not a verified new 2026 paper.** Metadata alone is not authorship/publication evidence. |
| [ZIM Writing samples](https://zim.vn/de-thi-vstep-writing-mau) | Retrieved five paired Writing samples and worked responses; page title mentions 2026 | Commercial authored practice. Search suggested a September update, but the retrieved body did not substantiate the exact date; do not present that date as verified. No answer text copied. |
| [ACE Speaking library](https://aceofenglish.com/vsteptestlibrary/skills/speaking) | Retrieved public format/topic guidance and description of model responses | Commercial practice; retrieved body did not substantiate specific September sitting dates returned by search. Not examiner-certified. |
| [VSTEPUP topic radar](https://vstepup.vn/du-doan-de-thi) | Retrieved monthly table through 09/2026; that row reports 262 entries and family/education/work themes | Publisher-reported aggregation. Exam authenticity, completeness, counts and predictive claims not independently verified. Only generic themes informed new original variants. |
| [VSTEPUP full-test page](https://vstepup.vn/full-test) | Public page retrieved | External practice product, not an official bank. No authenticated attempt, grading or audio acceptance performed. |

This is a bounded source review, not an exhaustive Internet inventory or licence clearance. Search answers/snippets were checked against retrieved material rather than used as authority. A search result pointing to an apparently replaced/spam page was not followed.

## Verification completed

```bash
cd knowledge-gym
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  bash scripts/test-english-room.sh --local-postgres
# BUILD SUCCESSFUL; 277 core + 17 migration/persistence + 13 presentation tests;
# zero failures/errors/skips. Disposable PostgreSQL stopped.

cd ../knowledge-gym-frontend
npm run lint
npm run typecheck
npm test
npm audit --omit=dev
npm run build
# Lint/typecheck/build pass; 675 tests / 37 files pass;
# production dependency audit: found 0 vulnerabilities.
```

Warnings: existing Gradle wrapper line 49 prints `: command not found` despite successful build; Java deprecation/unchecked notices and Node experimental-localStorage warnings remain. No unrelated wrapper/dependency refactor performed.

**Coverage boundary:** frontend tests are the existing vocabulary/session/assets suite. They do not yet verify all 31 catalog entries, the new reference-response component or all new recording paths. The old browser verifier still uses a nine-item fixture and stale vocabulary expectations. Passing build/unit checks is not browser, physical-device, human audio/pedagogy or authenticated production evidence.

## Audio blocker and preserved partial output

The generator now reads an ignored compiled catalog fixture exported by a test (`kg-presentation/build/reports/english/catalog-fixture.json`) rather than extracting three Java string literals. It validates paths, shared-path transcript consistency and pinned model/tool settings. A cache reuses assets only when input text/voices, bytes, model hashes, engine version, speed and normalisation match.

```bash
cd knowledge-gym-frontend
KG_KOKORO_MODELS=/tmp/kg-kokoro-model \
KG_KOKORO_PYTHON=/tmp/kg-kokoro-env/bin/python \
  node scripts/generate-english-audio.mjs
# Tool deadline: Command timed out after 600 seconds.
```

Five new clips completed before the timeout:

- `notices-campus-v1.mp3` — 146.249 sec
- `dialogue-study-space-v1.mp3` — 115.761 sec
- `dialogue-volunteer-shifts-v1.mp3` — 142.799 sec
- `talk-urban-shade-v1.mp3` — 170.739 sec
- `talk-retrieval-practice-v1.mp3` — 172.659 sec

No generation process remained after the deadline. The combined `listening-complete-practice-v1.mp3` is missing. The manifest remains the previous **163**-asset manifest and does not attest these five clips. Expected final distinct asset count is **169** (nine Listening recordings + 160 vocabulary clips). Existing 163-asset frontend tests therefore do not prove the new corpus is complete. Local changes and partial output are preserved, uncommitted and undeployed.

Logs: `/tmp/kg-vstep-bank-be-tests.log`, `/tmp/kg-vstep-bank-audio.log`, `/tmp/kg-vstep-bank-fe-tests.log`, `/tmp/kg-vstep-bank-fe-build.log`.

## Next dependency barriers

1. Finish the combined recording with a bounded authoring run (or deliberately concatenate the completed versioned clips without altering their speech); regenerate the full manifest and verify hashes, source-text binding, decoding and durations.
2. Update asset tests to the final corpus; add frontend reference-response and grouped-section checks using the compiled 31-item fixture, without leaking models/keys/transcripts through draft/catalog fixtures.
3. Update and run the desktop/mobile/landscape browser verifier for current eight-card topics, persisted account-scoped queues and five vocabulary modes; verify submit-only models and grouped-answer retention.
4. Reconcile stale frontend AUDIO/ROOM/provenance/browser documentation. Run the reusable ForumFlash research script; do not substitute previous temporary-script evidence.
5. Human pronunciation/pedagogical review, physical mobile audio/mic checks and real authenticated backend–frontend acceptance remain separate release gates.
6. Only then consider a requested commit/push/deploy. No production changes in this checkpoint.
