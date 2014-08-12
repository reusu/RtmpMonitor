package name.yumao.monitor;
/**
 * Copyright (c) 2006 - 2009 Smaxe Ltd (www.smaxe.com).
 * All rights reserved.
 */

import com.smaxe.io.ByteArray;
import com.smaxe.uv.client.ICamera;
import com.smaxe.uv.client.IMicrophone;
import com.smaxe.uv.client.INetConnection;
import com.smaxe.uv.client.INetStream;
import com.smaxe.uv.client.IVideo;
//import com.smaxe.uv.client.License;
//import com.smaxe.uv.client.NetConnection;
//import com.smaxe.uv.client.NetStream;
import com.weedong.net.rtmp.UltraNetConnection;
import com.weedong.net.rtmp.UltraNetStream;
import com.smaxe.uv.client.video.AbstractVideo;
import com.smaxe.uv.stream.MediaData;
import com.smaxe.uv.stream.support.MediaDataByteArray;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

/**
 * <code>RtmpRetransmitter</code> - retrieves RTMP stream from <code>sourceUrl</code> and
 * publishes it to the <code>destinationUrl</code>.
 * 
 * @author Andrei Sochirca
 */
public final class RtmpRetransmitter extends Object
{
    /**
     * Entry point.
     * 
     * @param args
     * @throws Exception if an exception occurred
     */
    public static void main(final String[] args) throws Exception
    {
		//设置配置文件读取路径
		InputStream inputStream=new BufferedInputStream(new FileInputStream("rtmp.properties"));
		Properties properties = new Properties();
		properties.load(inputStream);
		
		final String inServer = properties.getProperty("Rtmp_InServer");
		final String inKey = properties.getProperty("Rtmp_InKey");
		final String outServer = properties.getProperty("Rtmp_OutServer");
		final String outKey = properties.getProperty("Rtmp_OutKey");
		
        final AudioVideoStream avstream = new AudioVideoStream();
        
        new Thread(new Runnable()
        {
            public void run()
            {
                Player player = new Player();
                
                player.play(inServer, inKey, avstream);
            }
        }).start();
        
        Thread.sleep(1000);
        
        new Thread(new Runnable()
        {
            public void run()
            {
                Publisher publisher = new Publisher();  
                
                publisher.publish(outServer, outKey, avstream, avstream);
            }
        }).start();
    }
    
    
    /**
     * <code>AudioVideoStream</code> - a/v stream.
     */
    private static class AudioVideoStream extends AbstractVideo implements IMicrophone, ICamera
    {
        // fields
        private IMicrophone.IListener microphoneListener = null;
        private ICamera.IListener cameraListener = null;
        
        private byte[] audioConfiguration = null;
        private byte[] videoConfiguration = null;
        
        private boolean active = false;
        
        /**
         * Constructor.
         */
        public AudioVideoStream()
        {
        }
        
        // IVideo implementation
        
