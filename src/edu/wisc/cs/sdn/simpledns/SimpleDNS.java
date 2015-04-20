package edu.wisc.cs.sdn.simpledns;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import edu.wisc.cs.sdn.simpledns.packet.DNS;
import edu.wisc.cs.sdn.simpledns.packet.DNSQuestion;
import edu.wisc.cs.sdn.simpledns.packet.DNSRdataAddress;
import edu.wisc.cs.sdn.simpledns.packet.DNSRdataName;
import edu.wisc.cs.sdn.simpledns.packet.DNSResourceRecord;

/**
 * A simple dns server that listens for DNS queries in port 8053
 * and recursively resolves it.
 * 
 * @authors ashwin, haseeb
 *
 */
public class SimpleDNS 
{
	private final static int dnsPort = 8053;
	private static DatagramSocket dnsSocket;
	private static InetAddress selfAddress;
	private static InetAddress rootServerAddress;
	private static DatagramPacket firstQueryPacket,replyPacket;
	private static byte[] firstQueryData;
	
	private static DatagramPacket nonrecursivelyResolve(DatagramPacket queryPacket,DNS dnsRequestPacket) throws Exception{
		
		DatagramPacket newQueryPacket, receivePacket;
		DNS newDNSQueryPacket;
		
		byte[] receiveData = new byte[4096];
		
		// Create a new DNS Packet with the same question
		newDNSQueryPacket = new DNS();
		
		// Set the question
		newDNSQueryPacket.setQuestions(dnsRequestPacket.getQuestions());
		
		// Set the flags
		setDNSRequestFlags(newDNSQueryPacket);
		
		//Put the DNS Data into the Datagram Packet
		byte []dnsData = newDNSQueryPacket.serialize();
		
		newQueryPacket = new DatagramPacket(dnsData,dnsData.length,rootServerAddress,dnsPort);
		
		dnsSocket.send(newQueryPacket);
		
		receivePacket = new DatagramPacket(receiveData,receiveData.length);
		
		dnsSocket.receive(receivePacket);
		
		return receivePacket;
		
	}

	private static void setDNSRequestFlags(DNS dnsRequestPacket){
		
		// Set the flags
		dnsRequestPacket.setQuery(true);
		dnsRequestPacket.setOpcode((byte)0);
		dnsRequestPacket.setTruncated(false);
		dnsRequestPacket.setRecursionDesired(true);
		dnsRequestPacket.setAuthenicated(false);
	}
	
	private static void setDNSReplyFlags(DNS dnsReplyPacket){
		
		dnsReplyPacket.setQuery(false);
		dnsReplyPacket.setOpcode((byte)0);
		dnsReplyPacket.setAuthoritative(false);
		dnsReplyPacket.setTruncated(false);
		
		dnsReplyPacket.setRecursionAvailable(true);
		dnsReplyPacket.setRecursionDesired(true);
		dnsReplyPacket.setAuthenicated(false);
		dnsReplyPacket.setCheckingDisabled(false);
		
		dnsReplyPacket.setRcode((byte)0);
		
	}
	
