package client;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;



public class Client {

    public static int rSeqNum;
    public static String serverIP;
    public static int serverPort;
    private static int mss;
    private static int numberOfPackets;
    public static LinkedList<byte[]> data = new LinkedList<>();
    public static Timer timer = new Timer();
    public static int windowSize;
    public static int currSeqNum = 0;
    public static DatagramSocket listenSocket;
    public static DatagramSocket sendToServerSocket;
    public static Retransmit rt = new Retransmit();
    private static int leftBytes = 0;
    public static long startTime = 0;
    public static long endTime = 0;
    public static long fileSize;
    public static int win=0;

    public static int getMss() {
        return mss;
    }

    public static int getNumberOfPackets() {
        return numberOfPackets;
    }

    public static int getLeftBytes() {
        return leftBytes;
    }

    public static void setMss(int mss) {
        Client.mss = mss;
    }

    public static void setNumberOfPackets(int numberOfPackets) {
        Client.numberOfPackets = numberOfPackets;
    }

    public static void setLeftBytes(int leftBytes) {
        Client.leftBytes = leftBytes;
    }
    
    public static void setWindowSize(int windowSize) {
        Client.windowSize = windowSize;
    }

    public static int getWindowSize() {
        return windowSize;
    }
	
    
    	/**
	 *  
	 * @param buffer
	 * @return checksum 
	 */
	public static long computeCRC(byte[] buffer) {

		int length = buffer.length;
                int i = 0;
		long sum = 0, data1;

		while (length > 1) {
			data1 = (((buffer[i] << 8) & 0xFF00) | ((buffer[i + 1]) & 0xFF));
			sum += data1;
			
			if ((sum & 0xFFFF0000) > 0) {
				sum = sum & 0xFFFF;
				sum += 1;
			}
			i += 2;
			length -= 2;
		}

		if (length > 0) {
			sum += (buffer[i] << 8 & 0xFF00);
			
			if ((sum & 0xFFFF0000) > 0) {
				sum = sum & 0xFFFF;
				sum += 1;
			}
		}

		sum = ~sum;
		sum = sum & 0xFFFF;
		return sum;
	}
	

