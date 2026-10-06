# Java 8 / Spigot 1.8 synchronization

This is a behavior-aware compatibility backport, not a replacement with current main.
The legacy public packages, configuration defaults, user data formats and proxy payloads
remain in use. It does not claim feature parity with main.

## Immutable comparison inputs

| Repository | Clean 1.8 branch start | Main comparison |
| --- | --- | --- |
| AdvancedCore | `d4a8f667d91e7121a5e0929a0e16cd615556f00d` | `6390c1cab41bd4d7683c7df88dd36537c8c7861e` |
| VotingPlugin | `1b1e6d7d47a3d0af2ad5ede79ae3436c7aaddf73` | `834bcb84a59b6da40640eac83c20fd97ad1d64c9` |

The forks contain independently copied/adapted releases, rather than a continuous main
ancestry. The last represented releases are AdvancedCore 3.7.17
(`9e89ad0bfe4a0472f2fb01737546b024f4be87f0`) and VotingPlugin 6.18.7
(`caa70583638dcfebce67c0a0019daf1cd16402b5`). The retained
`VotingPlugin/src.main.java` tree is not an active Maven source root; it was not deleted.

## Dependency and platform decisions

Build with an actual Java 8 JDK. Test and production dependency base classes must be
compatible with Java 8. Artifact tests inspect the actual shaded jar and independently
load its Hikari pool and reflective scheduler classes; a compiler target alone is insufficient.
Java 8 ignores multi-release entries under `META-INF/versions/`.

The unavailable SimpleAPI `0.0.7-SNAPSHOT` is replaced by the fixed `0.0.7` release.
Its modern pool/Folia classes are excluded, and the existing Java 8 HikariCP 3.4.1 is
used. SLF4J API and binding are both 1.7.36. Minimized shading is disabled because it
removed reflectively selected scheduler implementations. A narrow source compatibility
bridge retains the old `GlobalMessageProxyHandler` ABI and existing ArrayList payload
behavior. SimpleAPI changed that ABI in `9cbde768823d09542f6909de86169a10d6360ea0`;
the bridge uses the old contract at `088b751bdc3ed50b74315aa64c4308a83288ca6a`.
It does not introduce the modern global-message wire format.

VotingPlugin shades the exact locally installed AdvancedCore jar, rather than adding its
raw transitive libraries again. This prevents Java 11/17 classes from reappearing downstream.

Bukkit and Bungee entry points are retained. Velocity sources are preserved but excluded
from the Java 8 artifact, including its descriptor and platform-specific adapters.
Although the cached Velocity API itself targeted Java 8, its 3.1.2 runtime targeted Java 11
at `ffa4c95435d1348d094d8a740ecae581c166f95b`. Current Velocity support is therefore
not claimed. The Java 8 Bungee acceptance runtime is archived Jenkins build 1485
(which reports git runtime build 1484); current build 2102 contains incompatible base classes.
This does not imply support for a current Bungee runtime on Java 8.

## Reproducible local build

Use a workspace-local Maven repository, separate from normal operator development caches:

```sh
export JAVA_HOME=/path/to/jdk8
export PATH="$JAVA_HOME/bin:$PATH"
mvn -B -f work/AdvancedCore-1.8/AdvancedCore/pom.xml \
  -Dmaven.resolver.transport=wagon -Dmaven.repo.local="$PWD/.m2/repository" clean install
mvn -B -f work/VotingPlugin-1.8/VotingPlugin/pom.xml \
  -Dmaven.resolver.transport=wagon -Dmaven.repo.local="$PWD/.m2/repository" clean verify
```

The coordinated dependency is `com.bencodez:advancedcore-1.8:3.7.17_1.8`, built from
this exact checkout and installed into that repository immediately before VotingPlugin.
No `LATEST`, deployment, release metadata change, or publication is required.
Final artifacts are `AdvancedCore/target/AdvancedCore.jar` and
`VotingPlugin/target/VotingPlugin.jar` respectively.

## Configuration, data and acceptance limits

No replacement of operator configuration, schema rewrite, or new proxy payload format
is introduced. Added behavior supplies defaults through existing getters and preserves
legacy signatures. The SQLite migration regression uses JDBC 3.7.2, bundled with Spigot
1.8.8, and checks repeat application with existing rows.

Unmodified baseline builds failed on dependency retrieval: the removed SimpleAPI snapshot
for AdvancedCore and a dead Velocity repository/missing AdvancedCore artifact for VotingPlugin.
These are recorded baseline failures, not passing baseline builds. The isolated candidate was
subsequently tested with Temurin 8u504 and an actual BuildTools-built Spigot 1.8.8 server.
NuVotifier 2.7.2 initialized; provider test votes exercised offline persistence, delivery on login,
online processing and rewards. Disabled sites were neither recreated nor counted. `/vote`
opened its inventory; reload and graceful shutdown completed; SQLite integrity was `ok`.

The archived Bungee runtime enabled VotingPlugin on Java 8 against an isolated loopback
MariaDB 11.8.6 database using its bundled legacy MySQL JDBC driver. NuVotifier ingress,
PLUGINMESSAGING delivery and backend rewards succeeded. One offline vote survived a
graceful proxy shutdown/restart, delivered once on login, and cleared from the JSON cache.
A backend configured with the documented shared MySQL database read the same totals;
the fixture recorded three votes and three points. This tests MariaDB interoperability,
not a separate Oracle MySQL installation or every proxy transport.
PlaceholderAPI 2.11.6 registered the VotingPlugin expansion and resolved persisted totals
and points; Vault 1.7.3 registered its permissions hook. An economy provider is absent, so
economy rewards remain unverified. A broad historical-data upgrade matrix remains unverified. These limitations must not be represented as passed runtime tests.

