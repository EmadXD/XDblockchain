package proxy.blockchain.emadxd;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;

import net.freehaven.tor.control.EventHandler;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import proxy.blockchain.emadxd.blockchain_sources.DefaultEventBroadcaster;
import proxy.blockchain.emadxd.blockchain_sources.EventBroadcaster;
import proxy.blockchain.emadxd.blockchain_sources.OnionProxyManager;
import proxy.blockchain.emadxd.blockchain_sources.TorConfig;
import proxy.blockchain.emadxd.blockchain_sources.TorConfigBuilder;
import proxy.blockchain.emadxd.blockchain_sources.TorSettings;
import proxy.blockchain.emadxd.blockchain_sources.android.AndroidDefaultTorSettings;
import proxy.blockchain.emadxd.blockchain_sources.android.AndroidOnionProxyManager;
import proxy.blockchain.emadxd.blockchain_sources.android.AndroidOnionProxyManagerEventHandler;
import proxy.blockchain.emadxd.blockchain_sources.android.AndroidTorInstaller;

import static android.telephony.AvailableNetworkInfo.PRIORITY_HIGH;

public class XDservice extends Service {
    private static final int ID_SERVICE = 101;
    private OnionProxyManager BlockChain;

    private BlockChainStarter blockChainStarter;
    private BlockChainStopper blockChainStopper;

    //--------------------
    Runnable runnable_connecting_checker;
    Thread thread_connecting_checker;

    //---
    SharedPreferences pref;
    SharedPreferences.Editor editor;

    //----
    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        pref = getSharedPreferences("prefXD", 0);
        editor = pref.edit();

