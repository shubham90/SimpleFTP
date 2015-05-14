package server;

import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.DatagramSocket; 
import java.net.InetAddress;
import java.util.Random;

public class Server {
	
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

	public static void listening(int port, String fileName , Double probability) throws IOException{
		
                // output stream to create the file
		FileOutputStream file = new FileOutputStream(fileName, true);
                // create a send and receive socket
		DatagramSocket fromClientSocket = new DatagramSocket(port);
		DatagramSocket toClient = new DatagramSocket();
		
		
		
		// buffers used to read the packet info 
		byte[] mssBufRec = new byte[4];
		byte[] numPktRec = new byte[4];
		byte[] leftBytesBuffer = new byte[4];

		DatagramPacket firstPacket = new DatagramPacket(mssBufRec, mssBufRec.length);
		fromClientSocket.receive(firstPacket);

		byte[] mssBuffer = new byte[4];
		byte[] recPacket = firstPacket.getData();

		if (recPacket.length < 4) {
			for (int i = 0; i < 4 - recPacket.length; i++) {
				mssBuffer[i] = 0;
			}
			for (int i = 4 - recPacket.length, j = 0; i < 4 && j < recPacket.length; i++, j++) {
				mssBuffer[i] = recPacket[j];
			}
		} else {
			mssBuffer = recPacket;
		}
                
                int numberOfPackets = 0, bytesLeft = 0;
                int mss = 0;
                mss = java.nio.ByteBuffer.wrap(mssBuffer).getInt();
                
		DatagramPacket p1 = new DatagramPacket(numPktRec,numPktRec.length);
		fromClientSocket.receive(p1);
		numPktRec = p1.getData();

		DatagramPacket p2 = new DatagramPacket(leftBytesBuffer, leftBytesBuffer.length);
		fromClientSocket.receive(p2);
		leftBytesBuffer = p2.getData();
                numberOfPackets = java.nio.ByteBuffer.wrap(numPktRec).getInt();
		bytesLeft = java.nio.ByteBuffer.wrap(leftBytesBuffer).getInt();
                		
                int expectedSeqNum = 0;

		byte[] dataPacket = new byte[mss + 8];
		DatagramPacket packet = new DatagramPacket(dataPacket, dataPacket.length);
               //Starting the server listener in a forever loop
		while(true) {
			fromClientSocket.receive(packet);
			dataPacket = packet.getData();

			Random rand = new Random();
			double randomNumber = rand.nextDouble();

			byte[] sequenceNumber = new byte[4];
                        System.arraycopy(dataPacket, 0, sequenceNumber, 0, 4);
			int recievedSeqNum = java.nio.ByteBuffer.wrap(sequenceNumber).getInt();
			// packet is accepted and processed
			if (randomNumber > probability) {
                                if(expectedSeqNum>recievedSeqNum){
                                    byte[] data = new byte[dataPacket.length - 8];
					for (int i = 8, j = 0; i < dataPacket.length && j < dataPacket.length - 8; i++) {
						data[j] = dataPacket[i];
                                                j++;
                                             //   System.out.println("Coming Data: " + buf[i] + "Copied Data: " + data[j]);
					}
					
					// computing the checksum for the received data here
                                        int checksum = (int) computeCRC(data);
					byte[] checksumData = new byte[2];
					byte[] tempChecksum = new BigInteger(Integer.toString(checksum), 10).toByteArray();

					if (tempChecksum.length < 2) {
						Integer padBits = 2 - tempChecksum.length;

						for (int i = 0; i < padBits; i++) {
							checksumData[i] = 0;
						}
						
						for (int j = padBits, k = 0; j < 2 && k < tempChecksum.length; j++, k++) {
							checksumData[j] = tempChecksum[k];
						}
					} else {
						checksumData = new BigInteger(Integer.toString(checksum), 10).toByteArray();
					}

					boolean flag = true;
					for (int i = 0; i < 2; i++) {
						if (checksumData[i] != dataPacket[i + 4]) {
                                                    System.out.println("Checksum is invalid");
							flag = false;
							break;
						}
					}

					if (expectedSeqNum == numberOfPackets) {
						flag = true;
					}

					
					if (flag) {
                                            byte[] ack = new byte[8];
                                            System.out.println("I am inside Ack:: " + recievedSeqNum);
                                            ack[0]=0;
                                            ack[1]=0;
                                            ack[2]=0;
                                            ack[3]=0;
                                            for(int i=0;i<4;i++){
                                                ack[i]=dataPacket[i];
                                            }
                                                
            
                                            // Two byes all zeros
						ack[4] = 0;
						ack[5] = 0;
                                            //two bytes special bytes
						byte[] temp = new BigInteger("1010101010101010", 2).toByteArray();
						ack[6] = temp[0];
						ack[7] = temp[1];
					
                                        
                                        
                                        byte[] sequences1 = new byte[4];
                                        System.arraycopy(ack, 0, sequences1, 0, 4);

                                        int sequence_number1 = java.nio.ByteBuffer.wrap(sequences1).getInt();
                                        System.out.println("My ACK SQUENCE NUM::" + sequence_number1);
                                        System.out.println("Actual Sequence Number::" + recievedSeqNum);
                                        
					InetAddress ipAddress = packet.getAddress();
					DatagramPacket packetAck = new DatagramPacket(ack, ack.length, ipAddress, 5678);
					toClient.send(packetAck);
                                }
                                }          
                                        
                                        
                                else if (expectedSeqNum == recievedSeqNum) {
                                        
					byte[] data = new byte[dataPacket.length - 8];
					for (int i = 8, j = 0; i < dataPacket.length && j < dataPacket.length - 8; i++, j++) {
						data[j] = dataPacket[i];
                                             //   System.out.println("Coming Data: " + buf[i] + "Copied Data: " + data[j]);
					}
					
					// computing the checksum for the received data here
                                        int checksum = (int) computeCRC(data);
					byte[] checksumData = new byte[2];
					byte[] tempChecksum = new BigInteger(Integer.toString(checksum), 10).toByteArray();

					if (tempChecksum.length < 2) {
						Integer padBits = 2 - tempChecksum.length;

						for (int i = 0; i < padBits; i++) {
							checksumData[i] = 0;
						}
						
						for (int j = padBits, k = 0; j < 2 && k < tempChecksum.length; j++, k++) {
							checksumData[j] = tempChecksum[k];
						}
					} else {
						checksumData = new BigInteger(Integer.toString(checksum), 10).toByteArray();
					}

					boolean flag=true;
					for (int i = 0; i < 2; i++) {
						if (checksumData[i] != dataPacket[i + 4]) {
                                                    System.out.println("Checksum is invalid");
							flag=false;
							break;
						}
					}

					if (expectedSeqNum == numberOfPackets) {
						flag=true;
					}

					
					if (flag) {
                                            byte[] ack = new byte[8];
                                            System.out.println("I am inside Ack:: " + recievedSeqNum);
                                            ack[0]=0;
                                            ack[1]=0;
                                            ack[2]=0;
                                            ack[3]=0;
                                            System.arraycopy(dataPacket, 0, ack, 0, 4);
                                            // Two bytes all zeros
						ack[4] = 0;
						ack[5] = 0;
                                            //two bytes special bytes
						byte[] temp = new BigInteger("1010101010101010", 2).toByteArray();
						ack[6] = temp[0];
						ack[7] = temp[1];
					
                                        byte[] sequences1 = new byte[4];
                                        System.arraycopy(ack, 0, sequences1, 0, 4);

                                       // int sequence_number1 = java.nio.ByteBuffer.wrap(sequences1).getInt();
                                       // System.out.println("My ACK SQUENCE NUM::" + sequence_number1);
                                       // System.out.println("Actual Sequence Number::" + recievedSeqNum);
                                        
					InetAddress ipAddress = packet.getAddress();
					DatagramPacket packetAck = new DatagramPacket(ack, ack.length, ipAddress, 5678);
					toClient.send(packetAck);

					if (recievedSeqNum == numberOfPackets) {
                                            
						file.write(data, 0, (bytesLeft));
						file.close();
						toClient.close();
						fromClientSocket.close();
						
						// break out of the while loop now
						break;
					} else {
						file.write(data, 0, data.length);
					}
                                        if(recievedSeqNum==expectedSeqNum)
					expectedSeqNum++;
                                        }
				}
			} else {
				System.out.println("Packet loss,  Sequence number = " + recievedSeqNum);
			}
		}
		System.out.println("Server successfully received the file.");
		System.exit(0);
	}

	public static void main(String args[]) throws IOException {
		
                 String fileName = args[2];
                
                 int port =  Integer.parseInt(args[1]);
	
                 double p =  Double.parseDouble(args[3]);

		listening(port , fileName ,p );
	}
}