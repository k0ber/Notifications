package com.academmedia.notifylib;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;
import java.util.Random;

public class NotifyService extends Service{

    public int onStartCommand(final Intent intent, int flags, int startId) {
        new Runnable() {
            @Override
            public void run() {
                sendNotification(intent);
            }
        }.run();
        stopSelf();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }



    private void sendNotification(Intent intent) {
        // get notification from intent
        Bundle bundleWithNotification = intent.getBundleExtra("BUNDLE_NOTIFICATION");
        Notification notification = bundleWithNotification.getParcelable("NOTIFICATION_IN_BUNDLE");


        if (notification == null) {
            Log.e("NotifyLib", "Cannot find notification");
            return;
        }

        // get random message from array (it's should be identical in meaning messages)
        Random random = new Random();
        Message message = notification.messages[random.nextInt(notification.messages.length)];

        // get class for explicit intent
        String launchActivityClass = intent.getStringExtra("LAUNCH_ACTIVITY");
        try {
            Class activityClass = Class.forName(launchActivityClass);

            Intent onClickIntent = new Intent(this, activityClass);
            onClickIntent.putExtra("NID", notification.nid);
            onClickIntent.putExtra("MID", message.mid);
            onClickIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);

            // int ID = (int)System.currentTimeMillis();
            PendingIntent onClickPendingIntent = PendingIntent.getActivity(this, 0, onClickIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            // Settings params for notification
            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this)
                    .setSmallIcon(intent.getIntExtra("ICON_ID", 0))
                    .setContentTitle(intent.getStringExtra("TITLE"))
                    .setContentText(message.text)
                    .setContentIntent(onClickPendingIntent)
                    .setAutoCancel(true);

            // Get an instance of the NotificationManager service
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

            // issues notification with notification manager.
            int notificationId = intent.getStringExtra("TITLE").hashCode(); // чтобы уведомления с разных приложений не заменяли друг друга
            notificationManager.notify(notificationId, notificationBuilder.build());

            Log.d("NotifyLib", "Notification has been pushed");

            /** ====  save id for next notification  ==== */
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
            int nextId = preferences.getInt("NEXT_ID", -1);
            int nidsSize = preferences.getInt("NIDS_SIZE", 0);
            boolean isCyclic = preferences.getBoolean("IS_CYCLIC", false);

            if(++nextId > 0) {
                //if (nextId > nidsSize - 1) nextId = isCyclic ? 0 : -1;
                if(nextId > nidsSize -1) {
                    if(isCyclic) {
                        nextId = 0;
                        NotificationsLib.setAlarms(this);
                    }
                    else {
                        nextId = -1;
                    }
                }
            } else {
                Log.e("NotifyLib", "NextId not found in preferences");
            }

            preferences.edit().putInt("NEXT_ID", nextId).apply();

        } catch (ClassNotFoundException e) {
            Log.e("NotifyLib", "Cannot find activity class for notification");
        }

    }
}