Modern storage/backend abstractions, current authenticated proxy transports, Control management,
Folia/Paper/Adventure features and new duration/milestone configuration systems have not been
copied wholesale. Their callers, data migrations and dependencies require separate review.
The full comparison ledger remains incomplete; these changes must not be described as matching main.

## VotingPlugin behavior backports

- Configured disabled or incomplete Vote Sites remain discoverable for identity matching,
  including legacy underscore normalization, but cannot become enabled or be recreated by
  vote receipt. The configured identity lookup itself never creates sites. Upstream: `b3505b65dda58ef86dccf6038e61819c64d50dfb` and
  `9f0f76fd9da563814bbb7c70585908aebeea4a45` (full identities are in the change ledger).
- Bungee non-voted player cache uses its actual `ProxiedPlayer` type and `getName()`;
  the existing UUID/name overload remains available.
- Java 8 packaging excludes Velocity-specific classes and metadata and retains Bukkit/Bungee.
  The stale source tree is preserved pending a separately justified migration.

No changes to external vote events, totals schemas, serialized offline rewards or existing
proxy message formats are made. Enabled sites remain filtered at the processing boundary.

Full administrator bulk-command access is configured explicitly on all legacy player-target
admin handlers; granular permissions still require their matching `.All` node. This adapts
the override integration from `6472e664dd4dd8b60d1659b9ed3bdef9d8305a82` to the old loader.

Latest coordinated validation: AdvancedCore Java 8 `clean install`: **54 unit tests + 3 artifact
integration test**, all pass. VotingPlugin Java 8 `clean verify`: **11 unit tests + 1 artifact
integration test**, all pass, using that exact isolated AdvancedCore installation.

Legacy SQLite statements and result sets now close on success, early returns and errors,
without closing the shared connection or changing legacy failure returns. SQLite player-name
lookup and update values are now bound parameters, so apostrophes remain data and SQL-looking
names cannot select another player (upstream `0f5a0477a2d38fb70732a76e46e15be6d725fe31`).
The regression tests execute against SQLite 3.7.2 and failed against the previous implementation.
This is a local old-layout adaptation, rather than adoption of the modern storage abstraction.
AdvancedCore is a library artifact with no `plugin.yml`; runtime acceptance occurs inside
VotingPlugin, which shades it, rather than independent Bukkit installation.

## Additional Java 8 runtime adaptations

SimpleAPI 0.0.7's Bungee JSON cache uses a newer static Gson parser API that is absent
from the Java 8 Bungee acceptance runtime's Gson 2.8.0. A narrow source bridge preserves
its public methods, nested cache layout and existing parsing behavior while using the
compatible parser instance methods. The bridge source is pinned to SimpleAPI
`1617356c3f026f741b5562bf62c8de59345637e1`; Gson 2.8.0 is a provided compile/test API,
not a new shaded runtime library. Nested existing-cache reload/save/reconstruction tests
run against that exact Gson API. Non-object/empty existing caches retain the old loud
initialization failure and remain byte-for-byte untouched; they are not replaced by empty
state. Malformed JSON recovery remains the inherited behavior and is not a guarantee of
recovering votes from damaged files. No cache-format migration is required.

VotingPlugin proxy shutdown now tolerates incomplete initialization while still closing
the initialized database and stopping the proxy. AdvancedCore time-checker shutdown is safe
before its timer has loaded and still stops an initialized timer. Three proxy tests cover missing proxy,
missing method/cache initialization, and database closure before the time checker exists.
This does not suppress startup failures or change normal vote routing.

Hardcoded admin/reward-editor icons use Spigot 1.8 materials. The dragon-head decorative
icon uses a skull item because 1.8 has no dragon-head material. Player-head items retain
legacy SKULL_ITEM data value 3 and the existing owner metadata. Bundled GUI defaults use
SIGN/WATCH rather than OAK_SIGN/CLOCK. Configuration keys and item purposes remain the
same; existing operator files are not replaced or automatically rewritten. Operators with
modern material names in old custom GUI files must select their 1.8 equivalents.

## MySQL bound-value backport

Upstream `f468b485e8ba17729f8288d3464df6aa0bff5097` is adapted to the fork's
existing MySQL-only table and Query APIs. Player-name/exact-row lookup, delete,
insert, and both synchronous/asynchronous update overloads bind values before
execution. Empty updates no longer execute an invalid SET statement. Table/column
identities and existing cache eviction/error-return behavior are unchanged.

Unlike newer database abstractions, this fork stores boolean values in TEXT columns
and reads them through Boolean.valueOf(String). Bound booleans therefore retain
`"true"`/`"false"` text rather than JDBC numeric boolean representation. This avoids
a data-format regression during the backport. Five focused write/dispatch tests
and two packaged read regressions cover apostrophes, SQL-looking names, boolean
format and empty updates. The read regressions execute JDBC queries through the
actual shaded Java8 dependency layout, with SQLite used for SELECT-compatible
fixtures; that is not a claim of exercising MySQL-specific write SQL on SQLite.

