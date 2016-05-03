#!/bin/sh
#sudo usermod -a -G dialout uzu($USER) 
cd ~/jMAVSim_Ubuntu/out/production
java -jar jmavsim_run.jar -serial /dev/ttyACM0 230400
