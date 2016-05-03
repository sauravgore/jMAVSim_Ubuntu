package me.drton.jmavsim;

import me.drton.jmavlib.mavlink.MAVLinkMessage;
import me.drton.jmavlib.mavlink.MAVLinkProtocolException;
import me.drton.jmavlib.mavlink.MAVLinkSchema;
import me.drton.jmavlib.mavlink.MAVLinkUnknownMessage;

import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

/**
 * User: ton Date: 28.11.13 Time: 23:30
 */
public class SerialMAVLinkPort extends MAVLinkPort {
    private MAVLinkSchema schema = null;
    private SerialPort serialPort = null;
    private byte txSeq = 0;
    private ByteBuffer buffer = ByteBuffer.allocate(8192);
    private boolean debug = false;
    boolean rxMtx = false;

    // connection information
    String portName;
    int baudRate;
    int dataBits;
    int stopBits;
    int parity;

    public SerialMAVLinkPort(MAVLinkSchema schema) {
        super(schema);
        this.schema = schema;
        buffer.flip();
    }

    public void setup(String portName, int baudRate, int dataBits, int stopBits, int parity) {
        this.portName = portName;
        this.baudRate = baudRate;
        this.dataBits = dataBits;
        this.stopBits = stopBits;
        this.parity = parity;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public void open() throws IOException {
        serialPort = new SerialPort(portName);
        try {
            serialPort.openPort();
            serialPort.setParams(baudRate, dataBits, stopBits, parity);
            serialPort.addEventListener(new PortEventListener(), SerialPort.MASK_RXCHAR);
        } catch (SerialPortException e) {
            try {
                serialPort.closePort();
            } catch (SerialPortException e2) {
                // ignore
            }
            serialPort = null;
            throw new IOException(e);
        }
    }

    @Override
    public void close() throws IOException {
        if (serialPort == null)
            return;
        
        rxMtx = false;
        try {
            serialPort.closePort();
        } catch (SerialPortException e) {
            throw new IOException(e);
        }
        serialPort = null;
    }

    @Override
    public boolean isOpened() {
        return serialPort != null && serialPort.isOpened();
    }

    @Override
    public void handleMessage(MAVLinkMessage msg) {
        if (!isOpened())
            return;
        
        try {
            ByteBuffer bb = msg.encode(txSeq++);
            byte[] b = new byte[bb.remaining()];
            bb.get(b);
            sendRaw(b);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void update(long t) {
        return;  // no-op
    }

    public boolean sendRaw(byte[] data) throws IOException {
        if (!isOpened())
            return false;
        
        try {
            return serialPort.writeBytes(data);
        } catch (SerialPortException e) {
            throw new IOException(e);
        }
    }
    
    private class PortEventListener implements SerialPortEventListener {
        MAVLinkMessage msg = null;
        
        public void serialEvent(SerialPortEvent spe) {
            if (!isOpened() || rxMtx)
                return;

            if(spe.isRXCHAR() && spe.getEventValue() > 0){
                try {
                    rxMtx = true;
                    while (rxMtx) {
                        try {
                            msg = new MAVLinkMessage(schema, buffer);
                        } catch (MAVLinkUnknownMessage | MAVLinkProtocolException e) {
                            if (debug)
                                System.err.println(e);
                            continue;
                        } catch (BufferUnderflowException e) {
                            buffer.compact();
                            int n = 0;
                            byte[] b = serialPort.readBytes(Math.min(serialPort.getInputBufferBytesCount(), buffer.remaining()));
                            if (b != null) {
                                buffer.put(b);
                                n = b.length;
                            }
                            buffer.flip();
                            if (n == 0)
                                rxMtx = false;
                            
                            continue;
                        }

                        sendMessage(msg);
                    }
                } catch (SerialPortException e) {
                    rxMtx = false;
                    e.printStackTrace();
                    return;
                }
            }
        }
    }
}