## Continued portable proxy backport

Upstream `e5668c862a1e4c73f1db3ec24382c70ff976791b` corrects name casing
before generating offline UUIDs. The retained Bungee implementation now uses a
connected player's canonical name; absent or disconnected players keep the
existing UTF-8 `OfflinePlayer:` algorithm. Online-mode UUID lookup and legacy
proxy payloads are unchanged. Its Velocity portion remains excluded with the
Java 8 artifact's unsupported Velocity runtime.

Three focused regressions cover connected casing, disconnected players, and the
absent-player UTF-8 fallback. The connected-casing assertion fails with the
unchanged legacy algorithm. The coordinated Java 8 `clean verify` then passes
14 unit tests and one packaged-artifact test, with no failures, errors, or skips
(`vp-offline-uuid-clean-verify.log` in the isolated workspace evidence).

The subsequent coordinated rebuild against AdvancedCore's cache scheduler fix
passes 14 unit tests and one packaged-artifact test; AdvancedCore passes 59 unit
and three packaged-artifact tests. The unchanged scheduler reproduced three
failures before its fix. No broad storage-failure durability claim follows from
that scheduler change.

Live Java8 acceptance on Spigot1.8.8 and archived Bungee1485, with NuVotifier2.7.2
and the isolated MariaDB fixture, delivered a lower-case `portproxy` vote while
`PortProxy` was connected. Proxy forwarding used the existing offline UUID. The
single matching user row remained single, and totals/points increased from4/4
to5/5; the backend and client observed the reward. Both plugins stopped cleanly,
and the temporary fixture-only OnlineMode change was restored. No class-version,
linkage, invalid-material, or SQL exception appeared in these logs. The client
placeholder query occurred before the vote and returned4/4; that is not evidence
of post-vote placeholder refresh. Proxy SQL has its own legacy table owner, so
this vote does not prove execution coverage of every AdvancedCore SQL branch.

The complete upstream ledger remains under review. These additional validated
fixes stay on the same two branches for one eventual full-scope PR per fork.

The subsequent MiscUtils compatibility backport also validates against the exact
local AdvancedCore build: AdvancedCore `clean install` passes63 unit+3artifact,
and VotingPlugin `clean verify` passes14 unit+1artifact, zero failures/errors/skips.
This adds Java8 command/date unit coverage; the preceding live casing acceptance
used the scheduler cohort artifact and is not claimed as a live run of MiscUtils.

AdvancedCore's subsequent checked SQL API foundation validates with69 unit and
7 packaged-artifact tests, zero failures/errors/skips; VotingPlugin `clean verify`
against that exact local artifact passes14 unit+1artifact with zero failures,
errors or skips (`vp-checked-write-autocommit-verify.log`). The new SQL methods
have real SQLite and isolated MariaDB acceptance. Legacy UserData/cache callers
remain under coordinated integration, so this is not a claim of cache write
failure recovery or changed vote-processing semantics.

## Checked common storage adapter dependency validation

AdvancedCore's additive checked FLAT/MySQL/SQLite batch adapter is now built with
Java8 and installed only into the isolated workspace Maven repository. This
consumer's exact-candidate `clean verify` passes14 unit+1 packaged-artifact test,
zero failures/errors/skips. The producer passes82 unit+8 packaged-artifact tests.
Both artifacts have maximum base class major52. No configuration keys, schema,
wire format, release version or production dependency changed in this cohort.

Existing cache callers still use the legacy write path pending coordinated
failure retention, flush ownership and retirement work. This consumer build is
not evidence of completed vote/point durability or whole-backport readiness.

## Checked cache batch dependency integration

AdvancedCore now routes queued cache batches through checked FLAT/MySQL/SQLite
writes, retains failed payloads for ordered retry, serializes claims and
same-instance retirement, and delivers callbacks outside the storage owner.
The exact Java8 coordinated builds pass91 unit+8 artifact tests in AdvancedCore
and14 unit+1 artifact test here, zero failures/errors/skips. The source/consumer
commands and logs are `ac-checked-cache-owner-final-install.log` and
`vp-checked-cache-owner-final-verify.log` in isolated evidence. No dependency,
release version, schema, configuration default or wire format changed.

Controlled Java8/Spigot1.8.8 SQLite fixture acceptance covers online vote/reward,
cached SetPoints/AddPoints, points10/total1, graceful stop and restart
persistence. Evidence JSON pins each tested artifact; it does not claim MySQL,
FLAT, proxy or failed-shutdown acceptance. Manager generation fencing,
population/snapshot reconciliation, direct non-queued writes and shutdown drain
remain under coordinated implementation. These builds and runtime samples are
not whole-backport or PR readiness; the upstream ledger and final independent
review are still incomplete.

## Cache snapshot dependency validation

The coordinated AdvancedCore snapshot backport preserves queued/claimed visible
values, rejects stale refresh publication after writes/replacement/eviction,
retains dynamic stored columns, and uses coherent owned integer/string lookups.
Existing mutable cache API and numeric-string parsing remain compatible.
Java8 producer `clean install`:101 unit+8 artifact tests, zero failures/errors/
skips. Exact consumer `clean verify`:14 unit+1 artifact test, all passing. Both
artifacts remain base class major<=52. Actual Spigot1.8.8/Java8 SQLite acceptance
against the exact consumer hash passes vote/reward, SetPoints/AddPoints to10,
total1, stop and restart persistence (`CacheSnapshot` runtime evidence).