	/**
	 * File transmission happens in this method
	 * 
         * @param fileName
	 * @throws SocketException
	 * @throws FileNotFoundException
	 * @throws IOException
	 * @throws UnknownHostException
	 */
	public static void transmit(String fileName) throws SocketException, FileNotFoundException, IOException, UnknownHostException {
		
	//	System.out.println("Client IP Address - "+listenSocket.getLocalSocketAddress());
//		System.out.println("Client port - "+listenSocket.getLocalPort());
		sendToServerSocket = new DatagramSocket();
		File f = new File(fileName);
                int i;
		byte[] bytesToRead;
                
                //Listening for Server Acknowledgement
                listenSocket = new DatagramSocket(5678);
		Recieve rec = new Recieve();
		rec.start();
                
                // Adding data to the linked list from file
                try (FileInputStream fs = new FileInputStream(f)) {
                    fileSize = f.length();

                    // Total number of packets and read them to a Linked list
                    setNumberOfPackets((int) fileSize / getMss());
                    //System.out.println("Number of Packets::: " + getNumberOfPackets());
                    for (i = 0; i < getNumberOfPackets(); i++) {
                        bytesToRead = new byte[getMss()];
                        fs.read(bytesToRead, 0, getMss());
                        data.add(bytesToRead);
                    }

                    //Last Packet Bytes Read into the Linked List
                    setLeftBytes((int) fileSize % getMss());

                    if (i == getNumberOfPackets()) {
                        bytesToRead = new byte[getLeftBytes()];
                            fs.read(bytesToRead, 0, getLeftBytes());
                            data.add(bytesToRead);
                    }
                    fs.close();
                }
                
                // Sending MSS value to Server
                
                ByteArrayOutputStream byte_out_stream = new ByteArrayOutputStream();
		DataOutputStream data_out_stream = new DataOutputStream(byte_out_stream);
		data_out_stream.writeInt(mss);
		
		byte[] mssByte = byte_out_stream.toByteArray();
                DatagramPacket d = new DatagramPacket(mssByte, mssByte.length, InetAddress.getByName(serverIP), serverPort);
		sendToServerSocket.send(d);
                
                //Sending the Number of Total Packets Value to Server
                
                byte_out_stream = new ByteArrayOutputStream();
		data_out_stream = new DataOutputStream(byte_out_stream);
		data_out_stream.writeInt(getNumberOfPackets());
		
		byte[] numberOfPacketsBytes = byte_out_stream.toByteArray();
		DatagramPacket d1 = new DatagramPacket(numberOfPacketsBytes, numberOfPacketsBytes.length, InetAddress.getByName(serverIP), serverPort);
		sendToServerSocket.send(d1);
                
                 //Sending the Number of Last Packet Byte Value to Server
                byte_out_stream = new ByteArrayOutputStream();
		data_out_stream = new DataOutputStream(byte_out_stream);
		data_out_stream.writeInt(getLeftBytes());
                
                byte[] numberOfBytesLeft = byte_out_stream.toByteArray();
        	DatagramPacket d2 = new DatagramPacket(numberOfBytesLeft, numberOfBytesLeft.length, InetAddress.getByName(serverIP), serverPort);
		sendToServerSocket.send(d2);
		
                // Start a local Timer to count the RTT
		startTime = System.currentTimeMillis();
                
		for (i = 0; i < getWindowSize(); i++) {
                    bytesToRead = new byte[getMss()];
                    System.arraycopy(data.get(i), 0, bytesToRead, 0, getMss());
                    rdt_send(bytesToRead, getMss(), i);                     
		}
		timer.schedule(rt, 3000);
	}
        
        
        	/**
	 * This method sends the transmission message to the server
	 * 
	 * @param buffer
         * @param byteSize
	 * @param seqNum
	 * @throws IOException
	 */
	public static void rdt_send(byte[] buffer, int byteSize, int seqNum) throws IOException {
	
		byte[] sendSeqNum = new byte[4];
                byte[] temp;
                int headerSize =8;
                int i, j, k;
                // Creating the Client Header
                temp = new BigInteger(Integer.toString(seqNum), 10).toByteArray();
              //  System.out.println("Sequence number to send:: " +seqNum);
		byte[] client_field = new BigInteger("0101010101010101", 2).toByteArray();
                
                int checksum = (int) computeCRC(buffer);
					byte[] checksumData = new byte[2];
					byte[] tempChecksum = new BigInteger(Integer.toString(checksum), 10).toByteArray();

					if (tempChecksum.length < 2) {
						Integer padBits = 2 - tempChecksum.length;

						for (i = 0; i < padBits; i++) {
							checksumData[i] = 0;
						}
						
						for (j = padBits, k = 0; j < 2 && k < tempChecksum.length; j++, k++) {
							checksumData[j] = tempChecksum[k];
						}
					} else {
						checksumData = new BigInteger(Integer.toString(checksum), 10).toByteArray();
					}
                
                //Padding the Sequence Number
                System.out.println("Length of Sequece Byte:: " + temp.length);
		if (temp.length < 4) {
			for (i = 0; i < 4 - temp.length; i++) {
				sendSeqNum[i] = 0;
			}
			for (j = 4 - temp.length, k=0; j < 4; j++) {
                         
                            sendSeqNum[j] = temp[k];
                            k++;
			}
		}
                
                //Building the Final Packet
		byte[] finalPacketToSend = new byte[headerSize + byteSize];
                
		for (i = 0; i < 4; i++) {
			finalPacketToSend[i] = sendSeqNum[i];
		}
                byte[] sn = new byte[getMss()];
                    System.arraycopy(finalPacketToSend, 0, sn, 0, 4);
                int s = java.nio.ByteBuffer.wrap(sn).getInt();
              //  System.out.println("Seq Num sent: " + s);
                
		for (i = 4,k=0; i < 6; i++) {
                    
                    finalPacketToSend[i] = checksumData[k];
                    k++;
		}
                
		for (i = 6,k=0;i < 8; i++) {
                    
                    finalPacketToSend[i] = client_field[k];
                    k++;
		}
                
		for (i = 8, k = 0; i < finalPacketToSend.length && k < buffer.length; i++, k++) {
			finalPacketToSend[i] = buffer[k];
		}
                
                //Sending Data packet with Header to Server                      
		DatagramPacket serverPacket = new DatagramPacket(finalPacketToSend, finalPacketToSend.length, InetAddress.getByName(serverIP), serverPort);
		sendToServerSocket.send(serverPacket);
	}
	
        
        
	
	public static class Recieve extends Thread {
                @Override
		public void run() {
			while (true) {
				byte[] rBuf = new byte[8];
                                //Initializing a Recieving packet into a Buffer of length 8 bytes
				DatagramPacket rPacket = new DatagramPacket(rBuf, rBuf.length);
                            try {
                                listenSocket.receive(rPacket);
                            } catch (IOException ex) {
                                Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
                            }
                     //Copying the Recieved Sequence Number to the rbuf
                            byte[] Seqnum= new byte[4];
                            System.arraycopy(rBuf, 0, Seqnum, 0, 4);

                            rSeqNum = java.nio.ByteBuffer.wrap(Seqnum).getInt();
                     //   System.out.println("Ack Recieved FOr:" + rSeqNum + "Current Seq Number::" + currSeqNum); 
                            //Check for the Last packet acknowledgement
                            if (rSeqNum == getNumberOfPackets()) {
				rt.cancel();
				timer.cancel();
				endTime = System.currentTimeMillis();	
				System.out.println("Total RTT: "+ (endTime - startTime)/1000+" seconds");
				// break out of the while loop now	
				break;
                            }
                            
                            //Check For all other Packet
                                if(rSeqNum < currSeqNum){
                                    rSeqNum = currSeqNum;
                                }
                                else if (rSeqNum >= currSeqNum) {
                                synchronized (timer) {
                                    rt.cancel();
                                    timer.cancel();
                                     timer.purge();
                                }
                                currSeqNum = rSeqNum;
                              
                              
                                byte[] bytesLeftBuf=new byte[getLeftBytes()];
                                byte[] sendBuf = new byte[getMss()];
                                try{
                                if(currSeqNum == (getNumberOfPackets()-1)){
                                    bytesLeftBuf = data.get(getNumberOfPackets());
                                    rdt_send(bytesLeftBuf, getLeftBytes(),(rSeqNum + 1)); 
                                }
                                else{
                                    sendBuf = data.get(rSeqNum+1);
                                    rdt_send(sendBuf, getMss(), rSeqNum+1);
                                }
                                Timer t = new Timer();
				rt = new Retransmit();
				t.schedule(rt, 3000);
                                }catch(IOException e){
                                   System.out.println("Exception");
                               }
                               if(currSeqNum==rSeqNum)
				currSeqNum++;
				}
                            
			}
			System.out.println("File has been successfully transmitted...");
			System.exit(0);
		}
	}
        
       
        
	public static class Retransmit extends TimerTask {

		@Override
		public void run() {
			rt.cancel();
			timer.cancel();
                  
			byte[] sendBuf;
			System.out.println("Timeout, Sequence Number: "+ (rSeqNum));
			
			for (int i = rSeqNum; i < (rSeqNum + getWindowSize()) && i <= getNumberOfPackets(); i++) {
                            sendBuf = data.get(i);
                           
                            try {
                                rdt_send(sendBuf, getMss(), i);
                            } catch (IOException ex) {
                                Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
                            }
				
			}
                        
			Timer timer1 = new Timer();
			rt = new Retransmit();
			timer1.schedule(rt, 4000);
		}
	}
        
        public static void main(String args[]) throws IOException {
                serverIP = args[1];
               // serverIP = "127.0.0.1";
		serverPort = Integer.parseInt(args[2]);
		//serverPort = 7735;
		String fileName = args[3];
               // String fileName = "hello.txt";
		windowSize = Integer.parseInt(args[4]);
                //windowSize = 64;
		setMss(Integer.parseInt(args[5]));
                //setMss(500);

                transmit(fileName);
	}
	
}

