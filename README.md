# Distributed Backup Service for the Internet

Second Project for group T5G21.

## Group members

1. Ana Inês Oliveira de Barros (up201806593@edu.fe.up.pt)
2. Eduardo da Costa Correia (up201806433@edu.fe.up.pt)
3. João de Jesus Costa (up201806560@edu.fe.up.pt)
4. João Lucas Silva Martins (up201806436@edu.fe.up.pt)

## How to run

These are the instructions on how to run our project, taking in account they are
executed from the `build` folder, so **start by changing your working
directory**.

```shell
mkdir build && cd build
```

Important: This project was developed and tested for Java 15.

### Compile

To compile the project code, execute the following command.

```shell
make -C ../
```

### Start a peer

To start a peer's execution, execute the following command.

```shell
java Peer <peer_id> <address> <port>
```

e.g.:

```shell
java Peer 1 localhost 8001
```

### Test the application

To test the application, execute the following command.

```shell
java TestApp <access_point> <BACKUP|RESTORE|DELETE|RECLAIM|STATE|JOIN> [operand_1 [operand_2]]
```

e.g.:

```shell
java TestApp peer1 JOIN
```

Note that the access point of each peer is the string "peer" prepended to its
ID. Alternatively, each peer's has a local command loop, so you can send
commands like `join`, e.g.:

```shell
<BACKUP|RESTORE|DELETE|RECLAIM|STATE|JOIN|ST> [operand_1]
```

These commands are mostly used for debugging and testing.  
**Note:** The `st` command was added to obtain the Chord's state information.

## Peer storage

The storage file structure for each peer is in its running directory (usually
the build directory). The root of the file storage of each peer is identified
using its ID, e.g.: for the peer with ID 1, the file storage root is peer-1.

The target 'cleanpeers' of the Makefile can be used to clear the storage of all
peers, e.g.: `make -C ../ cleanpeers`.

Each peer stores its internal state and replicated file chunks in these
structures (one for each peer).

## SSL keys

The keys used by the SSLEngine for communication are stored in the keys
directory.

## Test files

The `test_files` directory contains the files used during the development and
testing of the application.

## Project structure

- 📂 [build](build) - Compiled project files.
- 📂 [doc](doc) - Relevant document files.
- 📂 [keys](keys) - SSL client and server keys.
- 📂 [src](src) - Source code for the project.
  - 📂 [chord](src/chord) - Chord protocol implementation.
  - 📂 [file](src/file) - Storage handling operations.
  - 📂 [message](src/message) - Control messages exchanged between Chord nodes.
  - 📂 [sender](src/sender) - Message handling.
  - 📂 [state](src/state) - State information of a peer.
  - 📂 [utils](src/utils) - Miscellaneous utilities.
  - 📄 [Peer](src/Peer.java) - Class that represents a peer.
  - 📄 [TestApp](src/TestApp.java) - Class to test the application.