        runnable_connecting_checker = new Runnable() {
            @Override
            public void run() {
                while (BlockChain != null) {
                    try {
                        Thread.sleep(3000);
                        if (!is_running()) {
                            stopForegroundService();
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        thread_connecting_checker = new Thread(runnable_connecting_checker);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (!thread_connecting_checker.isInterrupted()) {
            try {
                thread_connecting_checker.interrupt();
            } catch (Exception e) {

            }
        }
        // -------------------------
        try {
            if (blockChainStarter != null)
                blockChainStarter.cancel(true);
            if (blockChainStopper != null)
                blockChainStopper.cancel(true);
            blockChainStopper = new BlockChainStopper();
            blockChainStopper.execute();
        } catch (Exception e) {
            //----
        }
        // -------------------------
        stopForegroundService();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // TODO Auto-generated method stub
    }

    @SuppressLint("CommitPrefEdits")
    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        // if user starts the service
        if (Constants.ACTION.START_ACTION.equals(Objects.requireNonNull(intent.getAction()))) {
            startForeground(ID_SERVICE, getMyActivityNotification("TeleProxy is Connecting ..."));
            // -------------------------
            if (blockChainStarter != null)
                blockChainStarter.cancel(true);
            blockChainStarter = new BlockChainStarter();
            blockChainStarter.execute();
            // -------------------------
        } else if (Constants.ACTION.STOP_ACTION.equals(Objects.requireNonNull(intent.getAction()))) {
            if (!thread_connecting_checker.isInterrupted()) {
                try {
                    thread_connecting_checker.interrupt();
                } catch (Exception e) {

                }
            }
            // -------------------------
            if (blockChainStarter != null)
                blockChainStarter.cancel(true);
            if (blockChainStopper != null)
                blockChainStopper.cancel(true);
            blockChainStopper = new BlockChainStopper();
            blockChainStopper.execute();
            // -------------------------
        } else {
            stopForeground(true);
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    private void stopForegroundService() {

        // Stop foreground service and remove the notification.
        stopForeground(true);

        // Stop the foreground service.
        stopSelf();
    }

    //----------------------------------------------------------------------------------------------
    public class BlockChainStarter extends AsyncTask<Void, Void, AsyncTaskResult<Integer>> {
        @Override
        protected AsyncTaskResult<Integer> doInBackground(Void... params) {
            if (BlockChain == null) {

                updateNotification("Tele Proxy is Connecting ....");

                editor.putString("ConnectCondition", "connecting").apply();
                //------
                String fileStorageLocation = "blockchainfiles";

                TorConfig torConfig = TorConfig.createDefault(new File(getCacheDir(), fileStorageLocation));

                AndroidTorInstaller torInstaller = new AndroidTorInstaller(XDservice.this, torConfig);
                TorSettings torSettings = new AndroidDefaultTorSettings(XDservice.this);
                EventBroadcaster eventBroadcaster = new DefaultEventBroadcaster();
                EventHandler eventHandler = new AndroidOnionProxyManagerEventHandler();
                BlockChain = new AndroidOnionProxyManager(XDservice.this, torConfig, torInstaller, torSettings, eventBroadcaster, eventHandler);

                try {
                    BlockChain.setup();

                    torConfig.setTorExecutableFile(torInstaller.getTorFile());
                } catch (Exception e) {
                    e.printStackTrace();
                    BlockChain = null;
                    Log.e("------>", "Failed to setup");
                    return null;
                }

                try {
                    final TorConfigBuilder builder = BlockChain.getContext().newConfigBuilder().updateTorConfig();
                    builder.useBridges();
                    builder.bridgeCustom("obfs4 78.47.219.204:30758 6798C378495108AC1C28C8CA2A923DC7D621493D cert=QUke6Gm+m4ZFshOPHayaF48aikTbiROkLVsuezyoWAzZA4LWr8ri3L/8oWTTWxIFShriKQ iat-mode=0");
                    //-----
                    File nativeDir = new File(XDservice.this.getApplicationInfo().nativeLibraryDir);
                    File pluggableTransport = new File(nativeDir, "lib_emadxd_bridge.so");
                    if (!pluggableTransport.canExecute()) pluggableTransport.setExecutable(true);
                    //-------------------------------
                    builder.configurePluggableTransportsFromSettings(pluggableTransport);
                    //----------------------------------
                    BlockChain.getContext().getInstaller().updateTorConfigCustom(builder.asString());
                } catch (Exception e) {
                    e.printStackTrace();
                    BlockChain = null;
                    Log.e("------>", "Failed to update Config");
                    return null;
                }
            } else {
                return null;
            }
            int totalSecondsPerStartup = (int) TimeUnit.MINUTES.toSeconds(1);
            int totalTriesPerStartup = 1;
            try {
                boolean ok = BlockChain.startWithRepeat(totalSecondsPerStartup, totalTriesPerStartup, false);
                if (!ok) {
                    return new AsyncTaskResult<>(new Exception("Can't start Tor onion proxy. Try again."));
                }

                int awaitCounter = 0;
                while (!BlockChain.isRunning() && awaitCounter <= 300) {
                    Thread.sleep(200);
                    awaitCounter++;
                }
                if (BlockChain.isRunning()) {
                    return new AsyncTaskResult<>(BlockChain.getIPv4LocalHostSocksPort());
                }
                return new AsyncTaskResult<>(new Exception("BlockChain is not running after start"));
            } catch (Exception e) {
                return new AsyncTaskResult<>(e);
            }
        }

        @Override
        protected void onPostExecute(AsyncTaskResult<Integer> asyncTaskResult) {
            super.onPostExecute(asyncTaskResult);
            //------
            if (asyncTaskResult == null) {
                return;
            }
            Integer port = asyncTaskResult.getResult();
            if (port != null) {
                Log.e("------>", port.toString());
                editor.putString("ConnectCondition", "connect").apply();

                send_broadcast("connect");
                updateNotification("Tele Proxy is Connected");
                if (!thread_connecting_checker.isAlive()) {
                    try {
                        thread_connecting_checker.start();
                    } catch (Exception e) {
                        thread_connecting_checker = new Thread(runnable_connecting_checker);
                        thread_connecting_checker.start();
                    }
                }
                return;
            }
            //------
            Exception error = asyncTaskResult.getError();
            if (error != null) {
                Log.e("------>", error.getMessage());
                return;
            }
            Log.e("------>", "Something strange");
        }
    }

    //----------------------------------------------------------------------------------------------
    public class BlockChainStopper extends AsyncTask<Void, Void, AsyncTaskResult<Integer>> {
        @Override
        protected AsyncTaskResult<Integer> doInBackground(Void... params) {
            if (BlockChain == null) {
                return null;
            }
            try {
                BlockChain.stop();
                //BlockChain.killTorProcess();
                BlockChain = null;
                editor.putString("ConnectCondition", "disconnecting").apply();
                send_broadcast("disconnecting");
                return new AsyncTaskResult<>(1);
            } catch (Exception e) {
                return new AsyncTaskResult<>(e);
            }
        }

        @Override
        protected void onPostExecute(AsyncTaskResult<Integer> asyncTaskResult) {
            super.onPostExecute(asyncTaskResult);
            //------
            if (asyncTaskResult == null) {
                return;
            }
            Integer result = asyncTaskResult.getResult();
            if (result != null) {
                Log.e("------>", result.toString());
                editor.putString("ConnectCondition", "disconnect").apply();
                send_broadcast("disconnect");
                stopForegroundService();
                return;
            }
            //------
            Exception error = asyncTaskResult.getError();
            if (error != null) {
                Log.e("------>", error.getMessage());
                return;
            }
            Log.e("------>", "Something strange");
        }
    }

    //----------------------------------------------------------------------------------------------
    private void send_broadcast(String data) {
        Intent intent = new Intent();
        intent.putExtra("data", data);
        intent.setAction("infinitec.telegramproxy.vpn.MainActivity.CUSTOM_INTENT");
        sendBroadcast(intent);
    }

    //----------------------------------------------------------------------------------------------
    public boolean is_running() {
        if (BlockChain == null || !BlockChain.isRunning()) {
            return false;
        } else {
            return true;
        }
    }

    //----------------------------------------------------------------------------------------------
    @RequiresApi(Build.VERSION_CODES.O)
    private String createNotificationChannel(NotificationManager notificationManager) {
        String channelId = "channel-01";
        String channelName = "Channel Name";
        NotificationChannel channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH);
        // omitted the LED color
        channel.setImportance(NotificationManager.IMPORTANCE_NONE);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        notificationManager.createNotificationChannel(channel);
        return channelId;
    }

    private Notification getMyActivityNotification(String text) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? createNotificationChannel(notificationManager) : "";
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, channelId);
        //---
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(getApplicationContext().getPackageName());
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(getApplicationContext());
        assert launchIntent != null;
        stackBuilder.addNextIntent(launchIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(
                0,
                PendingIntent.FLAG_UPDATE_CURRENT
        );
        notificationBuilder.setContentIntent(resultPendingIntent);
        //-----
        Notification notification = notificationBuilder.setOngoing(true)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(text)
                .setPriority(PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();


        //((((((((((((((((((((

        return notification;
/*        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(
                    new NotificationChannel("tele_proxy", "Tele Proxy", NotificationManager.IMPORTANCE_HIGH));
        }
        // The PendingIntent to launch our activity if the user selects
        // this notification
        Class test = null;
        try {
            test = Class.forName("infinitec.telegramproxy.vpn.MainActivity");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        PendingIntent contentIntent = PendingIntent.getActivity(this,
                0, new Intent(this, test), 0);

        return new NotificationCompat.Builder(this, "my_channel_01")
                .setContentTitle(text)
                .setOnlyAlertOnce(true) // so when data is updated don't make sound and alert in android 8.0+
                .setOngoing(true)
                .setSmallIcon(R.drawable.icon)
                .setContentIntent(contentIntent)
                .build();*/
    }

    private void updateNotification(String text) {
        Notification notification = getMyActivityNotification(text);
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(ID_SERVICE, notification);
    }

    //----------------------------------------------------------------------------------------------
}
