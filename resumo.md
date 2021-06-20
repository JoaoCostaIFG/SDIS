# Resumo SDIS

## Communication channels

- **Connection-based** - the processes must setup the channel before exchanging
  data.
- **Connectionless** - the processes need not set up the channel, can exchange
  data immediately.

- IP + port: identificam processo (actually endpoint, proc. pode mudar).

### UDP + TCP

```txt
| Property                            | UDP     | TCP    |
|=====================================|=========|========|
| Abstraction                         | Message | Stream |
| Connection-based                    | N       | Y      |
| Reliability(no loss or duplication) | N       | Y      |
| Order                               | N       | Y      |
| Flow control                        | N       | Y      |
| Number of recipients                | 1-n     | 1      |
```

- UDP supports multicast. A successful receive() always receives the message
  atomically.
- TCP channels are bidirectional. A read() might get only part of the stream (no
  separation).

---

## Multicast communication

- Broadcast efficiently on a point-to-point network
  - spanning tree that includes all nodes of the network.
- Multicast efficiently on a point-to-point network?
  - spanning tree that includes: the sender, the n receivers, the nodes between
    them (to ensure we have one tree).

### Switch Trees

Optimal algorithms are not pratical (SPT, MST). We change the topology of the
multicast tree: taking into account resource constraints and improving
performance (cost).  
Limitation: assumes that the multicast tree has been previously created (child
becomes child of root on join).

Node parent switches can be performed to any node that isn't the node's child:

- switch-sibling - switch to sibling.

- switch-one-hop - switch to sibling or grandparent (can implement all
  topologies in two-hop while taking more iterations).
  
- switch-two-hop - switch to node 1 ou 2 hops away.

- switch-any - go anywhere that isn't parent or child.

  ![image-20210615201708189](/home/joao/.config/Typora/typora-user-images/image-20210615201708189.png)

Decisão entre candidatos pode ser feita com base no custo da árvore e/ou no
delay até a root.

### Banana Tree Protocol (BTP)

- One-hop.
- Node joins and becomes child of root. If no tree, becomes root.
- Node asks new parent for permission to switch parent (request can be rejected).
- If node fails, its children become child of root (its tree partitions). Can become child of grandparent to avoid overloading root.
- Cycles can be generated:
  - Concurrent attempts by different nodes to switch their parents. When in process of switching, reject all requests to becomes parent.
  - Switch based on outdated topological information. May be prevented by including topological info in the auth request (in one-hop algorithm, the parent of the request node is enough).

### Epidemic algorithms

Update the info by passing it to some neighbor nodes. These will pass it on to their neighbors in a lazy way (not immediately). Eventually, all nodes with copies of that piece of info will update it.

- Robust: tolerates crashes, even if each node has only a partial view of the system.
- Highly scalable (sync between nodes is local) if we assume that any node can randomly select any other node (not very scalable, because we would need to know about every other node).

#### Anti-Entropy

Periodically node P randomly chooses node Q for exchanging messages. The probability of a node missing a message tends to 0.

