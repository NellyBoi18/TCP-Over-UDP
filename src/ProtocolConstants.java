public class ProtocolConstants {
    public static final int MTU = 1500; // Maximum Transmission Unit
    public static final int TIMEOUT_MS = 2000; // Timeout in milliseconds
    public static final int HEADER_SIZE = 24; // Header size in bytes
    public static final int MAX_RETRIES = 3; // Maximum number of retries
    public static final int MAX_PACKET_SIZE = MTU - HEADER_SIZE; // Maximum packet size
}