Checked read error propagation, registry generations, direct setter admission,
shutdown drain and the full upstream ledger remain incomplete. This consumer
validation does not imply whole-backport readiness or approval to open a PR.

## Checked snapshot read dependency acceptance

AdvancedCore cache population now distinguishes missing users from failed storage
reads through additive checked SQL/file APIs and one checked snapshot query.
Legacy APIs, schemas, configuration keys and serialized representations remain
available. Java8 producer clean install:111 unit+12 artifact tests; exact consumer
clean verify:14 unit+1 artifact test; zero failures/errors/skips, base major<=52.
The exact artifact additionally passes Java8 Spigot1.8.8 SQLite and FLAT vote,
reward, point-command, stop and restart acceptance (points10,total1). FLAT fixture
configuration is restored byte-for-byte; no read/write, event-dispatch or linkage
errors observed. Evidence logs/hashes are in the isolated workspace.

The packaged MySQL reader has SQLite-backed JDBC and failure/resource tests;
live MySQL/proxy validation of this new reader and failed-shutdown behavior are
not claimed. Manager generation fencing, direct setter admission, shutdown drain,
remaining feature ledger and final independent review remain incomplete. No PR
has been opened and this is not whole-backport readiness.

## AdvancedCore registry fencing integration

The paired AdvancedCore candidate protects cache registry generations: slow
population cannot overwrite an active entry, removal detaches the captured
instance before post-commit callbacks, and bulk clear preserves replacements.
Existing cache APIs remain; global storage/shutdown admission remains incomplete.
Java8 clean verify passes14 unit+1 artifact tests with the exact producer build
(119 unit+12 artifact tests). Both jars retain base bytecode major<=52. Exact
consumer Spigot1.8.8 SQLite acceptance (CacheRegistry) passes vote/reward,
point commands to10,total1, graceful stop/restart persistence with no observed
cache snapshot/write, event or linkage errors. Full ledger, final acceptance
and independent review remain due; no PR readiness is claimed.

## Checked direct setter integration

Paired AdvancedCore preserves typed setter signatures and explicit backend
selection while making uncached/queue=false writes checked. Cached direct writes
share queued-batch ownership, flush older work first and publish/notify only
after acknowledgement; concurrent newer queued changes remain pending/visible.
Async rejection does not change cache state. Cached queue=true retains existing
optimistic notification behavior. FLAT direct writes use the checked atomic
file owner. No configuration/schema/wire change is required. Global uncached
identity arbitration, bulk mutation and shutdown/reload admission remain under
audit; this is not whole-backport readiness.

Actual Java8 producer clean install129unit+12artifact and exact paired consumer
clean verify14unit+1artifact all pass with zero failures/errors/skips. Base
bytecode remains major<=52. The controlled Spigot1.8.8 fixture-only test plugin
invokes the packaged relocated direct setter, confirms acknowledged cache state,
and checks database persistence rather than only standard queued commands.
SQLite DirectWriteSQL passes vote/reward, direct SetPoints7, AddPoints3=>10,
total1, graceful stop/restart. No final whole-scope independent review or PR
readiness is claimed.

The same consumer additionally passes FLAT DirectWriteFlat with the direct setter
and restart persistence. The temporary FLAT configuration is restored byte for
byte, the fixture-only plugin removed, and both owned server runs are stopped.
Evidence is under direct-write-build-results.json and DirectWriteSQL/Flat live
JSON/logs in the isolated workspace. Live MySQL/proxy and failed-shutdown behavior
remain unverified for this cohort.

## Proxy VoteParty startup and Redis diagnostics

Pinned upstream fd4c6a3954baca3c6ed91478120bf9a29d453ad4 restores backend
BungeeVotePartyCurrent/Required for every transport, instead of only plugin
messaging. The fork restores them before any transport initialization so an
interrupted startup cannot persist sentinel -2 values over saved counts, and
a listener cannot publish new counts before the restoration overwrites them.
Five deterministic failure-injection regressions (one per existing transport)
prove shutdown preserves17/50 after early initialization failure; all five
failed before the fix. No schema, payload or defaults change.

Pinned upstream758632b5a5900859860edeece0638f345a26879f's exact debug guard
is ported: received Redis payload diagnostics require BungeeDebug and use the
plugin debug logger; message routing remains unchanged. This does not claim
live Redis/socket/MQTT/MySQL transport acceptance.

The full ledger has also gained26 exact release-metadata/empty-tree dispositions
across both repositories. No dependency updates or runtime patches are included
in those metadata classifications. The newer VoteStreaks section fix7510981f...
is not classified as ported because its prerequisite feature is absent in this
fork and still requires its behavior/configuration/reward audit.

Java8 clean verify passes19 unit+1 artifact tests, zero failures/errors/skips,
against unchanged exact AdvancedCore7ff2e714... local build (129unit+12artifact
validation reused). Exact new consumer07da736c... has2432 base classes major<=52
and passes controlled standalone Java8Spigot1.8.8SQLite PartySmokeSQL online
vote/reward,points10,total1,graceful stop/restart. The new transport-startup
failure behavior is proven by deterministic tests; the standalone smoke is not
live Redis/MQTT/socket acceptance.

