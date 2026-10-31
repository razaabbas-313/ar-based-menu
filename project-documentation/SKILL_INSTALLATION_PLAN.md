# Design/frontend skill installation proposal — awaiting approval

Prepared 2026-10-31. No skill has been installed and no Atelier application code or design changed. Temporary upstream downloads were inspected as data, not executed. This is a reviewed-source plan, not a claim of runtime certification.

## Installed inventory and scope

No requested candidate was found in the user .agents/skills or .codex/skills trees, the workspace/nested-repository .agents/skills trees, matching plugin-cache skill paths, or enabled skills.config entries. Existing system tools include skill-installer, skill-creator, openai-docs and imagegen; plugin capabilities such as computer use/visualization are separate and need no duplicate installation. Inventory is scoped to the configured user/workspace locations, not every unrelated directory on the machine.

Current official Codex documentation describes user skills at `%USERPROFILE%/.agents/skills` and repository skills at `.agents/skills` from CWD to Git root. The bundled supported installer defaults to legacy `.codex/skills`; use its explicit --dest for the documented user location, not two copies. It supports --repo, --path, --ref, --name and --dest; --help was checked with bundled Python3.12.14. No installer mutation command was run.

All selected upstream skills are general-purpose, so recommend one user/global copy per skill. None is intrinsically Atelier-specific. For project-local additions, recommend a future separate Atelier guardrail skill only if requested: preserve React/Vite/Tailwind/custom router/model-viewer/API contracts and current design, use disposable test resources, and retain existing AR/performance/accessibility requirements. Do not duplicate upstream skills locally or create new guardrail content during this approval stage. Interface Design can later store approved project decisions in `.interface-design/system.md`; that is project memory, not another global skill.

## Recommended initial installation: nine skills

All will be explicit-invocation only by adding/merging `agents/openai.yaml` with `policy.allow_implicit_invocation: false` after copying. This avoids overlapping automatic art-direction triggers and does not alter upstream SKILL.md. No project/global Codex config change, hooks, application dependencies or redesign are included. User/project instructions remain authoritative over third-party style preferences.

