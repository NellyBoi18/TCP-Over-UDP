public class TCPend {
    public static void main(String[] args) {
        int mtu = -1; 
        int senderPort = -1; 
        int remotePort = -1; 
        String remoteIP = ""; 
        int sws = -1; 
        String fileName = ""; 
        boolean sender = false;

        // Parse command line arguments
        if (args.length == 12) { // Sender
            sender = true;
            senderPort = Integer.parseInt(args[1]);
            remoteIP = args[3];
            remotePort = Integer.parseInt(args[5]);
            fileName = args[7];
            mtu = Integer.parseInt(args[9]);
            sws = Integer.parseInt(args[11]);
        } else if (args.length == 8) { // Receiver
            sender = false;
            senderPort = Integer.parseInt(args[1]);
            mtu = Integer.parseInt(args[3]);
            sws = Integer.parseInt(args[5]);
            fileName = args[7];
        }
        else {
            throw new IllegalArgumentException("Invalid mode or incorrect number of arguments specified.\n" +
                    "Sender Usage: java TCPend -p <port> -s <remote IP> -a <remote port> -f <file name> -m <mtu> -c <sws>\n" +
                    "Receiver Usage: java TCPend -p <port> -m <mtu> -c <sws> -f <file name>");
        }

        if (sender) {
			Sender.run(senderPort, remotePort, remoteIP, fileName, mtu, sws);
        } else {
			Receiver.run(senderPort, fileName, mtu, sws);
        }
    }
}
