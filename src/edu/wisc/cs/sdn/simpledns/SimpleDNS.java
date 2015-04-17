package edu.wisc.cs.sdn.simpledns;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;

import edu.wisc.cs.sdn.simpledns.packet.DNSRdata;
import edu.wisc.cs.sdn.simpledns.packet.DNSRdataAddress;

/**
 * A simple dns server that listens for DNS queries in port 8053
 * and recursively resolves it.
 * 
 * @author ashwin, haseeb
 *
 */
public class SimpleDNS 
{
	private final static int dnsPort = 8053;
	private static DatagramSocket dnsSocket;
	private static InetAddress selfAddress;
	private static InetAddress rootServerAddress;
	private static DatagramPacket queryPacket;
	private static ByteBuffer bb;
	
	private static byte[] convertToInetAddress(String address){
		int i,j;
		byte add = 0;

		byte[] inetAddress =  new byte[4];
		j = 0;

		for(i=0;i<address.length();i++){
			add = 0;
			for(j=i;j<address.length() && address.charAt(j)!='.';j++){
				add = (byte)(add*10 + address.charAt(j) - '0');
			}
			inetAddress[j++] = add;
		}
		return inetAddress;
	}

	public static void main(String[] args)
	{
		if(args.length != 4 || !args[0].equals("-r") || !args[0].equals("-e")){
			System.out.println("\nIncorrect format\nExpected: " +
					"java edu.wisc.cs.sdn.simpledns.SimpleDNS -r <root server ip> -e <ec2 csv>");
			System.exit(0);
		}
		
		byte[] receiveData = new byte[4096];
		
		try{
			
			dnsSocket = new DatagramSocket(dnsPort);
			selfAddress = InetAddress.getByName("localhost");
			rootServerAddress = InetAddress.getByAddress(convertToInetAddress(args[1]));
			queryPacket = new DatagramPacket(receiveData, receiveData.length);
			
			dnsSocket.receive(queryPacket);
			bb = ByteBuffer.allocate(queryPacket.getLength());
			bb.put(queryPacket.getData());
			
			DNSRdata dnsRequest = DNSRdataAddress.deserialize(bb, (short)queryPacket.getLength());
			
			//Now process this request
			//Construct a new DNS Query and recursively resolve
			
			
		}
		catch(Exception e){
			System.out.println("Exception: "+e);
		}
	}
}