- Push gets slower at the end (less probability of finding node that hasn't seen the message).
- Pull gets faster at the end (more probability of choosing a node with the message).
- Push-Pull combines the advantages of both (P and Q exchange messages, so they have the same messages after the exchange).

#### Gossiping

P loses the motivation to disseminate a message (based on a **probability p_stop**), if it tries to disseminate it to another node Q that already knows it. Efficient, but we **can't be sure that everyone receives the message**.

---

## Remote Procedure Call (RPC)

### Transparency

- Platform heterogeneity:
  - Different architectures and/or representations for composite data-structures.
  - Solution: standard format in the wires (less efficient but only needs 2 conversions); or receiver-makes-right.

- Addresses as args. Use call-by-copy/restore:
  - Works in most cases.
  - Complex: same address can be passed in different arguments.
  - Inefficient for complex data structures (like trees).

### RPC Semantics

- **At-least-once**: keep retransmitting until we obtain a response (be careful with non-idempotent requests). Suitable if requests are idempotent (message loss).
- **At-most-once**: not trivial for UDP. RPC can report an error on TCP connection break. Appropriate when requests are not idempotent (message loss).
- **Exactly-once**: not always possible to ensure this semantics (**Note: policies no slide 20 de RPC.pdf**).

---

## Clients and Servers (processing)

The goal is to overlap I/O with processing.

### Thread-based concurrency

- appears simple (need only grantee isolation in access to shared data).
- Could use only monitors and synchronized methods in Java and condition variables (modularity problems and possibility of deadlocks).
- Performance may suffer: the larger the critical sections, less concurrency (but main reason for concurrency is performance).
- Each thread requires resources. Increasing the number of threads, eventually leads to lower throughput. Context switching (kernel-level threads) can introduce latency.

### Event-based concurrency

- Structure of the code becomes very different.
- No errors like race conditions (may be elusive)
- Lack of support by debugging tools.
- Leads to poorly structured code: lack of atomicity (with multiple cores, we can have race conditions, even if there is no preemption).

### Leases

Server leases a resource to a client for only a finite time interval: upon its expiration, the resource may be taken away (unless the lease is renewed).

---

## Security and Cryptography

**Unconditionally secure**: an attacker cannot extract information about the plaintext by observing the cyphertext.

History shows that published algorithms are broken mostly because of the length of the keys (and not vulnerabilities). With unpublished algorithms, e.g.: DeCSS, the history is different.

### CIA triad

- **Confidentiality**: prevent unauthorized access;
- **Integrity**: prevent unauthorized modification;
- **Availability**: prevent authorized access from being denied

### Encryption/Decryption Algorithms

- Symmetrical, or with a shared key: the keys for encrypting and decrypting are the same. These key is shared by all principal authorized to access info, so it must be known to those principals only;
- Asymmetric, or with public key: the keys for encrypting/decrypting are different.

### Encryption with shared key: DES

Replaced by AES. Works by combining 2 basic operations on bit blocks: permutation of bits in a block + substitution of 6-bit sub-blocks with 4-bit sub-blocks. The basic algorithm operates on 64-bit blocks that are transformed in blocks with the same length. The encryption takes 16 rounds, each taking a different 48-bit key that is generated from the 56-bit master key.

### Public key encryption: RSA

Based on modular arithmetic (p and q are prime numbers):

- n = p * q and z = (p - 1) (q - 1)
- d * e = 1 mod z
- For any x (0 <= x < n): x^(d*e) = x mod n

The encryption key, Ke = (e, n), must be public. The decryption key, Kd = (d, n), must be secret.

* Pick 2 very large prime numbers, p and q
* Compute n and z
* Select e (it can be small)
* Compute d using Euclides algorithm: e * d = 1 mod z

### Cipher Block Modes of operation

- Block ciphers encrypt fixed-size blocks: use padding and split data in blocks
- Stream ciphers operate directly on sequences of bytes of arbitrary length

![image-20210616090451034](/home/joao/.config/Typora/typora-user-images/image-20210616090451034.png)

Can be problematic because identical blocks are encrypted in identical cyphered-blocks.

![image-20210616090504043](/home/joao/.config/Typora/typora-user-images/image-20210616090504043.png)

We can use an **Initialization Vector (IV)**. This is a (pseudo-)random number.

### Cryptographic hash functions

- Compression - maps input of arbitrary length into a fixed-length hash-value.
- Ease of computation.
- Non reversible (one-way) - given y, it is computationally infeasible to find a value x such that y=h(x)
- Weakly collision resistant - given x, it is infeasible to find a different value x' such that h(x)=h(x')
- Strongly collision resistant - infeasible to find two different x and x' such that h(x)=h(x')

#### MD5

Needs message to be multiple of 512-bit (uses padding if necessary). Makes k stages (number of 512-bit blocks). Input is 512-bit block and 128-bit number. Output is 128-bit number. Each stage makes 4 passes over message block.

### Authentication with Hash Functions

By adding a key to the message/data, hash functions can be used to authenticate the sender and to check message integrity: in this case hash functions are known as **MAC**.

In this case, the hash functions must be **computational resistant**: for any unknown key, k, given the values (x, h(k, x)), it is computationally infeasible to compute h(k, y) for a different value y.

Keys must be shared by all comm. ends: MAC is not a digital signature.

### Digital Signature

Based on asymmetric encryption systems, e.g.: RSA. Allows third-parties to identify the sender (not only the receiver can do it now). Basically, the encryption of a message with the senders private key van be considered as signature. In practice, we first compute the hash of the data to sign, and then encrypt that hash value (output is digital signature).

---

## Secure communication channels

- Authentication of comm parties
  - On the web, usually only one of the entities is authenticated.
  - It's common for the channel set up phase to include the establishment of a session key.
  - Password are not appropriate for authentication while setting up a secure channel: use challenge/response protocols.
- Integrity/Authenticity: messages were not modified in transit

### Shared key authentication protocols

Assume Alice and Bob are the two ends of a communication channel and share a secret key, K_A,B.

![image-20210616094742564](/home/joao/.config/Typora/typora-user-images/image-20210616094742564.png)

![image-20210616094757068](/home/joao/.config/Typora/typora-user-images/image-20210616094757068.png)

