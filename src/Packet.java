public class Packet {
    private static final int HEADER_SIZE = 24;
    private int sequenceNumber; 
    private int acknowledgmentNumber; 
    private long timestamp; 
    private int length; 
    private boolean syn;
    private boolean ack;
    private boolean fin;
    private int checksum; 
    private byte[] data;
    private int flags;

    // Constructor for the packet with data
    public Packet(int seqNum, int ack, long timestamp, byte[] data) {
        sequenceNumber = seqNum;
        acknowledgmentNumber = ack;
        this.timestamp = timestamp;
        this.syn = seqNum == 0;
        this.ack = ack >= 0;
        this.fin = false;  
    
        if (data != null) {
            this.length = data.length;
            this.data = new byte[this.length];
            System.arraycopy(data, 0, this.data, 0, this.length);
        } else {
            this.length = 0;
            this.data = new byte[0];  
        }
        calculateChecksum();
    }

    // Constructor for the packet with fin flag
    public Packet(int seqNum, int ack, long timestamp, boolean fin) {
        this.syn = seqNum == 0;
        this.ack = ack >= 0;
        this.fin = fin;
        sequenceNumber = seqNum;
        acknowledgmentNumber = ack;
        this.timestamp = timestamp;
    }

    //no wonder it wasn't working, hopefully fix
    public Packet(byte[] rawData) {
        this.deserialize(rawData);
    }
    //ack
    public boolean isSyn() {return syn;}
    public boolean isAck() {return ack;}
    public boolean isFin() {return fin;}
    //gets
    public int getSequenceNumber() { return sequenceNumber; }
    public int getAckNum() {return acknowledgmentNumber;}
    public long getTimestamp() {return timestamp;}
	public void setTimestamp(long x) {timestamp = x;calculateChecksum();}
    public int getLength() {return length;}
    public int getCheckSum() {return checksum;}
    public byte[] getData() {return data;}


    private byte[] deconstruct(int num) {
        byte[] arr = new byte[4];
        int i = 0;
        for (int j = 24; j >= 0; j -= 8) {
            arr[i++] = (byte)(num >> j);
        }
        return arr;
    }

    private byte[] deconstructLong(long x) {
        byte[] bytes = new byte[8];
        for (int i = 0; i < 8; i++) {
            bytes[7 - i] = (byte) (x >> (i * 8));
        }
        return bytes;
    }
    //big brain
    private int CalcLenFlag() {
        int flags = (syn ? 4 : 0) | (ack ? 2 : 0) | (fin ? 1 : 0);
        return (length << 3) | flags;
    }

    public byte[] serialize() {
        byte[] currdata = new byte[HEADER_SIZE + length];
        int i = 0;
        for (byte b : deconstruct(sequenceNumber)) {
            currdata[i++] = b;
        }

        for (byte b : deconstruct(acknowledgmentNumber)) {
            currdata[i++] = b;
        }

        for (byte b : deconstructLong(timestamp)) {
            currdata[i++] = b;
        }

        for (byte b : deconstruct(CalcLenFlag())) {
            currdata[i++] = b;
        }

        for (byte b : deconstruct(checksum)) {
            currdata[i++] = b;
        }

		if (data == null) {
			return currdata;
		}

        for (byte b : data) {
            currdata[i++] = b;
        }

        return currdata;
    }

    private void extractFlags(int compressed) {
        length = compressed >> 3; 
        syn = (compressed & 4) > 0; // Extract SYN flag
        ack = (compressed & 2) > 0; // Extract ACK flag
        fin = (compressed & 1) > 0; // Extract FIN flag
    }

    public void deserialize(byte[] bytes) {
        int index = 0;
    
        sequenceNumber = ((bytes[index++] & 0xFF) << 24) |
                         ((bytes[index++] & 0xFF) << 16) |
                         ((bytes[index++] & 0xFF) << 8)  |
                         ((bytes[index++] & 0xFF));
    
        acknowledgmentNumber = ((bytes[index++] & 0xFF) << 24) |
                               ((bytes[index++] & 0xFF) << 16) |
                               ((bytes[index++] & 0xFF) << 8)  |
                               ((bytes[index++] & 0xFF));
    
        timestamp = ((long)(bytes[index++] & 0xFF) << 56) |
                    ((long)(bytes[index++] & 0xFF) << 48) |
                    ((long)(bytes[index++] & 0xFF) << 40) |
                    ((long)(bytes[index++] & 0xFF) << 32) |
                    ((long)(bytes[index++] & 0xFF) << 24) |
                    ((long)(bytes[index++] & 0xFF) << 16) |
                    ((long)(bytes[index++] & 0xFF) << 8)  |
                    ((long)(bytes[index++] & 0xFF));
    
        int compressed = ((bytes[index++] & 0xFF) << 24) |
                         ((bytes[index++] & 0xFF) << 16) |
                         ((bytes[index++] & 0xFF) << 8)  |
                         ((bytes[index++] & 0xFF));
    
        extractFlags(compressed);
    
        checksum = ((bytes[index++] & 0xFF) << 24) |
                   ((bytes[index++] & 0xFF) << 16) |
                   ((bytes[index++] & 0xFF) << 8)  |
                   ((bytes[index++] & 0xFF));
    
        if (length > 0) {
            data = new byte[length];
            for (int i = 0; i < length; i++) {
                data[i] = bytes[index++];
            }
        }
    }
    
    
    private long usByte(byte b) {
        return b & 0xFF;
    }

    public int calculateChecksum() {
        checksum = 0;
        byte[] raw = this.serialize();
        long sum = 0;
    
        for (int i = 0; i < raw.length - 1; i += 2) {
            long high = usByte(raw[i]) << 8;
            long low = usByte(raw[i + 1]);
            sum += high + low;
    
            while ((sum >> 16) != 0) {
                sum = (sum & 0xFFFF) + (sum >> 16);
            }
        }
    
        if (raw.length % 2 != 0) {
            sum += usByte(raw[raw.length - 1]) << 8;
            while ((sum >> 16) != 0) {
                sum = (sum & 0xFFFF) + (sum >> 16);
            }
        }
    
        checksum = (int) (~sum & 0xFFFF);
        return checksum;
    }


}
