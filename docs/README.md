# Documentation

Written from what is built, as of the commit that added it. Where something is not built, not measured or not verified, the
document says so. The test protocol follows the revised handbook (Talent Hunt – KHOJ, October 2026) and the NeGD and SAI rulings.

| Document | For |
|---|---|
| [HANDBOOK_COMPLIANCE.md](HANDBOOK_COMPLIANCE.md) | Every handbook requirement and every ruling, with what was built and what was not. Start here. |
| [HLD.md](HLD.md) | High-level design: components, data flow, the two data zones, offline, security. |
| [LLD.md](LLD.md) | Low-level design: the state and method for each test, thresholds, data model, API. |
| [TECHNICAL.md](TECHNICAL.md) | Toolchain, dependencies and licences, how to run, build, deploy and test. |
| [USER_GUIDE.md](USER_GUIDE.md) | For the assessor: setting up and running each test, the report, and what to do when a test is rejected. |
| [MODEL_CARDS.md](MODEL_CARDS.md) | The pose model and the video-measurement methods: what they do, what they were and were not tested on. |
| [VALIDATION.md](VALIDATION.md) | The plan to measure accuracy on Indian footage with at least 50 subjects. Not yet done. |
| [DATA_PROTECTION.md](DATA_PROTECTION.md) | Data inventory, consent, retention, withdrawal, and the draft consent wording for review. |

Related: [`../backend/README.md`](../backend/README.md) (sync service, `openapi.json`, `schema.sql`),
[`../app/BUILD.md`](../app/BUILD.md) (Android build and reproduction), [`../ios/README.md`](../ios/README.md) (web app, offline, sync).