Five exact provided-Spigot-API-only upgrade commits and15 patches confined
to the excluded Velocity host package are now recorded as modern-only omissions
in the detailed workspace ledger. Velocity remains outside the Java8 runtime:
compiler and shade exclusions apply, its descriptor is absent, and the current
compatible-runtime contract does not advertise it. No shared proxy, Bukkit or
Bungee changes are included in those host-only omission classifications.

## Reward lookup compatibility integration

The paired AdvancedCore keeps its legacy RewardHandler/ArrayList APIs while
porting pinned reward alias normalization and final filename validation from
2599db95...,5d4d5bee... and1da8dd94.... Configured direct/sub rewards resolve
case, spaces, dots and underscores consistently; normalized duplicate handles
register once. VotingPlugin's existing loadDirectlyDefined clears/rebuilds the
list, so replacement configuration remains authoritative. Missing file and
generated-snapshot lookups reject absolute paths, separators and NUL before
construction; registered handles are resolved first. No schema/config/wire
change is required. The broader reward loader/quarantine/executor redesign
and storage/lifecycle ownership audit remain incomplete.

Java8 producer clean install136unit+12artifact and exactconsumer clean verify
19unit+1artifact all pass with zero failures/errors/skips. Both base bytecode
remain major<=52. Controlled Spigot1.8.8 fixture-only plugin verifies packaged
lowercase VoteSites reward aliases and rejects unsafe ordinary/generated file
lookups; production plugins do not depend on that acceptance plugin.

Exact consumerb795fac2... passes actual Java8/Spigot1.8.8SQLite RewardNamesSQL
vote/reward, packaged alias/file-guard checks,points10,total1,graceful stop
and restart persistence. No checkedwrite/event/linkage errors observed.
Fixture-only plugin removed, no escape probe created, owned server stopped.
Evidence reward-names-build-results.json and RewardNamesSQL runtime JSON/logs.
Live MySQL/proxy/quarantine/shutdown-failure acceptance remains unverified.

## Exact asynchronous injection API dependency validation

AdvancedCore's additive Java8 CompletionStage injection hooks preserve legacy
synchronous defaults and callbacks. VotingPlugin source and queue formats are
unchanged. Exact installed producer a16ec6e8e41f53637a134ef935afd419e58a5ebd886c896998dfdffc27713eee
builds143unit+12artifact; consumer39cc5ed87a281bf0b800ab3440ce15b33501600ba9e4cb51d9f933dfa799dbd9
builds19unit+1artifact, all zero failures/errors/skips, base major<=52.
This validates dependency/API packaging, not asynchronous reward dispatch or
durable queued replay; those integration phases and final acceptance remain
due. Generated snapshot quarantine remains disabled until replay completion
and persisted provenance can be preserved safely.

## Typed integer completion adapter

RewardInjectInt now adapts its ConfigurationSection callback to the typed
CompletionStage<String> hook without changing the existing synchronous
callback. Missing integer data remains skipped unless the legacy force flags
apply; configured/default integers and null-result placeholder fallback are
unchanged. The typed stage is awaited rather than treated as an immediate
result. Null stages and callback/storage failures settle exceptionally.
Java9 failedFuture calls from main are replaced with Java8 CompletableFuture
completion. Runtime async dispatch is still a separate integration step.

Five focused regressions pass. ActualJava8 clean install148unit+12artifact
and exactconsumer clean verify19unit+1artifact all pass with no failures,
errors or skips; base classes major<=52. Producer3b3947b8195f4e6a700fb5cc815ff7266849cc6a856462f60e1263f2bf891bdc;
consumer01d76c2fdcfcd58ca8ff8c46a6adc0f3a0c40534c210d4a0d035b83ecc349f60.
Evidence async-integer-build-results.json. No config/data/wire version changes.

Exact consumer01d76c2f... also passes controlled actualJava8/Spigot1.8.8
SQLite AsyncInjectSQL vote/reward, points10,total1, graceful shutdown and
restart persistence. Logs show no exception/linkage/write-failure markers;
owned fixture processes stopped. This proves legacy runtime compatibility
with the candidate API additions, not completion-aware queued replay.
Evidence checked-cache-runtime-results-AsyncInjectSQL.json and paired logs.

## Completion-aware injection dispatch on Bukkit1.8

Reward.giveInjectedRewardsAsync now runs a registration-order snapshot on
the Bukkit owner, waits for each opted-in physical stage, publishes its
placeholder on that owner, and runs post injections last. Async failure or
a null stage stops later effects. Ordinary legacy callback exceptions retain
their logged per-injection isolation; their hidden scheduled work is still
not observed. Existing synchronous reward entry points remain unchanged.

The additive continueOnServerThread API uses the same per-plugin dispatcher.
Bukkit1.8 has one owner thread, so modern region/player scheduler APIs are
unnecessary. Java9 orTimeout is replaced with the existing plugin timer and
a monotonic claim-time deadline check. Scheduler admission, actual execution
and physical settlement are distinct. Timeouts, rejected scheduling and
plugin disable fence queued callbacks before side effects. Claimed work is
not timed out/cancelled as unexecuted. No new executor or dependency is added.
Enable publishes a new dispatcher generation; disable closes old admission
before shutting the timer down. A pipeline captures its generation and cannot
continue through a replacement dispatcher. User callbacks settle outside
dispatch ownership locks. This does not yet drain native storage at shutdown.

