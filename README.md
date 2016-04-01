## jMAVSim ##

#### Simple multirotor simulator with MAVLink protocol support

[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/DrTon/jMAVSim?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

![Flying with AutoQuad and jMAVSim, April 1, 2016](https://github.com/mpaperno/jMAVSim/wiki/img/jMAVSim_apr_1_ground.jpg)


### Installation ###

Note: check the new [Wiki](../../wiki/) for more information and [Releases](../../releases/) for (possible) pre-built binaries.

Requirements:
 * Java 7 or newer (JDK, http://www.oracle.com/technetwork/java/javase/downloads/index.html)
 * [Apache Ant](http://ant.apache.org/) for building using the included build.xml file.  
     Eg. on OS X with Homebrew: `brew install ant`

Clone repository and initialize submodules:
```
git clone https://github.com/mpaperno/jMAVSim.git
git submodule update --init
```

Create a standalone runnable JAR file with all libraries included, copy supporting resources, and use a shorter command to execute:

```
ant create_run_jar copy_res
cd out/production
java -jar jmavsim_run.jar [any jMAVSim options]
```

To create a complete package ready for distribution, build the `distro` target (this will create `out/production/jMAVSim-distrib.zip`):

```
ant distro
```

To delete everything in the build folder `ant clean-all`.

#### Alternate build / run / distribute

Compile:
```
ant
```

Run:
```
java -cp lib/*:out/production/jmavsim.jar me.drton.jmavsim.Simulator
```

Some shells (e.g. tcsh) will try to expand `*`, so use `\*` instead:
```
java -cp lib/\*:out/production/jmavsim.jar me.drton.jmavsim.Simulator
```

On **Windows** use `;` instead of `:` in -cp:
```
java -cp lib/*;out/production/jmavsim.jar me.drton.jmavsim.Simulator
```


### Troubleshooting ###

#### Java 3D

jMAVSim uses java3d library for visualization.
It was discontinued for long time, but now maintained again and uses JOGL backend.
All necessary jars with java classes and native binaries (Linux/Mac OS/Windows) included in this repo, no need to install java3d manually.
But need to make sure that java doesn't use any other deprecated version of java3d.
For more info related to java3d see this article: https://gouessej.wordpress.com/2012/08/01/java-3d-est-de-retour-java-3d-is-back/

On **Mac OS** java may use deprecated version of java3d as extension, if you get following error:
```
JavaVM WARNING: JAWT_GetAWT must be called after loading a JVM
AWT not found
Exception in thread "main" java.lang.NoClassDefFoundError: apple/awt/CGraphicsDevice
	at javax.media.j3d.GraphicsConfigTemplate3D.<clinit>(GraphicsConfigTemplate3D.java:55)
...
```

Then add `-Djava.ext.dirs=` option to command line when starting:
```
java -Djava.ext.dirs= -cp lib/*:out/production/jmavsim.jar me.drton.jmavsim.Simulator
```

#### Serial port

Serial port access is common problem. Make sure to pass the correct port on the command line:
```
java -jar jmavsim_run.jar -serial /dev/tty.usbmodem1 230400
```
(Baudrate for USB ACM ports (that PX4 uses) has no effect, you can use any value.)
(For AutoQuad USB port, always use baud rate of 2400.)

Usually port is:
```
Mac OS: /dev/tty.usbmodem1
Linux: /dev/ttyACM0
Windows: COM15 (varies)
```

On **Linux** you may also get `Permission denied` error, add your user to `dialout` group and relogin, or just run as root.

#### UDP

UDP port used to connect ground station, e.g. qgroundcontrol.
jMAVSim in this case works as bridge between ground station and autopilot (behavior can be configured of course).
Make sure that jMAVSim and ground station use the same ports.
In qgroundcontrol (or another GCS) you also need to add target host in UDP port configuration (localhost:14555), so both ends will know to which port they should send UDP packets.

### Development ###

The simulator configuration is hardcoded in file `src/me/drton/jmavsim/Simulator.java`. Critical settings like port names or IP addresses can be provided as commandline arguments.

New vehicle types (e.g. non standard multirotors configurations) can be added very easily.
(But for fixed wing you will need some more aerodynamics knowledge).
See files under `src/me/drton/jmavsim/vehicle/` as examples.

The camera can be placed on any point, including gimabal, that can be controlled by autopilot, see `CameraGimbal2D` class and usage example in Simulator.java.  Gimbal is visually represented and can be enabled by setting at least one of the control channels to > -1 (see DEFAULT_CAM_PITCH_CHAN and DEFAULT_CAM_ROLL_CHAN in Simulator.java).

Sensors data can be replayed from real flight log, use `LogPlayerSensors` calss for this.

Custom vehicle visual models in .obj format can be used, edit this line:
```
AbstractMulticopter vehicle = new Quadcopter(world, "models/3dr_arducopter_quad_x.obj", "x", 0.33 / 2, 4.0, 0.05, 0.005, gc);
```

Custom MAVLink protocols can be used, no any recompilation needed, just specify XML file instead of `custom.xml` here:
```
MAVLinkSchema schema = new MAVLinkSchema("mavlink/message_definitions/common.xml");
```

It's convinient to start simulator from IDE. Free and powerful "IntelliJ IDEA" IDE recommended, project files for it are already included, just open project file `jMAVSim.ipr` and right-click -> Run `Simulator`.
