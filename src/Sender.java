import java.io.*;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.concurrent.atomic.*;

public class Sender {
    private static DatagramSocket socket;
    private static InetAddress address;
    private static Packet[] packets;

    private static int mtu;
    private static int sws;
    private static int localPort;
    private static int remotePort;
    private static int curSeqNum = 0;
    private static int base = 0;
    private static int nextSeqNum = 0;
    private static int lastAck = 0;
    private static int lastAckSeq = 0;

    private static double[] timers;
    private static double ertt;
    private static double edev;

    private static AtomicBoolean running = new AtomicBoolean(true);
    private static AtomicInteger lastAckSeqNum = new AtomicInteger();
    private static AtomicInteger ackRepeatCounter = new AtomicInteger();
    private static AtomicInteger windowStart = new AtomicInteger();
    private static AtomicInteger windowEnd = new AtomicInteger();
    private static AtomicInteger bytesSent = new AtomicInteger();
    private static AtomicInteger packetsSent = new AtomicInteger();
    private static AtomicInteger retransmissions = new AtomicInteger();
    private static AtomicInteger duplicateAcks = new AtomicInteger();
    private static AtomicLong timeout = new AtomicLong(5000);
    
    private static final int HEADER_SIZE = 24;
    private static final int MAX_DATA_SIZE = mtu - HEADER_SIZE;
    private static final int MAX_TIMEOUT = 5000;
    private static final int MAX_DUPLICATE_ACKS = 3;

    // Runnable to receive packets
    private static Runnable receive = () -> {
        try {
            while (running.get()) {
                byte[] buffer = new byte[mtu];

                // Receive packet
                socket.receive(new DatagramPacket(buffer, buffer.length));
                packetsSent.getAndIncrement();

                // Parse packet
                Packet receivedPacket = new Packet(buffer);
                print(receivedPacket, false);
                assert(receivedPacket.isAck() == true);

                // Repeat ack
                if (lastAckSeqNum.get() == receivedPacket.getAckNum()) {
                    ackRepeatCounter.getAndIncrement();
                    duplicateAcks.getAndIncrement();
                } else {
                    ackRepeatCounter.set(1);
                }

                lastAckSeqNum.set(receivedPacket.getAckNum());

                if (windowStart.get() == packets.length) {
                    continue;
                }

                // If the ack is in the window, slide the window
                if(packets[windowStart.get()].getSequenceNumber() + packets[windowStart.get()].getLength() == receivedPacket.getAckNum()) {
                    windowStart.getAndIncrement();
                }

                // Update timeout
                Sender.timeout(receivedPacket.getSequenceNumber(), receivedPacket.getTimestamp());
            }
        } catch (Exception e) {e.printStackTrace();}
    };

    // Runnable to retransmit packets
    private static Runnable retransmitter = () -> {
        while (running.get()) {
            // For each packet in the window, check if it needs to be retransmitted
            for (int i = windowStart.get(); i < windowEnd.get(); i++) {
                if (!running.get()) break;

                // If the packet has been sent for more than the timeout, retransmit the packet
                double curTime = System.nanoTime() / 1000000;
                if (curTime - timers[i] > timeout.get()) {
                    packets[i].setTimestamp(System.nanoTime());

                    try {
                        // Retransmit the packet
                        socket.send(new DatagramPacket(packets[i].serialize(), packets[i].serialize().length, address, remotePort));
                        print(packets[i], true);
                    } catch (Exception e) {e.printStackTrace();}

                    timers[i] = System.nanoTime() / 1000000;
                    retransmissions.getAndIncrement();
                    packetsSent.getAndIncrement();
                    bytesSent.addAndGet(packets[i].getLength());
                }

                // If 3 duplicate acks are received, retransmit the packet
                if (ackRepeatCounter.get() == MAX_DUPLICATE_ACKS && packets[i].getSequenceNumber() == lastAckSeqNum.get()) {
                    packets[i].setTimestamp(System.nanoTime());
                    try {
                        socket.send(new DatagramPacket(packets[i].serialize(), packets[i].serialize().length, address, remotePort));
                        print(packets[i], true);
                    } catch (Exception e) {e.printStackTrace();}

                    timers[i] = System.nanoTime() / 1000000;
                    ackRepeatCounter.set(1);
                    retransmissions.getAndIncrement();
                    retransmissions.getAndIncrement();
                    packetsSent.getAndIncrement();
                    bytesSent.addAndGet(packets[i].getLength());
                }
            }
        }
    };

