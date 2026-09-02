# Requirements

The functional and non-functional requirements this crawler was designed against, plus the
assumptions and explicit non-goals that scope it. Each requirement links to the class(es) in
[`class-diagram.md`](class-diagram.md) that satisfy it and, where applicable, the endpoint in
[`README.md`](README.md#the-three-apis) that exercises it.

## Contents

- [Functional requirements](#functional-requirements)
- [Non-functional requirements](#non-functional-requirements)
- [Assumptions](#assumptions)
- [Explicit non-goals](#explicit-non-goals)

## Functional requirements

| ID | Requirement | Satisfied by | Notes |
|---|---|---|---|
| FR-1 | Accept a crawl request carrying **multiple** seed URLs and acknowledge it durably. | `CrawlController.create` → `CrawlJobService.submit` ([`POST /crawl-jobs`](README.md#1-post-crawl-jobs--submit-urls-for-crawling)) | Returns `202` with a `jobId` once the job + seed pages are persisted — not once crawling finishes. |
| FR-2 | For each page, find **all image URLs** on that page. | `PageUrlStrategy` → `PageParser.parse` | `<img src>`, `<img data-src>`, and `srcset` candidates, resolved to absolute URLs. |
| FR-3 | Follow links to **child pages** and crawl those too, recursively, up to a configurable depth. | `CrawlTaskProcessor.scheduleChildren` (enqueues child URLs at `depth + 1` while `depth < maxDepth`) | Seeds are depth 0; `maxDepth` defaults to `crawler.max-depth` and is overridable per request. |
| FR-4 | Handle different URL kinds polymorphically — a normal page, a direct image URL, and an un-crawlable URL are each processed differently. | `UrlClassifier` → `UrlType`; `UrlStrategy` (`PageUrlStrategy` / `ImageUrlStrategy` / `UnsupportedUrlStrategy`) chosen by `UrlStrategyResolver` | Strategy pattern — a new URL kind is a new strategy + enum value, no change to the processor. |
| FR-5 | Report the **current processing status** of a job by id. | `CrawlController.status` → `JobStatusService.getStatus` ([`GET /crawl-jobs/{jobId}/status`](README.md#2-get-crawl-jobsjobidstatus--processing-status)) | Job state (`QUEUED`/`RUNNING`/`COMPLETED`/`FAILED`) plus discovered / processed / failed counts and a completion percentage. |
| FR-6 | Return the **result** for a job by id as a **nested structure**: every image URL, grouped under its parent page, with parent and child pages nested. | `CrawlController.result` → `CrawlResultAssembler.assemble` ([`GET /crawl-jobs/{jobId}/result`](README.md#3-get-crawl-jobsjobidresult--nested-image-tree)) | Tree rebuilt from `CrawlPage.parentPageId`; each node is `url + imageUrls + children`. |
| FR-7 | A job must reach a terminal state on its own once every discovered URL has been processed. | `JobProgressTracker` (atomic pending counter) → `JobCompletionService.finalizeIfComplete` | The counter is decremented on **every** exit path of `CrawlTaskProcessor.process`; when it hits 0, exactly one worker triggers completion. |
| FR-8 | Track how many URLs have been discovered vs. processed so progress is knowable at any time. | `JobProgressTracker.onPageDiscovered` / `onPageProcessed`; `JobStatusService` derives the public counts from `crawl_pages` | Two views: an in-memory atomic counter for completion detection, DB row counts for the status API. |
| FR-9 | The same URL discovered more than once (within a job) must be crawled only once. | `JobProgressTracker.markDiscovered` (per-job visited set) + unique `(job_id, normalized_url)` constraint | The URL still shows up in the tree under whichever page reached it first. |
| FR-10 | A page that fails to fetch must still appear in the result as a partial result, not vanish. | `PageUrlStrategy` returns a `FAILED` `CrawlOutcome`; `CrawlTaskProcessor` persists the page with `status=FAILED` and an `error` | The job as a whole still completes. |
| FR-11 | A crawl of entirely unreachable seeds must end as `FAILED`, not hang. | `JobCompletionService` terminal-state rule (0 fetched + some failed → `FAILED`) | `job.error` records the per-page failure reasons. |
| FR-12 | Discovered child URLs must be fed back into the scheduler with the depth incremented. | `CrawlTaskProcessor.scheduleChildren` (normalize → classify → dedup → persist child page → enqueue `CrawlTask` at `depth + 1`) | This is the recursive step of the crawl. |
| FR-13 | Adding a new URL category (e.g. sitemap, video page) must not require changes to the scheduler, processor, or completion logic. | New `UrlStrategy` implementation + one `UrlType` value | FR-4's extensibility requirement stated as a constraint. |
| FR-14 | A job must survive a process restart mid-crawl. | `JobReconciliationScheduler.reconcile` rebuilds the tracker + re-enqueues `PENDING` pages for any orphaned `RUNNING` job | The database is the source of truth; in-memory state is derivable from it. |

## Non-functional requirements

| ID | Requirement | How it's satisfied | Tradeoff / limit |
|---|---|---|---|
| NFR-1 | **Parallelism** — crawling must run concurrently, not as a sequential loop. | `CrawlTaskExecutor`: a fixed thread pool (size `crawler.worker-count`) consuming a `BlockingQueue` of `CrawlTask`s — the producer/consumer pattern. | Single JVM; the queue is in-memory (see non-goals). |
| NFR-2 | **Thread-safe shared state** — every worker touches the same job-progress and cache structures. | `ConcurrentHashMap` / `ConcurrentHashMap.newKeySet()` / `AtomicInteger` throughout `JobProgressTracker` and `PageContentCache`; job status transitions guarded so they fire once. | No locks on the hot path; completion is a lock-free `decrementAndGet() == 0`. |
| NFR-3 | **Breadth-first, depth-bounded traversal** — the crawl order and its bound must be explicit and predictable. | FIFO queue + a `depth` field on every task; children only enqueued while `depth < maxDepth`. | BFS chosen over DFS: the result is a breadth-oriented tree and a depth-bounded BFS has a predictable frontier. Exact level-ordering isn't enforced (workers run in parallel) and isn't needed for correctness — the visited set handles dedup. |
| NFR-4 | **URL de-duplication across the whole system** — visited set, DB, and cache must agree on "same URL". | `UrlNormalizer` produces one canonical form (lower-case scheme/host, drop default port, drop `#fragment`, trim trailing `/`, keep query) used as the key for all three. | Extension-based, not content-addressed — two URLs serving identical bytes at different paths are still two pages. |
| NFR-5 | **Scalability with job count** — the same URL requested by many jobs shouldn't be fetched many times. | `PageContentCache`: a process-wide `ConcurrentHashMap<normalizedUrl, ParsedPage>`; `computeIfAbsent` collapses concurrent fetches of one URL. | In-memory and unbounded (no eviction/TTL) — fine for a demo, a real deployment would bound it or use Redis. |
| NFR-6 | **Configurable max crawl depth.** | `CrawlJob.maxDepth` (request override or `crawler.max-depth`). | — |
| NFR-7 | **Bounded work per job** — a job can't crawl the whole internet. | `CrawlJob.maxPages` hard cap on unique pages discovered (`crawler.max-pages-per-job`). | Once hit, further child URLs on a page are silently not enqueued. |
| NFR-8 | **Per-URL fetch timeout.** | Connect + request timeout on the JDK `HttpClient` in `HttpPageFetcher` (`crawler.fetch.timeout-millis`). | A timeout is treated as a retryable failure. |
| NFR-9 | **Retry policy with exponential backoff** for transient fetch failures. | `RetryingPageFetcher` (Decorator): bounded attempts, `initialBackoff * multiplier^n` capped at `maxBackoff`, retryable failures only. | Backoff is a synchronous `Thread.sleep` on the worker — acceptable because a page fetch is short (unlike an async notification pipeline). |
| NFR-10 | **Rate limiting per domain** — one job with many links into a host must not hammer it. | `DomainRateLimiter`: minimum interval between requests to the same host, one lock per host (`crawler.rate-limit.per-domain-min-interval-millis`). | Politeness throttle, not a full quota system; it's the seam for a robots.txt `Crawl-delay`. |
| NFR-11 | **Guard against pathological responses.** | Response bodies past `crawler.fetch.max-body-bytes` are truncated before parsing. | — |
| NFR-12 | **Error isolation** — one bad page (or a bug processing it) must not take down a worker or stall the job. | `CrawlTaskProcessor.process` converts every failure into a `FAILED` page and always accounts for the page; `CrawlTaskExecutor`'s worker loop catches anything that still escapes. | Partial results, never a hung job. |
| NFR-13 | **Async status/result communication** — callers get progress and results without blocking on the crawl. | `POST` returns `202` immediately; `/status` returns a live completion percentage from row counts; `/result` returns the tree built so far. | Polling model — no push/webhook is wired up, though `JobLifecycleListener` (Observer) is the seam for one. |
| NFR-14 | **Maintainability / SRP** — scheduling, fetching, retry, parsing, classification, dedup, completion, and result assembly are each independently testable units. | One class per concern (see [Project layout](README.md#project-layout)); `CrawlTaskProcessor` coordinates but delegates every decision. | Verified by the unit suite testing each in isolation, plus one full-stack integration test. |
| NFR-15 | **Deployability with minimal infrastructure** — runs and is fully testable with one command, no external services. | H2 in-memory DB; in-process queue + worker pool instead of a broker; `./gradlew bootRun` / `./gradlew test`. | Explicitly single-instance — see non-goals. |
| NFR-16 | **Observability** — every fetch, retry, page outcome, and job transition is logged, and failure reasons are queryable. | SLF4J logging across the pipeline; `CrawlPage.error` / `CrawlJob.error`; the `/status` and `/result` endpoints. | No metrics/tracing backend. |

## Assumptions

These mirror the clarifying questions a real interview would open with:

- A request carries **multiple seed URLs**, each crawled as an independent root in the same job
  (FR-1, FR-6).
- "Child pages" means pages reachable by following `<a href>` links, bounded by `maxDepth`
  (FR-3). `maxDepth` default is 2; depth 0 is the seed itself.
- **Images are attributes of a page**, not crawlable nodes — they appear in that page's
  `imageUrls`. The exception is a URL that is *itself* an image (a seed pointing at a `.png`,
  or an `<a href>` to a full-size image): it becomes its own leaf node via `ImageUrlStrategy`
  (FR-4).
- A URL is classified by scheme + file extension **before** fetching; refining the type from
  the response `Content-Type` is out of scope (NFR-4).
- "Same URL" is decided by the normalized form (NFR-4); query strings are significant, fragments
  are not.
- A transient fetch problem (timeout, connection reset, HTTP 5xx, 429) is retryable; a 4xx, an
  unknown host, or a malformed URL is permanent (NFR-9).
- **At-least-once processing** of a queued task is acceptable — a task may be re-run (e.g. by
  the reconciliation sweep); the `(job_id, normalized_url)` unique constraint and the page's
  optimistic-lock `version` keep that idempotent. Exactly-once is not promised.
- Cross-page ordering of the crawl is not required; nothing depends on the order pages are
  processed in.
- Scale numbers were not given, so the design targets a correct, well-separated object model
  (an LLD) for a single instance rather than a specific throughput or a distributed topology.

## Explicit non-goals

Called out so they aren't mistaken for oversights:

- **`robots.txt` / `Crawl-delay` compliance.** Not implemented. `DomainRateLimiter` is the seam
  where it would plug in.
- **JavaScript-rendered pages.** Only the served HTML is parsed; no headless browser.
- **A dead-letter queue for permanently-failed URLs.** Failed pages are kept in `crawl_pages`
  with `status=FAILED` and an error marker instead — queryable via `/result`, but there is no
  separate replay endpoint.
- **Exactly-once task processing / a distributed transaction.** `deliveryId`-style idempotency
  is provided only by the DB unique constraint + optimistic lock within this one instance.
- **No message broker.** `CrawlTaskExecutor` is an in-process queue, not Kafka/SQS. The
  database is the source of truth, and `JobReconciliationScheduler` recovers from a restart by
  re-reading it — but a dropped in-flight task within a single run is only recovered on the
  reconciliation sweep, not instantly.
- **Not horizontally scaled.** The task queue, the URL cache, and the per-domain rate limiter
  are all per-instance. Running multiple instances would need a shared queue, a shared cache
  (e.g. Redis), and a shared rate-limit store — the `PageFetcher`, cache, and publish/consume
  boundaries are already the seams for that.
- **No cache eviction.** `PageContentCache` grows for the life of the process.
- **No authentication/authorization.** Every endpoint is unauthenticated — this is an LLD/demo
  surface.
- **No UI.** API-only.
- **No Docker/infra-as-code.** Runs directly via `./gradlew bootRun` against in-memory H2.
- **No metrics/tracing backend.** Logging only.