Seven dispatch and six production-pipeline regressions pass. Actual Java8
producer clean install161unit+12artifact and exact consumer clean verify
19unit+1artifact pass, all zero failures/errors/skips; base major<=52.
Evidence reward-pipeline-build-results.json, ac-reward-pipeline-clean-install.log
and vp-reward-pipeline-clean-verify.log.

The initial live harness attempt used a17-character Minecraft username and
failed login before exercising the API; cleanup ran. The valid-name rerun
RewardPipeSQL invokes the actual relocated API via a fixture-only console
plugin. An actual Bukkit worker settles its stage; subsequent placeholder,
normal callback, post callback and final receipt assert the main thread.
The fixture snapshots its test hooks then immediately restores the registry.
This fixture plugin is not a production dependency and is removed afterward.

Root giveRewardAsync/event/requirements/deferral integration, native async
storage action receipts, persisted queue provenance/checkpoints/replay and
generated snapshot quarantine remain incomplete. PlayerRewardEvent is
explicitly asynchronous and must retain that contract in the root adapter.
No queue format, schema/configuration/wire or release version change occurs.
The full upstream923e741a90ad87d3ae1717301ded125f6749fc43 remains partial.

Exactconsumer09a7a449... passes the packaged async API assertion, real online
vote/reward, points10,total1, graceful shutdown and restart persistence.
No exception/linkage/checked-write failure markers appear in first/restart
logs. Evidence checked-cache-runtime-results-RewardPipeSQL.json. Fixture-only
acceptance jar removed and all owned fixture processes stopped. This is not
MySQL/FLAT/proxy/failed-shutdown or durable queued-replay acceptance.

## Awaited user delivery and caller-state isolation

The established giveRewardUser now routes applicable opted-in injections
through giveRewardUserAsync. Ordinary synchronous injections keep the legacy
inline path; an async feature requiring absent configuration does not move
unrelated rewards to that path. Player/UUID/display placeholders are prepared
on the captured Bukkit owner. The returned stage waits for injection and
post-reward settlement before success notification and repeat scheduling.
Failures or unavailable players do not schedule repeats. Existing config
ForceOffline handling, RepeatOnStartup and CheckRepeat behavior are retained.

Caller placeholders and all existing RewardOptions fields are copied before
admission, including CheckRepeat and the unset OnlineSet distinction. The
copy does not share mutable maps or insert a new Server placeholder. Existing
public option signatures and serialized/configuration/data/wire formats stay
unchanged. Null user-delivery options use defaults. Ordinary legacy callbacks
still have their logged exception-isolation contract; hidden scheduled work
is not made durable by this bridge.

Seven added user-delivery tests plus a complete legacy option-copy regression
pass. ActualJava8 producer clean install169unit+12artifact and exactconsumer
clean verify19unit+1artifact, all zero failures/errors/skips; base major<=52.
Evidence reward-user-build-results.json and paired clean build logs.

Root giveRewardAsync/event/requirements/deferral and checked queue persistence
still require integration. PlayerRewardEvent declares an asynchronous event;
it must remain off-primary while Bukkit state phases use the owner. Deferred
snapshot/file and queue writes need their own checked acknowledgements, not
completion inferred from existing void addOfflineRewards. Native async action
receipts, persisted provenance/checkpoints/replay, global storage lifecycle
and generated snapshot quarantine remain incomplete. Temp-cache player-name
lookup may read storage; the coordinated root preflight must resolve this
off-owner before live preparation. No complete durability/backport claim.

Exactconsumer8c7f497a... passes actualJava8/Spigot1.8.8 packaged
giveRewardUserAsync with dynamic player/UUID placeholders, worker settlement
and owner completion. RewardUserSQL vote/reward,points10,total1,stop/restart
pass; logs show no exception/linkage/checked-write failure markers. Evidence
checked-cache-runtime-results-RewardUserSQL.json. Fixture-only jar removed,
owned processes stopped. Not MySQL/FLAT/proxy/failed-shutdown/queued-replay
acceptance. The full upstream commit remains partial.

## Shared off-primary admission and identity preflight

ServerThreadRewardDispatch now supports both Bukkit-owner and off-primary
continuations with the same pending-admission set, timer, close generation,
monotonic deadline and physical completion receipt. Off-primary setup runs
inline when already away from the owner, or uses Bukkit's async scheduler
when called on the main thread. Admission rejection/timeout/disable prevents
late effects; claimed physical work is not misreported as unexecuted.
Reward.continueOffServerThread exposes that existing owner for event/storage
setup without introducing another executor or runtime owner.

Awaited user delivery resolves the player name off-owner because temporary
cache lookup can read storage. It snapshots registered injections on the
Bukkit owner before that boundary, then reuses the resolved name for live
preparation and success notification. A registry change while preflight
waits cannot replace this delivery's captured sequence. Identity failure
prevents injections and repeats. Legacy synchronous delivery remains inline.

Three new dispatcher tests and two identity-preflight regressions pass;
existing user/injection/admission coverage remains. ActualJava8 producer
clean install174unit+12artifact and exactconsumer clean verify19unit+1artifact
pass, all zero failures/errors/skips. Base major<=52; evidence
reward-preflight-build-results.json and paired clean Maven logs.