    public static void run(int port, int rPort, String remoteIP, String fileName, int mtu, int sws) {
        try {
            // Initiate connection
            remotePort = rPort;
            address = InetAddress.getByName(remoteIP);
            Sender.mtu = mtu;

            socket = new DatagramSocket(port);
			socket.connect(address, rPort);

            // Send SYN
            Packet udpPacket = new Packet(curSeqNum++, -1, System.nanoTime(), false);
            try {
                socket.send(new DatagramPacket(udpPacket.serialize(), udpPacket.serialize().length, address, remotePort));
                print(udpPacket, true);
            } catch (Exception e) {e.printStackTrace();}

            packetsSent.getAndIncrement();

            // Receive SYN-ACK
            byte[] packBuff = new byte[mtu];
            DatagramPacket packet = new DatagramPacket(packBuff, packBuff.length);
            socket.receive(packet);

            packetsSent.getAndIncrement();

            // Parse the received packet
            Packet receivedPack = new Packet(packBuff);
            print(receivedPack, false);

            // If the received packet is a SYN-ACK packet, send an ACK packet
            if (receivedPack.isSyn() && receivedPack.isAck()) {
                lastAckSeqNum.set(receivedPack.getAckNum());
                ackRepeatCounter.set(1);

                Packet ackPacket = new Packet(-1, receivedPack.getSequenceNumber() + 1, System.nanoTime(), false);
                try {
                    socket.send(new DatagramPacket(ackPacket.serialize(), ackPacket.serialize().length, address, remotePort));
                    print(ackPacket, true);
                } catch (Exception e) {e.printStackTrace();}

                packetsSent.getAndIncrement();

                Sender.timeout(receivedPack.getSequenceNumber(), receivedPack.getTimestamp());
            }

            // Read the file and split it into packets
            File file = new File(fileName);
            FileInputStream fileIn = null;
            byte[] fileBytes = new byte[(int) file.length()];
            try {
                fileIn = new FileInputStream(file);
                fileIn.read(fileBytes);
                fileIn.close();
            } catch (IOException e) {e.printStackTrace();}

            int segmentNumber = fileBytes.length / mtu;

            if (fileBytes.length % mtu != 0) {
                segmentNumber++;
            }

            packets = new Packet[segmentNumber];
            int max = MAX_DATA_SIZE;
            int maxData = mtu - 24; 

            // Split the file into packets
            for (int s = 0, f = 0; s < segmentNumber; s++) {
                int size;
                if (s != segmentNumber - 1) {
                    size = maxData;
                } else {
                    size = fileBytes.length - f;
                }

                byte[] data = new byte[size];
				int tempFileIndex = f + size;
                for (int i = 0; f < tempFileIndex; i++) {
                    data[i] = fileBytes[f++];
                }
                packets[s] = new Packet(curSeqNum, 1, System.nanoTime(), data);

                curSeqNum += size;
            }

			timers = new double[packets.length];

            new Thread(receive).start();
            new Thread(retransmitter).start();

            // While the window is not full, send packets
            while (windowStart.get() < packets.length) {
                // If the window is less than the sws, send packets
                if ((windowEnd.get() + 1) - windowStart.get() < sws) {
                    if (windowEnd.get() < packets.length) {
						packets[windowEnd.get()].setTimestamp(System.nanoTime());

                        try {
                            socket.send(new DatagramPacket(packets[windowEnd.get()].serialize(), packets[windowEnd.get()].serialize().length, address, remotePort));
                            print(packets[windowEnd.get()], true);
                        } catch (Exception e) {e.printStackTrace();}

                        timers[windowEnd.get()] = System.nanoTime() / 1000000;
                        packetsSent.getAndIncrement();
                        bytesSent.addAndGet(packets[windowEnd.get()].getLength());
						windowEnd.getAndIncrement();
                    }
                }
            }

            packetsSent.getAndIncrement();

            // Send FIN
            running.set(false);
            Packet fin = new Packet(curSeqNum, -1, System.nanoTime(), true);

            try {
                socket.send(new DatagramPacket(fin.serialize(), fin.serialize().length, address, remotePort));
                print(fin, true);
            } catch (Exception e) {e.printStackTrace();}

            packetsSent.getAndIncrement();
            packBuff = new byte[mtu];
            socket.receive(new DatagramPacket(packBuff, packBuff.length)); 

            // Receive FIN-ACK
			Packet temp = new Packet(packBuff);
			print(new Packet(packBuff), false);
            // If temp is not a FIN-ACK packet, keep receiving
			if (!temp.isFin()) { 
		        packBuff = new byte[mtu];
		        socket.receive(new DatagramPacket(packBuff, packBuff.length));
			}

            // Send ACK
            Packet ack = new Packet(-1, new Packet(packBuff).getSequenceNumber() + 1, System.nanoTime(), false);
            try {
                socket.send(new DatagramPacket(ack.serialize(), ack.serialize().length, address, remotePort));
                print(ack, true);
            } catch (Exception e) {e.printStackTrace();}

            running.set(false); // Stop the threads
            packetsSent.getAndIncrement();
            socket.close();

            System.out.println("\n-------------- Statistics --------------");
            System.out.println("| Data Transferred (bytes)    |    " + bytesSent.get() + " |");
            System.out.println("| Packets Sent/Received       |     " + packetsSent.get() + " |");
            System.out.println("| Retransmissions             |     " + retransmissions.get() + " |");
            System.out.println("| Duplicate Acknowledgements  |     " + duplicateAcks.get() + " |");
            System.out.println("----------------------------------------");

        } catch (Exception e) {e.printStackTrace();}
        
    }

