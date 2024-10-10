import java.io.File;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.io.*;
import java.net.InetAddress;
import java.util.concurrent.*;
import java.util.*;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.FileOutputStream;

public class Receiver {
    private static int mtu;
    private static final int HEADER_SIZE = 24;

    private static int limit;
    private static int remotePort;
    private static int SequenceNumber = 0;

    private static ConcurrentLinkedQueue<Packet> window;
    private static DatagramSocket sckt;
    private static InetAddress addr;

    private static boolean isRunning = true;
    private static AtomicInteger OBpacks = new AtomicInteger();
    private static AtomicInteger corruptPacks = new AtomicInteger();
    private static AtomicInteger ExpectedNext = new AtomicInteger();

    static private int getsws() {
        return window.size();
    }

    private static void writeFile(byte[] bytes, String fileName) {
        File file = new File(fileName);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(bytes);
        } catch (IOException e) {
            System.err.println("Failed to write to file: " + e.getMessage());
        }
    }

    private static void sendPacket(Packet pack) {
        try {
            byte[] Bs = pack.serialize();
            sckt.send(new DatagramPacket(Bs, Bs.length, addr, remotePort));
        } catch (IOException e) {
            System.err.println("Error sending UDP packet: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void run(int port, String fileName, int mtu, int sws) {
        try {
            window = new ConcurrentLinkedQueue<Packet>();
            sckt = new DatagramSocket(port);
            Receiver.mtu = mtu;
            limit = sws;
            byte[] b2 = new byte[mtu];
            // Receive SYN
            DatagramPacket uniquePacket = new DatagramPacket(b2, b2.length);
            sckt.receive(uniquePacket); 
            remotePort = uniquePacket.getPort();
			addr = uniquePacket.getAddress();
            Packet packSyn = new Packet(b2);
            // Send SYN-ACK
            sendPacket(new Packet(SequenceNumber, packSyn.getSequenceNumber() + 1, packSyn.getTimestamp(), false));
            SequenceNumber++;
            new Thread(listen).start();
            ArrayList<Byte> list = new ArrayList<Byte>();

            while (isRunning) {
                // Find the next packet in window
                for (Packet p : window) {
                    if (p.getSequenceNumber() - 1 == ExpectedNext.get()) { 
                        byte[] data = p.getData();
                        for (byte b : data) {
                            list.add(b);
                        }
                        window.remove(p);
                        ExpectedNext.addAndGet(data.length);
                        break;
                    }
                }
            }
            // Receive FIN
			b2 = new byte[mtu];
            sckt.receive(new DatagramPacket(b2, b2.length)); 
            sckt.close();
            byte[] list2 = new byte[list.size()];
            for (int i = 0; i < list.size(); i++) {
                list2[i] = list.get(i);
            }
            writeFile(list2, fileName);
            System.out.println("\n--------------------- Statistics --------------------");
            System.out.println("Out of Sequence Packets Discarded             |  " + OBpacks.get() + "  |");
            System.out.println("Packets Discarded Due to Incorrect Checksum   |  " + corruptPacks.get() + "  |");
            System.out.println("--------------------- Statistics --------------------");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Runnable listen = new Runnable() {
        public synchronized void run() {
            try {
                while (isRunning) {
                    byte[] room = new byte[mtu];
                    DatagramPacket pckt = new DatagramPacket(room, room.length);
                    sckt.receive(pckt);
                    addr = pckt.getAddress();
                    remotePort = pckt.getPort();
                    Packet received = new Packet(room);
                    if (received.getSequenceNumber() < 0) {
                        continue;
                    }
                    if (received.isFin()) {
                        // Send ACK
                        sendPacket(new Packet(SequenceNumber, received.getSequenceNumber() + 1, received.getTimestamp(), false));
                        // Send FIN
                        sendPacket(new Packet(SequenceNumber, -1, received.getTimestamp(), true));
                        isRunning = false;
                        break;
                    }
                    // Check if the window is full
                    if (getsws() < limit) {
                        int firstByte = received.getSequenceNumber() - 1 - received.getLength();
                        // Check if the packet is out of order
                        if (firstByte > (mtu - HEADER_SIZE) * (limit - 1) + ExpectedNext.get()) {
                            // Send ACK
                            sendPacket(new Packet(SequenceNumber, ExpectedNext.get() + 1, received.getTimestamp(), false));
                            OBpacks.incrementAndGet();
                            continue;
                        }
                        // Check if the checksum is correct
                        if (!checkCheckSum(received)) {
                            // Send ACK
                            sendPacket(new Packet(-1, ExpectedNext.get() + 1, received.getTimestamp(), false));
                            corruptPacks.incrementAndGet();
                            continue;
                        }
                        window.add(received);
                        sendPacket(new Packet(SequenceNumber, received.getSequenceNumber() + received.getLength(), received.getTimestamp(), false));
                    } else { // Window is full
                        sendPacket(new Packet(SequenceNumber, ExpectedNext.get() + 1, received.getTimestamp(), false));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    private static boolean checkCheckSum(Packet pack) {
        int check1 = pack.getCheckSum();
        int check2 = pack.calculateChecksum();
        return check1 == check2;
    }
}
