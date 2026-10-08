# Current VotingPlugin proxy with a Java 8 / Spigot 1.8 backend

This fork produces a **backend-only** artifact. Install current VotingPlugin on
BungeeCord or Velocity separately, with the Java runtime required by that current
release. The 1.8 JAR has neither proxy entry point nor proxy plugin descriptor.
The historical proxy source trees remain available; they are not deleted.

The current-proxy compatibility path in this candidate is **PLUGINMESSAGING**.
It uses the current SimpleAPI JSON envelope (`t`, `v`, `f`) and UTF framing,
not the old delimiter protocol. HTTP, modern Redis/MQTT/SOCKETS and the current
MySQL mailbox transport are not implemented in this candidate. A requested
CURRENT configuration using them fails explicitly instead of falling back to
plugin messaging or sending incompatible legacy packets.

Configure the backend, and match its identity/channel/encryption with the proxy:

```yaml
UseBungeecord: true
ProxyProtocol: CURRENT
BungeeMethod: PLUGINMESSAGING
Server: backend-a
PluginMessageChannel: vp:vp
PluginMessageEncryption: false
CommunicationEncryption: false
```

For PLUGINMESSAGING, an absent `ProxyProtocol` defaults to CURRENT. Existing
dedicated legacy transports with no `ProxyProtocol` retain LEGACY mode; explicit
CURRENT never silently downgrades to an old wire format. Use `LEGACY` only when deliberately connecting
to an older delimiter-protocol proxy. The older backend transport implementations
and public message APIs remain available in that mode. Changing protocol or
identity policy requires a full restart; ordinary same-mode reload remains supported.
No operator configuration file is replaced and no release version changes.

The backend reads current `v2` total snapshots without treating the first value
as a legacy milestone counter. `manageTotals` controls totals ownership; the
obsolete `setTotals` field cannot cause a second increment of proxy-managed data.
A queued delivery bypasses the backend cooldown only with its explicit queue
marker and valid stable vote ID, matching current main. A live validation marker
alone does not bypass cooldown. VoteUpdate retains its service and last-vote time.

The player-facing proxy is authoritative for PLUGINMESSAGING presence. The backend
sends a simple JSON `login` through the existing authenticated, storage-ready
AdvancedCore login path, fencing the physical player session before publication,
and waits for actual plugin-channel registration before sending. It uses current
correlated status replies. It does not attach the dedicated-transport incarnation fields
which current main rejects on this transport. No connected carrier is an idle
condition, not an initialization failure; status publishing waits quietly.

Optional `CommunicationEncryption` uses the current authenticated AES-GCM envelope.
Provision the same owner-protected `secretkey.key` on both endpoints before enabling
it. `PluginMessageEncryption` remains a separate legacy framing option. Keys and
credentials must never be committed or included in reports.

This candidate advertises delivery/rejection acknowledgement version **0**. It
cannot promise current durable acknowledgement/release semantics. Current main
negotiates its compatible JSON delivery path instead. Vote-ID duplicate tracking is
bounded and in-memory (4,096 completed IDs), not a crash-safe receipt ledger.
Incoming backend work is serialized with a bounded 128-message admission queue;
retirement closes admission and waits for admitted processing to settle.

## Offline UUID compatibility

Current main derives offline voting identities from lower-case names. CURRENT
proxy mode opts into that policy in the paired AdvancedCore fork, including its
player/name lookups; it prevents proxy and backend writes creating two accounts
for differently cased identities. Standalone and LEGACY mode retain the existing
case-sensitive 1.8 policy. Online-mode identities are unchanged.

Existing case-sensitive offline records are **not automatically merged, deleted,
or rewritten**. Back up data and plan an explicit identity migration before
switching an existing offline network; this candidate does not resolve conflicting
balances or merge accounts. Existing records remain accessible by their stored
UUID through the legacy APIs. Use matching OnlineMode settings across the network.

## Comparison and validation

Initial VotingPlugin comparison: `834bcb84a59b6da40640eac83c20fd97ad1d64c9`.
Latest proxy compatibility validation: `22c630b10c0a57ee809e5353145372d2c237c6a5`
(the intervening NameMC/POM change does not change this wire contract).
Current AdvancedCore reference: `6390c1cab41bd4d7683c7df88dd36537c8c7861e`.
These are immutable comparison inputs, not a claim of complete main parity.

Validation uses an exact reference-built current proxy JAR on Java 21 and the
candidate backend on a real Java 8 runtime / Spigot 1.8.8, with a fixture-owned
MariaDB, external NuVotifier RSA ingress, and a real protocol client through the
proxy. No Control service or standalone legacy proxy artifact participates.
The integration checks cover shared identity/totals and native backend rewards;
unsupported modern transports and crash-safe delivery receipts remain unverified.

The coordinated producer and consumer builds use Temurin Java 8u504 and Maven
3.9.9 with the workspace-local Maven repository. AdvancedCore's unchanged final
producer build passed 848 unit +78 artifact tests (926 total). The backend build
passed 83 unit +1 packaged-artifact tests (84 total), zero failures/errors/skips.
All packaged base classes are checked for Java 8 bytecode (major <=52). Tests cover
JSON bounds/invalid fields, V2 totals ownership, queued/live cooldown markers,
reference AES framing, ciphertext tampering and wrong keys/domains, preserved
legacy protocol defaults, post-authentication/physical-session fencing, and late
plugin-channel registration. Exact local build commands:

```sh
JAVA_HOME=/workspace/votingplugin-1.8-port-workspace/tools/jdk8u504-b01 \
PATH=/workspace/votingplugin-1.8-port-workspace/tools/jdk8u504-b01/bin:/usr/bin:/bin \
mvn -B -ntp -f AdvancedCore/pom.xml -Dmaven.resolver.transport=wagon \
-Dmaven.repo.local=/workspace/votingplugin-1.8-port-workspace/.m2/repository \
-Djava.io.tmpdir=/workspace/votingplugin-1.8-port-workspace/runtime/tmp clean install

JAVA_HOME=/workspace/votingplugin-1.8-port-workspace/tools/jdk8u504-b01 \
PATH=/workspace/votingplugin-1.8-port-workspace/tools/jdk8u504-b01/bin:/usr/bin:/bin \
mvn -B -ntp -f VotingPlugin/pom.xml -Dmaven.resolver.transport=wagon \
-Dmaven.repo.local=/workspace/votingplugin-1.8-port-workspace/.m2/repository \
-Djava.io.tmpdir=/workspace/votingplugin-1.8-port-workspace/runtime/tmp clean verify
```