    // Calculate timeout
    private static synchronized void timeout(int seqNum, long timestamp) {
        if (seqNum != 0) {
            double srtt = (System.nanoTime() / 1000000) - timestamp / 1000000;
            double sdev = Math.abs(srtt - ertt);
            ertt = 0.875 * ertt + (1 - 0.875) * srtt;
            edev = 0.75 * edev + (1 - 0.75) * sdev;
            timeout.set((long) (ertt + 4 * edev));
        } else {
            edev = 0;
            ertt = (System.nanoTime() / 1000000) - timestamp / 1000000;
            timeout.set((long) (2 * ertt));
        }
    }

    private static void print(Packet packet, boolean sending) {
        String sendRcv;
        if (sending) {
            sendRcv = "snd";
        } else {
            sendRcv = "rcv";
        }

        String flags = "";

        if (packet.isSyn()) {
            flags += "S ";
        } else {
            flags += "- ";
        }
        if (packet.isAck()) {
            flags += "A ";
        } else {
            flags += "- ";
        }
        if (packet.isFin()) {
            flags += "F ";
        } else {
            flags += "- ";
        }
        if (packet.getLength() != 0) {
            flags += "D ";
        } else {
            flags += "- ";
        }

        int ackNum = packet.getAckNum();
		int ack;
        if (ackNum == -1) {
            ack = 0;
        } else {
            ack = packet.getAckNum();
        }
        int seqNum = packet.getSequenceNumber();
		int seq;
        if (seqNum == -1) {
            seq = 1;
        } else {
            seq = packet.getSequenceNumber();
        }

        System.out.println(sendRcv + " " + (packet.getTimestamp() / 1000000) + " " + flags + seq + " " + packet.getLength() + " " + ack);
    }

}