**Reflection attack**: We trick B into sending use the response to its own challenge (challenge in message 2, we attack in message 3 and 4 and then answer in 5). We can avoid this by making the protocol asymmetrical (one party gets the even challenge and the other, the odd).

![image-20210616095130490](/home/joao/.config/Typora/typora-user-images/image-20210616095130490.png)

### Mediated authentication

Each pair of principals sharing a key is not scalable. We need a Key Distribution Center (KDC) which must be trusted by all principals. The KDC shares a secret key with each principal and generates secret keys for sharing between principals that wish to communicate securely.

![image-20210616095655573](/home/joao/.config/Typora/typora-user-images/image-20210616095655573.png)

This is not complete, because A and B must first prove they know K_A,B before starting the communication (A can receive key and start to communicate before B has received its key).

#### Needham-Schroeder's protocol

![image-20210616100048213](/home/joao/.config/Typora/typora-user-images/image-20210616100048213.png)

- Nonce (R_A1) is used to prevent **replay attacks**: C intercepts A message to KDC and sends a copy of it (replay) after the communication is over to act as A.
- The KDC includes B's identity in the response, to prevent C from impersonating B, by replacing B with C, in message 1.
- Messages 3 + 4 allow A to auth B.
- Messages 4 + 5 allow B to auth A.
- K_B,KDC(A, K_A,B) in message 2 is known as **ticket** to Bob.
- If an attacker learns about Ka,b, he can replay-attack. We solve this by requiring A to ask for a challenge Kb,kdc(Rb1). This is a random number that will be sent Kb,kdc(A, Ka,b, Rb1) to B on first comm for validation.

### Public-key authentication

A and B know public keys of one another. Private keys are known exclusively by the respective principal.

![image-20210616103313279](/home/joao/.config/Typora/typora-user-images/image-20210616103313279.png)

In addition to authenticating both principals, this protocol also negotiates a **session key**.

**Authenticated encryption**:

Channel should be able to detect modification of the messages. **Encrypt-then-MAC (EtM)**: first encrypt the message then compute MAC (different keys should be used, but there are approaches that use a single key).

### Session key

- Public key encryption is less efficient
- Keys wear out with use: the more ciphertext an attacker has, the more likely the attack will succeed.
- Using the same key over multiple sessions, makes replay attacks more likely
- If session key is compromised, the attacker will not be able to decrypt messages exchanged in other sessions.
- Channels can change keys in the middle of a session to prevent replay attacks in long running sessions.

### Diffie-Hellman Key-Agreement protocol

n is a large prime number and g a number less than n. These can be public. Each principal picks a secret large number x and y, and executes the following protocol:

![image-20210616105347384](/home/joao/.config/Typora/typora-user-images/image-20210616105347384.png)

An attacker overhearing the communication can't compute the session key, because the discrete logarithm is considered to be computationally intractable (no efficient algorithm is known to compute a from g^a mod n, knowing only n and g).

Algorithm is vulnerable to man in the man-in-the-middle attacks. To defend:

- A and B need to publish g^x mod n.
- Use authenticated DH: either share secret key between A and B or public-keys (and private).

### Perfect Forward Secrecy

An attacker will not be able to decrypt a recorded session even if:

- breaks into both A and B
- steals their long-term secrets

as long as A and B delete their secret numbers.

### Session key for unidirectional Authentication

Servers do not authenticate clients. Client generates (randomly) the session key and sends it to the server encrypted with the server's public key. Client and server can execute the Diffie-Hellman, but only the server's messages are authenticated.

- Client is guaranteed that it has set up a secure channel with the server.
- Server has no idea who the client is, but knows it is always the same client on the other end of the channel.

### Public key certificates

Public-key certificates/digital certificate contains: subject's name, public key that matches private key, signature by CA, and name of CA.

---

## Fault tolerance

### Triple modular redundancy (TMR)

- Each node is triplicated and works in parallel.
- The output of each module is connected to a voting element, also triplicated, whose output is the majority of its inputs.

### Reliability and availability

Reliability R(t): the probability that a has not failed until time t (often characterized by the MTTF).

Availability: a = MTTF / (MTTF + MTTR).

### Failure models

- **crash**: does not respond to any input after some instant
- **omission**: a component does not respond to some of its inputs
- **timing/performance**: a component does not respond on time
- **byzantine/arbitrary**: component behaves in a totally arbitrary way

---

## Leader Election

coordinator/leader roles lead to simpler and more efficient algorithms. Only one node is elected the coordinator. All nodes know the identity of the coordinator.

### Garcia-Molina's algorithms

To achieve fault tolerance, we can:

- mask failures (only approach if we need continuous operation)
- reorganize the system (simpler)

- **State**: DOWN, ELECTION, NORMAL
- **Coordinator**: coordinator according to node.