|Exact manifest/invocation name|Verified original source path|Purpose|Code/compatibility assessment|
|---|---|---|---|
|`emil-design-eng` / `$emil-design-eng`|[emilkowalski/skills/skills/emil-design-eng](https://github.com/emilkowalski/skills/tree/a81dbca07f2217b7103e510c0f13f8d5b017afc9/skills/emil-design-eng)|General UI polish and interaction decisions|Instruction-only selected folder; portable SKILL.md; no bundled executable helper found|
|`animate` / `$animate`|[emilkowalski/skills/skills/animate](https://github.com/emilkowalski/skills/tree/a81dbca07f2217b7103e510c0f13f8d5b017afc9/skills/animate)|Implement requested web motion using the least costly tool|Instruction-only selected folder; portable SKILL.md; no bundled executable helper found|
|`review-animations` / `$review-animations`|[emilkowalski/skills/skills/review-animations](https://github.com/emilkowalski/skills/tree/a81dbca07f2217b7103e510c0f13f8d5b017afc9/skills/review-animations)|Focused motion review, not an automatic rewrite|Instruction-only selected folder; portable SKILL.md; no bundled executable helper found|
|`animation-performance` / `$animation-performance`|[emilkowalski/skills/skills/animation-performance](https://github.com/emilkowalski/skills/tree/a81dbca07f2217b7103e510c0f13f8d5b017afc9/skills/animation-performance)|Diagnose dropped frames; particularly relevant alongside 3D rendering|Instruction-only selected folder; portable SKILL.md; no bundled executable helper found|
|`apple-design` / `$apple-design`|[emilkowalski/skills/skills/apple-design](https://github.com/emilkowalski/skills/tree/a81dbca07f2217b7103e510c0f13f8d5b017afc9/skills/apple-design)|Apple-inspired web gesture/physicality guidance; not Apple-authored|Instruction-only selected folder; portable SKILL.md; no bundled executable helper found|
|`frontend-design` / `$frontend-design`|[anthropics/skills/skills/frontend-design](https://github.com/anthropics/skills/tree/ca45fc496a3436251f191354091d557da31c15b5/skills/frontend-design)|Intentional frontend visual direction, subject to existing brand constraints|Instruction-only selected folder; portable SKILL.md; no bundled executable helper found|
|`interface-design` / `$interface-design`|[Dammyjay93/interface-design/.claude/skills/interface-design](https://github.com/Dammyjay93/interface-design/tree/2f9be3206855bcb2d1d0af262c8bae25cba6658d/.claude/skills/interface-design)|Dashboard/admin design consistency; distinct from marketing design|Portable manifest/references despite .claude source path; Claude slash-command names are not native Codex commands|
|`web-design-guidelines` / `$web-design-guidelines`|[vercel-labs/agent-skills/skills/web-design-guidelines](https://github.com/vercel-labs/agent-skills/tree/1a2c79d4e6c4a2283dc2c614b6538cbe1ed5ca62/skills/web-design-guidelines)|Read-only standards/accessibility/interaction review|Instruction-only; WebFetch naming maps to available read-only web tools; fresh rules are fetched at invocation|
|`webapp-testing` / `$webapp-testing`|[anthropics/skills/skills/webapp-testing](https://github.com/anthropics/skills/tree/ca45fc496a3436251f191354091d557da31c15b5/skills/webapp-testing)|Local Playwright browser testing; Python dependency not yet installed|Portable manifest; Python shell-command/browser helper requires controlled execution and missing Playwright dependency|

## Every requested category and duplicate/obsolete handling

|Requested item|Resolved actual names / decision|
|---|---|
|Emil Design Engineering|emil-design-eng, selected|
|Emil animation skills|animate, review-animations, animation-performance, selected; other motion/native/library helpers excluded from initial scope|
|Frontend Design|anthropics/skills frontend-design, selected; no aggregator/fork copy|
|Taste Skill|Leonxlnx/taste-skill folder skills/taste-skill; manifest name remains design-taste-frontend. OPTIONAL/defer from initial nine due scripts, strong style rules and overlap|
|Apple Design|emilkowalski/skills apple-design, selected; not an official Apple skill or iOS-only replacement stack|
|Impeccable|pbakaus/impeccable .agents/skills/impeccable, manifest impeccable version4.8.0. OPTIONAL/defer native toolchain activation; do not install multiple provider copies or old frontend-design/standalone command bundles|
|Interface Design|Dammyjay93/interface-design .claude/skills/interface-design, selected; old claude-design-skill/design-principles paths are not installed|
|Vercel Web Interface Guidelines|Install web-design-guidelines from vercel-labs/agent-skills; rules source is separate vercel-labs/web-interface-guidelines/command.md, not a second skill|
|webapp-testing|anthropics/skills webapp-testing, selected as instructions; package/browser readiness separately gated|

Some concepts intentionally overlap: Emil core gives general philosophy, animate builds, review-animations reviews, performance diagnoses, apple-design specializes gestures; Interface Design covers operational UI while Frontend Design covers general visual direction. Use one lead skill plus a focused review/testing skill per task. Taste and Impeccable overlap most with the general direction/polish set, so initial installation excludes them rather than stacking every framework. Swift/Expo/React Native/Sonner/library-picking helpers are unnecessary for this React web codebase and are not selected.

## Script and instruction review

- **Emil selected folders:** five SKILL.md plus required Markdown standards references; no shell/Python/JS helper files in those folders. They contain implementation examples and opinionated motion guidance, not a runtime dependency install. Initial-response/table-format preferences do not override the user. Claude-only disable-model-invocation metadata on review-animations is not relied on for Codex policy; use openai.yaml explicit-only.
- **Frontend Design:** instruction-only manifest/license. Design choices can conflict with an established product (fonts/copy/layout), so explicitly preserve existing Atelier identity unless a scoped redesign is separately approved. No code execution bundled.
- **Interface Design:** instruction/reference files only. Can propose image references and write `.interface-design/system.md` after consent. Its slash commands are Claude plugin syntax; in Codex use `$interface-design` plus a natural-language operation. Current source reuses existing components/native controls first; stale mirrors with a blanket custom-control rule should not be used.
- **Vercel web-design-guidelines:** no helper script. Requests latest read-only official raw guideline Markdown for each review, so pinning the skill does not freeze its remote rules. Treat fetched content as review input; do not install Vercel deployment/token tools alongside it.
- **webapp-testing:** inspected SKILL.md, with_server.py and example inventory. with_server.py uses subprocess.Popen(shell=True) for caller-supplied server commands, polls localhost ports, runs the supplied test command and terminates/kills children. Commands therefore have the process user privileges; do not pass untrusted shell strings or point browser actions at production. Piped server logs are not drained and shutdown targets parent processes, so verbose servers/orphan child cleanup deserve caution, especially on Windows. Skill says run helpers as black boxes; that is not a reason to skip this requested pre-install source review. No helper was executed.
- **Taste design-taste-frontend:** manifest plus scripts/references inspected; Node22+ and installed Chrome/Edge required for browser path. draw.mjs writes project `.taste-skill` and user history; inspect/scenes launch a temporary-profile browser over CDP, serve local files and create/replace screenshots; assets.mjs downloads media from public sources. cdp.mjs recursively removes owned temporary profiles, can taskkill its spawned browser process tree on Windows, and enables unsafe SwiftShader. This is a larger execution surface, not plain design notes. Current house-style rules can conflict with Atelier. No API key requirement found for the bundled asset path, but external URLs still receive requests. No scripts executed; full data gallery/CSS asset audit is not claimed.
- **Impeccable:** inspected current Codex SKILL.md, text references and launchers. It requests a context command that uses a native engine; Windows .cmd exists. Launcher can download engine-vVERSION into user `.impeccable` cache, validates against shipped SHA256 manifest, and fails closed if checksum unavailable. Operator IMPECCABLE_BIN/PATH or home-binary paths have different trust/probe behavior. Subcommands can write PRODUCT.md, DESIGN.md, sidecars and config, create command pins, manage provider hooks, or use browser/subagent tools. Native binary and full large browser bundle/agent TOML were not audited or run. Windows launcher comments acknowledge limited actual Windows execution testing. Do not call context/doctor/hooks/pin/update during installation verification.

No credential-reading/exfiltration or destructive application-data instruction was identified in the inspected proposed initial-nine paths. This is a focused review, not a malware certificate or a guarantee about generated commands. Installing copied instructions does not authorize their scripts, network downloads, server startup, dependency installs, design changes or production access.

## Runtime readiness

Bundled Python3.12.14 and supported installer help verified. Python Playwright is **not present** in that interpreter. Chrome and Edge executables exist, but browser automation was not run; their presence does not establish Python Playwright browser-cache readiness. Existing Node24 meets Taste22+ requirement. No pip/npm package or browser-runtime installation is part of the initial skills-only proposal. A later separately approved local testing task can provision/verify Python Playwright and Chromium in the intended interpreter.

## Pinned source manifest

Original GitHub repositories checked directly: canonical owner/repository, fork=false, archived=false, default branch main at review time. Skill paths were obtained from complete recursive trees rather than marketplace labels. Entries below preserve full commit IDs and reviewed SKILL.md hashes to prevent unreviewed moving-main installation.

|Skill|Commit|SKILL.md SHA256|
|---|---|---|
|emil-design-eng|`a81dbca07f2217b7103e510c0f13f8d5b017afc9`|`ffbe68e6007fb42cb8149f089b400a1ca007d59ba23e8948e2be4476f3175939`|
|animate|`a81dbca07f2217b7103e510c0f13f8d5b017afc9`|`b8ede161270458e6ae2e45e8a1985373515d4c63de5568ddb0f9012d7634356c`|
|review-animations|`a81dbca07f2217b7103e510c0f13f8d5b017afc9`|`6ff590d29e436766135aa3d137be1fac2a867165c7257fc315b189361f1873af`|
|animation-performance|`a81dbca07f2217b7103e510c0f13f8d5b017afc9`|`ceb9dd3fe912615830f725a0120ccf8d012d15a6218001a5cd6c15d38e39a957`|
|apple-design|`a81dbca07f2217b7103e510c0f13f8d5b017afc9`|`77bb63b7043bb93aca2ff4ab040c249484eef35682bb6ff7163433c31d30adc7`|
|frontend-design|`ca45fc496a3436251f191354091d557da31c15b5`|`d91970639e9f5c37682ac7ab60094d35f1c7c1f38d731bd56396563aee10c1d3`|
|interface-design|`2f9be3206855bcb2d1d0af262c8bae25cba6658d`|`720b31a31276e899fbe3006a33cbfeaceb77a73ba4c27b80d29afd38c8b7b208`|
|web-design-guidelines|`1a2c79d4e6c4a2283dc2c614b6538cbe1ed5ca62`|`f4647ca866a3accf763777f83e7682954f0187cd6bea7eea0399796652414e8f`|
|webapp-testing|`ca45fc496a3436251f191354091d557da31c15b5`|`51b7349e77ec63b7744a6f63647e7566a0b4d2e301121cc10e8c2113af6556a2`|
|design-taste-frontend (optional)|`12af471f627f866411b4bd785b19646d3672784c`|`3435ed963b15a1b6447746ec8844ab2cd38d8d2e8ead3451937f42df01e408c4`|
|impeccable (optional)|`2f97a85a4a87a80066a693bd3546b092bd300772`|`fab04b31f946573b00a8559db1fc070f8ea5825254d491c2b13156e5661a86d7`|

## Supported installer commands — NOT RUN; await approval

Use the bundled installer, not a curl-piped shell installer or an npm CLI with lifecycle scripts. The helper downloads/copies selected folders and refuses existing destinations; it does not intentionally execute the downloaded skill helpers. Global destination writes require the normal filesystem approval. Recheck conflicts and source hashes immediately before installation.

```powershell
$skillPython = "C:/Users/IQRA COMPUTER/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe"
$skillInstaller = "C:/Users/IQRA COMPUTER/.codex/skills/.system/skill-installer/scripts/install-skill-from-github.py"
$globalSkills = "C:/Users/IQRA COMPUTER/.agents/skills"
& $skillPython $skillInstaller --repo emilkowalski/skills --ref a81dbca07f2217b7103e510c0f13f8d5b017afc9 --path skills/emil-design-eng skills/animate skills/review-animations skills/animation-performance skills/apple-design --dest $globalSkills
& $skillPython $skillInstaller --repo anthropics/skills --ref ca45fc496a3436251f191354091d557da31c15b5 --path skills/frontend-design skills/webapp-testing --dest $globalSkills
& $skillPython $skillInstaller --repo Dammyjay93/interface-design --ref 2f9be3206855bcb2d1d0af262c8bae25cba6658d --path .claude/skills/interface-design --dest $globalSkills
& $skillPython $skillInstaller --repo vercel-labs/agent-skills --ref 1a2c79d4e6c4a2283dc2c614b6538cbe1ed5ca62 --path skills/web-design-guidelines --dest $globalSkills
```

If the user additionally selects Taste, use --path skills/taste-skill --name design-taste-frontend at its pinned commit. Impeccable uses only .agents/skills/impeccable, not every provider path. Optional commands are deliberately not part of the default initial-nine execution list.

## Post-approval verification and invocation

1. Recheck destination inventory; never overwrite an existing skill silently. Install only approved paths with pinned refs through the supported helper.
2. Compare copied SKILL.md hashes with this manifest, confirm name/description and supporting Markdown/scripts exist, and validate relative references. Preserve upstream content/licenses.
3. Add/merge supported openai.yaml explicit-only invocation policy for each approved skill; preserve any existing display/dependency metadata. Do not enable project hooks, execute engines, create design memory or install application dependencies.
4. Verify Codex discovery on the next turn; restart only if necessary. If the outside-parent workspace does not discover future repository-local skills, open the nested Git root rather than creating duplicate global/local copies.
5. Run a dry read-only invocation only when requested, e.g. `$review-animations inspect ModelStage and report only; do not edit`, `$web-design-guidelines review frontend/src/components/guest; report only`, `$interface-design review existing dashboard consistency; preserve current design`. General calls: `$emil-design-eng`, `$animate`, `$animation-performance`, `$apple-design`, `$frontend-design`. `$webapp-testing` requires separately confirmed localhost services/test credentials; no production or automatic Spring prod startup. Optional `$impeccable audit` and `$design-taste-frontend` are not available until separately selected/installed.

The current request authorizes investigation/preparation only. Installation approval is still pending.

Sources: [official Codex skill discovery/invocation](https://learn.chatgpt.com/docs/build-skills), original upstream repository links in the table, and the locally inspected bundled skill-installer SKILL.md/helper. Extra metadata from another agent is not assumed to be enforced by Codex.