The strengthened fixture asserts the receipt remains pending after the first
callback before allowing actual worker settlement, then checks placeholder/
normal/post/final ownership. No assertion is relaxed to accept early
completion. Root asynchronous event/decision/defer, checked snapshot/file
publication and queue acknowledgement, native async action collection,
occurrences/checkpoints/provenance/replay and global storage lifecycle remain
incomplete. Full upstream923e741a90ad87d3ae1717301ded125f6749fc43 stays partial.
No configuration/schema/data/wire/release version change is introduced.

Exactconsumer78e83c3f... passes actualJava8/Spigot1.8.8 RewardPrepSQL
packaged user-delivery/preflight/pending-receipt/owner checks plus real
vote/reward,points10,total1,gracefulstop/restart. Logs have no exception,
linkage or checked-write failure markers. Fixture-only plugin removed and
owned processes stopped. Evidence checked-cache-runtime-results-RewardPrepSQL.json.
This is not liveMySQL/FLAT/proxy/failed-shutdown or durable replay acceptance.

## Checked reward-configuration publication prerequisite

RewardFileData.saveStrict and FilesManager.editFileStrict now publish through
FileThread.saveConfigurationStrict under the existing file owner. The new
checked path stages a YAML document beside its target and atomically replaces
it only after serialization succeeds. Existing modes/owner/group and canonical
symlink target location are preserved. Malformed or non-file predecessors fail
without replacement; failed staging/publication cleans the temporary file and
propagates the original exception. An unsupported atomic move remains a visible
failure. This is publication acknowledgement, not an fsync/power-loss guarantee.

The audit corrected a prior assumption: FilesManager.editFile calls
FileThread.getThread().run(Runnable), which executes synchronously under the
FileThread lock. It is not a queued asynchronous write; its defect for checked
completion is swallowed IOException, and it may start the deprecated polling
thread. The additive checked path reuses the same serialization owner without
calling getThread or starting that thread. Legacy void methods remain available.

Six regressions cover nested YAML/readback, failed serialization/atomic move,
malformed/non-file predecessors, symlink/POSIX identity preservation and actual
RewardFileData-to-FilesManager delegation with missing backing-document failure.
ActualJava8 producer clean install180unit+12artifact and exactconsumer clean
verify19unit+1artifact pass, all zero failures/errors/skips; base major<=52.
Evidence checked-configuration-build-results.json and paired clean Maven logs.

This API is a prerequisite for completion-aware snapshot/queue deferral.
The legacy setRewardFile path has not been replaced: its in-memory mutation
and public registry publication must be coordinated with checked file success
and queue persistence before being wired into durable replay. Do not mutate
a registered snapshot before a failed publication or acknowledge queue creation
from a void setter. Root async decisions/events/defer, checked queue append,
native action receipts, provenance/occurrences/checkpoints/replay, generated
snapshot quarantine, global storage lifecycle and the full ledger remain due.
No configuration/schema/data/wire/release version change.

Exactconsumerb8bb5a48... passes actualJava8/Spigot1.8.8 CheckedSnapSQL
checked RewardFileData publication/readback, pending user receipt and
owner/worker/owner callbacks, realvote/reward,points10,total1,stop/restart.
Logs contain no exception/linkage/checked-write failure markers. Temporary
snapshot deleted, fixture-only console jar removed, owned processes stopped.
Evidence checked-cache-runtime-results-CheckedSnapSQL.json. This validates
the checked API; generated queue snapshot transaction/replay is not yet wired.
Not liveMySQL/FLAT/proxy/failed-shutdown acceptance.

### Detached generated reward candidates (partial async reward backport)

`RewardFileData.prepareGeneratedSnapshot(ConfigurationSection)` now constructs
an independent Reward/YAML candidate without legacy `setData` per-key save and
reload calls. Existing target metadata, generated header and
`DirectlyDefinedReward` flag are preserved. Supplied parent sections replace
predecessor sections as in legacy `setData`; unrelated keys remain. A serialized
copy isolates the candidate from later source and predecessor edits. Preparation
neither writes nor registers it. The caller must separately acknowledge
`saveStrict()` before publishing the candidate in the reward registry.

Three regressions exercise independent candidate/source/predecessor state,
malformed predecessor publication failure, and successful checked publication
with section replacement/header/flag/readback. Actual Java 8 clean producer
install: 183 unit + 12 artifact tests. Exact shaded consumer clean verify:
19 unit + 1 artifact test. All zero failures/errors/skips; both jars' base
classes have major version <=52. Evidence: `snapshot-candidate-build-results.json`
and paired `*-snapshot-candidate-clean-*.log` in the isolated workspace.

This is a prerequisite, not the complete generated reward transaction. Legacy
root deferral remains unchanged; registry/file ordering under concurrent
publication, checked queue append, occurrence/provenance/checkpoint/replay,
native action completion and global storage shutdown remain to be integrated.
No claim of fsync/power-loss durability, full pinned-main parity or final PR
readiness. No configuration/schema/wire/version changes.

Live acceptance: exact consumer `84391f96c6a1a735ffac15e955c3d15ec926a3fa94483f057d8ea3783ca45849`
on real Java 8 / Spigot 1.8.8 with unique fixture identity `DetachedSnapSQL`
passed detached candidate/source/predecessor isolation, checked save/readback,
legacy parent-section replacement, pending async user delivery, vote/reward,
points 10 / total 1, graceful stop, SQLite integrity and restart persistence.
Evidence: `checked-cache-runtime-results-DetachedSnapSQL.json` and
`snapshot-candidate-live.log`. Fixture-only diagnostic plugin removed; owned
server/client processes exited. This does not prove full queued replay,
MySQL/FLAT/proxy acceptance or failed-shutdown behavior for the candidate API.