### Specification of election

Assuming that the communication system does not fail, has an upper bound on time to deliver a message, and incoming messages arrive with no delay.

At any time instant, for any 2 nodes, if they are both in NORMAL state, then they agree on the coordinator: S(i).s == S(j).s == NORMAL => S(i).c == S(j).c

If no failures occur during election, the protocol will eventually transform a system in any state to a state where:

- there is a node i such that S(i).s == NORMAL && S(i).c == i
- all other non-faulty nodes such that j != i have S(j).s == NORMAL && S(j).c == i

### Leader election vs mutual exclusion

- in an election fairness is not important (all we need is that one node becomes the leader)
- Election must deal properly with the failure of the leader (mutual exclusion assume that a process in a critical section does not fail)
- All nodes need to learn who the coordinator is

### Bully election algorithm

- Smaller identifier means we are stronger.
- Look around for anyone stronger (the leader): ARE-U-THERE messages are answered with YES by stronger nodes (they initiate a new election when this happens). We wait 2 * T for this answer. If challenge is answered, we back off and start a timeout to detect failure of the challenger.
- If there isn't anyone, impose self as leader and tell everyone: HALT message to weaker nodes. Weaker nodes set state to ELECTION and stop any elections they started. T time units later, the node will send NEW-LEADER to these weaker nodes (they set state to NORMAL and register new leader).
- ARE-U-NORMAL message by leader checks if all remote nodes are NORMAL. Start new election if anyone reports anything different from NORMAL. This also allows nodes to detect leader failures.

### Groups

Menos assumptions (time upper bound e instant reply).

- se somos os 2 NORMAL e estamos no mesmo grupo, então temos o mesmo coordinator
- se es NORMAL e o teu leader é o i, entao o i está no teu grupo

### Invitation algorithm

All nodes initially create a singleton group, of which it is the coordinator. Periodically, coordinators try to merge their group with other groups in order to form larger groups.