        @Override
        public void onAudioData(final MediaData data)
        {
            if (microphoneListener == null)
            {
                final int tag = data.tag();
                
                if (getAudioCodec(tag) == 0x0A) // AAC
                {
                    byte[] id = new byte[2];
                    
                    try
                    {
                        data.read().read(id);
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                    
                    if (id[0] == (byte) 0xAF && id[1] == 0x00) // is config frame?
                    {
                        audioConfiguration = new byte[data.size()];
                        
                        try
                        {
                            data.read().read(audioConfiguration);
                        }
                        catch (IOException e)
                        {
                            e.printStackTrace();
                        }
                    }
                }
            }
            else
            {
                if (active)
                {
                    microphoneListener.onAudioData(data);
                }
            }
        }
        
        @Override
        public void onVideoData(final MediaData data)
        {
            if (cameraListener == null)
            {
                final int tag = data.tag();
                
                if (getVideoCodec(tag) == 0x07) // AVC
                {
                    byte[] id = new byte[2];
                    
                    try
                    {
                        data.read().read(id);
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                    
                    if (id[0] == 0x17 && id[1] == 0x00) // is config frame?
                    {
                        videoConfiguration = new byte[data.size()];
                        
                        try
                        {
                            data.read().read(videoConfiguration);
                        }
                        catch (IOException e)
                        {
                            e.printStackTrace();
                        }
                    }
                }
            }
            else
            {
                if (!active)
                {
                    if (getVideoFrame(data.tag()) != 0x01) // is key frame?
                    {
                        return;
                    }
                    else
                    {
                        active = true;
                    }
                }
                
                cameraListener.onVideoData(data);
            }
        }
        
        @Override
        public void onFlvData(final MediaData data)
        {
            if (cameraListener != null)
            {
                cameraListener.onFlvData(data);
            }
        }
        
        @Override
        public void onCuePoint(final Object data)
        {
            System.out.println("onCuePoint: " + data);
        }
        
        @Override
        public void onMetaData(final Object data)
        {
            System.out.println("onMetaData: " + data);
        }
        
        // IMicrophone implementation
        
        public void addListener(final IMicrophone.IListener listener)
        {
            if (audioConfiguration != null)
            {
                listener.onAudioData(new MediaDataByteArray(0, new ByteArray(audioConfiguration)));
            }
            
            microphoneListener = listener;
        }
        
        public void removeListener(final IMicrophone.IListener listener)
        {
            microphoneListener = null;
        }
        
        // ICamera implementation
        
        public void addListener(final ICamera.IListener listener)
        {
            if (videoConfiguration != null)
            {
                listener.onVideoData(new MediaDataByteArray(0, new ByteArray(videoConfiguration)));
            }
            
            cameraListener = listener;
        }
        
        public void removeListener(final ICamera.IListener listener)
        {
            cameraListener = new ICamera.ListenerAdapter();
        }
        
        // inner use methods
        /**
         * @param tag
         * @return format encoded in the <code>tag</code>
         */
        public int getAudioCodec(final int tag)
        {
            return (tag >> 4) & 0x0F;
        }
        
        /**
         * @param tag
         * @return codec encoded in the <code>tag</code>
         */
        public int getVideoCodec(final int tag)
        {
            return tag & 0x0F;
        }
        
        /**
         * @param tag
         * @return frame encoded in the <code>tag</code>
         */
        public int getVideoFrame(final int tag)
        {
            return (tag >> 4) & 0x0F;
        }
    }
    
    /**
     * <code>Player</code> - player.
     */
    public static final class Player extends Object
    {
        /**
         * <code>NetConnectionListener</code> - {@link NetConnection} listener implementation.
         */
        public class NetConnectionListener extends UltraNetConnection.ListenerAdapter
        {
            /**
             * Constructor.
             */
            public NetConnectionListener()
            {
            }
            
            @Override
            public void onAsyncError(final INetConnection source, final String message, final Exception e)
            {
                System.out.println("Player#NetConnection#onAsyncError: " + message + " " + e);
            }
            
            @Override
            public void onIOError(final INetConnection source, final String message)
            {
                System.out.println("Player#NetConnection#onIOError: " + message);
            }
            
            @Override
            public void onNetStatus(final INetConnection source, final Map<String, Object> info)
            {
                System.out.println("Player#NetConnection#onNetStatus: " + info);
                
                final Object code = info.get("code");
                
                if (UltraNetConnection.CONNECT_SUCCESS.equals(code))
                {
                }
                else
                {
                    disconnected = true;
                }
            }
        }
        
        
        // fields
        private volatile boolean disconnected = false;
        
        /**
         * Constructor.
         */
        public Player()
        {
        }
        
        /**
         * Plays the stream.
         * 
         * @param url 
         * @param streamName 
         * @param video 
         */
        public void play(final String url, final String streamName, final IVideo video)
        {
            final UltraNetConnection connection = new UltraNetConnection();
            
            connection.configuration().put(UltraNetConnection.Configuration.INACTIVITY_TIMEOUT, -1);
            connection.configuration().put(UltraNetConnection.Configuration.IO_TIMEOUT, 20 /*milliseconds*/);
            connection.configuration().put(UltraNetConnection.Configuration.RECEIVE_BUFFER_SIZE, 256 * 1024);
            connection.configuration().put(UltraNetConnection.Configuration.SEND_BUFFER_SIZE, 256 * 1024);
            connection.configuration().put(UltraNetConnection.Configuration.STREAM_BUFFER_SIZE, 4 * 1024 * 1024);
            
            connection.addEventListener(new NetConnectionListener());
            
            connection.connect(url);
            
            // wait till connected
            while (!connection.connected() && !disconnected)
            {
                try
                {
                    Thread.sleep(100);
                }
                catch (Exception e) {/*ignore*/}
            }
            
            if (!disconnected)
            {
            	UltraNetStream stream = new UltraNetStream(connection);
                
                stream.addEventListener(new UltraNetStream.ListenerAdapter()
                {
                    @Override
                    public void onNetStatus(final INetStream source, final Map<String, Object> info)
                    {
                        System.out.println("Player#NetStream#onNetStatus: " + info);
                    }
                });
                
                try
                {
                    stream.play(video, streamName);
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
            
            while (!disconnected)
            {
                try
                {
                    Thread.sleep(100);
                }
                catch (Exception e) {/*ignore*/}
            }
            
            connection.close();
        }
    }
    
    /**
     * <code>Publisher</code> - publisher.
     */
    public static final class Publisher extends Object
    {
        /**
         * <code>NetConnectionListener</code> - {@link NetConnection} listener implementation.
         */
        private final class NetConnectionListener extends UltraNetConnection.ListenerAdapter
        {
            /**
             * Constructor.
             */
            public NetConnectionListener()
            {
            }
            
            @Override
            public void onAsyncError(final INetConnection source, final String message, final Exception e)
            {
                System.out.println("Publisher#NetConnection#onAsyncError: " + message + " " + e);
            }
            
            @Override
            public void onIOError(final INetConnection source, final String message)
            {
                System.out.println("Publisher#NetConnection#onIOError: " + message);
            }
            
            @Override
            public void onNetStatus(final INetConnection source, final Map<String, Object> info)
            {
                System.out.println("Publisher#NetConnection#onNetStatus: " + info);
                
                final Object code = info.get("code");
                
                if (UltraNetConnection.CONNECT_SUCCESS.equals(code))
                {
                }
                else
                {
                    disconnected = true;
                }
            }
        }
        
        
        // fields
        private volatile boolean disconnected = false;
        private UltraNetStream stream = null;
        
        /**
         * Publishes the stream.
         * 
         * @param url
         * @param streamName
         * @param microphone microphone
         * @param camera camera
         */
        public void publish(final String url, final String streamName, final IMicrophone microphone, final ICamera camera)
        {
            final UltraNetConnection connection = new UltraNetConnection();
            
            connection.configuration().put(UltraNetConnection.Configuration.INACTIVITY_TIMEOUT, -1);
            connection.configuration().put(UltraNetConnection.Configuration.IO_TIMEOUT, 20 /*milliseconds*/);
            connection.configuration().put(UltraNetConnection.Configuration.RECEIVE_BUFFER_SIZE, 256 * 1024);
            connection.configuration().put(UltraNetConnection.Configuration.SEND_BUFFER_SIZE, 256 * 1024);
            connection.configuration().put(UltraNetConnection.Configuration.ENABLE_MEDIA_STREAM_ABSOLUTE_TIMESTAMP, true);
            
            connection.addEventListener(new NetConnectionListener());
            
            connection.connect(url);
            
            // wait till connected
            while (!connection.connected() && !disconnected)
            {
                try
                {
                    Thread.sleep(100);
                }
                catch (Exception e) {/*ignore*/}
            }
            
            if (!disconnected)
            {
                final UltraNetStream stream = new UltraNetStream(connection);
                
                stream.addEventListener(new UltraNetStream.ListenerAdapter()
                {
                    @Override
                    public void onNetStatus(final INetStream source, final Map<String, Object> info)
                    {
                        System.out.println("Publisher#NetStream#onNetStatus: " + info);
                        
                        final Object code = info.get("code");
                        
                        if (UltraNetStream.PUBLISH_START.equals(code))
                        {
                            if (microphone != null)
                            {
                                stream.attachAudio(microphone);
                            }
                            
                            if (camera != null)
                            {
                                stream.attachCamera(camera, -1 /*snapshotMilliseconds*/);
                            }
                        }
                    }
                });
                
                stream.publish(streamName, UltraNetStream.LIVE);
                
                this.stream = stream;
            }
            
            while (!disconnected)
            {
                try
                {
                    Thread.sleep(100);
                }
                catch (Exception e) {/*ignore*/}
            }
            
            connection.close();
        }
        
        /**
         * Sends a message on the published stream to all subscribing clients.
         * 
         * @param handler handler name
         * @param args optional arguments that can be of any type
         * @return <code>true</code> if sent; otherwise <code>false</code>
         */
        public boolean sendStreamMessage(final String handler, final Object... args)
        {
            if (stream == null) return false;
            
            stream.send(handler, args);
            
            return true;
        }
    }
}