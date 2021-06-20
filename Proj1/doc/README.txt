# Proj1 SDIS T5G08 - FEUP, 2021

## Group members

- João de Jesus Costa, up201806560@fe.up.pt
- João Lucas Silva Martins, up201806436@fe.up.pt

## Project usage details

### Compiling

In the 'src/' directory, run: '../scripts/compile.sh'

### Running a peer

Note: No setup is required, because the application creates the needed file
structure by itself.

In the 'src/build/' directory, created in the 'Compiling' step described
above, run: '../../scripts/peer.sh' with the desired arguments.

### Testing a peer

#### RMI

The TestApp connects to a peer through RMI. For this to work, an 'rmiregistry'
has to be started inside the 'src/build/' prior to starting both the Peer and
the TestApp. The access point command line argument of the Peer takes the name
it will use to register itself on the 'rmiregistry', e.g.: 'rmithing'.

By default, the Peer will assume that the rmiregistry has been opened on the
default port. If a different port is needed, it can passed as such: 'rmithing:12345'.
This can be used to have multiple 'rmiregistriy' running at the same time.

In the 'src/build/' directory, created in the 'Compiling' step described
above, run: '../../scripts/test.sh' with the desired arguments.

### Cleaning up a peer's file structure

In the 'src/build/' directory, created in the 'Compiling' step described
above, run: '../../scripts/cleanup.sh' with the desired argument#.