### Checked cached read-modify-write foundation

`UserDataCache.mutateDirect(key, transform, storageWrite)` performs the read,
side-effect-free transform and synchronous physical write under the existing
batch owner. It flushes older queued changes first, publishes the candidate only
after the supplied checked write succeeds, and preserves a later optimistic
queued replacement. Null cached values remain missing evidence; transforms must
explicitly handle them, not assume an empty durable queue. Null transformed
values are rejected by the new API, while the legacy public `writeDirect`
replacement retains its prior nullable-value behavior. Extension notifications
remain outside ownership; their failure does not mean the physical write failed.
Recursive storage-phase mutation/retirement is rejected. No new owner/executor.

Six regressions exercise two concurrent append transformations without a lost
update, queued-predecessor ordering and later replacement, write failure and
unknown value preservation, legacy null compatibility and checked null rejection,
recursive mutation/retirement fencing, and a post-commit notification failure
carrying the acknowledged value in `CommittedUserDataMutationException`. Legacy
`writeDirect` retains its original callback exception behavior. This is not yet
a complete persisted reward queue API: root deferral, capacity/codec/provenance/occurrences/checkpoints,
uncached canonical UUID ownership, cache-generation transitions and lifecycle
coordination still require integration. No schema/config/wire/version changes.

Actual Java 8 producer clean install passed 189 unit + 12 artifact tests;
exact consumer clean verify passed 19 unit + 1 artifact test, all zero failures,
errors and skips. Base classes 1816 / 2436 have maximum major 52. Evidence:
`queue-mutation-build-results.json` and paired clean Maven logs.

Final consumer SHA256 `cf7564df70f88ccadef1211dd28ab4130ed2b4277d21309da272be489aaf81f0`
passed real Java 8 / Spigot 1.8.8 acceptance with owned identity `QueueAckSQL`:
worker-thread cached mutation followed by checked SQLite readback and restoration
of the original fixture field; detached snapshot; pending async reward receipt;
real vote/reward; points10,total1; graceful stop; SQLite integrity; restart
persistence; no observed linkage/checked-write errors. Evidence:
`checked-cache-runtime-results-QueueAckSQL.json`, `queue-mutation-final-live.log`.
Fixture plugin removed and all owned server/client processes exited. This does
not claim persisted queue replay, MySQL/FLAT/proxy or failed-shutdown acceptance.

### Plugin-local storage ownership across cache generations

`UserStorageOwnership` supplies 64 fixed lock/revision slots per plugin, keyed
by UUID. Checked UserData reads/writes and cache batch ownership share a slot
across cache generations and uncached wrappers. Checked recursive writes and
reads from an in-flight write are rejected. The mutation-attempt revision
changes before attempting a write, including failed/uncertain outcomes: cleanup
can throw after a physical commit, so success-only fencing would be too weak.
Private candidates read outside registry locks; a stale candidate re-reads under
ownership before publication, and an already published live successor still wins.
Direct typed setters resolve the cache when accepted work executes, preserving
a cache published after admission. Retirement detaches the old generation before
its callbacks; change notifications remain outside ownership. Lock order is
storage owner -> cache monitor / native storage owner; no registry compute holds
I/O or extension notifications. No new executor or unbounded identity map.

Seven ownership regressions cover uncached wrapper serialization, private and
existing-cache snapshot fencing, callbacks awaiting another thread's population,
failed/reentrant writes, bounded ownership and an uncertain post-commit exception.
An additional direct setter test covers cache publication after async admission.
Existing cache-generation, callback, optimistic-overlay and direct-write tests
remain intact. Actual Java 8 clean producer install: 197 unit + 12 artifact tests;
exact consumer clean verify: 19 unit + 1 artifact test, all zero failures/errors/
skips. Base classes 1819 / 2439 have maximum major 52. Evidence:
`canonical-owner-build-results.json` and paired clean Maven logs.

Exact consumer `be395e91d63bae4296a1efa6eff2949a3535ce8d343e2cd02cd224df1faf3fd7`
passed real Java 8 / Spigot 1.8.8 `OwnerFenceSQL` acceptance: checked cached
mutation, retirement, uncached typed write, repopulation/readback and restoration
of the owned fixture field, detached reward snapshot, pending async user receipt,
vote/reward, points10,total1, graceful stop, SQLite integrity and restart
persistence. No observed linkage/checked-write errors. Evidence:
`checked-cache-runtime-results-OwnerFenceSQL.json`, `canonical-owner-live.log`.
Temporary diagnostic plugin removed; all owned server/client processes exited.

This remains a partial native storage backport. Legacy bulk setters/deletes/wipes/
migrations, cross-process atomicity, lifecycle admission/drain and failed-close
recovery are not claimed covered. Strict bounded reward queue append/provenance/
occurrences/checkpoints/replay and root async decisions/defer/native action
receipts remain to be integrated. No schema/configuration/wire/release-version
change; additive ownership getter/helper only. This cohort is not live MySQL,
FLAT, proxy or failed-shutdown acceptance, full main parity, or final PR readiness.