	private static DatagramPacket recursivelyResolve(DatagramPacket queryPacket,DNS dnsRequestPacket) 
			throws Exception{
		
		DatagramPacket newQueryPacket = null, receivePacket = null;
		DatagramPacket finalReplyPacket = null;
		DNS dnsQueryPacket,dnsReceivePacket;
		List<DNSResourceRecord> cnameList = new ArrayList<DNSResourceRecord>();
		boolean resolved = false;
		
		byte[] receiveData = new byte[4096];
		
		// Create a new DNS Packet with the same question
		dnsQueryPacket = new DNS();
		
		// Set the question
		dnsQueryPacket.setQuestions(dnsRequestPacket.getQuestions());
		
		// Set the flags
		setDNSRequestFlags(dnsQueryPacket);
		
		// Put the DNS Data into the Datagram Packet
		byte []dnsData = dnsQueryPacket.serialize();
		
		newQueryPacket = new DatagramPacket(dnsData,dnsData.length,rootServerAddress,dnsPort);
		
		dnsSocket.send(newQueryPacket);
		
		while(!resolved){
			
			receivePacket = new DatagramPacket(receiveData,receiveData.length);
			dnsSocket.receive(receivePacket);
			
			// Check if the data received has an answer or should be resolved
			dnsReceivePacket = DNS.deserialize(receivePacket.getData(),receivePacket.getData().length);
			
			if(dnsReceivePacket.getAnswers().size()==0){
				
				// No answers - check the authority section and get the next NS INET Address
				List<DNSResourceRecord> authorityList = dnsReceivePacket.getAuthorities();
				List<DNSResourceRecord> additionalList = dnsReceivePacket.getAdditional();
				InetAddress nextNSAddress = null;
				
				DNSResourceRecord authRR = null, addRR = null;
				boolean matchFound = false;
				
				for(int i=0; i<authorityList.size(); i++){
					
					authRR = authorityList.get(i);
					
					// Find if there is a corresponding IP in the Additional Section
					for(int j=0; j<additionalList.size(); j++){
						addRR = additionalList.get(j);
						
						if(authRR.getType() == DNS.TYPE_NS && authRR.getName() == addRR.getName() && 
								(addRR.getType() == DNS.TYPE_A || addRR.getType() == DNS.TYPE_AAAA)){
							// Match the name server with an IP address
							matchFound = true;
						}
					}
					
					if(matchFound)
						break;
				}
				
				if(matchFound){
					// Get the ip address
					DNSRdataAddress dnsRdata = (DNSRdataAddress)addRR.getData();
					nextNSAddress = dnsRdata.getAddress();
				}
				else{
					// Just return the packet
					return receivePacket;
				}
				
				// Form the new DNS Query
				dnsQueryPacket = new DNS();
				
				// Set the flags
				setDNSRequestFlags(dnsQueryPacket);
				
				// Set the question
				dnsQueryPacket.setQuestions(dnsRequestPacket.getQuestions());
				
				// Form the Datagram Packet and send
				newQueryPacket = new DatagramPacket(dnsData,dnsData.length,nextNSAddress,dnsPort);
				dnsSocket.send(newQueryPacket);
			}
			else{
				// Got answer - check if it is only CNAME
				List<DNSResourceRecord> answerList = dnsReceivePacket.getAnswers();
				DNSResourceRecord answer = null;
				answer = answerList.get(0);
				
				if(answer.getType() == DNS.TYPE_CNAME && answerList.size() == 1){
					
					// Only CNAME is received, resolve again looking at authority section
					cnameList.add(answer);
					
					// Create a new question
					DNSQuestion newQuestion = new DNSQuestion();
					DNSRdataName dnsRdataname = (DNSRdataName)answer.getData();
					newQuestion.setName(dnsRdataname.getName());
					
					// Form the new DNS Query
					dnsQueryPacket = new DNS();
					
					// Set the flags
					setDNSRequestFlags(dnsQueryPacket);
					
					// Set the question
					List<DNSQuestion> newQuestionList = new ArrayList<DNSQuestion>();
					newQuestionList.add(newQuestion);
					dnsQueryPacket.setQuestions(newQuestionList);
					
					// Form the Datagram Packet and send to root server
					newQueryPacket = new DatagramPacket(dnsData,dnsData.length,rootServerAddress,dnsPort);
					dnsSocket.send(newQueryPacket);
					
				}
				else{
					resolved = true;
					
					// Remove everything other than answers
					dnsReceivePacket.setAuthorities(null);
					dnsReceivePacket.setAdditional(null);
					
					// Add any CNAME that was resolved
					for(int i=0; i<cnameList.size(); i++){
						dnsReceivePacket.addAnswer(cnameList.get(i));
					}
					
					// Set it back to original question
					dnsReceivePacket.setQuestions(dnsRequestPacket.getQuestions());
					
					// Set the appropriate flags
					setDNSReplyFlags(dnsReceivePacket);
					
					// Set up the Datagram Packet to be returned
					finalReplyPacket = new DatagramPacket(dnsReceivePacket.serialize(),dnsReceivePacket.serialize().length);
				}
			}
		}
		
		
		return finalReplyPacket;
	}
	
	public static void main(String[] args)
	{
		if(args.length != 4 || !args[0].equals("-r") || !args[0].equals("-e")){
			System.out.println("\nIncorrect format\nExpected: " +
					"java edu.wisc.cs.sdn.simpledns.SimpleDNS -r <root server ip> -e <ec2 csv>");
			System.exit(0);
		}
		
		firstQueryData = new byte[4096];
		
		try{
			
			dnsSocket = new DatagramSocket(dnsPort);
			selfAddress = InetAddress.getByName("localhost");
			rootServerAddress = InetAddress.getByName(args[1]);
			firstQueryPacket = new DatagramPacket(firstQueryData, firstQueryData.length);
			
			dnsSocket.receive(firstQueryPacket);
			
			DNS dnsRequestPacket = DNS.deserialize(firstQueryPacket.getData(), (short)firstQueryPacket.getLength());
			
			// Check for flags
			if(dnsRequestPacket.getOpcode() != (byte)0){
				System.out.println("Opcode is not 0");
			}
			
			// Now process this request
			// Construct a new DNS Query and recursively resolve
			List<DNSQuestion> dnsQuestionList = dnsRequestPacket.getQuestions();
			
			switch(dnsQuestionList.get(0).getType()){
			case DNS.TYPE_A:
				break;
			case DNS.TYPE_AAAA:
				break;
			case DNS.TYPE_CNAME:
				break;
			case DNS.TYPE_NS:
				break;
			default:
				System.out.println("Case not compatible");
				break;
			}
			
			if(dnsRequestPacket.isRecursionDesired()){
				replyPacket = recursivelyResolve(firstQueryPacket, dnsRequestPacket);
			}
			else{
				replyPacket = nonrecursivelyResolve(firstQueryPacket, dnsRequestPacket);
			}
			replyPacket.setPort(dnsPort);
			replyPacket.setAddress(firstQueryPacket.getAddress());
			dnsSocket.send(replyPacket);
		}
		catch(Exception e){
			System.out.println("Exception: "+e);
		}
	}
}