- ARE-U-THERE message to detect if group leader is still alive. If not, create new own singleton group.
- ARE-U-LEADER messages to find other groups. If any reply, start merge algorithm after random delay.
- INVITATION message to all leaders that responded and members of own group. Upon receiving INVITATION, leaders forward it to their group members. Everyone with INVITATION responds with ACCEPT. After some time, send READY message to all ACCEPTS (if a node doesn't receive the READY after timeout, initiates a new election).
- Only answer ARE-U-LEADER and INVITATION if NORMAL. Only allowed to participate in 1 election at a time.

---

## DNS (is iterative)

- name - up.pt ou fe.up.pt
- domain(subdomain) - up.pt(fe.up.pt)
- zone - never overlap, region managed by an administrative authority, can be just a subdomain.
- dns server - each zone has a primary server that is responsible for the info of that zone. This server can contain the info of more than one zone. For availability reasons, this info needs to be replicated in at least 1 secondary server.

### Resource records

- name/value - not necessarily host name and IP
- type - how the value should be interpreted (depends on the class)
- class - specifies the name space (usually IN)
- TTL

### Name resolution

- Resolution is based on longest prefix matching
- Every server must keep (NS, A) RR pairs for each of its direct subdomains.
- Answers provided using caches are said to be non-authoritative.

### Replication

Rely heavily on replication: performance + availability. Problems of inconsistency. Zone info is updated/added at only the primary server. Replicas don't need to be updated synchronously. The use of stale data is usually detected upon use.

To detect changes to a zone's RR, each zone has a Start of Authority (SOA) RR with the following fields: Serial (32 bits that identify a version; increased on update) and Refresh (32 bits that identify maximum time in seconds between update attempts).

### Update detection and zone propagation

Detect update by comparing serial field of the secondary with the primary. Poll primary server for updates or get notifications (not yet approved). Zone transfer sends AXFR query requesting for the transfer of an entire zone (incremental (only changes) not yet approved).

DNS requires zone transfers to use TCP. All other queries usually use UDP (although TCP is possible).

---

## Chord

### Distributed Hash Table (DHT)

Key-Value pairs are stored in a potentially very large number of nodes. Provides a single operation: lookup(key) returns the address of the node responsible for the key.

### Chord

- m-bit identifiers in a ring (mod 2^m). m=7 => 2^7=128 identifiers
- The node responsible for key k is the successor of key k, succ(k): smallest id that is larger or equal to k (modular arithmetic)

### Fingers

- node stores info about successor (next node in ring).
- each node knows more about nodes closer to it than about nodes further away.
- Key resolution requires O(log(N)) steps, where N is the number of nodes in the system.

Finger table is an array of m pointers to nodes: succ((n + 2^(i-1)) mod 2^m). First finger is the successor. Chord works correctly iff FT[0] is correct.

### Other issues

- Every node needs to keep info about its predecessor: periodically node asks its successor about its predecessor, p. If p is between self and successor, update successor to p and notify p about choosing it as the new successor (p will set the new predecessor).
- Periodically update elements in FT one at a time.
- Periodically check if predecessor is still alive
- Node can keep a list of r successors: if node fails, the next node can replace it.
- Cryptographic hash functions help achieve tolerance against denial-of-service attacks.

### Virtual topology issues

Chord and P2P systems use an overlay network. If topology is oblivious to the underlying physical network, routing may be inefficient. Solution 1: assign identifiers so that the overlay topology is close to that of the underlying physical topology (not possible in chord). Solution 2: route based on the underlying topology. Keep several nodes per interval and resolve to the closest one. Solution 3: pick neighbors according to the underlying topology (not possible in chord).

---

## Names

Are layers of indirection.

### Identifier is a name with 3 properties

- refer to one entity at most
- an entity has at most one identifier
- an identifier always refers to the same entity (never reused)

### Binding

Binding is a mapping from a name to an object/entity. Name resolution is the process of finding a binding for a name.

A name is always resolved in the context of its name space.

### Name resolution

In small scale, can use a single server (e.g.: rmiregistry).

![image-20210616141146735](/home/joao/.config/Typora/typora-user-images/image-20210616141146735.png)

We can use 3 different strategies. Recursive allows for caching at server, but requires servers to keep state and makes it harder to set values of timeouts. Transitive makes it harder for the client to set a timeout.

### Closure mechanism

Allows one to get a context to use to resolve a name. Selecting the initial node in a name space from which name resolution is to start. Ad-hoc and simple.

---

## Clock synchronization

### Local clocks

Generate periodic interrupts. OS uses these interrupts to keep the local time. These clocks have a drift (dH(t)/dt - 1) wrt a perfect clock. Even if this drift bounded, unless clocks synchronize, their values may diverge forever.

### Clock sync (CS)

- External CS - sync against an external time reference (R) - |Ci(t) - R(t)| < a (accuracy), para todo i. This implies internal clock sync too.
- Internal CS - sync among local clocks in the system (not necessarily related to real time) - |Ci(t) - Cj(t)| < pi (precision), para todos i, j.

### CS Centralized Algorithm

Each process has a local clock:

- Master/server clock - provides the time reference (periodically read the local time and broadcast a SYNC message).
- Slave/client clock - synchronize with the master/server clock (update clock upon reception of a SYNC message).

Local clock drifts are bounded. System is synchronous: there are known bounds (both lower and upper) for comm delays; the time a process requires to take a step is negligible with respect to comm delays.

Accuracy: |Ci(t) - Cm(t)| < a for all correct processes i, where Cm(t) is the master's clock.

### Master time estimation

There is a delay to notify slaves. Delay can be estimated min < delay < max (max is known because system is synchronous). To minimize error, use midpoint of [Cm(t) + min, Cm(t) + max]: Cs(t) = Cm(t) + (min + max)/2

### SYNC message period

To ensure accuracy: |Cm(t) - Cs(t)| < a. Because the clock drifts are bounded.

The clock skew lower bound is determined by the delay jitter.

### Message delay estimation

Christian approach (use round-trip-delay): [t + min, t + round - min] -> round/2 - min

Reduce error by time stamping with the local clocks the sending/receiving of messages.

![image-20210616152846395](/home/joao/.config/Typora/typora-user-images/image-20210616152846395.png)

Precision depends on: rate synchronization, symmetry assumption, time stamp accuracy (the lower in the protocol stack, the better).

### Precision Time Protocol

**Syntonization**: ensure master and slave have the same clock rate. Sync message requires the ability to insert the timestamp into the message on the fly. Follow_up message requires an additional message (optionally if hardware doesn't support inserting the timestamp in Sync on-the-fly):

- T1^(k + 1) - T1^k = T2^(k + 1) - T2^k
- T2^(k + 1) - T1^(k + 1) = T2^k - T1^k

**Offset and delay estimation**: Delay_Req + Delay_Resp to obtain round-trip-delay (T3 and T4).

![image-20210616154003065](/home/joao/.config/Typora/typora-user-images/image-20210616154003065.png)

### Local clock correction

- Instantaneous: correct offset only at every sync point (may lead to non-monotonic clocks).
- Amortized: over the sync period adjust both a and b: Cs(t) = a * t + b

Each clock between resync points can be seen as a different clock. Therefore, clock correction can be seen as jumping from one clock to the next.

### NTP

Synchronization hierarchical networks. Servers at the top are primary (lowest stratum), next stratum (level) are secondary, and so on... Leaves are clients. Subnet can be reconfigured in case of failure: secondary server switches primary server; primary becomes secondary when UTC source becomes unreachable.

Uses UDP. Server multicasts its time to clients (low precision but OK for LANs). Request-reply (similar to Cristian's algorithm). Symmetrical (servers swap their times) (used by servers at the lowest strata, ensures highest precision, decentralized algorithm).

### At-most-once messaging

Remember all messages received and discard duplicates. But can't remember all: forget far away past: session-based (handshake after a while), synchronized clocks (all nodes have a synchronized clocks with accuracy a). Each message has connection id (selected by the sender) and must be unique. Synchronized clocks improve performance at the cost of occasionally rejecting a message.

### Leases

Ensure some property during a limited time (lease duration). Absolute duration require synchronized clocks, relative requires synchronized rates. Can be renewed.

### Kerberos

Uses Needham-Schroeder's but instead of their solution for replay attacks, uses synchronized clocks (and to prevent the use of keys for too long).

### Synchronized rates

Whenever possible, we should use synchronized rates. They work for most applications and require little communication. Synchronized clocks are more powerful than rates but support more algorithms (can provide warning when clocks get out of sync).

---

## Events and logical clocks

We often don't need SC, we just need to order events.

### Happened-before relation (->) (captures potential causality)

1. if e and e' happen in that order: e -> e'
2. if e is sending of message m and e' is the corresponding reception: e -> e'
3. if e -> e' and e' -> e'', then e -> e'' (transitive)

a -/-> e /\ e -/-> a  => concurrent a || e

### Lamport clocks

Logical clock used to assign (Lamport) timestamps to events. Each process has its own Lamport clock Li.

Lamport condition: e -> e' => L(e) < L(e') or L(e) >= L(e') => e -/-> e'

### Clock condition

- **C1:** e -> e' in the same process => L(e) < L(e')
- **C2:** e is send msg by i and e' is receive by j => L(e) < L(e')

Note: free-running physical clocks cannot be used as Lamport clocks (problem with C2).

To satisfy the condition a Lamport clock must be updated **before** assigning its value to the event (incrementing a Lamport clock is not an event):

- LC1 if e is not the receiver of a message, just increment Li
- LC2 if e is the receiver of a message m: Li = max(Li, Ts(m)) + 1 (requires sending the timestamp of the sending of event in every message)

### Extended Lamport timestamp

Order the pair (L(e), i), where i is the process where e happens. Somewhat arbitrary but total.

A process executes its command timestamped T when it has learned of all commands issued by all other processes with timestamps less than or equal to T.

### Vector clocks

The main limitation of Lamport clocks is that: **L(e) < L(e') =/=> e -> e'**. We can only conclude only if HB does not hold.

Each process pi keeps its own vector Vi, which it updates as follows:

- VC1 if e is not the receiver of a message, just increment Vi[i]
- VC2 if e is the receiver of a message m:
  - Vi[i] = Vi[i] + 1
  - Vi[j] = max(Vi[j], TS(m)[j])

Initially Vi[j] = 0, for all j. The timestamp of the sending event is piggybacked on the corresponding message (TS(m) = V(send(m))).

Vi[i] is the number of events timestamped by pi. Vi[j] is the number of events in pj that pi knows about.

![image-20210616171525153](/home/joao/.config/Typora/typora-user-images/image-20210616171525153.png)

---

## Atomic commitment

- all processes that decide, must decide the same value
- the decision of a process is final (can't change)
- If some process decides commit, all processes must vote commit
- If a all processes voted commit and there are no failures, then all processes must decide commit
- If in an execution containing only failures that the can be tolerated, all are repaired and no new failures occur (for sufficiently long), then all processes eventually reach a decision.

### Two-phase commit (may block even when there is no failure on all sites)

1 coordinator + several participants. Coordinator can be participant too.

- Phase 1: upon request from the app
  - coordinator sends a VOTE-REQUEST to each participant and wait for reply
  - participant upon receiving a VOTE-REQUEST sends vote: VOTE-COMMIT/YES or VOTE-ABORT/NO
- Phase 2: once coordinator determines that it is time to decide
  - Coordinator decides/sends GLOBAL-COMMIT (everyone voted commit) or GLOBAL-ABORT (else)
  - Participant decides according to the message received from the coordinator

![image-20210616173414489](/home/joao/.config/Typora/typora-user-images/image-20210616173414489.png)

#### Termination protocol

Participant will communicate with other participants to decide the outcome: someone voted abort; someone knows the decision; or just wait.

#### Recovery

Need to keep logs in stable storage of the state of the protocol. 2PC satisfies assertions even in the presence of non-byzantine node failures and communication faults (including partitions).

The main problem with this protocol is that it may require participants to block (wait longer than a comm timeout) (problem less likely in 3PC).

### Independent recovery and blocking

No AC protocol that always allows for local recovery (without comm with other procs). No AC protocol that never blocks in the presence of comm failures or failures on all other processes.

### Three-Phase commit

Adds the PRECOMMIT phase to 2PC. PRECOMMIT states ensure the non-blocking cond:

- no process can commit while another proc is in an uncertain state (INIT, WAIT, READY)
- proc in PRECOMMIT will decide commit (not uncertain) unless the coordinator fails.

This cond is necessary and sufficient to prevent blocking unless all processes fail:

- if there's a majority, see majority. If READY decide ABORT; if PRECOMMIT decide PRECOMMIT.
- processes may block if there is no majority.

---

## State replication with primary backup

### Basic algorithm

- one server is the **primary** and the remaining are **backups**.
- the client send requests to the primary only: it executes the requests, updates the state of the **backups** and responds to the clients (orders the different client requests).
- If the primary fails, a failover occurs and one of the backups becomes the new primary (may use leader election).

### Failure detection

- send I'M ALIVE messages periodically or acknowledgment messages (only reliable if the system is synchronous).
- Failover: select new primary.

### Primary failure

- Crashes after sending response to client - transparent to client unless message is lost.
- Crashes before sending update to backups - no backup receives the update; if client retransmits the request, it will be handled as a new request by the new primary.
- Crashes after sending update (and before sending response) (must ensure update delivery atomicity):
  - all backups receive update - if client retransmits request, new primary will respond (update message must include response if operation is non-idempotent).
  - not all receive update - backups will be in inconsistent state.

### Recovery

When a replica recovers, its state is stale (can't apply updates and send ACKs to primary). Solution is to use a state transfer protocol to bring the state of the backup in sync with that of the primary:

- resending missing UPDATES
- transferring the state itself

In both cases, the recovering replica can buffer the UPDATE messages received from the primary and process them when its state is sufficiently up to date.

### Non-blocking algorithm

Waiting for backup ACKs increases latency. Solution is allowing primary to send response to client before receiving ACKs from backups.

---

## Consensus

- all procs get a set of values, V, as input
- each proc chooses a **single** value, v, from V (decision is irreversible)
- all procs must choose the same val (agreenment)
- the value must be in the input set (validity)
- in a failure free execution, eventually all procs chose a value (termination)

### Synod

- Proposers - send proposals to the acceptors. Proposal is a pair, (n, v), containing an unique number (proposal identifier) and v (some value from V).
- Acceptors - accept the proposals. A proposal is accepted if it is valid. A value is chosen if a majority of acceptors accepts a proposal with a given value.
- Learners - learn the chosen value.

2 phases:

1. find a number that makes the proposal likely to be accepted (and the value that has been chosen, if any)
2. submit a proposal

#### Synod phase 1

- **Proposer** - selects a proposal number n and sends a PREPARE(n) request to a majority of acceptors
- **Acceptor** - upon receiving a PREPARE where n is larger than any previous PREPARE request to which it has already responded, responds with:
  - a promise to not accept any more proposals numbered less than n + highest-numbered proposal (if any) that it has accepted.
  - else, the acceptor doesn't need to respond

#### Synod phase 2

- **Proposer** - If receives response to its PREPARE request (numbered n) from a majority of acceptors, then it sends an ACCEPT(n ,v) request to each of those acceptors for a proposal numbered n with value v, where v is:
  - value of the highest-numbered proposal among the responses received in phase 1
  - or is the proposer's input value (if the responses didn't report values)
- **Acceptor** - if receives an ACCEPT request for a proposal numbered n, it accepts the proposal unless it has already responded to a PREPARE request having a number greater than n.

![image-20210617110124848](/home/joao/.config/Typora/typora-user-images/image-20210617110124848.png)

#### Learners

- acceptor can notify all learners: minimizes learning delay, but too much traffic
- A learner may learn from another learner: acceptors notify the learner leader (leader selection) or set of distinguished learners
- Learner can ask the acceptors what proposals they accepted, or ask proposer to make another proposal so we can see what happens

### Paxos

Each process plays the role of proposer, acceptor and learner. The chosen one is the _**distinguished**_ PROPOSER AND LEARNER.

Stable storage is used to keep state used by acceptors and that must survive crashes:

- the largest number of any PREPARE to which it responded
- the highest-numbered proposal it has accepted
- updates to stable storage must occur before sending the response

Uniqueness of the proposal numbers is ensured by using a pair (cnt, id). Each proposer stores in stable storage the highest-numbered proposal it has tried to issue.

### State machine replication with Paxos

Paxos decides which command should be n: the ith instance is used to decide the ith command in the sequence; different executions may run concurrently but must ensure that each command is executed only once.

A single server is elected to be the leader (distinguished proposer). When leader receives command, it chooses a sequence number for the command and proposes the execution of that command. Leader may not succeed if it fails or another server believes itself to be leader.

1. leader executes phase 1 for all instances (using a single very short message (same proposal number, 1, for all instances))
2. acceptor will respond with a simple OK message
3. for each client command (received by the leader), it can run phase 2 for the next instance
4. a leader may start phase 2 for instance i before it learns the value chosen by instance i - 1 (an implementation may bound the number of pending phase 2 instances). This can lead to problems with leader failure, because new leader might have learned cmd for instance i, but not for instance i - k.
5. server may execute command i if it learned the command chosen in the ith instance and has executed all commands up to command i.

If failure of a leader is a rare event, the cost of executing a state machine command is essentially the cost of executing only phase 2: this is the best we can hope for; Paxos is almost optimal.

Election of a single leader is needed only to ensure progress.

---

## Group based communication

### Group membership (which processes belong to the group)

Outputs a **view** of the group. Each view is a set of processes with an identifier (vi) (alternatively, a process can get a new identifier every time it join the group). If a process has a view, all procs in that view must have agreed to join the view.

Type:

- Primary component - ensures that at "any time" there is at most one view (the views are totally ordered; achieved by requiring a view to comprise a majority of the procs)
- Partitionable - allows the existence of more than one view at any time.

Interface:

- join/leave - used by procs to request to join/leave groups
- new-view - used to notify a view change, either in response to: voluntary request (join/leave); unexpected events (failure or recovery of procs).

Failure detection: needs not be reliable (a process may be expelled from a group by mistake).

### Reliable broadcast in static groups

- Validity - if a correct proc broadcasts m, then all correct procs in the group deliver m eventually
- Agreement - if a correct proc delivers m, then all correct procs in the group deliver m eventually
- Integrity - a proc delivers m at most once and only if it was previously broadcasted by another proc (in closed groups, broadcaster must be a group member too).

Failure assumptions: procs may fail by crash and recover; any network partition eventually heals.

### Reliable broadcast in dynamic groups

Validity and agreement change from **all correct procs** to **all correct procs in that view**.

Validity and agreement conflict with: groups as a mean to keep track of the state of procs in the system in a coordinated way; impossiblity of distinguishing between slow, failed, and unreachable procs.

### View/Virtual Synchronous Multicast

**Virtual synchrony**: if a proc changes from view V to V', it delivers the same set of messages in view V (variation of agreement).

**Self delivery**: if a correct proc p broadcasts message m in view v, then it delivers m in that view (variation of validity)

Assume:

- point-to-point channels
- reliable channels - if the procs at the end of a point-to-point channel are correct, then a msg send in one end will be delivered at the other.
- FIFO channels - msgs delivered in order
- Procs fail by crash only.

Sender crash in middle of multicast:

- deliver the msg **only if** all correct procs receive it (increases delivery latency).
- deliver the msg immediately - upon view change, procs that survive the current view must send each other the messages they have delivered that may have not been received by other view members.

A message m is **stable** for proc p, if p knows that all other procs in the view have received it:

- keep a copy of the messages delivered until they become stable.
- upon view change: resend all non-stable messages to the remaining procs; wait for the reception of non-stable msgs from other procs (and deliver them if haven't done it done); change to the new view. **OR** a proc is elected as coordinator (doesn't require extra message because all group members are known so we can use a rule like selecting member with smallest id); each proc sends its non-stable msg to the coordinator.

Procs send a FLUSH message after sending all non-stable msgs. Upon receiving a FLUSH from each proc in the current and next view, a proc may change its view. Procs initiate a new view change if a proc crashes during this protocol.

### PBR implementation with View Sync

Primary can be determined from the view membership without further communication. Primary sends the updates to the backups using view sync multicast.

- Upon receiving a request, the primary:
  - executes it;
  - generates and update representing the change in the state;
  - multicasts the UPDATE to the current view, Vi.
- Upon receiving an UPDATE, the backup:
  - updates its state accordingly
  - sends its acknowledgment to the sender

Upon failure, a new view is generated (and a new primary is generated if the primary failed).

Ensures UPDATE delivery atomicity to replicas that move from one view to the next: either all replicas that move from that view to the next deliver the UPDATE or none does it.

Upon view change, new members must sync their state: still need a state transfer protocol. At most-once semantics: if uncertain, cache the response (retransmit response if client retransmits the request).
