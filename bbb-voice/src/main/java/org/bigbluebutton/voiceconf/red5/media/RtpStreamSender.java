/*
 * BigBlueButton - http://www.bigbluebutton.org
 * 
 * Copyright (c) 2008-2009 by respective authors (see below). All rights reserved.
 * 
 * BigBlueButton is free software; you can redistribute it and/or modify it under the 
 * terms of the GNU Lesser General Public License as published by the Free Software 
 * Foundation; either version 3 of the License, or (at your option) any later 
 * version. 
 * 
 * BigBlueButton is distributed in the hope that it will be useful, but WITHOUT ANY 
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License along 
 * with BigBlueButton; if not, If not, see <http://www.gnu.org/licenses/>.
 *
 * $Id: $
 */
package org.bigbluebutton.voiceconf.red5.media;

import local.net.RtpPacket;
import local.net.RtpSocket;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.slf4j.Logger;
import org.bigbluebutton.voiceconf.red5.media.transcoder.Transcoder;
import org.bigbluebutton.voiceconf.sip.SipConnectInfo;
import org.bigbluebutton.voiceconf.util.StackTraceUtil;
import org.red5.logging.Red5LoggerFactory;

public class RtpStreamSender {
    private static Logger log = Red5LoggerFactory.getLogger(RtpStreamSender.class, "sip");
	
    private static final int RTP_HEADER_SIZE = 12;
    private RtpSocket rtpSocket = null;
    private byte[] transcodedAudioDataBuffer;
    private RtpPacket rtpPacket;
    private int sequenceNum = 0;
    private long timestamp = 0;
    private final Transcoder transcoder;
    private final DatagramSocket srcSocket;
    private final SipConnectInfo connInfo;
    
    public RtpStreamSender(Transcoder transcoder, DatagramSocket srcSocket, SipConnectInfo connInfo)  {
        this.transcoder = transcoder;        
        this.srcSocket = srcSocket;
        this.connInfo = connInfo;
    }

    public void connect() throws StreamException {
    	try {
			rtpSocket = new RtpSocket(srcSocket, InetAddress.getByName(connInfo.getRemoteAddr()), connInfo.getRemotePort());
			init();
		} catch (UnknownHostException e) {
			log.error("Failed to connect to {}", connInfo.getRemoteAddr());
			log.error(StackTraceUtil.getStackTrace(e));
			throw new StreamException("Rtp sender failed to connect to " + connInfo.getRemoteAddr() + ".");
		}    	
    }

    private void init() {
        transcodedAudioDataBuffer = new byte[transcoder.getOutgoingEncodedFrameSize() + RTP_HEADER_SIZE];
        rtpPacket = new RtpPacket(transcodedAudioDataBuffer, 0);
        rtpPacket.setPayloadType(transcoder.getCodecId());
        sequenceNum = 0;
        timestamp = 0;    	
    }
    
    public void send(byte[] audioData, int offset, int num) {
    	transcoder.transcode(audioData, offset, num, transcodedAudioDataBuffer, RTP_HEADER_SIZE, this);
    }
    
    public void sendTranscodedData() throws StreamException {
        rtpPacket.setSequenceNumber(sequenceNum++);
        timestamp += transcoder.getOutgoingEncodedFrameSize();        
        rtpPacket.setTimestamp(timestamp);
        rtpPacket.setPayloadLength(transcoder.getOutgoingEncodedFrameSize());
        rtpSocketSend(rtpPacket);    	
    }
    
    private synchronized void rtpSocketSend(RtpPacket rtpPacket) throws StreamException  {
    	try {
			rtpSocket.send(rtpPacket);
			timestamp += rtpPacket.getPayloadLength();
		} catch (IOException e) {
			log.error("Exception while trying to send rtp packet");
			log.error(StackTraceUtil.getStackTrace(e));
			throw new StreamException("Failed to send data to server.");
		}         
    }
}