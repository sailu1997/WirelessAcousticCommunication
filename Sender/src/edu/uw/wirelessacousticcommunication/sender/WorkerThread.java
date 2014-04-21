package edu.uw.wirelessacousticcommunication.sender;

import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.AsyncTask;
import android.util.Log;

public class WorkerThread extends AsyncTask<String, Void, Void> {
	
	//we don't know destination so just broadcast
	private final String destIP = "255.255.255.255";
		
	private BitSet message;// = new BitSet();
	private final int duration = 1; // seconds
	private final int bitRate = 300;
    private final int sampleRate = 44100;
    private final int numSamples = duration * sampleRate;
    private final double sample[] = new double[numSamples];
    private double freqOfTone = 12001; // hz
    private final byte generatedSnd[] = new byte[2 * numSamples];
    
    //temp header buffer
    private byte[] headerBuf = new byte[12]; 
    
    
    /*
	 * convertData(String message) ** 
	 * convert to byte array ** 
	 * create header and calculate CRC
	 * put all in a big byte array and then in a bitset
	 * add preamble
	 */
	
	@SuppressLint("NewApi")
	private BitSet convertData(String msg) {
		
		//get IP, if not available -> 0.0.0.0
		String srcIP = "1.1.1.1";//Utils.getIPAddress(true);
		
		//get bytes of message
		byte[] data = msg.getBytes();
		
		//src string to byte array
		String[] srcStr = srcIP.split("\\.");
		byte[] srcBytes = new byte[5];
		for (int i = 0; i < srcStr.length; i++) {
			srcBytes[i] = (byte)Integer.parseInt(srcStr[i]);
		}		
		
		//dest string to byte array
		String[] destStr = destIP.split("\\.");
		byte[] destBytes = new byte[5];
		for (int i = 0; i < srcStr.length; i++) {
			destBytes[i] = (byte)(int)Integer.parseInt(destStr[i]);
		}
		
		//Length of payload to byte array
		int len = data.length;
		byte[] length = ByteBuffer.allocate(4).putInt(len).array(); 
		
		//create 12 byte header
		byte[] header = new byte[12];
		
		for (int i = 0; i < srcBytes.length; i++) {
			header[i] = srcBytes[i];
		}
		for (int j = 0; j < destBytes.length; j++) {
			header[j+4] = destBytes[j];
		}
		for (int k = 0; k < length.length; k++) {
			header[k+8] = length[k];
		}
		
		String heaString = byteToString(length);
		System.out.println("len length: "+heaString.length()+" bytes:"+heaString.length()/8+" bits:"+heaString);
		

		//create payload: header + data + CRC
		//actually we just add the preamble in front of it
		//preamble 1111'1111 1111'1111 -> so in byte rep.: 255 255
		int prelen = 2;
		byte[] payload = new byte[prelen+header.length+data.length+8];
		
		payload[0] = (byte) 0xff;
		payload[1] = (byte) 0xff;
		
		String preaString = byteToString(payload);
		System.out.println("preamble length: "+preaString.length()+" bytes:"+preaString.length()/8+" bits:"+preaString);
		

		for (int i = 0; i < header.length; i++) {
			payload[i+prelen] = header[i];
		}
		for (int i = 0; i < data.length; i++) {
			payload[i+header.length+prelen] = data[i];
		}
		
		String headString = byteToString(header);
		System.out.println("header length: "+headString.length()+" bytes:"+headString.length()/8+" bits:"+headString);
		
		// checksum with the specified array of bytes
		Checksum checksum = new CRC32();
		checksum.update(payload, 0, payload.length-8);
		byte[] check = ByteBuffer.allocate(8).putLong(checksum.getValue()).array();
		
		for (int i = 0; i < check.length; i++) {
			payload[i+payload.length-8] = check[i];
		}
		
		String checkString = byteToString(check);
		System.out.println("check length: "+checkString.length()+" bytes:"+checkString.length()/8+" bits:"+checkString);
		
		//this is our "bitarray" of the packet
		BitSet bitstring = BitSet.valueOf(payload);
		
		return bitstring;
	}

	public String byteToString(byte[] b){
		
		String s = "";
		
		for(int j=0; j<b.length; j++){
			
			s = s+String.format("%8s", Integer.toBinaryString(b[j] & 0xFF)).replace(' ', '0');
		}
		
		return s;
	}


	@SuppressLint("NewApi")
	@Override
	protected Void doInBackground(String... params) {
		
		//get message
		String msg = params[0];
		
		//get frequency
		int freq = Integer.parseInt(params[1]);
		
		//debug
		freqOfTone = freq;
		
		//get bits per symbol
		int bps = Integer.parseInt(params[2]);
			
		//convert message to data packet
		BitSet packet = convertData(msg);
		
		
		//modulate
		//modulate(bits, carrier signal, bitspersymbol)
		Log.v("WORKER","Working!!!!!!!!!!!!!!!");
		genTone();
		//playSound();
		
		return null;
	}
	
	public void genCarrierSamples(){
        // fill out the array
        for (int i = 0; i < numSamples; ++i) {
            sample[i] = Math.sin(2 * Math.PI * i * (freqOfTone/sampleRate));
        }
    }
	
	public void genWave(){
		// convert to 16 bit pcm sound array
        // assumes the sample buffer is normalised.
		int idx = 0;
        for (final double dVal : sample) {
            // scale to maximum amplitude
            final short val = (short) ((dVal * 32767));
            // in 16 bit wav PCM, first byte is the low order byte
            generatedSnd[idx++] = (byte) (val & 0x00ff);
            generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);
        }
	}
	
	public void Modulate(BitSet bits, byte[] carrierWave, int bitsPerSymbol){
		int samplesPerSymbol=(int) Math.ceil(sampleRate/freqOfTone);
		Integer[] amp=calcAmp(bits,samplesPerSymbol,bitsPerSymbol);
		for (int i = 0; i < numSamples; ++i) {
            sample[i] = sample[i]*amp[i];
        }
	}
	public Integer[] calcAmp(BitSet bits, int sps, int bps){
		Integer[] amp=new Integer[bits.size()];
		for(int i=0;i<bits.size();i=i+bps){
			String bitStr="";
			for(int j=0;j<bps;j++){
				bitStr=bitStr+bits.get(i+j);
			}
			amp[i]=Integer.parseInt(bitStr, 2);
		}
		return amp;
	}
	
	public void genTone(){
        // fill out the array
        for (int i = 0; i < numSamples; ++i) {
            sample[i] = Math.sin(2 * Math.PI * i * (freqOfTone/sampleRate));
        }
        double msgSample[] = new double[numSamples];
        
        for (int i=0; i < numSamples; ++i){
        	msgSample[i] = sampleRate/bitRate;
        }
        // convert to 16 bit pcm sound array
        // assumes the sample buffer is normalised.
        int idx = 0;
        for (final double dVal : sample) {
            // scale to maximum amplitude
            final short val = (short) ((dVal * 32767));
            // in 16 bit wav PCM, first byte is the low order byte
            generatedSnd[idx++] = (byte) (val & 0x00ff);
            generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);

        }
    }
	
	public void playSound(){
        final AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                sampleRate, AudioFormat.CHANNEL_CONFIGURATION_MONO,
                AudioFormat.ENCODING_PCM_16BIT, generatedSnd.length,
                AudioTrack.MODE_STATIC);
        audioTrack.write(generatedSnd, 0, generatedSnd.length);
        audioTrack.play();
    }

}