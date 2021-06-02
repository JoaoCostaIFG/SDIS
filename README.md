# Distributed Backup Service for the Internet

Second Project for group T5G21.

## Group members

1. Ana Inês Oliveira de Barros (up201806593@edu.fe.up.pt)
2. Eduardo da Costa Correia (up201806433@edu.fe.up.pt)
3. João de Jesus Costa (up201806560@edu.fe.up.pt)
4. João Lucas Silva Martins (up201806436@edu.fe.up.pt)

## How to run

These are the instructions on how to run our project, taking in account they are executed from the `build` folder, so
start by changing directory.

```shell
cd build
```

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

### Test the application

To test the application, execute the following command.

```shell
java TestApp <access_point> <BACKUP|RESTORE|DELETE|RECLAIM|STATE|JOIN> [operand_1 [operand_2]]
```

Alternatively, each peer's has a permanent while loop waiting to receive commands.

```shell
<BACKUP|RESTORE|DELETE|RECLAIM|STATE|JOIN|ST> [operand_1]
```

**Note:** Additionally, an `st` command was added to check the Chord's state.

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
